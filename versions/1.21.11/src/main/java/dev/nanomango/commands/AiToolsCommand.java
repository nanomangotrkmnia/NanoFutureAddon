package dev.nanomango.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.nanomango.modules.AIAssistant;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class AiToolsCommand extends Command {
    public AiToolsCommand() {
        super("aitools", "Turns ai-assistant tool use on or off.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> toggle());
        builder.then(literal("on").executes(context -> set(true)));
        builder.then(literal("off").executes(context -> set(false)));
        builder.then(literal("toggle").executes(context -> toggle()));
    }

    private int toggle() {
        AIAssistant assistant = Modules.get().get(AIAssistant.class);
        if (assistant == null) return missing();

        assistant.setTools(!assistant.toolsEnabled());
        return report(assistant);
    }

    private int set(boolean value) {
        AIAssistant assistant = Modules.get().get(AIAssistant.class);
        if (assistant == null) return missing();

        assistant.setTools(value);
        return report(assistant);
    }

    private int report(AIAssistant assistant) {
        info("Tool use is %s.", assistant.toolsEnabled() ? "on" : "off");
        return SINGLE_SUCCESS;
    }

    private int missing() {
        error("AI Assistant is not loaded.");
        return SINGLE_SUCCESS;
    }
}
