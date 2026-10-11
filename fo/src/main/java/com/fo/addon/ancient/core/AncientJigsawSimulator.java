package com.fo.addon.ancient.core;

import com.fo.addon.ancient.core.AncientGeometry.Aabb;
import com.fo.addon.ancient.core.AncientGeometry.BoundingBox;
import com.fo.addon.ancient.core.AncientGeometry.VoxelShape;
import com.fo.addon.ancient.core.AncientPool.PoolElement;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * 古城 Jigsaw 拼图模拟（纯逻辑，可单测）。
 *
 * <p>忠实移植 MC 的 {@code JigsawPlacement.addPieces} / {@code Placer}（对齐 zychen027
 * AncientCityLootPredictor 的 Python 复刻，后者经 557/557 验证）。
 * 输入世界种子 + 古城原点区块坐标，输出放置的全部拼图块（piece）坐标与包围盒。
 */
public final class AncientJigsawSimulator {

    public static final int MAX_DEPTH = 7;
    private static final int MAX_DISTANCE = 116;

    private AncientJigsawSimulator() {
    }

    /** 一个 jigsaw 块（世界坐标 + 旋转后的朝向 + 连接信息）。 */
    public record Jigsaw(int[] pos, int[] localPos, String orientation, String name,
                         String target, String pool, String joint, int placementPriority) {
    }

    /** 一个放置好的拼图块。 */
    public static final class Piece {
        public final PoolElement element;
        public final int[] position;
        public final int rotation;
        public final BoundingBox bb;
        public final int depth;
        public final int groundLevelDelta;

        public Piece(PoolElement element, int[] position, int rotation, BoundingBox bb,
                     int depth, int groundLevelDelta) {
            this.element = element;
            this.position = position;
            this.rotation = rotation;
            this.bb = bb;
            this.depth = depth;
            this.groundLevelDelta = groundLevelDelta;
        }

        public String location() {
            switch (element.type) {
                case "single": return element.location;
                case "feature": return "minecraft:feature";
                case "empty": return "minecraft:empty";
                case "list":
                    return !element.children.isEmpty() && "single".equals(element.children.get(0).type)
                        ? element.children.get(0).location : "minecraft:list";
                default: return "unknown";
            }
        }
    }

    /** 可变 VoxelShape 引用（模拟 MC 的 MutableObject<VoxelShape>）。 */
    private static final class Holder {
        VoxelShape value;
        Holder(VoxelShape v) { this.value = v; }
    }

    private static final class State {
        final Piece piece;
        final Holder free;
        final int depth;
        State(Piece p, Holder f, int d) { piece = p; free = f; depth = d; }
    }

    /** 模拟一座古城的拼图放置（目录版，内部转数据源，保留原有签名）。 */
    public static List<Piece> simulate(long worldSeed, int chunkX, int chunkZ,
                                       Path structureDir, Path poolDir) {
        return simulate(worldSeed, chunkX, chunkZ,
            AncientResourceSource.directory(structureDir),
            AncientResourceSource.directory(poolDir));
    }

