package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import dev.nanomango.utils.ReachHelper;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class AutoTorch extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Horizontal radius to spawn proof around you.")
        .defaultValue(8)
        .range(1, 24)
        .build()
    );

    private final Setting<Integer> vertical = sgGeneral.add(new IntSetting.Builder()
        .name("vertical")
        .description("Blocks above and below you to scan.")
        .defaultValue(4)
        .range(1, 16)
        .build()
    );

    private final Setting<Integer> minLight = sgGeneral.add(new IntSetting.Builder()
        .name("min-light")
        .description("Torches are placed where light is at or below this level. 7 is safe on old versions.")
        .defaultValue(7)
        .range(0, 15)
        .build()
    );

    private final Setting<Integer> perTick = sgGeneral.add(new IntSetting.Builder()
        .name("per-tick")
        .description("Torches to place per attempt.")
        .defaultValue(1)
        .range(1, 4)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between placement attempts.")
        .defaultValue(2)
        .range(0, 10)
        .build()
    );

    private final Setting<Boolean> useInfiniteReach = sgGeneral.add(new BoolSetting.Builder()
        .name("use-infinite-reach")
        .description("Places torches beyond normal reach when InfiniteReach is active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards each torch placement.")
        .defaultValue(true)
        .build()
    );

    private final List<BlockPos> targets = new ArrayList<>();

    private int timer;
    private int scanTimer;
    private boolean warned;

    public AutoTorch() {
        super(NanoFutureAddon.CATEGORY, "auto-torch",
            "Places torches on dark spawnable blocks around you.");
    }

    @Override
    public void onActivate() {
        targets.clear();
        timer = 0;
        scanTimer = 0;
        warned = false;
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        timer = 0;
        scanTimer = 0;
        warned = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;
        if (timer > 0) {
            timer--;
            return;
        }

        if (--scanTimer <= 0 || targets.isEmpty()) {
            scan();
            scanTimer = 20;

            if (targets.isEmpty()) return;
        }

        FindItemResult torch = InvUtils.findInHotbar(Items.TORCH);
        if (!torch.found()) {
            if (!warned) {
                warning("No torches in the hotbar or offhand.");
                warned = true;
            }

            timer = 20;
            return;
        }

        warned = false;

        int placed = 0;
        Iterator<BlockPos> iterator = targets.iterator();

        while (iterator.hasNext() && placed < perTick.get()) {
            BlockPos pos = iterator.next();
            iterator.remove();

            if (!inRange(pos)) continue;
            if (!isSpawnable(pos)) continue;

            boolean far = !ReachHelper.inServerReach(pos);
            if (far && !useInfiniteReach.get()) continue;
            if (!ReachHelper.inReach(pos, useInfiniteReach.get())) continue;

            boolean ok = far
                ? ReachHelper.place(pos, torch, true)
                : BlockUtils.place(pos, torch, rotate.get(), 50);

            if (ok) placed++;
        }

        timer = delay.get();
    }

    private void scan() {
        targets.clear();

        BlockPos center = mc.player.getBlockPos();
        int radius = this.radius.get();
        int vertical = this.vertical.get();
        int radiusSquared = radius * radius;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radiusSquared) continue;

                for (int y = -vertical; y <= vertical; y++) {
                    BlockPos pos = center.add(x, y, z);

                    if (!isSpawnable(pos)) continue;
                    if (!ReachHelper.inReach(pos, useInfiniteReach.get())) continue;

                    targets.add(pos);
                }
            }
        }

        targets.sort(Comparator.comparingDouble(pos ->
            mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos())));

        if (targets.size() > 512) targets.subList(512, targets.size()).clear();
    }

    private boolean isSpawnable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isAir()) return false;
        if (!BlockUtils.canPlace(pos)) return false;

        BlockPos player = mc.player.getBlockPos();
        if (pos.equals(player) || pos.equals(player.up())) return false;

        BlockUtils.MobSpawn spawn = BlockUtils.isValidMobSpawn(pos, state, minLight.get());
        return spawn == BlockUtils.MobSpawn.Always || spawn == BlockUtils.MobSpawn.Potential;
    }

    private boolean inRange(BlockPos pos) {
        BlockPos center = mc.player.getBlockPos();

        int x = pos.getX() - center.getX();
        int y = pos.getY() - center.getY();
        int z = pos.getZ() - center.getZ();

        return x * x + z * z <= radius.get() * radius.get() && Math.abs(y) <= vertical.get();
    }
}
