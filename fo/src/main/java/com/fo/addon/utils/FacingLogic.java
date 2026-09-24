package com.fo.addon.utils;

/**
 * 朝向计算（纯逻辑，可单测）：给定目标点与眼睛位置，计算 yaw/pitch（度）。
 * 与 ElytraCollector.lookAtBoxCenter / faceBlockForPlace 同款算法：
 * 视线从眼睛指向目标点，yaw 绕 Y 轴（正北为 0），pitch 俯仰（水平为 0，向下为负）。
 */
public final class FacingLogic {

    private FacingLogic() {
    }

    /** @return float[]{yaw, pitch}，单位度 */
    public static float[] yawPitchTo(double targetX, double targetY, double targetZ,
                                     double eyeX, double eyeY, double eyeZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        return new float[]{yaw, pitch};
    }
}
