package dev.nanomango;

import dev.nanomango.commands.AiCommand;
import dev.nanomango.commands.ContextClearCommand;
import dev.nanomango.modules.AIAssistant;
import dev.nanomango.modules.InfiniteReach;
import dev.nanomango.modules.InstantBreak;
import dev.nanomango.modules.KeepMining;
import dev.nanomango.modules.Phase;
import dev.nanomango.modules.Reminder;
import dev.nanomango.modules.TimeShift;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class NanoFutureAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("NanoFuture");

    @Override
    public void onInitialize() {
        LOG.info("Initializing NanoFutureAddon");

        Modules.get().add(new AIAssistant());
        Modules.get().add(new InfiniteReach());
        Modules.get().add(new InstantBreak());
        Modules.get().add(new KeepMining());
        Modules.get().add(new Phase());
        Modules.get().add(new Reminder());
        Modules.get().add(new TimeShift());

        Commands.add(new AiCommand());
        Commands.add(new ContextClearCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "dev.nanomango";
    }
}
