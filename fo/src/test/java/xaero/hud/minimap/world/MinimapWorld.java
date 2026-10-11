package xaero.hud.minimap.world;

import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.container.MinimapWorldContainer;

/** 与真实 Xaero 的 {@code MinimapWorld} 同形。 */
public class MinimapWorld {
    private final WaypointSet currentWaypointSet = new WaypointSet();
    private final MinimapWorldContainer container = new MinimapWorldContainer();

    public WaypointSet getCurrentWaypointSet() {
        return currentWaypointSet;
    }

    public MinimapWorldContainer getContainer() {
        return container;
    }
}
