package com.nanomango.addon.modules;

import com.nanomango.addon.Nanomango;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class InstantBreak extends Module {
    private BlockPos target;
    private Direction direction;
    private int startAge;

    public InstantBreak() {
        super(Nanomango.CATEGORY, "instant-break",
            "Starts the break with a packet, then repeatedly STOPs once the server considers it finished.");
    }

    @Override
    public void onActivate() {
        target = null;
    }

    @Override
    public void onDeactivate() {
        if (target != null) abort();
        target = null;
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!Utils.canUpdate() || !BlockUtils.canBreak(event.blockPos)) return;

        event.cancel();

        if (event.blockPos.equals(target)) return;

        if (target != null) abort();

        target = event.blockPos;
        direction = event.direction;
        startAge = mc.player.age;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, direction));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (target == null) return;

        if (!Utils.canUpdate() || mc.world.getBlockState(target).isAir()) {
            target = null;
            return;
        }

        Vec3d center = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        double range = mc.player.getBlockInteractionRange();

        if (mc.player.getEyePos().squaredDistanceTo(center) > range * range) {
            abort();
            target = null;
            return;
        }

        float delta = mc.world.getBlockState(target).calcBlockBreakingDelta(mc.player, mc.world, target);

        if (delta <= 0) {
            abort();
            target = null;
            return;
        }

        mc.player.swingHand(Hand.MAIN_HAND);

        // Vanilla finishes the block at 70% of the server's calculated progress.
        // Sending STOP from that point skips the last 30%. Repeat it because the
        // server ignores a STOP while another delayed break is pending.
        float progress = delta * (mc.player.age - startAge + 1);

        if (progress >= 0.7f) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, direction));
        }
    }

    private void abort() {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, target, direction));
        }

        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
    }
}
