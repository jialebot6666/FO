package com.fo.addon.elytra;
import com.fo.addon.elytra.core.FOElytraLog;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public abstract class FOElytraModule extends Module {
    protected static final Logger LOG = LoggerFactory.getLogger("FO-Elytra");
    protected FOElytraModule(String name, String description) {
        super(com.fo.addon.AddonTemplate.CATEGORY, name, description);
    }
    protected FOElytraModule(String name, String description, String... aliases) {
        super(com.fo.addon.AddonTemplate.CATEGORY, name, description, aliases);
    }
    protected void onError(String phase, Throwable t) {
        LOG.error("{} failed in {}", name, phase, t);
        try {
            error("内部异常 [%s]: %s", phase, String.valueOf(t));
        } catch (Throwable ignored) {
        }
    }
    protected void debugMsg(String fmt, Object... args) {
        FOElytraLog.debug(fmt, args);
    }
}
