package dev.nanomango.modules;

import dev.nanomango.NanoFutureAddon;
import dev.nanomango.utils.BaritoneSupport;
import dev.nanomango.utils.ReachHelper;
import dev.nanomango.utils.BaritoneBridge;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class AutoChunkMiner extends Module {
    public enum Mode {
        Baritone,
        Reach
    }

    public enum Area {
        Chunk,
        Radius
    }

    public enum Order {
        Nearest,
        Serpentine
    }

    public enum MineMode {
        Legit,
        Instant
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Baritone lets it walk and mine on its own. Reach only mines blocks in range.")
        .defaultValue(Mode.Baritone)
        .build()
    );

    private final Setting<Integer> targetY = sgGeneral.add(new IntSetting.Builder()
        .name("target-y")
        .description("Y level to mine down to.")
        .defaultValue(0)
        .range(-64, 320)
        .build()
    );

    private final Setting<Integer> startOffset = sgGeneral.add(new IntSetting.Builder()
        .name("start-offset")
        .description("Layers above the block you stand on to start from.")
        .defaultValue(3)
        .range(1, 64)
        .build()
    );

    private final Setting<Area> area = sgGeneral.add(new EnumSetting.Builder<Area>()
        .name("area")
        .description("Mine a full chunk or a radius around you.")
        .defaultValue(Area.Chunk)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Radius used by the Radius area.")
        .defaultValue(4)
        .range(1, 16)
        .visible(() -> area.get() == Area.Radius)
        .build()
    );

    private final Setting<Order> order = sgGeneral.add(new EnumSetting.Builder<Order>()
        .name("order")
        .description("Block order inside a layer.")
        .defaultValue(Order.Nearest)
        .build()
    );

    private final Setting<MineMode> mineMode = sgGeneral.add(new EnumSetting.Builder<MineMode>()
        .name("mine-mode")
        .description("Legit drives vanilla mining. Instant sends the break packets itself.")
        .defaultValue(MineMode.Instant)
        .build()
    );

    private final Setting<Boolean> useInfiniteReach = sgGeneral.add(new BoolSetting.Builder()
        .name("use-infinite-reach")
        .description("Uses InfiniteReach to break blocks up to its max distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> sliceHeight = sgGeneral.add(new IntSetting.Builder()
        .name("slice-height")
        .description("Layers cleared per Baritone job.")
        .defaultValue(4)
        .range(1, 8)
        .visible(() -> mode.get() == Mode.Baritone)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait between blocks.")
        .defaultValue(0)
        .range(0, 20)
        .build()
    );

    private final Setting<Boolean> breakUnder = sgGeneral.add(new BoolSetting.Builder()
        .name("break-under")
        .description("Breaks the block under you to drop to the next layer.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand while mining with Legit mode.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopWhenDone = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-done")
        .description("Disables the module when the target Y is reached.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> skipFluids = sgFilter.add(new BoolSetting.Builder()
        .name("skip-fluids")
        .description("Skips waterlogged and fluid blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> skipBlockEntities = sgFilter.add(new BoolSetting.Builder()
        .name("skip-block-entities")
        .description("Skips chests, spawners, signs, and other blocks with data.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> blacklist = sgFilter.add(new BlockListSetting.Builder()
        .name("blacklist")
        .description("Blocks to never mine.")
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the current layer.")
        .defaultValue(true)
        .build()
    );

    private final SettingColor sideColor = new SettingColor(30, 200, 60, 30);
    private final SettingColor lineColor = new SettingColor(30, 200, 60, 160);

    private final List<BlockPos> queue = new ArrayList<>();
    private final Set<BlockPos> failed = new HashSet<>();

    private boolean initialized;
    private boolean finished;
    private boolean useBaritone;
    private boolean descending;

    private int chunkX;
    private int chunkZ;
    private int layer;
    private int timer;

    private BlockPos target;
    private Direction targetDirection;
    private int targetTicks;
    private int targetLimit;
    private int breakWait;
    private int startAge;
    private int teleportCooldown;

    private boolean baritoneJob;
    private boolean baritoneStarted;
    private boolean baritoneUsed;
    private int baritoneTicks;
    private int baritoneBottom;

    public AutoChunkMiner() {
        super(NanoFutureAddon.CATEGORY, "auto-chunk-miner",
            "Mines a chunk from above your head down to a target Y.");
    }

    @Override
    public void onActivate() {
        initialized = false;
        finished = false;
        useBaritone = false;
        descending = false;
        target = null;
        breakWait = 0;
        teleportCooldown = 0;
        timer = 0;
        layer = 0;
        queue.clear();
        failed.clear();
        baritoneJob = false;
        baritoneStarted = false;
        baritoneUsed = false;
        baritoneTicks = 0;
        baritoneBottom = 0;
    }

    @Override
    public void onDeactivate() {
        target = null;
        queue.clear();
        failed.clear();
        baritoneJob = false;
        baritoneStarted = false;

        if (baritoneUsed) {
            baritoneUsed = false;
            if (BaritoneBridge.isBusy()) BaritoneBridge.stop();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        if (!initialized) {
            if (!initialize()) {
                toggle();
                return;
            }

            initialized = true;
            return;
        }

        if (finished) return;

        if (teleportCooldown > 0) teleportCooldown--;

        if (useBaritone) {
            tickBaritone();
            return;
        }

        if (target != null) {
            tickTarget();
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        if (queue.isEmpty()) {
            nextLayer();
            return;
        }

        BlockPos pos = nextTarget();
        if (pos == null) return;

        queue.remove(pos);
        startTarget(pos);
    }

    private boolean initialize() {
        int end = targetY.get();
        int base = groundLevel();
        int start = base + startOffset.get();

        if (start <= end) {
            error("Target Y must be below the start layer (Y %d).", start);
            return false;
        }

        chunkX = mc.player.blockPosition().getX() >> 4;
        chunkZ = mc.player.blockPosition().getZ() >> 4;
        layer = start;
        finished = false;
        descending = false;
        target = null;
        timer = 0;
        failed.clear();
        queue.clear();

        useBaritone = mode.get() == Mode.Baritone && BaritoneSupport.AVAILABLE;

        if (mode.get() == Mode.Baritone && !useBaritone) {
            warning("Baritone is not installed, using Reach mode.");
        }

        if (!useBaritone) buildLayer();

        info("Mining from Y %d down to Y %d.", start, end);
        return true;
    }

    private void buildLayer() {
        queue.clear();
        failed.clear();

        int[] bounds = bounds();
        BlockPos support = mc.player.blockPosition().below();

        if (order.get() == Order.Serpentine) {
            for (int z = bounds[2]; z <= bounds[3]; z++) {
                boolean reverse = ((z - bounds[2]) & 1) == 1;

                for (int i = 0; i <= bounds[1] - bounds[0]; i++) {
                    int x = reverse ? bounds[1] - i : bounds[0] + i;
                    add(x, z, support);
                }
            }
        } else {
            for (int x = bounds[0]; x <= bounds[1]; x++) {
                for (int z = bounds[2]; z <= bounds[3]; z++) add(x, z, support);
            }
        }
    }

    private void add(int x, int z, BlockPos support) {
        BlockPos pos = new BlockPos(x, layer, z);

        if (pos.equals(support)) return;
        if (!isBreakable(pos)) return;

        queue.add(pos);
    }

    private BlockPos nextTarget() {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Iterator<BlockPos> iterator = queue.iterator(); iterator.hasNext();) {
            BlockPos pos = iterator.next();

            if (failed.contains(pos)) {
                iterator.remove();
                continue;
            }

            if (!isBreakable(pos)) {
                iterator.remove();
                continue;
            }

            if (!ReachHelper.inReach(pos, canTeleport())) continue;

            if (order.get() == Order.Serpentine) return pos;

            double distance = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }

        return best;
    }

    private void startTarget(BlockPos pos) {
        target = pos;
        targetTicks = 0;
        targetLimit = breakLimit(pos) + 20;
        startAge = mc.player.tickCount;

        boolean far = !ReachHelper.inServerReach(pos);

        if (mineMode.get() == MineMode.Legit) {
            BlockUtils.breakBlock(pos, swing.get());
            return;
        }

        Direction direction = ReachHelper.startBreak(pos, far && canTeleport());

        if (direction == null) {
            failed.add(pos);
            target = null;
            timer = delay.get();
            return;
        }

        targetDirection = direction;
        breakWait = ReachHelper.breakTicks(pos);

        if (far) teleportCooldown = 5;
    }

    private void tickTarget() {
        targetTicks++;

        BlockState state = mc.level.getBlockState(target);

        if (state.isAir()) {
            completeTarget();
            return;
        }

        if (mineMode.get() == MineMode.Legit) {
            if (!ReachHelper.inServerReach(target) || targetTicks > targetLimit) {
                failed.add(target);
                target = null;
                descending = false;
                timer = delay.get();
                return;
            }

            BlockUtils.breakBlock(target, swing.get());
            return;
        }

        if (--breakWait <= 0) {
            ReachHelper.stopBreak(target, targetDirection);
            completeTarget();
            timer = Math.max(1, delay.get());
        }
    }

    private void completeTarget() {
        target = null;
        timer = delay.get();

        if (descending) {
            descending = false;
            layer--;
            timer = 5;
            buildLayer();
        }
    }

    private void nextLayer() {
        if (layer <= targetY.get()) {
            finish();
            return;
        }

        if (breakUnder.get() && mc.player.blockPosition().getY() == layer + 1) {
            BlockPos support = mc.player.blockPosition().below();

            if (support.getY() == layer && isBreakable(support)
                && !mc.level.getBlockState(support.below()).isAir()) {
                descending = true;
                startTarget(support);
                return;
            }
        }

        layer--;
        buildLayer();
    }

    private void tickBaritone() {
        if (baritoneJob) {
            if (!baritoneStarted) {
                if (BaritoneBridge.isBusy()) {
                    baritoneStarted = true;
                    baritoneTicks = 0;
                    return;
                }

                if (++baritoneTicks > 40) {
                    baritoneJob = false;
                    layer = baritoneBottom - 1;
                    timer = 2;
                }

                return;
            }

            if (BaritoneBridge.isBusy()) return;

            baritoneJob = false;
            baritoneStarted = false;
            layer = baritoneBottom - 1;
            timer = 2;
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        if (layer < targetY.get()) {
            finish();
            return;
        }

        int[] bounds = bounds();
        int bottom = Math.max(targetY.get(), layer - sliceHeight.get() + 1);

        BaritoneBridge.clearArea(bounds[0], bounds[2], bottom, bounds[1], bounds[3], layer);
        baritoneBottom = bottom;
        baritoneJob = true;
        baritoneStarted = false;
        baritoneTicks = 0;
        baritoneUsed = true;
    }


    private boolean isBreakable(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir() || !BlockUtils.canBreak(pos, state)) return false;
        if (skipBlockEntities.get() && state.hasBlockEntity()) return false;
        if (skipFluids.get() && !state.getFluidState().isEmpty()) return false;
        if (blacklist.get().contains(state.getBlock())) return false;

        return true;
    }

    private boolean canTeleport() {
        return useInfiniteReach.get() && mineMode.get() == MineMode.Instant && teleportCooldown == 0;
    }

    private int breakLimit(BlockPos pos) {
        float delta = mc.level.getBlockState(pos).getDestroyProgress(mc.player, mc.level, pos);
        if (delta <= 0) return 40;

        return Math.min(600, (int) Math.ceil(1 / delta) + 40);
    }

    private int groundLevel() {
        BlockPos pos = mc.player.blockPosition();

        while (pos.getY() > mc.level.getMinY()
            && mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
            pos = pos.below();
        }

        return pos.getY();
    }

    private int[] bounds() {
        if (area.get() == Area.Chunk) {
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            return new int[]{minX, minX + 15, minZ, minZ + 15};
        }

        int radius = this.radius.get();
        BlockPos pos = mc.player.blockPosition();
        return new int[]{pos.getX() - radius, pos.getX() + radius, pos.getZ() - radius, pos.getZ() + radius};
    }

    private void finish() {
        finished = true;
        info("Finished mining down to Y %d.", targetY.get());

        if (stopWhenDone.get()) toggle();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || !initialized || finished || !Utils.canUpdate()) return;

        int[] bounds = bounds();
        event.renderer.box(bounds[0], layer, bounds[2], bounds[1] + 1, layer + 1, bounds[3] + 1,
            sideColor, lineColor, ShapeMode.Lines, 0);
    }
}