    /** 模拟一座古城的拼图放置。 */
    public static List<Piece> simulate(long worldSeed, int chunkX, int chunkZ,
                                       AncientResourceSource structureSource,
                                       AncientResourceSource poolSource) {
        AncientRandom rng = new AncientRandom(0);
        rng.setLargeFeatureSeed(worldSeed, chunkX, chunkZ);

        int centerRotation = rng.nextInt(4);
        AncientPool startPool = AncientPool.load("minecraft:ancient_city/city_center", poolSource);
        int centerIdx = rng.nextInt(startPool.elements.size());
        PoolElement centerElement = startPool.elements.get(centerIdx);

        int[] position = {chunkX * 16, -27, chunkZ * 16};
        List<Jigsaw> centerJigsaws = shuffledJigsawBlocks(centerElement, position, centerRotation, rng, structureSource);
        int[] anchoredPos = null;
        for (Jigsaw j : centerJigsaws) {
            if (j.name().equals("minecraft:city_anchor")) { anchoredPos = j.pos(); break; }
        }
        if (anchoredPos == null) return new ArrayList<>();

        AncientTemplate centerTpl = AncientTemplate.load(centerElement.location, structureSource);
        if (centerTpl == null) return new ArrayList<>();
        int[] anchorLocal = null;
        for (AncientTemplate.JigsawBlock jb : centerTpl.jigsawBlocks) {
            if (jb.name().equals("minecraft:city_anchor")) { anchorLocal = jb.pos(); break; }
        }
        if (anchorLocal == null) return new ArrayList<>();

        // adjustedPos = position - transform(anchorLocal, rotation)
        int[] rotatedAnchor = AncientGeometry.transform(anchorLocal, centerRotation);
        int[] adjustedPos = {position[0] - rotatedAnchor[0], position[1] - rotatedAnchor[1], position[2] - rotatedAnchor[2]};

        BoundingBox centerBb = templateBoundingBox(adjustedPos, centerRotation, centerTpl.size);
        int gld = 1; // 古城起始件 ground_level_delta
        int[] centerPos = {adjustedPos[0], adjustedPos[1] - gld, adjustedPos[2]};
        centerBb = centerBb.moved(0, -gld, 0);

        Piece centerPiece = new Piece(centerElement, centerPos, centerRotation, centerBb, 0, gld);
        List<Piece> pieces = new ArrayList<>();
        pieces.add(centerPiece);

        // 初始 contextFree：允许区域 - 中心件包围盒
        Aabb allowed = new Aabb(
            position[0] - MAX_DISTANCE, Math.max(position[1] - MAX_DISTANCE, -64), position[2] - MAX_DISTANCE,
            position[0] + MAX_DISTANCE + 1, Math.min(position[1] + MAX_DISTANCE + 1, 320), position[2] + MAX_DISTANCE + 1);
        VoxelShape ctxFree = VoxelShape.create(allowed)
            .joinOnlyFirst(VoxelShape.create(Aabb.of(centerBb)));
        Holder ctxFreeHolder = new Holder(ctxFree);

        Deque<State> queue = new ArrayDeque<>();
        queue.add(new State(centerPiece, ctxFreeHolder, 0));
        while (!queue.isEmpty()) {
            State st = queue.poll();
            tryPlacingChildren(st.piece, st.free, st.depth, rng, pieces, queue, structureSource, poolSource);
        }
        return pieces;
    }

