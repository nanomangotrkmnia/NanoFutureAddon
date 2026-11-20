package dev.nanomango.ai;

import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class ContainerTracker {
    private static BlockPos lastPos;
    private static String lastDimension;
    private boolean recorded;

    @EventHandler
    private void onInteract(InteractBlockEvent event) {
        if (event.result == null || mc.world == null) return;

        BlockPos pos = event.result.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);

        if (state.createScreenHandlerFactory(mc.world, pos) == null) return;

        lastPos = pos.toImmutable();
        lastDimension = mc.world.getRegistryKey().getValue().toString();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            recorded = false;
            return;
        }

        if (recorded) return;

        AwarenessTools.container(new JsonObject());
        recorded = true;
    }

    public static BlockPos lastPos() {
        return lastPos;
    }

    public static String lastDimension() {
        return lastDimension;
    }
}
