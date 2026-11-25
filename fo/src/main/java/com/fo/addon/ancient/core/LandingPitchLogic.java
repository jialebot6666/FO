package com.fo.addon.ancient.core;

/**
 * 进近俯仰（纯逻辑，可单测）。
 *
 * <p>离得远按高度差俯冲（限 5–45°），3 格以内松前进缓降，不会抬成 -45°。
 */
public final class LandingPitchLogic {

    private LandingPitchLogic() {
    }

    public static float pitchFor(double dy, double horizontalDistance) {
        if (horizontalDistance <= 3.0) return 0.0f;
        return (float) Math.max(5.0, Math.min(45.0, dy * 0.8));
    }
}
