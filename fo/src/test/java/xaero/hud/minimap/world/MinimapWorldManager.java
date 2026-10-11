package xaero.hud.minimap.world;

/** 与真实 Xaero 的 {@code MinimapWorldManager} 同形。 */
public class MinimapWorldManager {
    private MinimapWorld currentWorld;

    public MinimapWorld getCurrentWorld() {
        return currentWorld;
    }

    public void setCurrentWorldForTest(MinimapWorld world) {
        this.currentWorld = world;
    }
}
