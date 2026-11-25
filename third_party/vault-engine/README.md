# FO 不祥宝库战利品引擎（源码 / 构建 / 校验数据）

这个压缩包是 FO 模组「不祥宝库战利品定位」所用预测引擎（vaulttool.exe）的完整源码、构建脚本、固定版本信息和校验数据，
方便作者把它重写成纯 Java。

## 包内结构

| 路径 | 内容 |
|---|---|
| engine/vaulttool.c | 引擎主程序（CLI：pred / matches / narrow / detect / search；含状态缓存 + 多线程扫描） |
| engine/build-msvc.bat | MSVC 构建脚本（自动找 VS Build Tools，输出 build/vaulttool.exe） |
| engine/vaulttool.exe | 参考二进制（用于对拍，SHA-256 见 VERSION.txt） |
| cubiomes/ | 引擎依赖的 C 源码：rng.h / xrms.h / biomes.h + loot/（战利品表生成、附魔模拟、cJSON、trial chamber 四张表的生成 C 代码） |
| patches/cubiomes-loot-patches.diff | 相对上游 cubiomes master 的全部改动（12 个文件） |
| upstream/cubiomes-master-06393cf40f0d.zip | 上游原始压缩包（固定版本，便于校验补丁） |
| golden/ | 黄金测试数据（用参考 exe 生成，Java 版必须逐字节复现） |
| VERSION.txt | 固定版本与 SHA-256 |
| SHA256SUMS.txt | 包内所有文件的 SHA-256 |

## 构建

前置：Windows + Visual Studio Build Tools（含 C++ x64 工具链），或已有 vcvars 环境。

    cd engine
    build-msvc.bat
    :: 输出 engine/build/vaulttool.exe

如自动探测失败，可显式指定 vcvars：

    set VCVARS_PATH=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat
    build-msvc.bat

## CLI 用法

    vaulttool.exe pred    <seed> <ominous 0|1> <offset> <count>
    vaulttool.exe matches <seed> <ominous> <maxScan> <itemspec>
    vaulttool.exe narrow  <seed> <ominous> <rangeStart> <rangeEnd> <delta> <itemspec> <candidates.txt>
    vaulttool.exe detect  <seed> <ominous> <start> <maxAttempts> <itemspec>
    vaulttool.exe search  <seed> <ominous> <start> <limit> <amount> <targets>

- itemspec：按吐出顺序的物品清单，格式 minecraft:book:1;minecraft:diamond:3;minecraft:enchanted_golden_apple:1
  （比对时会再试一次整体倒序，兼容服务端从后往前生成）。
- matches：输出所有匹配 offset（M <offset>），即候选集。
- narrow：只在 [rangeStart, rangeEnd) 扫，且只保留与 candidates.txt 中某个 offset 相差 1..delta 次的命中（KEEP <offset>）。
- detect：从 start 起找第一个命中，输出 MATCH <offset> STATE <32 hex>；找不到输出 NOMATCH（exit code 4）。
- search：从 start 起找目标物品，输出 HIT <offset> <item> <count>（模组预测用）。

所有扫描按 100 万次把随机状态缓存到当前目录的 vault-state-<seed>-<ominous>.bin，
下次扫描从缓存状态出发并自动按 CPU 核数分块多线程（结果与单线程完全一致，已对拍验证）。

## 算法要点（重写 Java 时需要 1:1 复现）

1. 序列种子：
   - lo = worldSeed ^ 0x6A09E667F3BCC909，hi = lo + 0x9E3779B97F4A7C15；
   - 不祥：lo ^= 0x05a13d5ce5edaab3，hi ^= 0x1a3950a30a86bc23；
     普通：lo ^= 0x102ea793e31f23ff，hi ^= 0xd7c231952cddadf1；
   - 再对 lo/hi 各做一次 mix13：x^=x>>>30; x*=0xBF58476D1CE4E5B9; x^=x>>>27; x*=0x94D049BB133111EB; x^=x>>>31。
2. Xoroshiro128++（rng.h 的 xNextLong / xNextInt / xNextFloat）—— xNextInt 的拒绝采样必须完全一致。
3. 战利品表生成（loot/loot_table_context.c）：池条件（random_chance 用 nextFloat）→ rolls（constant/uniform）→
   加权选条目（只有一个条目时不消耗随机数）→ loot_table 条目递归子表 → 逐件应用 functions（消耗随机数）。
4. 附魔模拟（loot/loot_functions.c，约 1300 行）：移植量最大、最容易出错——
   enchant_randomly / enchant_with_levels / set_enchantments 会按附魔权重、最高等级、适用性、互斥、宝藏标记消耗随机数；
   任何一处不一致都会让后续 offset 全部错位。
5. 比对：物品名 + 数量 + 顺序（可选再做附魔组件比对）。
6. 缓存/并行只是加速手段，不影响结果；Java 可用 ExecutorService + 分块复刻。

## 相对上游 cubiomes 的改动（patches/ 里是完整 diff）

1. 生成表 bug ①：subtable_pool_offset[N] = offset[N-1] + count[N] 应加 count[N-1]（生成器索引写错）。
2. 生成表 bug ②：merge_loot_pools 不更新父表 pool_count，unique 池（附魔金苹果 / 沉重核心所在池）
   必须靠 subtable_pool_offset 按需生成；直接照抄会永远算不到 unique 池。
3. MSVC 适配：VLA → 固定 [64]、未命名形参补名、__builtin_popcountll → __popcnt64（非 MSVC 仍用 builtin）。
4. 生成表里的零长数组 / 静态结构体数组初始化改为运行时赋值（MSVC 不支持）。

若改用 GCC/Clang，可只保留 ①② 两处修复；③④ 是 MSVC 专用适配。

## 纯 Java 移植建议

按依赖顺序推进，每步都用 engine/vaulttool.exe 做对拍（golden/ 里有现成样本）：

1. Xoroshiro128++ + 种子推导（最容易，先做）；
2. 战利品表解析/生成（池、权重、rolls、条件、loot_table 递归、set_count / set_potion / set_ominous_bottle_amplifier）；
3. 附魔模拟（难点；建议对照 loot/loot_functions.c 逐函数移植，并用随机种子大批量对比）；
4. 比对与候选扫描（顺序 + 数量；可加附魔组件）；
5. 缓存/多线程（性能项，最后做）。

验收标准：对 golden/ 里所有文件逐字节一致（pred 四种种子、matches 200 万候选、narrow、detect、search）；
再随机抽 100+ 组（种子, offset）与 exe 对比，全部一致才算移植完成。

## 许可证

- cubiomes：MIT（见 cubiomes/LICENSE）
- loot 库：MIT（见 cubiomes/loot/LICENSE_loot_library.h.txt，Copyright (c) 2024 ScriptLine）
- SeedMapper 仅作为算法参考（MIT，commit 见 VERSION.txt），本包不包含其代码。
