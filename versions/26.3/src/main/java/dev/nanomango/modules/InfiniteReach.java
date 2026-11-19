package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import dev.nanomango.utils.ReachHelper;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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

        boolean attack = mc.options.keyAttack.isDown();
        boolean use = useBlocks.get() && mc.options.keyUse.isDown();
        if (!attack && !use) return;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1).scale(maxDistance.get()));

        BlockHitResult hit = mc.level.clip(new ClipContext(
            eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));

        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (mc.level.getBlockState(pos).isAir()) return;

        Vec3 center = Vec3.atCenterOf(pos);
        double range = mc.player.blockInteractionRange();

        if (mc.player.getEyePosition().distanceToSqr(center) <= range * range) return;

        boolean teleported = ReachHelper.teleportAction(pos, hit.getLocation(), () -> {
            if (attack) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, hit.getDirection()));
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, hit.getDirection()));
            } else {
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND, hit, 0));
            }
        });

        if (!teleported) return;

        if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        cooldownTicks = cooldown.get();
    }

    public double maxDistance() {
        return maxDistance.get();
    }

    public int packets() {
        return packets.get();
    }
}
