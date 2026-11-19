package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import dev.nanomango.utils.SpawnScanner;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class SpawnOverlay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Horizontal radius to scan.")
        .defaultValue(16)
        .range(1, 32)
        .build()
    );

    private final Setting<Integer> vertical = sgGeneral.add(new IntSetting.Builder()
        .name("vertical")
        .description("Blocks above and below you to scan.")
        .defaultValue(8)
        .range(1, 16)
        .build()
    );

    private final Setting<Integer> minLight = sgGeneral.add(new IntSetting.Builder()
        .name("min-light")
        .description("Show blocks at or below this light level.")
        .defaultValue(7)
        .range(0, 15)
        .build()
    );

    private final SettingColor sideColor = new SettingColor(255, 80, 80, 25);
    private final SettingColor lineColor = new SettingColor(255, 80, 80, 180);

    private final List<BlockPos> positions = new ArrayList<>();
    private int timer;

    public SpawnOverlay() {
        super(NanoFutureAddon.CATEGORY, "spawn-overlay",
            "Renders blocks where hostile mobs can spawn.");
    }

    @Override
    public void onActivate() {
        positions.clear();
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        positions.clear();
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;
        if (--timer > 0) return;

        timer = 20;
        positions.clear();
        positions.addAll(SpawnScanner.find(radius.get(), vertical.get(), minLight.get()));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!Utils.canUpdate()) return;

        for (BlockPos pos : positions) {
            event.renderer.box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                sideColor, lineColor, ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() {
        return positions.size() + " spawns";
    }
}
