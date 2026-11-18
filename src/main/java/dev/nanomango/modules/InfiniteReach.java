package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class InfiniteReach extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum reach in blocks.")
        .defaultValue(22)
        .min(4.5)
        .max(22)
        .build()
    );

    private final Setting<Integer> packets = sgGeneral.add(new IntSetting.Builder()
        .name("packets")
        .description("Status packets sent before the teleport. Vanilla allows 22 blocks with four.")
        .defaultValue(4)
        .range(1, 5)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Ticks between actions.")
        .defaultValue(5)
        .range(1, 20)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your hand after acting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("use-blocks")
        .description("Also use blocks on right click.")
        .defaultValue(true)
        .build()
    );

    private int cooldownTicks;

    public InfiniteReach() {
        super(NanoFutureAddon.CATEGORY, "infinite-reach",
            "Teleports to distant blocks to break or use them, then teleports back.");
    }

    @Override
    public void onActivate() {
        cooldownTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        boolean attack = mc.options.attackKey.isPressed();
        boolean use = useBlocks.get() && mc.options.useKey.isPressed();
        if (!attack && !use) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(mc.player.getRotationVec(1).multiply(maxDistance.get()));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
            eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));

        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (mc.world.getBlockState(pos).isAir()) return;

        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double range = mc.player.getBlockInteractionRange();

        if (mc.player.getEyePos().squaredDistanceTo(center) <= range * range) return;

        Vec3d start = mc.player.getEntityPos();
        Vec3d destination = findPosition(pos, center, hit.getPos(), start);
        if (destination == null) return;

        for (int i = 0; i < packets.get(); i++) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, false));
        }

        sendMove(destination);

        if (attack) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, hit.getSide()));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, hit.getSide()));
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));
        }

        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);

        Vec3d home = start.add(0.05, 0.01, 0);
        sendMove(start);
        sendMove(home);
        mc.player.setPosition(home);

        cooldownTicks = cooldown.get();
    }

    private Vec3d findPosition(BlockPos target, Vec3d center, Vec3d hitPos, Vec3d start) {
        Box base = mc.player.getBoundingBox();
        double maxReach = mc.player.getBlockInteractionRange() - 0.5;
        Vec3d best = null;
        double bestDistance = Double.MAX_VALUE;

        for (double dx = -3; dx <= 3; dx += 0.5) {
            for (double dy = -3; dy <= 3; dy += 0.5) {
                for (double dz = -3; dz <= 3; dz += 0.5) {
                    Vec3d pos = new Vec3d(center.x + dx, center.y + dy, center.z + dz);
                    if (BlockPos.ofFloored(pos.x, pos.y, pos.z).equals(target)) continue;
                    if (pos.squaredDistanceTo(center) > maxReach * maxReach) continue;
                    if (pos.squaredDistanceTo(start) > maxDistance.get() * maxDistance.get()) continue;

                    Box box = base.offset(pos.x - start.x, pos.y - start.y, pos.z - start.z);
                    if (!mc.world.isSpaceEmpty(mc.player, box)) continue;

                    // Stay on the visible face so the teleport path stays clear.
                    double distance = pos.squaredDistanceTo(hitPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    private void sendMove(Vec3d pos) {
        PlayerMoveC2SPacket.PositionAndOnGround packet =
            new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, false, false);

        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.getNetworkHandler().sendPacket(packet);
    }
}
