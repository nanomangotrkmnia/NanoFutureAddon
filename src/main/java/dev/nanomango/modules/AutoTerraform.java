package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoTerraform extends Module {
    public enum Mode {
        Flatten,
        Clear,
        Fill
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Flatten breaks and fills, Clear only breaks, Fill only places.")
        .defaultValue(Mode.Flatten)
        .build()
    );

    private final Setting<Integer> offset = sgGeneral.add(new IntSetting.Builder()
        .name("offset")
        .description("Target layer relative to the block you stand on.")
        .defaultValue(0)
        .range(-8, 8)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Horizontal radius around you.")
        .defaultValue(4)
        .range(1, 6)
        .build()
    );

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("Layers above the target to clear.")
        .defaultValue(3)
        .range(1, 16)
        .visible(() -> mode.get() != Mode.Fill)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait after a block is finished.")
        .defaultValue(2)
        .range(0, 20)
        .build()
    );

    private final Setting<List<Block>> placeBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("place-blocks")
        .description("Blocks used to fill holes, in order of preference. Must be in your hotbar or offhand.")
        .defaultValue(Blocks.DIRT, Blocks.COBBLESTONE, Blocks.STONE)
        .visible(() -> mode.get() != Mode.Clear)
        .build()
    );

    private final Setting<Boolean> skipBlockEntities = sgGeneral.add(new BoolSetting.Builder()
        .name("skip-block-entities")
        .description("Does not break chests, spawners, signs, and other blocks with data.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates when placing blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand while breaking.")
        .defaultValue(true)
        .build()
    );

    private final Set<BlockPos> ignored = new HashSet<>();

    private BlockPos target;
    private int timer;
    private int targetTicks;
    private int targetLimit = 400;

    public AutoTerraform() {
        super(NanoFutureAddon.CATEGORY, "auto-terraform",
            "Clears, fills, or levels the blocks around you to a target layer.");
    }

    @Override
    public void onActivate() {
        target = null;
        timer = 0;
        targetTicks = 0;
        ignored.clear();
    }

    @Override
    public void onDeactivate() {
        clearTarget();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) {
            clearTarget();
            return;
        }

        int layer = groundLevel() + offset.get();

        if (target != null) {
            targetTicks++;

            if (targetTicks > targetLimit || !canBreak(target)) {
                if (targetTicks > targetLimit) ignored.add(target);
                clearTarget();
                timer = delay.get();
            } else {
                BlockUtils.breakBlock(target, swing.get());
                return;
            }
        }

        if (timer > 0) {
            timer--;
            return;
        }

        if (mode.get() != Mode.Fill) {
            BlockPos pos = findBreak(layer);

            if (pos != null) {
                target = pos;
                targetTicks = 0;
                targetLimit = breakLimit(pos);
                BlockUtils.breakBlock(target, swing.get());
                return;
            }
        }

        if (mode.get() != Mode.Clear && place(layer)) timer = delay.get();
    }

    private BlockPos findBreak(int layer) {
        int radius = this.radius.get();
        BlockPos feet = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;

                for (int y = 1; y <= height.get(); y++) {
                    BlockPos pos = new BlockPos(feet.getX() + x, layer + y, feet.getZ() + z);

                    if (ignored.contains(pos) || !canBreak(pos)) continue;

                    double distance = mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos());

                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    private boolean place(int layer) {
        int radius = this.radius.get();
        BlockPos feet = mc.player.getBlockPos();
        List<BlockPos> positions = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;

                BlockPos pos = new BlockPos(feet.getX() + x, layer, feet.getZ() + z);
                if (!mc.world.getBlockState(pos).isReplaceable() || !isInReach(pos)) continue;

                positions.add(pos);
            }
        }

        positions.sort(Comparator.comparingDouble(pos ->
            mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos())));

        for (int i = 0; i < Math.min(positions.size(), 8); i++) {
            BlockPos pos = positions.get(i);

            for (Block block : placeBlocks.get()) {
                FindItemResult item = InvUtils.findInHotbar(block.asItem());
                if (!item.found()) continue;

                if (BlockUtils.place(pos, item, rotate.get(), 50)) return true;
            }
        }

        return false;
    }

    private boolean canBreak(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);

        if (state.isAir() || !BlockUtils.canBreak(pos, state)) return false;
        if (skipBlockEntities.get() && state.hasBlockEntity()) return false;

        return isInReach(pos);
    }

    private boolean isInReach(BlockPos pos) {
        double range = mc.player.getBlockInteractionRange();
        return mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) <= range * range;
    }

    private int breakLimit(BlockPos pos) {
        float delta = mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos);
        if (delta <= 0) return 40;

        return Math.min(600, (int) Math.ceil(1 / delta) + 40);
    }

    private int groundLevel() {
        BlockPos pos = mc.player.getBlockPos();

        while (pos.getY() > mc.world.getBottomY()
            && mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
            pos = pos.down();
        }

        return pos.getY();
    }

    private void clearTarget() {
        targetTicks = 0;

        if (target != null && mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.cancelBlockBreaking();
        }

        target = null;
    }
}
