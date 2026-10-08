package com.fo.addon.elytra;

import com.fo.addon.AddonTemplate;
import com.fo.addon.elytra.core.FOElytraLog;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FOElytraModule
extends Module {
    protected static final Logger LOG = LoggerFactory.getLogger((String)"FO");

    protected FOElytraModule(String name, String description) {
        super(AddonTemplate.CATEGORY, name, description);
    }

    protected FOElytraModule(String name, String description, String ... aliases) {
        super(AddonTemplate.CATEGORY, name, description, aliases);
    }

    protected void onError(String phase, Throwable t) {
        LOG.error("{} failed in {}", new Object[]{this.name, phase, t});
        try {
            this.error("\u5185\u90e8\u5f02\u5e38 [%s]: %s", new Object[]{phase, String.valueOf(t)});
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    protected void debugMsg(String fmt, Object ... args) {
        FOElytraLog.debug(fmt, args);
    }
}