    /** Placer.tryPlacingChildren 复刻。 */
    private static void tryPlacingChildren(Piece sourcePiece, Holder contextFree, int depth,
                                           AncientRandom rng, List<Piece> pieces, Deque<State> queue,
                                           AncientResourceSource structureSource,
                                           AncientResourceSource poolSource) {
        PoolElement sourceElement = sourcePiece.element;
        int[] sourceBoxPos = sourcePiece.position;
        int sourceRotation = sourcePiece.rotation;
        boolean sourceRigid = sourceElement.isRigid();
        Holder sourceFree = new Holder(null);
        BoundingBox sourceBb = sourcePiece.bb;
        int sourceBoxY = sourceBb.minY;

        List<Jigsaw> sourceJigsaws = shuffledJigsawBlocks(sourceElement, sourceBoxPos, sourceRotation, rng, structureSource);

        for (Jigsaw sourceJigsaw : sourceJigsaws) {
            int[] sourceJigsawPos = sourceJigsaw.pos();
            String sourceOrient = sourceJigsaw.orientation();
            int[] sourceDir = AncientGeometry.DIR_VEC.get(AncientGeometry.getFront(sourceOrient));
            int[] targetJigsawPos = {sourceJigsawPos[0] + sourceDir[0], sourceJigsawPos[1] + sourceDir[1], sourceJigsawPos[2] + sourceDir[2]};

            boolean attachInside = sourceBb.isInsidePos(targetJigsawPos);
            Holder childrenFree;
            if (attachInside) {
                childrenFree = sourceFree;
                if (childrenFree.value == null) childrenFree.value = VoxelShape.create(Aabb.of(sourceBb));
            } else {
                childrenFree = contextFree;
            }

            String poolName = sourceJigsaw.pool();
            if (poolName == null || poolName.isEmpty()) continue;

            List<PoolElement> targetPieces = new ArrayList<>();
            if (depth != MAX_DEPTH) {
                targetPieces.addAll(AncientGeometry.shuffledCopy(
                    AncientPool.load(poolName, poolSource).elements, rng));
            }
            String fallbackName = AncientPool.load(poolName, poolSource).fallback;
            targetPieces.addAll(AncientGeometry.shuffledCopy(
                AncientPool.load(fallbackName, poolSource).elements, rng));

            String targetName = sourceJigsaw.target();
            boolean placed = false;

            for (PoolElement targetElement : targetPieces) {
                if (placed) break;
                if ("empty".equals(targetElement.type)) break;

                int[] rotations = AncientGeometry.rotationGetShuffled(rng);
                for (int targetRotation : rotations) {
                    if (placed) break;

                    List<Jigsaw> targetJigsaws = shuffledJigsawBlocks(targetElement, new int[]{0, 0, 0}, targetRotation, rng, structureSource);

                    for (Jigsaw targetJigsaw : targetJigsaws) {
                        if (placed) break;
                        if (!AncientGeometry.canAttach(sourceOrient, targetName, sourceJigsaw.joint(),
                                targetJigsaw.orientation(), targetJigsaw.name())) {
                            continue;
                        }

                        int[] targetJigsawLocalPos = targetJigsaw.pos();
                        int[] rawTargetBoxPos = {targetJigsawPos[0] - targetJigsawLocalPos[0],
                            targetJigsawPos[1] - targetJigsawLocalPos[1], targetJigsawPos[2] - targetJigsawLocalPos[2]};
                        BoundingBox rawTargetBb = elementBoundingBox(targetElement, rawTargetBoxPos, targetRotation, structureSource);
                        int rawTargetY = rawTargetBb.minY;
                        boolean targetRigid = targetElement.isRigid();

                        int targetJigsawLocalY = targetJigsawLocalPos[1];
                        int sourceJigsawLocalY = sourceJigsawPos[1] - sourceBoxY;
                        int deltaY = sourceJigsawLocalY - targetJigsawLocalY + sourceDir[1];

                        int targetBoxY = sourceRigid ? sourceBoxY + deltaY : rawTargetY;
                        int yOffset = targetBoxY - rawTargetY;
                        BoundingBox targetBb = rawTargetBb.moved(0, yOffset, 0);
                        int[] targetBoxPos = {rawTargetBoxPos[0], rawTargetBoxPos[1] + yOffset, rawTargetBoxPos[2]};

                        Aabb targetAabb = Aabb.of(targetBb).deflate(0.25);
                        VoxelShape targetShape = VoxelShape.create(targetAabb);
                        if (AncientGeometry.joinIsNotEmpty(childrenFree.value, targetShape, true)) {
                            continue; // 碰撞
                        }
                        childrenFree.value = childrenFree.value.joinOnlyFirst(VoxelShape.create(Aabb.of(targetBb)));

                        int sourceGld = sourcePiece.groundLevelDelta;
                        int targetGld = targetRigid ? sourceGld - deltaY : 0;

                        Piece targetPiece = new Piece(targetElement, targetBoxPos, targetRotation, targetBb, depth + 1, targetGld);
                        pieces.add(targetPiece);

                        if (depth + 1 > MAX_DEPTH) {
                            placed = true;
                            break;
                        }
                        queue.add(new State(targetPiece, childrenFree, depth + 1));
                        placed = true;
                        break;
                    }
                }
            }
        }
    }

    // ---- PoolElement 方法 ----

