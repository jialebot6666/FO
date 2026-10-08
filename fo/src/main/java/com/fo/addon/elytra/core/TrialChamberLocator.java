package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapDecorationsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;

public final class TrialChamberLocator {
    private static final Identifier TRIAL_CHAMBERS = Identifier.ofVanilla((String)"trial_chambers");
    private static final TagKey<Structure> ON_TRIAL_CHAMBERS_MAPS = TagKey.of((RegistryKey)RegistryKeys.STRUCTURE, (Identifier)Identifier.ofVanilla((String)"on_trial_chambers_maps"));
    private static final String MULTIPLAYER_REASON = "\u591a\u4eba\u670d\u52a1\u5668\u62ff\u4e0d\u5230\u4e16\u754c\u79cd\u5b50\uff0c\u8bf7\u7528\u300e\u624b\u52a8\u5750\u6807\u300f\u6216\u300e\u8bfb\u5730\u56fe\u300f";
    private static final int DEFAULT_RADIUS_CHUNKS = 100;
    private static final int MAX_RADIUS_CHUNKS = 200;
    private static final int TRIAL_CHAMBERS_SPACING = 34;
    private static final int TRIAL_CHAMBERS_SEPARATION = 12;
    private static final int TRIAL_CHAMBERS_SALT = 94251327;
    private static final int SEED_MIN_REGIONS = 1;
    private static final int SEED_MAX_REGIONS = 8;
    private volatile String lastFailReason = "";
    private final AtomicReference<BlockPos> pendingHit = new AtomicReference();
    private volatile String pendingNote = "";
    private volatile boolean searching;

