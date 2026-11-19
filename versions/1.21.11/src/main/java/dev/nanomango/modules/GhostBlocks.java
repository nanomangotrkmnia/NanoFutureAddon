package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;

public class GhostBlocks extends Module {
    public enum PlaceMode {
        RightClick,
        SneakRightClick
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Block> block = sgGeneral.add(new BlockSetting.Builder()
        .name("block")
        .description("The block to place as a ghost.")
        .defaultValue(Blocks.STONE)
        .build()
    );

    private final Setting<PlaceMode> placeMode = sgGeneral.add(new EnumSetting.Builder<PlaceMode>()
        .name("place-mode")
        .description("When right click places a ghost block.")
        .defaultValue(PlaceMode.RightClick)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait between placements.")
        .defaultValue(2)
        .range(0, 20)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand when placing or breaking.")
        .defaultValue(true)
        .build()
    );

    private record Ghost(BlockState state, BlockState previous) {}

    private final Map<BlockPos, Ghost> ghosts = new HashMap<>();

    private int timer;

    public GhostBlocks() {
        super(NanoFutureAddon.CATEGORY, "ghost-blocks",
            "Places client-side ghost blocks that stay until broken in one hit.");
    }

    @Override
    public void onActivate() {
        ghosts.clear();
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.world != null) {
            for (Map.Entry<BlockPos, Ghost> entry : ghosts.entrySet()) {
                mc.world.setBlockState(entry.getKey(), entry.getValue().previous());
            }
        }

        ghosts.clear();
        timer = 0;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        ghosts.clear();
        timer = 0;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        ghosts.clear();
        timer = 0;
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (!Utils.canUpdate() || mc.currentScreen != null) return;
        if (placeMode.get() == PlaceMode.SneakRightClick && !mc.player.isSneaking()) return;
        if (!mc.options.useKey.isPressed()) return;

        BlockPos pos = placementPos(event.result);
        if (pos == null) return;

        // Swallow the interaction either way so held right click never reaches the server.
        event.cancel();

        if (timer > 0) return;

        ghosts.put(pos, new Ghost(block.get().getDefaultState(), mc.world.getBlockState(pos)));
        mc.world.setBlockState(pos, block.get().getDefaultState());

        if (swing.get()) mc.player.swingHand(event.hand);
        timer = delay.get();
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        Ghost ghost = ghosts.remove(event.blockPos);
        if (ghost == null) return;

        event.cancel();

        if (mc.interactionManager.isBreakingBlock()) mc.interactionManager.cancelBlockBreaking();
        mc.world.setBlockState(event.blockPos, ghost.previous());

        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
        timer = delay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (timer > 0) timer--;
        if (ghosts.isEmpty() || mc.world == null) return;

        for (Map.Entry<BlockPos, Ghost> entry : ghosts.entrySet()) {
            BlockState state = entry.getValue().state();
            if (mc.world.getBlockState(entry.getKey()) != state) mc.world.setBlockState(entry.getKey(), state);
        }
    }

    private BlockPos placementPos(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos().offset(hit.getSide());

        if (!mc.world.isInBuildLimit(pos)) return null;
        if (!mc.world.getBlockState(pos).isReplaceable()) return null;
        if (mc.player.getBoundingBox().intersects(new Box(pos))) return null;

        double range = mc.player.getBlockInteractionRange();
        if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > range * range) return null;

        return pos.toImmutable();
    }
}