    private static List<Jigsaw> shuffledJigsawBlocks(PoolElement element, int[] position, int rotation,
                                                     AncientRandom rng, AncientResourceSource structureSource) {
        switch (element.type) {
            case "empty":
                return new ArrayList<>();
            case "feature": {
                List<Jigsaw> l = new ArrayList<>();
                l.add(new Jigsaw(position, new int[]{0, 0, 0}, "down_south", "minecraft:bottom",
                    "", "minecraft:empty", "rollable", 0));
                return l;
            }
            case "list":
                if (!element.children.isEmpty()) {
                    return shuffledJigsawBlocks(element.children.get(0), position, rotation, rng, structureSource);
                }
                return new ArrayList<>();
            case "single": {
                AncientTemplate tpl = AncientTemplate.load(element.location, structureSource);
                if (tpl == null) return new ArrayList<>();
                List<Jigsaw> jigsaws = new ArrayList<>();
                for (AncientTemplate.JigsawBlock jb : tpl.jigsawBlocks) {
                    int[] world = AncientGeometry.transform(jb.pos(), rotation);
                    world = new int[]{world[0] + position[0], world[1] + position[1], world[2] + position[2]};
                    String orient = AncientGeometry.rotateOrientation(jb.orientation(), rotation);
                    jigsaws.add(new Jigsaw(world, jb.pos(), orient, jb.name(), jb.target(),
                        jb.pool(), jb.joint(), jb.placementPriority()));
                }
                // Util.shuffle（顺序即 RNG 消耗顺序，必须保持）
                List<Jigsaw> shuffled = new ArrayList<>(jigsaws);
                for (int i = shuffled.size(); i > 1; i--) {
                    int j = rng.nextInt(i);
                    Jigsaw tmp = shuffled.get(i - 1);
                    shuffled.set(i - 1, shuffled.get(j));
                    shuffled.set(j, tmp);
                }
                // sortBySelectionPriority（古城全为 0，稳定排序等价 no-op）
                shuffled.sort(Comparator.comparingInt(Jigsaw::placementPriority).reversed());
                return shuffled;
            }
            default:
                return new ArrayList<>();
        }
    }

    private static BoundingBox elementBoundingBox(PoolElement element, int[] position, int rotation,
                                                  AncientResourceSource structureSource) {
        switch (element.type) {
            case "single": {
                AncientTemplate tpl = AncientTemplate.load(element.location, structureSource);
                if (tpl == null) return pointBb(position);
                return templateBoundingBox(position, rotation, tpl.size);
            }
            case "list": {
                List<BoundingBox> boxes = new ArrayList<>();
                for (PoolElement e : element.children) {
                    if (!"empty".equals(e.type)) boxes.add(elementBoundingBox(e, position, rotation, structureSource));
                }
                if (boxes.isEmpty()) return pointBb(position);
                BoundingBox r = boxes.get(0);
                int minX = r.minX, minY = r.minY, minZ = r.minZ, maxX = r.maxX, maxY = r.maxY, maxZ = r.maxZ;
                for (BoundingBox b : boxes) {
                    minX = Math.min(minX, b.minX); minY = Math.min(minY, b.minY); minZ = Math.min(minZ, b.minZ);
                    maxX = Math.max(maxX, b.maxX); maxY = Math.max(maxY, b.maxY); maxZ = Math.max(maxZ, b.maxZ);
                }
                return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            }
            default:
                return pointBb(position);
        }
    }

    private static BoundingBox templateBoundingBox(int[] position, int rotation, int[] size) {
        int[] delta = {size[0] - 1, size[1] - 1, size[2] - 1};
        int[] corner1 = AncientGeometry.transform(new int[]{0, 0, 0}, rotation);
        int[] corner2 = AncientGeometry.transform(delta, rotation);
        return BoundingBox.fromCorners(corner1, corner2).moved(position[0], position[1], position[2]);
    }

    private static BoundingBox pointBb(int[] position) {
        return new BoundingBox(position[0], position[1], position[2], position[0], position[1], position[2]);
    }
}
