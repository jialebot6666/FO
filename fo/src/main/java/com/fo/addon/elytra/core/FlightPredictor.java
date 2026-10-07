package com.fo.addon.elytra.core;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.Vec3d;
public final class FlightPredictor {
    private FlightPredictor() {}
    public static List<Vec3d> predictPath(int ticks, Vec3d pos, Vec3d vel, Vec3d look) {
        List<Vec3d> points = new ArrayList<>(ticks);
        Vec3d p = pos;
        Vec3d v = vel;
        for (int i = 0; i < ticks; i++) {
            points.add(p);
            v = v.add(
                look.x * 0.1 + (look.x * 1.5 - v.x) * 0.5,
                look.y * 0.1 + (look.y * 1.5 - v.y) * 0.5,
                look.z * 0.1 + (look.z * 1.5 - v.z) * 0.5);
            double horizontalVel = v.horizontalLength();
            float f = (float) look.y;
            float g = f <= -0.5F ? 2.0F : 1.0F;
            v = v.add(0.0, -0.08 + (double) f * 0.06 * (double) g, 0.0);
            if (v.y < 0.0 && horizontalVel > 0.0) {
                double lift = v.y * -0.1 * (double) g;
                v = v.add(look.x * lift / horizontalVel, lift, look.z * lift / horizontalVel);
            }
            v = v.multiply(0.99, 0.98, 0.99);
            p = p.add(v);
        }
        return points;
    }
}
