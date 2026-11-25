#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include "biomes.h"
#include "rng.h"
#include "loot/loot_table_context.h"
#include "loot/loot_table_parser.h"
#include "loot/loot_tables/chests/trial_chambers/reward_ominous_1_21_1.h"
#include "loot/loot_tables/chests/trial_chambers/reward_1_21_1.h"

static uint64_t mix13(uint64_t x)
{
    x = (x ^ (x >> 30)) * 0xBF58476D1CE4E5B9ULL;
    x = (x ^ (x >> 27)) * 0x94D049BB133111EBULL;
    return x ^ (x >> 31);
}

static void vault_seed128(long long worldSeed, int ominous, uint64_t* outLo, uint64_t* outHi)
{
    uint64_t lo = (uint64_t)worldSeed ^ 0x6A09E667F3BCC909ULL;
    uint64_t hi = lo + 0x9E3779B97F4A7C15ULL;
    if (ominous) { lo ^= 0x05a13d5ce5edaab3ULL; hi ^= 0x1a3950a30a86bc23ULL; }
    else         { lo ^= 0x102ea793e31f23ffULL; hi ^= 0xd7c231952cddadf1ULL; }
    *outLo = mix13(lo);
    *outHi = mix13(hi);
}

static void cache_note(int offset, LootTableContext* ctx);
static void cache_save(const char* path, long long seed, int ominous);

static LootTableContext* make_ctx(long long seed, int ominous)
{
    uint64_t lo, hi;
    vault_seed128(seed, ominous, &lo, &hi);
    LootTableContext* ctx = ominous ? init_reward_ominous_1_21_1() : init_reward_1_21_1();
    if (!ctx) return NULL;
    set_loot_prng_type(ctx, XOROSHIRO);
    RandomState st;
    st.xr.lo = lo;
    st.xr.hi = hi;
    set_internal_loot_seed(ctx, st);
    cache_note(0, ctx);
    return ctx;
}

static void print_items(LootTableContext* ctx)
{
    for (int j = 0; j < ctx->generated_item_count; j++) {
        ItemStack* st = &ctx->generated_items[j];
        const char* nm = get_item_name(ctx, st->item);
        printf("%s:%d%s", nm ? nm : "?", st->count, j + 1 < ctx->generated_item_count ? ";" : "");
    }
}

static char g_tokens[256][512];
static char* g_names[256];
static int g_counts[256];
static int g_want;

