package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.entity.player.StoppedUsingItemEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class FastThrow extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> xVelocity = sgGeneral.add(new DoubleSetting.Builder()
        .name("x-velocity")
        .description("X offset sent before a bow release or throw.")
        .defaultValue(0.1)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    public FastThrow() {
        super(NanoFutureAddon.CATEGORY, "fast-throw",
            "Sends a small X velocity before you release a bow or throw an item.");
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        if (mc.player == null) return;

        ItemStack stack = mc.player.getStackInHand(event.hand);
        if (isThrowable(stack)) nudge();
    }

    @EventHandler
    private void onSendMovementPackets(SendMovementPacketsEvent.Post event) {
        if (mc.player == null || !mc.player.isUsingItem()) return;
        if (isBow(mc.player.getActiveItem())) nudge();
    }

    @EventHandler
    private void onStoppedUsing(StoppedUsingItemEvent event) {
        if (isBow(event.itemStack)) nudge();
    }

    private void nudge() {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.player.isSpectator()) return;

        double offset = xVelocity.get();
        if (offset <= 0) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        boolean onGround = mc.player.isOnGround();
        boolean collision = mc.player.horizontalCollision;

        for (int i = 0; i < 4; i++) sendPos(x, y, z, onGround, collision);
        sendPos(x - offset, y, z, onGround, collision);
        sendPos(x, y, z, onGround, collision);
    }

    private void sendPos(double x, double y, double z, boolean onGround, boolean collision) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, onGround, collision));
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;

        return stack.getItem() instanceof EnderPearlItem
            || stack.getItem() instanceof SnowballItem
            || stack.getItem() instanceof EggItem
            || stack.getItem() instanceof ThrowablePotionItem
            || stack.getItem() instanceof ExperienceBottleItem;
    }

    private boolean isBow(ItemStack stack) {
        return !stack.isEmpty()
            && (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem);
    }
}