    public static boolean seedSearchAvailable() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.getServer() != null;
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.seedSearchAvailable", t);
            return false;
        }
    }

    public Target locateBySeed(int fromX, int fromZ, int radiusChunks) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) {
                return this.fail("\u5ba2\u6237\u7aef\u8fd8\u6ca1\u521d\u59cb\u5316\uff0c\u62ff\u4e0d\u5230\u6574\u5408\u670d\u52a1\u7aef\uff08\u5355\u673a\u4e16\u754c\u751f\u6210\u5668\uff09");
            }
            IntegratedServer server = mc.getServer();
            if (server == null) {
                return this.fail("\u591a\u4eba\u670d\u52a1\u5668\u62ff\u4e0d\u5230\u4e16\u754c\u79cd\u5b50\uff0c\u8bf7\u7528\u300e\u624b\u52a8\u5750\u6807\u300f\u6216\u300e\u8bfb\u5730\u56fe\u300f\uff08\u4e5f\u53ef\u4ee5\u586b\u4e16\u754c\u79cd\u5b50\uff0c\u7528\u300e\u624b\u586b\u79cd\u5b50\u63a8\u7b97\u300f\uff09");
            }
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) {
                return this.fail("\u6574\u5408\u670d\u52a1\u7aef\u8fd8\u6ca1\u52a0\u8f7d\u4e3b\u4e16\u754c\uff08\u6b63\u5728\u8fdb\u5165\u4e16\u754c\uff1f\uff09\uff0c\u7a0d\u540e\u518d\u8bd5");
            }
            if (this.searching) {
                return this.fail("\u6b63\u5728\u540e\u53f0\u641c\u7d22\uff08\u4e0a\u4e00\u6b21\u8fd8\u6ca1\u7ed3\u675f\uff09\uff1a\u7b49 isSearching() \u53d8 false\uff0c\u518d\u7528 pollSeedSearch() \u53d6\u7ed3\u679c");
            }
            Clamped radiusInfo = TrialChamberLocator.resolveRadius(radiusChunks);
            this.pendingHit.set(null);
            this.pendingNote = "";
            this.searching = true;
            try {
                server.execute(() -> this.runSeedSearch(server, fromX, fromZ, radiusInfo.value(), radiusInfo.notice()));
            }
            catch (Throwable t) {
                this.searching = false;
                FOElytraLog.detailError("TrialChamberLocator.locateBySeed/dispatch", t);
                return this.fail(TrialChamberLocator.withNotice("\u628a\u79cd\u5b50\u641c\u7d22\u4ea4\u7ed9\u6574\u5408\u670d\u52a1\u7aef\u7ebf\u7a0b\u65f6\u51fa\u9519\uff1a" + String.valueOf(t), radiusInfo.notice()));
            }
            FOElytraLog.detail("\u5df2\u628a\u79cd\u5b50\u641c\u7d22\u4ea4\u7ed9\u6574\u5408\u670d\u52a1\u7aef\u7ebf\u7a0b\uff1a\u8d77\u70b9=(%d,0,%d) \u534a\u5f84=%d \u533a\u5757", fromX, fromZ, radiusInfo.value());
            return this.fail(TrialChamberLocator.withNotice(String.format(Locale.ROOT, "\u6b63\u5728\u540e\u53f0\u641c\u7d22\uff08\u5df2\u4ea4\u7ed9\u6574\u5408\u670d\u52a1\u7aef\u7ebf\u7a0b\uff0c\u5ba2\u6237\u7aef\u4e0d\u4f1a\u88ab\u5361\u4f4f\uff09\uff1a\u8d77\u70b9 (%d, %d)\uff0c\u534a\u5f84 %d \u533a\u5757 \u2248 %d \u683c\uff1b\u6bcf tick \u770b isSearching()\uff0c\u641c\u7d22\u7ed3\u675f\u540e\u7528 pollSeedSearch() \u53d6\u7ed3\u679c", fromX, fromZ, radiusInfo.value(), radiusInfo.value() * 16), radiusInfo.notice()));
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.locateBySeed", t);
            return this.fail("\u53d1\u8d77\u540e\u53f0\u79cd\u5b50\u641c\u7d22\u65f6\u51fa\u9519\uff1a" + String.valueOf(t) + "\uff08\u53ef\u6539\u7528\u624b\u52a8\u5750\u6807\u6216\u8bfb\u5730\u56fe\uff09");
        }
    }

    public boolean isSearching() {
        return this.searching;
    }

    public Target pollSeedSearch() {
        try {
            BlockPos hit = this.pendingHit.getAndSet(null);
            if (hit == null) {
                return null;
            }
            String note = this.pendingNote;
            this.lastFailReason = "";
            FOElytraLog.detail("\u53d6\u5230\u540e\u53f0\u79cd\u5b50\u641c\u7d22\u7ed3\u679c\uff1a(%d, %d)", hit.getX(), hit.getZ());
            return new Target(hit.getX(), hit.getZ(), Source.SEED, note == null || note.isEmpty() ? "\u5355\u673a\u4e16\u754c\u751f\u6210\u5668\u540e\u53f0\u5b9a\u4f4d\uff1a\u641c\u7d22\u5b8c\u6210" : note);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.pollSeedSearch", t);
            return this.fail("\u53d6\u540e\u53f0\u79cd\u5b50\u641c\u7d22\u7ed3\u679c\u65f6\u51fa\u9519\uff1a" + String.valueOf(t));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void runSeedSearch(IntegratedServer server, int fromX, int fromZ, int radius, String radiusNotice) {
        try {
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) {
                this.fail(TrialChamberLocator.withNotice("\u540e\u53f0\u641c\u7d22\u5f00\u59cb\u65f6\u6574\u5408\u670d\u52a1\u7aef\u8fd8\u6ca1\u52a0\u8f7d\u4e3b\u4e16\u754c\uff08\u6b63\u5728\u8fdb\u51fa\u4e16\u754c\uff1f\uff09\uff0c\u7a0d\u540e\u7528\u540c\u4e00\u4e2a\u534a\u5f84\u91cd\u8bd5", radiusNotice));
                return;
            }
            BlockPos from = new BlockPos(fromX, 0, fromZ);
            BlockPos hit = null;
            String route = "";
            try {
                hit = overworld.locateStructure(ON_TRIAL_CHAMBERS_MAPS, from, radius, false);
                route = "\u7ed3\u6784\u6807\u7b7e #minecraft:on_trial_chambers_maps";
            }
            catch (Throwable t) {
                FOElytraLog.detailError("TrialChamberLocator.runSeedSearch/tag", t);
                FOElytraLog.detail("\u6807\u7b7e\u8def\u7ebf\u5931\u8d25\uff08%s\uff09\uff0c\u6539\u8d70\u6ce8\u518c\u9879\u76f4\u8fde\u8def\u7ebf", t);
            }
            if (hit == null) {
                try {
                    Registry<Structure> lookup = overworld.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                    RegistryEntry<Structure> entry = lookup.getOptional(RegistryKey.of(RegistryKeys.STRUCTURE, TRIAL_CHAMBERS)).orElse(null);
                    if (entry != null) {
                        RegistryEntryList.Direct<Structure> list = RegistryEntryList.of(List.of(entry));
                        ChunkGenerator generator = overworld.getChunkManager().getChunkGenerator();
                        Pair found = generator.locateStructure(overworld, (RegistryEntryList)list, from, radius, false);
                        if (found != null) {
                            hit = (BlockPos)found.getFirst();
                            route = "\u6ce8\u518c\u9879\u76f4\u8fde minecraft:trial_chambers";
                        }
                    }
                }
                catch (Throwable t) {
                    FOElytraLog.detailError("TrialChamberLocator.runSeedSearch/entry", t);
                    FOElytraLog.detail("\u6ce8\u518c\u9879\u76f4\u8fde\u8def\u7ebf\u4e5f\u5931\u8d25\uff1a%s", t);
                }
            }
            if (hit == null) {
                this.fail(TrialChamberLocator.withNotice(TrialChamberLocator.diagnoseNoHit(overworld, radius), radiusNotice));
                return;
            }
            this.lastFailReason = "";
            FOElytraLog.detail("\u8bd5\u70bc\u5bc6\u5ba4\uff08\u79cd\u5b50/\u751f\u6210\u5668\uff09\u540e\u53f0\u5b9a\u4f4d\u6210\u529f\uff1a(%d, %d) \u8def\u7ebf=%s \u8d77\u70b9=(%d,%d) \u534a\u5f84=%d \u533a\u5757", hit.getX(), hit.getZ(), route, fromX, fromZ, radius);
            this.pendingNote = TrialChamberLocator.withNotice(String.format(Locale.ROOT, "\u5355\u673a\u4e16\u754c\u751f\u6210\u5668\u540e\u53f0\u5b9a\u4f4d\uff08%s\uff09\uff1a\u641c\u7d22\u534a\u5f84 %d \u533a\u5757 \u2248 %d \u683c", route, radius, radius * 16), radiusNotice);
            this.pendingHit.set(hit);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.runSeedSearch", t);
            this.fail(TrialChamberLocator.withNotice("\u540e\u53f0\u5b9a\u4f4d\u65f6\u51fa\u9519\uff1a" + String.valueOf(t) + "\uff08\u53ef\u6539\u7528\u624b\u52a8\u5750\u6807\u6216\u8bfb\u5730\u56fe\uff09", radiusNotice));
        }
        finally {
            this.searching = false;
        }
    }

    public Target locateBySeedValue(long seed, int fromX, int fromZ, int maxRegions) {
        try {
            List<Target> all = this.locateBySeedValues(seed, fromX, fromZ, maxRegions);
            if (all.isEmpty()) {
                if (this.failReason().isEmpty()) {
                    return this.fail("\u6309\u624b\u586b\u79cd\u5b50\u63a8\u7b97\u6ca1\u6709\u7b97\u51fa\u4efb\u4f55\u5019\u9009\u70b9\uff08\u5708\u6570\u88ab\u5939\u5230 8 \u4e5f\u4e0d\u8be5\u4e3a\u7a7a\uff09");
                }
                return null;
            }
            return all.get(0);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.locateBySeedValue", t);
            return this.fail("\u6309\u624b\u586b\u79cd\u5b50\u5b9a\u4f4d\u65f6\u51fa\u9519\uff1a" + String.valueOf(t));
        }
    }

    public List<Target> locateBySeedValues(long seed, int fromX, int fromZ, int maxRegions) {
        try {
            Clamped ringsInfo = TrialChamberLocator.resolveRegionCount(maxRegions);
            int rings = ringsInfo.value();
            int centerRegionX = Math.floorDiv(fromX >> 4, 34);
            int centerRegionZ = Math.floorDiv(fromZ >> 4, 34);
            int side = 2 * rings + 1;
            ArrayList<Candidate> candidates = new ArrayList<Candidate>(side * side);
            for (int regionX = centerRegionX - rings; regionX <= centerRegionX + rings; ++regionX) {
                for (int regionZ = centerRegionZ - rings; regionZ <= centerRegionZ + rings; ++regionZ) {
                    ChunkPos startChunk = TrialChamberLocator.computeSeedStartChunk(seed, regionX, regionZ);
                    int x = startChunk.getStartX() + 8;
                    int z = startChunk.getStartZ() + 8;
                    long dx = (long)x - (long)fromX;
                    long dz = (long)z - (long)fromZ;
                    candidates.add(new Candidate(startChunk.getStartX() >> 4, startChunk.getStartZ() >> 4, regionX, regionZ, x, z, dx * dx + dz * dz));
                }
            }
            Comparator<Candidate> byDistance = Comparator.comparingLong(Candidate::distanceSq).thenComparingInt(Candidate::x).thenComparingInt(Candidate::z);
            candidates.sort(byDistance);
            if (candidates.isEmpty()) {
                return List.of();
            }
            ArrayList<Target> out = new ArrayList<Target>(candidates.size());
            for (Candidate c : candidates) {
                out.add(new Target(c.x(), c.z(), Source.SEED, TrialChamberLocator.seedNote(c, seed, rings, ringsInfo.notice())));
            }
            this.lastFailReason = "";
            Candidate nearest = (Candidate)candidates.get(0);
            FOElytraLog.detail("\u624b\u586b\u79cd\u5b50\u63a8\u7b97\uff1aseed=%d \u8d77\u70b9=(%d,%d) \u5708\u6570=%d \u5019\u9009=%d \u6700\u8fd1=(%d,%d) \u533a\u5757(%d,%d) region(%d,%d)", seed, fromX, fromZ, rings, out.size(), nearest.x(), nearest.z(), nearest.startChunkX(), nearest.startChunkZ(), nearest.regionX(), nearest.regionZ());
            return out;
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.locateBySeedValues", t);
            this.fail("\u6309\u624b\u586b\u79cd\u5b50\u63a8\u7b97\u7ed3\u6784\u4f4d\u7f6e\u65f6\u51fa\u9519\uff1a" + String.valueOf(t));
            return List.of();
        }
    }

    public static ChunkPos computeSeedStartChunk(long seed, int regionX, int regionZ) {
        int span = 22;
        ChunkRandom random = new ChunkRandom((Random)new CheckedRandom(0L));
        random.setRegionSeed(seed, regionX, regionZ, 94251327);
        int dx = random.nextInt(span);
        int dz = random.nextInt(span);
        return new ChunkPos(regionX * 34 + dx, regionZ * 34 + dz);
    }

    private static String seedNote(Candidate c, long seed, int rings, String notice) {
        return TrialChamberLocator.withNotice(String.format(Locale.ROOT, "\u624b\u586b\u79cd\u5b50\u63a8\u7b97\uff08seed=%d\uff09\uff1a\u8d77\u59cb\u533a\u5757 (%d, %d) \u2192 \u533a\u5757\u4e2d\u5fc3 (%d, %d)\uff1bregion \u4e0b\u6807 (%d, %d)\uff1b\u641c\u7d22 %d \u5708\uff1b\u53c2\u6570 spacing=%d separation=%d salt=%d\uff08\u6765\u6e90\uff1a\u672c\u673a\u5ba2\u6237\u7aef jar \u7684 trial_chambers \u7ed3\u6784\u96c6 JSON\uff0c\u7248\u672c\u5347\u7ea7\u540e\u8981\u91cd\u65b0\u6838\u5bf9\uff09\uff1b\u6ce8\u610f\u8fd9\u53ea\u662f random_spread \u63a8\u7b97\u51fa\u7684\u5019\u9009\u8d77\u59cb\u533a\u5757\uff0c\u771f\u5b9e\u751f\u6210\u8fd8\u8981\u8fc7\u751f\u7269\u7fa4\u7cfb\u7b49\u6761\u4ef6\uff0c\u53ef\u80fd\u538b\u6839\u6ca1\u751f\u6210\u7ed3\u6784\uff08\u5efa\u8bae\u5230\u4e86\u9644\u8fd1\u518d\u6309\u5355\u673a\u79cd\u5b50\u641c\u7d22\u6216\u770b\u5730\u56fe\u786e\u8ba4\uff09", seed, c.startChunkX(), c.startChunkZ(), c.x(), c.z(), c.regionX(), c.regionZ(), rings, 34, 12, 94251327), notice);
    }

    public static Target manual(int x, int z) {
        return new Target(x, z, Source.MANUAL, "\u624b\u52a8\u5750\u6807\uff08\u672a\u9a8c\u8bc1\u771f\u5b9e\u7ed3\u6784\u4f4d\u7f6e\uff09\uff1a\u591a\u4eba\u670d\u52a1\u5668\u6ca1\u6709\u4e16\u754c\u79cd\u5b50\uff0c\u53ea\u80fd\u9760\u624b\u52a8\u5750\u6807\u6216\u300e\u8bfb\u5730\u56fe\u300f");
    }

    public Target readMapTarget() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) {
                return this.fail("\u8fd8\u6ca1\u8fdb\u4e16\u754c\uff08\u62ff\u4e0d\u5230\u80cc\u5305\uff09\uff0c\u300e\u8bfb\u5730\u56fe\u300f\u8981\u8fdb\u4e16\u754c\u4e4b\u540e\u624d\u80fd\u7528");
            }
            PlayerInventory inventory = mc.player.getInventory();
            if (inventory == null) {
                return this.fail("\u62ff\u4e0d\u5230\u73a9\u5bb6\u80cc\u5305\uff0c\u8bfb\u4e0d\u4e86\u5730\u56fe");
            }
            int size = inventory.size();
            String trialNamedButUnreadable = null;
            for (int i = 0; i < size; ++i) {
                Map<String, MapDecorationsComponent.Decoration> marks;
                MapDecorationsComponent decorations;
                ItemStack stack;
                try {
                    stack = inventory.getStack(i);
                }
                catch (Throwable t) {
                    FOElytraLog.detailError("TrialChamberLocator.readMapTarget(slot " + i + ")", t);
                    continue;
                }
                if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) continue;
                String name = TrialChamberLocator.stackName(stack);
                boolean nameLooksTrial = TrialChamberLocator.looksLikeTrialMap(name);
                try {
                    decorations = (MapDecorationsComponent)stack.get(DataComponentTypes.MAP_DECORATIONS);
                }
                catch (Throwable t) {
                    FOElytraLog.detailError("TrialChamberLocator.readMapTarget(component)", t);
                    decorations = null;
                }
                marks = decorations == null ? null : decorations.decorations();
                if (marks == null || marks.isEmpty()) {
                    if (!nameLooksTrial || trialNamedButUnreadable != null) continue;
                    trialNamedButUnreadable = name;
                    continue;
                }
                MapDecorationsComponent.Decoration trialDecoration = null;
                MapDecorationsComponent.Decoration firstDecoration = null;
                for (MapDecorationsComponent.Decoration decoration : marks.values()) {
                    if (decoration == null) continue;
                    if (firstDecoration == null) {
                        firstDecoration = decoration;
                    }
                    if (!TrialChamberLocator.isTrialChambersDecoration(decoration)) continue;
                    trialDecoration = decoration;
                    break;
                }
                MapDecorationsComponent.Decoration chosen = trialDecoration;
                String how = "\u6807\u8bb0\u7c7b\u578b = minecraft:trial_chambers";
                if (chosen == null && nameLooksTrial && marks.size() == 1) {
                    chosen = firstDecoration;
                    how = "\u6807\u8bb0\u7c7b\u578b\u672a\u8bc6\u522b\uff0c\u6309\u5730\u56fe\u540d\u5b57\u5224\u5b9a";
                }
                if (chosen == null) {
                    if (!nameLooksTrial || trialNamedButUnreadable != null) continue;
                    trialNamedButUnreadable = name;
                    continue;
                }
                int x = (int)Math.round(chosen.x());
                int z = (int)Math.round(chosen.z());
                if (x == 0 && z == 0) {
                    if (!nameLooksTrial || trialNamedButUnreadable != null) continue;
                    trialNamedButUnreadable = name;
                    continue;
                }
                this.lastFailReason = "";
                FOElytraLog.detail("\u8bfb\u5730\u56fe\u6210\u529f\uff1aslot=%d \u540d\u5b57=%s \u5750\u6807=(%d,%d)\uff08%s\uff09", i, name, x, z, how);
                return new Target(x, z, Source.MAP, String.format(Locale.ROOT, "\u8bfb\u5730\u56fe\u300c%s\u300d\u7684\u76ee\u6807\u6807\u8bb0\uff1a(%d, %d)\uff0c%s", name, x, z, how));
            }
            if (trialNamedButUnreadable != null) {
                String detail = TrialChamberLocator.mapStateHint(mc);
                return this.fail("\u627e\u5230\u4e86\u300c" + trialNamedButUnreadable + "\u300d\u4f46\u8bfb\u4e0d\u5230\u5730\u56fe\u88c5\u9970\u70b9\uff08\u8fd9\u4e2a\u5ba2\u6237\u7aef\u7248\u672c\u8bfb\u4e0d\u5230\u5730\u56fe\u88c5\u9970\u70b9\uff0c\u6216\u5730\u56fe\u6570\u636e\u8fd8\u6ca1\u540c\u6b65\uff09" + detail);
            }
            return this.fail("\u80cc\u5305\u91cc\u6ca1\u6709\u300c\u57cb\u85cf\u7684\u8bd5\u70bc\u5bc6\u5ba4\u5730\u56fe\u300d\uff08\u8981\u653e\u5728\u8eab\u4e0a\uff0c\u4e0d\u80fd\u7559\u5728\u7bb1\u5b50\u91cc\uff09");
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.readMapTarget", t);
            return this.fail("\u8bfb\u5730\u56fe\u65f6\u51fa\u9519\uff1a" + String.valueOf(t));
        }
    }

    public String failReason() {
        String reason = this.lastFailReason;
        return reason == null ? "" : reason;
    }

    private Target fail(String reason) {
        this.lastFailReason = reason == null ? "" : reason;
        FOElytraLog.detail("\u8bd5\u70bc\u5bc6\u5ba4\u5b9a\u4f4d\u5931\u8d25\uff1a%s", this.lastFailReason);
        return null;
    }

    private static String diagnoseNoHit(ServerWorld overworld, int radius) {
        try {
            boolean structuresEnabled = overworld.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures();
            if (!structuresEnabled) {
                return "\u8fd9\u4e2a\u4e16\u754c\u5728\u521b\u5efa\u65f6\u5173\u6389\u4e86\u300c\u751f\u6210\u7ed3\u6784\u300d\uff08GeneratorOptions#shouldGenerateStructures=false\uff09\uff0c\u4e16\u754c\u751f\u6210\u5668\u6839\u672c\u4e0d\u4f1a\u653e\u8bd5\u70bc\u5bc6\u5ba4\uff0c\u6362\u8d77\u70b9\u6216\u52a0\u5927\u534a\u5f84\u90fd\u6ca1\u7528";
            }
            Registry lookup = overworld.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            if (lookup.getOptional(ON_TRIAL_CHAMBERS_MAPS).isEmpty()) {
                return "\u5f53\u524d\u6570\u636e\u5305\u6ca1\u6709\u7ed3\u6784\u6807\u7b7e #minecraft:on_trial_chambers_maps\uff08\u88ab\u6570\u636e\u5305\u5220\u4e86/\u6539\u4e86\uff1f\uff09\uff0c\u6807\u7b7e\u4e3a\u7a7a\u65f6\u7ed3\u6784\u641c\u7d22\u53ea\u4f1a\u9759\u9ed8\u8fd4\u56de null";
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.diagnoseNoHit", t);
        }
        return String.format(Locale.ROOT, "\u534a\u5f84 %d \u533a\u5757\uff08\u2248%d \u683c\uff09\u5185\u6ca1\u627e\u5230\u8bd5\u70bc\u5bc6\u5ba4\uff1a\u6362\u4e2a\u8d77\u70b9\uff0c\u6216\u628a\u534a\u5f84\u8c03\u5927\u518d\u8bd5\uff08\u8d8a\u8fdc\u8d8a\u6162\uff09", radius, radius * 16);
    }

    private static Clamped resolveRadius(int radiusChunks) {
        if (radiusChunks <= 0) {
            String notice = String.format(Locale.ROOT, "\u534a\u5f84 %d \u65e0\u6548\uff08<=0\uff09\uff0c\u5df2\u6309\u9ed8\u8ba4\u503c %d \u533a\u5757\uff08\u2248%d \u683c\uff09\u641c\u7d22", radiusChunks, 100, 1600);
            FOElytraLog.warn("\u8bd5\u70bc\u5bc6\u5ba4\u5b9a\u4f4d\uff1a%s", notice);
            FOElytraLog.detail("\u534a\u5f84\u5939\u53d6\uff1a\u8bf7\u6c42 %d \u2192 \u5b9e\u9645 %d\uff08<=0 \u8d70\u9ed8\u8ba4\u503c\uff09", radiusChunks, 100);
            return new Clamped(100, notice);
        }
        if (radiusChunks > 200) {
            String notice = String.format(Locale.ROOT, "\u534a\u5f84 %d \u533a\u5757\u8d85\u8fc7\u4e0a\u9650\uff0c\u5df2\u5939\u5230 %d \u533a\u5757\uff08\u2248%d \u683c\uff09", radiusChunks, 200, 3200);
            FOElytraLog.warn("\u8bd5\u70bc\u5bc6\u5ba4\u5b9a\u4f4d\uff1a%s", notice);
            FOElytraLog.detail("\u534a\u5f84\u5939\u53d6\uff1a\u8bf7\u6c42 %d \u2192 \u5b9e\u9645 %d\uff08\u4e0a\u9650 %d\uff09", radiusChunks, 200, 200);
            return new Clamped(200, notice);
        }
        return new Clamped(radiusChunks, "");
    }

    private static Clamped resolveRegionCount(int maxRegions) {
        if (maxRegions < 1) {
            String notice = String.format(Locale.ROOT, "\u5708\u6570 %d \u65e0\u6548\uff08< %d\uff09\uff0c\u5df2\u6309 %d \u5708\u641c\u7d22\uff08\u6bcf\u5708 %d \u533a\u5757\uff09", maxRegions, 1, 1, 34);
            FOElytraLog.warn("\u8bd5\u70bc\u5bc6\u5ba4\u79cd\u5b50\u63a8\u7b97\uff1a%s", notice);
            FOElytraLog.detail("\u5708\u6570\u5939\u53d6\uff1a\u8bf7\u6c42 %d \u2192 \u5b9e\u9645 %d\uff08\u4e0b\u9650 %d\uff09", maxRegions, 1, 1);
            return new Clamped(1, notice);
        }
        if (maxRegions > 8) {
            String notice = String.format(Locale.ROOT, "\u5708\u6570 %d \u8d85\u8fc7\u4e0a\u9650\uff0c\u5df2\u5939\u5230 %d \u5708\uff08\u6bcf\u5708 %d \u533a\u5757\uff0c\u6700\u591a %d \u4e2a\u5019\u9009\u70b9\uff09", maxRegions, 8, 34, 289);
            FOElytraLog.warn("\u8bd5\u70bc\u5bc6\u5ba4\u79cd\u5b50\u63a8\u7b97\uff1a%s", notice);
            FOElytraLog.detail("\u5708\u6570\u5939\u53d6\uff1a\u8bf7\u6c42 %d \u2192 \u5b9e\u9645 %d\uff08\u4e0a\u9650 %d\uff09", maxRegions, 8, 8);
            return new Clamped(8, notice);
        }
        return new Clamped(maxRegions, "");
    }

    private static String withNotice(String text, String notice) {
        if (notice == null || notice.isEmpty()) {
            return text;
        }
        return text + "\uff1b" + notice;
    }

    private static String stackName(ItemStack stack) {
        try {
            return stack.getName().getString();
        }
        catch (Throwable t) {
            return "";
        }
    }

    private static boolean looksLikeTrialMap(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.contains("\u8bd5\u70bc")) {
            return true;
        }
        return name.toLowerCase(Locale.ROOT).contains("trial");
    }

    private static boolean isTrialChambersDecoration(MapDecorationsComponent.Decoration decoration) {
        try {
            RegistryEntry type = decoration.type();
            if (type == null) {
                return false;
            }
            if (type == MapDecorationTypes.TRIAL_CHAMBERS) {
                return true;
            }
            return type.matchesId(TRIAL_CHAMBERS);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.isTrialChambersDecoration", t);
            return false;
        }
    }

    private static String mapStateHint(MinecraftClient mc) {
        try {
            ClientWorld world = mc.world;
            if (world == null || mc.player == null) {
                return "";
            }
            PlayerInventory inventory = mc.player.getInventory();
            for (int i = 0; i < inventory.size(); ++i) {
                MapState state;
                ItemStack stack = inventory.getStack(i);
                if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP) || (state = FilledMapItem.getMapState((ItemStack)stack, (World)world)) == null || !state.hasExplorationMapDecoration()) continue;
                return "\uff1b\u5730\u56fe state \u91cc\u53ea\u6709\u50cf\u7d20\u5750\u6807\uff08\u5ba2\u6237\u7aef centerX/centerZ \u6052\u4e3a 0\uff09\uff0c\u8fd8\u539f\u4e0d\u51fa\u4e16\u754c\u5750\u6807\uff0c\u8bf7\u6539\u7528\u624b\u52a8\u5750\u6807";
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.mapStateHint", t);
        }
        return "";
    }

    public record Target(int x, int z, Source source, String note) {
    }

    private record Clamped(int value, String notice) {
    }

    public static enum Source {
        NONE("\u65e0"),
        SEED("\u79cd\u5b50"),
        MANUAL("\u624b\u52a8"),
        MAP("\u5730\u56fe");


        private final String label;

        Source(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private record Candidate(int startChunkX, int startChunkZ, int regionX, int regionZ, int x, int z, long distanceSq) {
    }
}

