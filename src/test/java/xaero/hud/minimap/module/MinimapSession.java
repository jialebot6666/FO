package xaero.hud.minimap.module;

import xaero.hud.minimap.waypoint.WaypointSession;
import xaero.hud.minimap.world.MinimapWorldManager;

/** 与真实 Xaero 的 {@code MinimapSession} 同形。 */
public class MinimapSession {
    private final MinimapWorldManager worldManager = new MinimapWorldManager();
    private final WaypointSession waypointSession = new WaypointSession();

    public MinimapWorldManager getWorldManager() {
        return worldManager;
    }

    public WaypointSession getWaypointSession() {
        return waypointSession;
    }
}