static void parse_spec(const char* spec)
{
    g_want = 0;
    char buf[8192];
    strncpy(buf, spec, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = 0;
    char* save = NULL;
    for (char* tok = strtok_s(buf, ";", &save); tok; tok = strtok_s(NULL, ";", &save)) {
        if (g_want >= 256) break;
        char* c = strrchr(tok, ':');
        if (!c) continue;
        *c = 0;
        strncpy(g_tokens[g_want], tok, sizeof(g_tokens[0]) - 1);
        g_tokens[g_want][sizeof(g_tokens[0]) - 1] = 0;
        g_names[g_want] = g_tokens[g_want];
        g_counts[g_want] = atoi(c + 1);
        g_want++;
    }
}

static int compare_generated(LootTableContext* ctx, int reverse)
{
    if (g_want != ctx->generated_item_count) return 0;
    for (int i = 0; i < ctx->generated_item_count; i++) {
        int idx = reverse ? (g_want - 1 - i) : i;
        ItemStack* st = &ctx->generated_items[i];
        const char* nm = get_item_name(ctx, st->item);
        if (!nm) return 0;
        if (strcmp(g_names[idx], nm) != 0) return 0;
        if (g_counts[idx] != st->count) return 0;
    }
    return 1;
}

// itemspec: "minecraft:book:1;minecraft:wind_charge:8"，按顺序比对
static int matches_spec_ordered(LootTableContext* ctx, const char* spec, int reverse)
{
    char buf[4096];
    strncpy(buf, spec, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = 0;
    char* names[128];
    int counts[128];
    int want_count = 0;
    char* save = NULL;
    for (char* tok = strtok_s(buf, ";", &save); tok; tok = strtok_s(NULL, ";", &save)) {
        if (want_count >= 128) break;
        char* c = strrchr(tok, ':');
        if (!c) continue;
        *c = 0;
        names[want_count] = tok;
        counts[want_count] = atoi(c + 1);
        want_count++;
    }
    if (want_count != ctx->generated_item_count) return 0;
    for (int i = 0; i < ctx->generated_item_count; i++) {
        int idx = reverse ? (want_count - 1 - i) : i;
        ItemStack* st = &ctx->generated_items[i];
        const char* nm = get_item_name(ctx, st->item);
        if (!nm) return 0;
        if (strcmp(names[idx], nm) != 0) return 0;
        if (counts[idx] != st->count) return 0;
    }
    return 1;
}

static int cmp_int(const void* a, const void* b)
{
    int x = *(const int*)a, y = *(const int*)b;
    return (x > y) - (x < y);
}

/** 候选集合里是否存在 c 满足 1 <= offset - c <= delta（cands 已排序）。 */
static int has_candidate_near(const int* cands, int cand_count, int offset, int delta)
{
    int lo = offset - delta;
    if (lo < 0) lo = 0;
    int a = 0, b = cand_count;
    while (a < b) {
        int m = (a + b) / 2;
        if (cands[m] < lo) a = m + 1; else b = m;
    }
    return a < cand_count && cands[a] < offset;
}


// ===================== 状态缓存 + 多线程扫描 =====================
#include <windows.h>

#define CHUNK 1000000
#define MAX_WORKERS 64

typedef struct { uint64_t lo, hi; } XState;

static XState* g_cache = NULL;
static int g_cache_count = 0;   /* 已记录的 chunk 状态数：state[k] = 第 k*CHUNK 次开箱之后的随机状态 */
static char g_cache_path[1024];

static int* g_results = NULL;
static int g_results_count = 0;
static int g_results_cap = 0;
static int g_scan_mode = 0;     /* 0=matches 1=narrow 2=detect */
static int g_scan_delta = 0;
static int* g_scan_cands = NULL;
static int g_scan_cand_count = 0;
static int g_hit_offset = 0;
static XState g_hit_state;
static CRITICAL_SECTION g_hit_cs;
static CRITICAL_SECTION g_results_cs;

static void results_add(int off)
{
    EnterCriticalSection(&g_results_cs);
    if (g_results_count >= g_results_cap) {
        g_results_cap = g_results_cap ? g_results_cap * 2 : 1024;
        g_results = (int*)realloc(g_results, g_results_cap * sizeof(int));
    }
    g_results[g_results_count++] = off;
    LeaveCriticalSection(&g_results_cs);
}

static void cache_ensure(int count)
{
    if (count <= g_cache_count && g_cache) return;
    g_cache = (XState*)realloc(g_cache, (size_t)count * sizeof(XState));
}

/** 生成第 offset 次开箱之后记录一次状态（offset 为 CHUNK 的倍数）。 */
static void cache_note(int offset, LootTableContext* ctx)
{
    if (offset % CHUNK != 0) return;
    int idx = offset / CHUNK;
    cache_ensure(idx + 1);
    g_cache[idx].lo = ctx->prng_state.xr.lo;
    g_cache[idx].hi = ctx->prng_state.xr.hi;
    if (idx + 1 > g_cache_count) g_cache_count = idx + 1;
}

static void cache_load(const char* path)
{
    FILE* f = fopen(path, "rb");
    if (!f) return;
    char magic[5];
    long long seed;
    int ominous, chunk, count;
    if (fread(magic, 1, 5, f) != 5 || memcmp(magic, "FVLT1", 5) != 0) { fclose(f); return; }
    if (fread(&seed, sizeof(seed), 1, f) != 1) { fclose(f); return; }
    if (fread(&ominous, sizeof(int), 1, f) != 1) { fclose(f); return; }
    if (fread(&chunk, sizeof(int), 1, f) != 1 || chunk != CHUNK) { fclose(f); return; }
    if (fread(&count, sizeof(int), 1, f) != 1 || count <= 0 || count > 100000) { fclose(f); return; }
    cache_ensure(count);
    if (fread(g_cache, sizeof(XState), (size_t)count, f) != (size_t)count) { fclose(f); return; }
    g_cache_count = count;
    fclose(f);
}

static void cache_save(const char* path, long long seed, int ominous)
{
    if (g_cache_count <= 0) return;
    FILE* f = fopen(path, "wb");
    if (!f) return;
    fwrite("FVLT1", 1, 5, f);
    fwrite(&seed, sizeof(seed), 1, f);
    fwrite(&ominous, sizeof(int), 1, f);
    int chunk_size = CHUNK;
    fwrite(&chunk_size, sizeof(int), 1, f);
    fwrite(&g_cache_count, sizeof(int), 1, f);
    fwrite(g_cache, sizeof(XState), (size_t)g_cache_count, f);
    fclose(f);
}

typedef struct {
    int chunk_index;
    int end;            /* 以 offset 计（1 起）的结束位置（不含） */
    LootTableContext ctx;
} ScanWorker;

static DWORD WINAPI scan_worker(LPVOID param)
{
    ScanWorker* w = (ScanWorker*)param;
    LootTableContext ctx = w->ctx;
    for (int i = w->chunk_index * CHUNK; i < w->end; i++) {
        generate_loot(&ctx);
        int offset = i + 1;
        if (compare_generated(&ctx, 0) || compare_generated(&ctx, 1)) {
            if (g_scan_mode == 1) {
                if (has_candidate_near(g_scan_cands, g_scan_cand_count, offset, g_scan_delta)) results_add(offset);
            } else if (g_scan_mode == 2) {
                EnterCriticalSection(&g_hit_cs);
                if (g_hit_offset == 0 || offset < g_hit_offset) {
                    g_hit_offset = offset;
                    g_hit_state.lo = ctx.prng_state.xr.lo;
                    g_hit_state.hi = ctx.prng_state.xr.hi;
                }
                LeaveCriticalSection(&g_hit_cs);
                return 0; /* detect：有命中就够 */
            } else {
                results_add(offset);
            }
        }
    }
    return 0;
}

static int cpu_cores(void)
{
    SYSTEM_INFO info;
    GetSystemInfo(&info);
    int n = (int)info.dwNumberOfProcessors;
    if (n < 1) n = 1;
    if (n > MAX_WORKERS) n = MAX_WORKERS;
    return n;
}

/**
 * 用缓存的状态把 [start, end) 拆成 CHUNK 块，多线程扫描。
 * 缓存必须覆盖到 end（返回 0 表示覆盖不足，调用方走单线程）。
 */
static int scan_parallel(LootTableContext* base, int start, int end, int mode, int delta)
{
    if (end > g_cache_count * CHUNK) return 0;
    int first_chunk = start / CHUNK;
    int last_chunk = (end + CHUNK - 1) / CHUNK; /* exclusive */
    int chunk_count = last_chunk - first_chunk;
    if (chunk_count <= 0) return 0;
    if (g_cache_count < last_chunk) return 0;

    g_scan_mode = mode;
    g_scan_delta = delta;
    int threads = cpu_cores();
    if (threads > chunk_count) threads = chunk_count;

    ScanWorker* workers = (ScanWorker*)calloc((size_t)chunk_count, sizeof(ScanWorker));
    for (int k = 0; k < chunk_count; k++) {
        int chunk = first_chunk + k;
        workers[k].chunk_index = chunk;
        long long chunk_end = (long long)(chunk + 1) * CHUNK;
        workers[k].end = (int)(chunk_end < end ? chunk_end : end);
        workers[k].ctx = *base;
        workers[k].ctx.prng_state.type = XOROSHIRO;
        workers[k].ctx.prng_state.xr.lo = g_cache[chunk].lo;
        workers[k].ctx.prng_state.xr.hi = g_cache[chunk].hi;
    }

    for (int done = 0; done < chunk_count; done += threads) {
        int batch = chunk_count - done < threads ? chunk_count - done : threads;
        HANDLE handles[MAX_WORKERS];
        for (int b = 0; b < batch; b++) {
            handles[b] = CreateThread(NULL, 0, scan_worker, &workers[done + b], 0, NULL);
        }
        WaitForMultipleObjects((DWORD)batch, handles, TRUE, INFINITE);
        for (int b = 0; b < batch; b++) {
            if (handles[b]) CloseHandle(handles[b]);
        }
    }
    free(workers);
    return 1;
}

static int int_cmp(const void* a, const void* b)
{
    int x = *(const int*)a, y = *(const int*)b;
    return (x > y) - (x < y);
}

int main(int argc, char** argv)
{
    if (argc < 3) return 2;
    long long seed = _strtoi64(argv[2], NULL, 10);
    int ominous = atoi(argv[3]);
    snprintf(g_cache_path, sizeof(g_cache_path), "vault-state-%lld-%d.bin", seed, ominous);
    cache_load(g_cache_path);
    InitializeCriticalSection(&g_results_cs);
    InitializeCriticalSection(&g_hit_cs);

    if (strcmp(argv[1], "pred") == 0 && argc >= 6) {
        int offset = atoi(argv[4]);
        int count = atoi(argv[5]);
        LootTableContext* ctx = make_ctx(seed, ominous);
        if (!ctx) return 3;
        for (int i = 0; i < offset; i++) generate_loot(ctx);
        for (int i = 0; i < count; i++) {
            generate_loot(ctx);
            printf("OFFSET %d ITEMS %d ", offset + i + 1, ctx->generated_item_count);
            print_items(ctx);
            printf("\n");
        }
        fflush(stdout);
        return 0;
    }

    if (strcmp(argv[1], "search") == 0 && argc >= 8) {
        int start = atoi(argv[4]);
        int limit = atoi(argv[5]);
        int amount = atoi(argv[6]);
        const char* targets = argv[7];
        LootTableContext* ctx = make_ctx(seed, ominous);
        if (!ctx) return 3;
        for (int i = 0; i < start; i++) generate_loot(ctx);
        int hits = 0;
        for (int i = 0; i < limit && hits < amount; i++) {
            generate_loot(ctx);
            for (int j = 0; j < ctx->generated_item_count; j++) {
                ItemStack* st = &ctx->generated_items[j];
                const char* nm = get_item_name(ctx, st->item);
                if (!nm) continue;
                int wanted = 0;
                if (strcmp(targets, "all") == 0) {
                    wanted = 1;
                } else {
                    char tbuf[2048];
                    strncpy(tbuf, targets, sizeof(tbuf) - 1);
                    tbuf[sizeof(tbuf) - 1] = 0;
                    char* tsave = NULL;
                    for (char* tok = strtok_s(tbuf, ";", &tsave); tok; tok = strtok_s(NULL, ";", &tsave)) {
                        if (strcmp(tok, nm) == 0) { wanted = 1; break; }
                    }
                }
                if (wanted) {
                    printf("HIT %d %s %d\n", start + i + 1, nm, st->count);
                    hits++;
                }
            }
        }
        printf("DONE %d\n", hits);
        fflush(stdout);
        return 0;
    }

    if (strcmp(argv[1], "matches") == 0 && argc >= 6) {
        int maxScan = atoi(argv[4]);
        const char* spec = argv[5];
        LootTableContext* ctx = make_ctx(seed, ominous);
        if (!ctx) return 3;
        parse_spec(spec);
        if (scan_parallel(ctx, 0, maxScan, 0, 0)) {
            qsort(g_results, (size_t)g_results_count, sizeof(int), int_cmp);
            for (int r = 0; r < g_results_count; r++) printf("M %d\n", g_results[r]);
            printf("DONE\n");
            fflush(stdout);
            return 0;
        }
        int printed = 0;
        for (int i = 0; i < maxScan; i++) {
            if (i > 0 && i % 1000000 == 0) {
                printf("PROGRESS %d\n", i);
                fflush(stdout);
            }
            generate_loot(ctx);
            cache_note(i + 1, ctx);
            if (compare_generated(ctx, 0) || compare_generated(ctx, 1)) {
                printf("M %d\n", i + 1);
                if (++printed >= 2000000) {
                    printf("CAPPED\n");
                    fflush(stdout);
                    return 0;
                }
            }
        }
        cache_save(g_cache_path, seed, ominous);
        printf("DONE\n");
        fflush(stdout);
        return 0;
    }

    if (strcmp(argv[1], "narrow") == 0 && argc >= 9) {
        int rangeStart = atoi(argv[4]);
        int rangeEnd = atoi(argv[5]);
        int delta = atoi(argv[6]);
        const char* spec = argv[7];
        const char* candPath = argv[8];
        LootTableContext* ctx = make_ctx(seed, ominous);
        if (!ctx) return 3;
        parse_spec(spec);
        FILE* cf = fopen(candPath, "rb");
        if (!cf) { fprintf(stderr, "cannot open candidates file\n"); return 3; }
        int cap = 1024;
        int cand_count = 0;
        int* cands = (int*)malloc(cap * sizeof(int));
        while (fscanf(cf, "%d", &cands[cand_count]) == 1) {
            if (++cand_count >= cap) { cap *= 2; cands = (int*)realloc(cands, cap * sizeof(int)); }
        }
        fclose(cf);
        qsort(cands, cand_count, sizeof(int), cmp_int);
        g_scan_cands = cands;
        g_scan_cand_count = cand_count;
        if (scan_parallel(ctx, rangeStart, rangeEnd, 1, delta)) {
            qsort(g_results, (size_t)g_results_count, sizeof(int), int_cmp);
            for (int r = 0; r < g_results_count; r++) printf("KEEP %d\n", g_results[r]);
            printf("DONE\n");
            fflush(stdout);
            free(cands);
            return 0;
        }
        int printed = 0;
        if (rangeStart < 0) rangeStart = 0;
        for (int i = 0; i < rangeStart; i++) {
            generate_loot(ctx);
            cache_note(i + 1, ctx);
        }
        for (int i = rangeStart; i < rangeEnd; i++) {
            if (i > rangeStart && (i - rangeStart) % 1000000 == 0) {
                printf("PROGRESS %d\n", i);
                fflush(stdout);
            }
            generate_loot(ctx);
            cache_note(i + 1, ctx);
            if (compare_generated(ctx, 0) || compare_generated(ctx, 1)) {
                int offset = i + 1;
                if (has_candidate_near(cands, cand_count, offset, delta)) {
                    printf("KEEP %d\n", offset);
                    if (++printed >= 2000000) {
                        printf("CAPPED\n");
                        fflush(stdout);
                        free(cands);
                        return 0;
                    }
                }
            }
        }
        cache_save(g_cache_path, seed, ominous);
        printf("DONE\n");
        fflush(stdout);
        free(cands);
        return 0;
    }

    if (strcmp(argv[1], "detect") == 0 && argc >= 7) {
        int start = atoi(argv[4]);
        int maxAttempts = atoi(argv[5]);
        const char* spec = argv[6];
        LootTableContext* ctx = make_ctx(seed, ominous);
        if (!ctx) return 3;
        parse_spec(spec);
        InitializeCriticalSection(&g_hit_cs);
        if (scan_parallel(ctx, start, start + maxAttempts, 2, 0)) {
            if (g_hit_offset > 0) {
                printf("MATCH %d STATE %016" PRIx64 "%016" PRIx64 "\n", g_hit_offset, g_hit_state.hi, g_hit_state.lo);
            } else {
                printf("NOMATCH\n");
            }
            fflush(stdout);
            return 0;
        }
        for (int i = 0; i < start; i++) {
            generate_loot(ctx);
            cache_note(i + 1, ctx);
        }
        for (int i = 0; i < maxAttempts; i++) {
            if (i > 0 && i % 500000 == 0) {
                printf("PROGRESS %d\n", start + i);
                fflush(stdout);
            }
            generate_loot(ctx);
            cache_note(start + i + 1, ctx);
            if (compare_generated(ctx, 0) || compare_generated(ctx, 1)) {
                cache_save(g_cache_path, seed, ominous);
                printf("MATCH %d STATE %016" PRIx64 "%016" PRIx64 "\n", start + i + 1, ctx->prng_state.xr.hi, ctx->prng_state.xr.lo);
                fflush(stdout);
                return 0;
            }
        }
        cache_save(g_cache_path, seed, ominous);
        printf("NOMATCH\n");
        fflush(stdout);
        return 4;
    }

    fprintf(stderr, "usage: pred <seed> <ominous> <offset> <count> | detect <seed> <ominous> <start> <max> <itemspec>\n");
    return 2;
}
