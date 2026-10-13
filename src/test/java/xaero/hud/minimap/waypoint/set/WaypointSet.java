package xaero.hud.minimap.waypoint.set;

import java.util.ArrayList;
import java.util.List;
import xaero.common.minimap.waypoints.Waypoint;

/**
 * 与真实 Xaero 的 {@code WaypointSet} 同形，并<b>故意保留同名重载</b>
 * {@code remove(Waypoint)} / {@code remove(int)}——用来验证桥按参数类型而不是按下标选方法。
 */
public class WaypointSet {
    private final List<Waypoint> list = new ArrayList<>();

    public Iterable<Waypoint> getWaypoints() {
        return list;
    }

    public void add(Waypoint waypoint) {
        list.add(waypoint);
    }

    public void add(Waypoint waypoint, boolean global) {
        list.add(waypoint);
    }

    public void remove(Waypoint waypoint) {
        list.remove(waypoint);
    }

    public Waypoint remove(int index) {
        return list.remove(index);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public int size() {
        return list.size();
    }

    public Waypoint get(int index) {
        return list.get(index);
    }
}
