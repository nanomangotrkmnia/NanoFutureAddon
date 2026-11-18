package com.nanomango.addon;

import com.nanomango.addon.modules.InfiniteReach;
import com.nanomango.addon.modules.InstantBreak;
import com.nanomango.addon.modules.Phase;
import com.nanomango.addon.modules.Reminder;
import com.nanomango.addon.modules.TimeShift;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Nanomango extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("nanomango");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Nanomango");

        Modules.get().add(new InfiniteReach());
        Modules.get().add(new InstantBreak());
        Modules.get().add(new Phase());
        Modules.get().add(new Reminder());
        Modules.get().add(new TimeShift());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.nanomango.addon";
    }
}
