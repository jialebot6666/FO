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

    /**
     * 六面检测（V4.16）：根据眼睛相对方块中心的位置，返回玩家正对的那个面。
     *
     * 修复：开盒/交互此前固定 Direction.UP——盒子顶面被其他方块盖住、
     * 或玩家站在盒子侧面/下方时，UP 面交互会被服务器按视线回溯拒绝。
     * 这里按视线主导轴从六个面中选玩家正对的面，配合 InteractionUtils 对准
     * 面中心交互，顶面被盖/站位刁钻也能打开。
     *
     * @return 面 ID（与 net.minecraft.util.math.Direction.ID 一致）：
     *         0=DOWN 1=UP 2=NORTH 3=SOUTH 4=WEST 5=EAST
     */
    public static int bestFaceIndex(double eyeX, double eyeY, double eyeZ,
                                    double centerX, double centerY, double centerZ) {
        double dx = centerX - eyeX;
        double dy = centerY - eyeY;
        double dz = centerZ - eyeZ;
        double adx = Math.abs(dx), ady = Math.abs(dy), adz = Math.abs(dz);
        // 水平轴优先（玩家站盒子旁时 dy 通常很小，眼睛比盒子中心高 1 格左右）；
        // 只有玩家明显在盒子正上方/正下方时才选 UP/DOWN。
        // 玩家在哪一侧，看到的就是那一侧的面：眼睛在东 → EAST(5)，眼睛在上 → UP(1)
        if (adx >= ady && adx >= adz) return dx < 0 ? 5 : 4; // 眼睛在东 → EAST / 眼睛在西 → WEST
        if (adz >= ady && adz >= adx) return dz < 0 ? 3 : 2; // 眼睛在南 → SOUTH / 眼睛在北 → NORTH
        return dy < 0 ? 1 : 0;                               // 眼睛在上 → UP / 眼睛在下 → DOWN
    }
}
