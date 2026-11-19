package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import dev.nanomango.utils.ReachHelper;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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

    private final Setting<Boolean> containers = sgGeneral.add(new BoolSetting.Builder()
        .name("containers")
        .description("Opens chests and other containers from up to max-distance.")
        .defaultValue(true)
        .build()
    );

    private int cooldownTicks;

    private BlockPos remoteContainer;
    private Vec3d remoteFrom;
    private int remoteWait;
    private boolean remoteOpen;

    public InfiniteReach() {
        super(NanoFutureAddon.CATEGORY, "infinite-reach",
            "Teleports to distant blocks to break, use, or open containers, then teleports back.");
    }

    @Override
    public void onActivate() {
        cooldownTicks = 0;
        clearRemote();
    }

    @Override
    public void onDeactivate() {
        finishRemote(true);
        clearRemote();
        cooldownTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) {
            clearRemote();
            return;
        }

        if (remoteContainer != null) {
            tickRemoteContainer();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        boolean attack = mc.options.attackKey.isPressed();
        boolean use = mc.options.useKey.isPressed();
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

        if (use && containers.get() && hasContainer(pos)) {
            Vec3d from = ReachHelper.teleportOpen(pos, hit.getPos(), () ->
                mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0)));

            if (from == null) return;

            remoteContainer = pos.toImmutable();
            remoteFrom = from;
            remoteWait = 20;
            remoteOpen = false;

            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            cooldownTicks = cooldown.get();
            return;
        }

        if (!attack && !useBlocks.get()) return;

        boolean teleported = ReachHelper.teleportAction(pos, hit.getPos(), () -> {
            if (attack) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, hit.getSide()));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, hit.getSide()));
            } else {
                mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));
            }
        });

        if (!teleported) return;

        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
        cooldownTicks = cooldown.get();
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (remoteContainer == null) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket packet)) return;
        if (((IPlayerMoveC2SPacket) packet).meteor$getTag() == 1337) return;

        event.cancel();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearRemote();
        cooldownTicks = 0;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        clearRemote();
        cooldownTicks = 0;
    }

    private void tickRemoteContainer() {
        ReachHelper.hold(remoteFrom);

        if (!remoteOpen) {
            if (isContainerOpen()) {
                remoteOpen = true;
                return;
            }

            if (--remoteWait <= 0) finishRemote(false);
            return;
        }

        if (!isContainerOpen()) finishRemote(false);
    }

    private boolean isContainerOpen() {
        return mc.player.currentScreenHandler != mc.player.playerScreenHandler;
    }

    private boolean hasContainer(BlockPos pos) {
        return mc.world.getBlockState(pos).createScreenHandlerFactory(mc.world, pos) != null;
    }

    private void finishRemote(boolean closeScreen) {
        if (remoteContainer == null) return;

        if (closeScreen && Utils.canUpdate() && isContainerOpen()) {
            mc.player.closeHandledScreen();
        }

        if (remoteFrom != null && Utils.canUpdate() && mc.getNetworkHandler() != null) {
            ReachHelper.restore(remoteFrom, mc.player.getEntityPos());
        }

        clearRemote();
    }

    private void clearRemote() {
        remoteContainer = null;
        remoteFrom = null;
        remoteWait = 0;
        remoteOpen = false;
    }

    public double maxDistance() {
        return maxDistance.get();
    }

    public int packets() {
        return packets.get();
    }
}
