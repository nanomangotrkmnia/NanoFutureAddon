package com.nanomango.addon.modules;

import com.nanomango.addon.Nanomango;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class Phase extends Module {
    // ServerGamePacketListenerImpl allows 100 per received move packet; four
    // extra packets raise the square-distance limit to 500.
    private static final double MAX_DISTANCE = Math.sqrt(500);

    private Vec3d direction = Vec3d.ZERO;
    private int preJumpTicks;
    private int postJumpTicks;
    private boolean needsButtonRelease;
    private boolean allowJumpPacket;

    public Phase() {
        super(Nanomango.CATEGORY, "phase",
            "Teleports through walls while your hitbox is inside a block (e.g. sand on your head). Abuses the vanilla movement packet limit.",
            "noclip");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (postJumpTicks > 0) postJumpTicks--;

        if (!Utils.canUpdate() || !mc.player.isAlive() || mc.player.isSpectator() || mc.currentScreen != null) {
            preJumpTicks = 0;
            return;
        }

        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double left = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double up = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

        direction = new Vec3d(left, up, forward).normalize()
            .rotateY(-(float) Math.toRadians(mc.player.getYaw()));

        if (direction.lengthSquared() == 0) {
            needsButtonRelease = false;
            preJumpTicks = 0;
            return;
        }

        if (needsButtonRelease) return;

        if (mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().contract(1e-5))) {
            preJumpTicks = 0;
            return;
        }

        preJumpTicks++;

        // Hold the position back for two ticks so the jump goes out as one
        // movement packet.
        if (preJumpTicks <= 2) return;

        preJumpTicks = 0;
        needsButtonRelease = true;

        Vec3d destination = findDestination();
        if (destination == null) return;

        for (int i = 0; i < 4; i++) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, false));
        }

        mc.player.setPosition(destination);
        allowJumpPacket = true;
        postJumpTicks = 2;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerMoveC2SPacket packet)) return;

        if (allowJumpPacket && (packet instanceof PlayerMoveC2SPacket.Full
            || packet instanceof PlayerMoveC2SPacket.PositionAndOnGround)) {
            allowJumpPacket = false;
            return;
        }

        if (preJumpTicks > 0 || postJumpTicks > 0) event.cancel();
    }

    private Vec3d findDestination() {
        Vec3d start = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Box box = mc.player.getBoundingBox();

        for (double distance = 0.25; distance <= MAX_DISTANCE; distance += 0.25) {
            Vec3d offset = direction.multiply(distance);
            Box moved = box.offset(offset.x, offset.y, offset.z);

            if (mc.world.isSpaceEmpty(mc.player, moved)) return start.add(offset);
        }

        return null;
    }

    private void reset() {
        direction = Vec3d.ZERO;
        preJumpTicks = 0;
        postJumpTicks = 0;
        needsButtonRelease = false;
        allowJumpPacket = false;
    }
}
