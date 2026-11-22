package com.fo.addon.ancient.core;

/**
 * 竖井每下挖一格前的流体/不可破坏判定（纯逻辑，可单测）。
 *
 * <p>检查范围按方案：下一格方块 + 下一格的东/南/西/北四个水平邻格 + 向下前瞻 N 格。
 * 任一处是水、岩浆、气泡柱（模块把气泡柱也归入流体）就停止，不允许「先挖了再说」。
 */
public final class ShaftFluidLogic {

    /** 本次检查的结论。 */
    public enum Risk {
        /** 安全，可以继续下挖。 */
        SAFE,
        /** 下一格或四邻有流体，立即停止。 */
        FLUID,
        /** 前瞻格有流体，提前停止。 */
        LOOKAHEAD_FLUID,
        /** 遇到基岩/强化深板岩等不可破坏方块，停止。 */
        UNBREAKABLE
    }

    private ShaftFluidLogic() {
    }

    /**
     * 汇总判定。
     *
     * <p>顺序：先看当前格与四邻（最近的风险优先），再看前瞻，最后才看不可破坏。
     * 流体和不可破坏同时出现时按流体处理 —— 两者都要停，但流体原因更能指导用户换点。
     */
    public static Risk assess(boolean belowFluid, boolean northFluid, boolean southFluid,
                              boolean westFluid, boolean eastFluid, boolean[] lookaheadFluid,
                              boolean unbreakable) {
        if (belowFluid || northFluid || southFluid || westFluid || eastFluid) {
            return Risk.FLUID;
        }
        if (lookaheadFluid != null) {
            for (boolean b : lookaheadFluid) {
                if (b) return Risk.LOOKAHEAD_FLUID;
            }
        }
        if (unbreakable) return Risk.UNBREAKABLE;
        return Risk.SAFE;
    }

    /** 是否必须停止（除 SAFE 外都停）。 */
    public static boolean mustStop(Risk risk) {
        return risk != Risk.SAFE;
    }

    /** 四邻任意一处有流体。 */
    public static boolean anyNeighborFluid(boolean north, boolean south, boolean west, boolean east) {
        return north || south || west || east;
    }

    /** 给聊天栏用的中文原因。 */
    public static String reasonText(Risk risk) {
        return switch (risk) {
            case SAFE -> "安全";
            case FLUID -> "下一格或四邻有流体";
            case LOOKAHEAD_FLUID -> "向下前瞻发现流体";
            case UNBREAKABLE -> "遇到不可破坏方块";
        };
    }
}
