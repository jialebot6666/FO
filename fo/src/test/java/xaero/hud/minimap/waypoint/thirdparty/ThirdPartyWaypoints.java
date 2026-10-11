package xaero.hud.minimap.waypoint.thirdparty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.testkit.OriginKey;

/** 与真实 Xaero 的 {@code ThirdPartyWaypoints} 同形（按 id 增删）。 */
public class ThirdPartyWaypoints {
    private final OriginKey origin;
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    public ThirdPartyWaypoints(OriginKey origin) {
        this.origin = origin;
    }

    public void add(String id, Waypoint waypoint) {
        waypoints.put(id, waypoint);
    }

    public void remove(String id) {
        waypoints.remove(id);
    }

    /** 真实 Xaero 也有这个方法；桥靠它先探存在性再报「删掉了没有」。 */
    public Waypoint get(String id) {
        return waypoints.get(id);
    }

    public Iterable<String> getIds() {
        return waypoints.keySet();
    }

    public OriginKey getOrigin() {
        return origin;
    }

    public Map<String, Waypoint> getAll() {
        return Collections.unmodifiableMap(waypoints);
    }
}
