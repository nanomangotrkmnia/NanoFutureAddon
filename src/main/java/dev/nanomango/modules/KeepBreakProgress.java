package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class KeepBreakProgress extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand while the block is being broken.")
        .defaultValue(true)
        .build()
    );

    private BlockPos target;
    private Direction direction;

    public KeepBreakProgress() {
        super(NanoFutureAddon.CATEGORY, "keep-break-progress",
            "Keeps breaking the block you started after you release attack.");
    }

    @Override
    public void onActivate() {
        clear();
    }

    @Override
    public void onDeactivate() {
        clear();
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!Utils.canUpdate()) return;

        target = event.blockPos.toImmutable();
        direction = event.direction;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (target == null) return;

        if (!Utils.canUpdate()) {
            clear();
            return;
        }

        BlockState state = mc.world.getBlockState(target);
        if (state.isAir() || !BlockUtils.canBreak(target, state)) {
            clear();
            return;
        }

        Vec3d center = target.toCenterPos();
        double range = mc.player.getBlockInteractionRange();

        if (mc.player.getEyePos().squaredDistanceTo(center) > range * range) {
            clear();
            return;
        }

        // Vanilla already updates this block while attack is held on it.
        if (isVanillaMining()) return;

        mc.interactionManager.updateBlockBreakingProgress(target, direction);

        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    public boolean shouldKeepBreaking() {
        return isActive() && target != null && Utils.canUpdate();
    }

    private boolean isVanillaMining() {
        if (mc.currentScreen != null || !mc.options.attackKey.isPressed()) return false;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return false;

        return hit.getBlockPos().equals(target);
    }

    private void clear() {
        target = null;

        if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }
}
