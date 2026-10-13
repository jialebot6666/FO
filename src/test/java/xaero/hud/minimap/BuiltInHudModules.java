package xaero.hud.minimap;

import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.module.HudModule;

/** 与真实 Xaero 的 {@code BuiltInHudModules} 同形。 */
public class BuiltInHudModules {
    public static final HudModule<MinimapSession> MINIMAP = new HudModule<>();
}
