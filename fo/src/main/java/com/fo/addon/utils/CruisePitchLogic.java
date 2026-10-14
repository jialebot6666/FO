package com.fo.addon.utils;

/**
 * 巡航俯仰曲线（对齐 Meteor ElytraFly 的 Pitch40 模式：「快拉慢压」）。
 *
 * <p>Meteor 的 Pitch40 模式不是「爬升时恒定一个角度」，而是把俯仰角当成一个逐 tick 推进的量：
 * 上升段以较快的速度把视角拉到抬头极限，一到位就翻成滑翔段；滑翔段再以很慢的速度把视角压回下滑角。
 * 这样爬升是「一段抛物线」而不是「一条固定斜线」，同样的高度增益下更省推力、水平推进更多。
 *
 * <p>本类为纯逻辑，不依赖 MC 运行时，可单元测试。
 */
public final class CruisePitchLogic {
    private CruisePitchLogic() {
    }

    /**
     * 推进一 tick 的俯仰角（度，负值 = 抬头）。
     *
     * @param current    当前俯仰角（度）
     * @param climbing   true = 爬升段（目标 {@code -climbAngle}），false = 滑翔段（目标 {@code +glideAngle}）
     * @param climbAngle 巡航爬升角度（正值，例如 54.77 → 目标 -54.77）
     * @param glideAngle 巡航滑翔角度（正值，例如 37.72 → 目标 +37.72）
     * @param upSpeed    抬头速度（度/tick，越大拉得越快）
     * @param downSpeed  下压速度（度/tick，越小压得越慢）
     * @return 推进后的俯仰角（不会越过目标）
     */
    public static float step(float current, boolean climbing,
                             double climbAngle, double glideAngle,
                             double upSpeed, double downSpeed) {
        if (climbing) {
            float target = (float) -climbAngle;
            if (current > target) {
                current -= (float) upSpeed;
                if (current < target) current = target;
            }
            return current;
        }

        float target = (float) glideAngle;
        if (current < target) {
            current += (float) downSpeed;
            if (current > target) current = target;
        }
        return current;
    }
}
