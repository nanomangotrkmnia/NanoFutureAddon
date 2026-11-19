package dev.nanomango.utils;

import dev.nanomango.modules.InfiniteReach;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.Reach;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class ReachHelper {
    private ReachHelper() {
    }

    public static double serverReach() {
        double range = mc.player.getBlockInteractionRange();
        Reach reach = Modules.get().get(Reach.class);

        if (reach != null) range -= reach.blockReach();

        return range + 1.0;
    }

    public static double maxReach(boolean teleport) {
        if (teleport) {
            InfiniteReach infinite = Modules.get().get(InfiniteReach.class);

            if (infinite != null && infinite.isActive()) {
                return Math.max(serverReach(), infinite.maxDistance());
            }
        }

        return serverReach();
    }

    public static boolean inServerReach(BlockPos pos) {
        double range = serverReach();
        return mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) <= range * range;
    }

    public static boolean inReach(BlockPos pos, boolean teleport) {
        double range = maxReach(teleport);
        return mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) <= range * range;
    }

    public static int breakTicks(BlockPos pos) {
        float delta = mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos);
        if (delta <= 0) return 1;

        return Math.max(1, (int) Math.ceil(0.75f / delta));
    }

    public static Direction startBreak(BlockPos pos, boolean teleport) {
        Direction direction = BlockUtils.getDirection(pos);

        if (inServerReach(pos)) {
            sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction);
            return direction;
        }

        if (!teleport) return null;
        if (!teleportAction(pos, pos.toCenterPos(), () ->
            sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction))) return null;

        return direction;
    }

    public static void stopBreak(BlockPos pos, Direction direction) {
        sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction);
    }

    public static void abortBreak(BlockPos pos, Direction direction) {
        sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, direction);
    }

    public static boolean place(BlockPos pos, FindItemResult item, boolean teleport) {
        if (inServerReach(pos)) return BlockUtils.place(pos, item, false, 50);
        if (!teleport) return false;

        boolean[] placed = {false};
        teleportAction(pos, pos.toCenterPos(), () -> placed[0] = BlockUtils.place(pos, item, false, 50));

        return placed[0];
    }

    public static boolean teleportAction(BlockPos target, Vec3d hitPos, Runnable action) {
        Vec3d start = mc.player.getEntityPos();
        Vec3d center = target.toCenterPos();
        Vec3d destination = findPosition(target, center, hitPos, start);

        if (destination == null) return false;

        for (int i = 0; i < teleportPackets(); i++) sendStatus(false, false);

        sendMove(destination);
        action.run();

        Vec3d home = start.add(0.05, 0.01, 0.0);
        sendMove(start);
        sendMove(home);
        mc.player.setPosition(home);

        return true;
    }

    public static Vec3d teleportOpen(BlockPos target, Vec3d hitPos, Runnable action) {
        Vec3d start = mc.player.getEntityPos();
        Vec3d center = target.toCenterPos();
        Vec3d destination = findPosition(target, center, hitPos, start);

        if (destination == null) return null;

        for (int i = 0; i < teleportPackets(); i++) sendStatus(false, false);

        sendMove(destination);
        action.run();

        return destination;
    }

    public static void hold(Vec3d pos) {
        PlayerMoveC2SPacket.PositionAndOnGround packet =
            new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, true, false);

        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.getNetworkHandler().sendPacket(packet);
    }

    public static void restore(Vec3d from, Vec3d to) {
        int packets = statusCount(from.distanceTo(to));

        for (int i = 0; i < packets; i++) sendStatus(false, false);
        sendMove(to);
    }

    private static int statusCount(double distance) {
        int packets = (int) Math.ceil(distance * distance / 90.0);
        return Math.max(4, packets);
    }

    private static int teleportPackets() {
        InfiniteReach infinite = Modules.get().get(InfiniteReach.class);
        return infinite != null && infinite.isActive() ? infinite.packets() : 4;
    }

    private static Vec3d findPosition(BlockPos target, Vec3d center, Vec3d hitPos, Vec3d start) {
        Box base = mc.player.getBoundingBox();
        double reach = serverReach() - 0.5;
        double limit = maxReach(true);
        Vec3d best = null;
        double bestDistance = Double.MAX_VALUE;

        for (double x = -3.5; x <= 3.5; x += 0.5) {
            for (double y = -3.5; y <= 3.5; y += 0.5) {
                for (double z = -3.5; z <= 3.5; z += 0.5) {
                    Vec3d pos = new Vec3d(center.x + x, center.y + y, center.z + z);

                    if (BlockPos.ofFloored(pos.x, pos.y, pos.z).equals(target)) continue;
                    if (pos.squaredDistanceTo(center) > reach * reach) continue;
                    if (pos.squaredDistanceTo(start) > limit * limit) continue;

                    Box box = base.offset(pos.x - start.x, pos.y - start.y, pos.z - start.z);
                    if (!mc.world.isSpaceEmpty(mc.player, box)) continue;

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

    private static void sendAction(PlayerActionC2SPacket.Action action, BlockPos pos, Direction direction) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, direction));
    }

    private static void sendMove(Vec3d pos) {
        PlayerMoveC2SPacket.PositionAndOnGround packet =
            new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, false, false);

        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.getNetworkHandler().sendPacket(packet);
    }

    private static void sendStatus(boolean onGround, boolean collision) {
        PlayerMoveC2SPacket.OnGroundOnly packet = new PlayerMoveC2SPacket.OnGroundOnly(onGround, collision);

        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.getNetworkHandler().sendPacket(packet);
    }
}
