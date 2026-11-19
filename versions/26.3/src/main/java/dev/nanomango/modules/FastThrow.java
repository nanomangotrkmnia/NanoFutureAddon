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
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

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
        ItemStack stack = mc.player.getItemInHand(event.hand);
        if (isThrowable(stack)) nudge();
    }

    @EventHandler
    private void onSendMovementPackets(SendMovementPacketsEvent.Post event) {
        if (mc.player == null || !mc.player.isUsingItem()) return;
        if (isBow(mc.player.getUseItem())) nudge();
    }

    @EventHandler
    private void onStoppedUsing(StoppedUsingItemEvent event) {
        if (isBow(event.itemStack)) nudge();
    }

    private void nudge() {
        if (mc.player == null || mc.getConnection() == null) return;

        double x = mc.player.getX() + xVelocity.get();

        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
            x, mc.player.getY(), mc.player.getZ(), mc.player.onGround(), mc.player.horizontalCollision));
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;

        return stack.getItem() instanceof EnderpearlItem
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
