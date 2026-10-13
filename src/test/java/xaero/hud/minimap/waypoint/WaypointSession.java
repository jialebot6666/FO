package xaero.hud.minimap.waypoint;

/** 与真实 Xaero 的 {@code WaypointSession} 同形；注意 {@code setSetChangedTime} 是基本类型参数。 */
public class WaypointSession {
    private long setChangedTime;

    public void setSetChangedTime(long time) {
        this.setChangedTime = time;
    }

    public long getSetChangedTime() {
        return setChangedTime;
    }
}
