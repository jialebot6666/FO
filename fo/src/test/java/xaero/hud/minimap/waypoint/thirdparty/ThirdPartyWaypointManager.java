package xaero.hud.minimap.waypoint.thirdparty;

import java.util.LinkedHashMap;
import java.util.Map;
import xaero.testkit.OriginKey;

/** 与真实 Xaero 同形：{@code get} 内部是 computeIfAbsent，外部模组靠它自助注册。 */
public class ThirdPartyWaypointManager {
    private final Map<OriginKey, ThirdPartyWaypoints> storage = new LinkedHashMap<>();

    public ThirdPartyWaypoints get(OriginKey origin) {
        return storage.computeIfAbsent(origin, ThirdPartyWaypoints::new);
    }

    public int getCount() {
        return storage.size();
    }
}
