package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class TimeShift extends Module {
    public enum PacketType {
        OnGroundOnly,
        PositionAndOnGround,
        LookAndOnGround,
        Full
    }

    private final SettingGroup sgHealth = settings.createGroup("Health");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> health = sgHealth.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Accelerate health regeneration below the trigger health.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> triggerHealth = sgHealth.add(new IntSetting.Builder()
        .name("health")
        .description("Health below which packets are sent.")
        .defaultValue(16)
        .range(0, 20)
        .build()
    );

    private final Setting<Integer> increment = sgHealth.add(new IntSetting.Builder()
        .name("increment")
        .description("Multiplier applied to the missing health.")
        .defaultValue(20)
        .range(0, 60)
        .build()
    );

    private final Setting<Boolean> badEffects = sgGeneral.add(new BoolSetting.Builder()
        .name("bad-effects")
        .description("Shorten harmful status effects.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> fire = sgGeneral.add(new BoolSetting.Builder()
        .name("fire")
        .description("Shorten fire ticks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maximumSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("maximum-speed")
        .description("Packet cap for bad-effects.")
        .defaultValue(100)
        .range(5, 200)
        .build()
    );

    private final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
        .name("timer")
        .description("Client timer multiplier while packets are being sent.")
        .defaultValue(1)
        .range(0.1, 10)
        .build()
    );

    private final Setting<PacketType> packetType = sgGeneral.add(new EnumSetting.Builder<PacketType>()
        .name("packet-type")
        .description("Movement packet to spam.")
        .defaultValue(PacketType.Full)
        .build()
    );

    private final Setting<Boolean> pauseInAir = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-air")
        .description("Stop while airborne.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseWhenMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-when-moving")
        .description("Stop while using movement keys.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseDuringRegeneration = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-during-regeneration")
        .description("Stop while the Regeneration effect is active.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseWhenHungry = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-when-hungry")
        .description("Stop unless the hunger bar is full.")
        .defaultValue(false)
        .build()
    );

    private boolean timerOverridden;

    public TimeShift() {
        super(NanoFutureAddon.CATEGORY, "time-shift",
            "Sends movement packet bursts to speed up regen and effects on 1.8 servers. Client 1.17+ is supported.",
            "zoot", "regen");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) return;

        ServerInfo server = mc.getCurrentServerEntry();
        if (server == null || FabricLoader.getInstance().isModLoaded("viafabricplus")) return;

        if (server.protocolVersion > 47) {
            warning("This server is not 1.8. TimeShift will not speed anything up.");
        }
    }

    @Override
    public void onDeactivate() {
        resetTimer();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || !mc.player.isAlive() || mc.player.getAbilities().creativeMode || shouldPause()) {
            resetTimer();
            return;
        }

        int speed = 0;

        if (health.get()) {
            double missing = triggerHealth.get() - mc.player.getHealth();
            if (missing > 0) speed = Math.max(speed, (int) (missing * increment.get()));
        }

        if (fire.get() && mc.player.isOnFire() && !mc.player.isTouchingWaterOrRain()) {
            speed = Math.max(speed, 9);
        }

        if (badEffects.get()) {
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                if (effect.getEffectType().value().getCategory() != StatusEffectCategory.HARMFUL) continue;

                int duration = effect.getDuration();
                if (duration <= 0) continue;

                speed = Math.max(speed, Math.min(duration / 20, maximumSpeed.get()));
            }
        }

        if (speed <= 0) {
            resetTimer();
            return;
        }

        Modules.get().get(Timer.class).setOverride(timer.get());
        timerOverridden = true;

        for (int i = 0; i < speed; i++) {
            mc.getNetworkHandler().sendPacket(createPacket());
        }
    }

    private void resetTimer() {
        if (!timerOverridden) return;

        Modules.get().get(Timer.class).setOverride(Timer.OFF);
        timerOverridden = false;
    }

    private boolean shouldPause() {
        if (pauseInAir.get() && !mc.player.isOnGround()) return true;
        if (pauseWhenMoving.get() && isMoving()) return true;
        if (pauseDuringRegeneration.get() && mc.player.hasStatusEffect(StatusEffects.REGENERATION)) return true;
        return pauseWhenHungry.get() && mc.player.getHungerManager().getFoodLevel() < 20;
    }

    private boolean isMoving() {
        return mc.options.forwardKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed();
    }

    private PlayerMoveC2SPacket createPacket() {
        boolean onGround = mc.player.isOnGround();
        boolean collision = mc.player.horizontalCollision;

        return switch (packetType.get()) {
            case OnGroundOnly -> new PlayerMoveC2SPacket.OnGroundOnly(onGround, collision);
            case PositionAndOnGround -> new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), onGround, collision);
            case LookAndOnGround -> new PlayerMoveC2SPacket.LookAndOnGround(
                mc.player.getYaw(), mc.player.getPitch(), onGround, collision);
            case Full -> new PlayerMoveC2SPacket.Full(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYaw(), mc.player.getPitch(), onGround, collision);
        };
    }
}
