package xaero.hud.minimap.world.container;

import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager;

/** 与真实 Xaero 的 {@code MinimapWorldContainer} 同形。 */
public class MinimapWorldContainer {
    private final ThirdPartyWaypointManager thirdPartyWaypointManager = new ThirdPartyWaypointManager();

    public ThirdPartyWaypointManager getThirdPartyWaypointManager() {
        return thirdPartyWaypointManager;
    }
}
