package dev.nanomango.utils;

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SpawnScanner {
    private static final int MAX_POSITIONS = 512;

    private SpawnScanner() {
    }

    public static boolean isSpawnable(BlockPos pos, int minLight) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.isAir()) return false;
        if (!BlockUtils.canPlace(pos)) return false;

        BlockPos player = mc.player.blockPosition();
        if (pos.equals(player) || pos.equals(player.above())) return false;

        BlockUtils.MobSpawn spawn = BlockUtils.isValidMobSpawn(pos, state, minLight);
        return spawn == BlockUtils.MobSpawn.Always || spawn == BlockUtils.MobSpawn.Potential;
    }

    public static List<BlockPos> find(int radius, int vertical, int minLight) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos center = mc.player.blockPosition();
        int radiusSquared = radius * radius;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radiusSquared) continue;

                for (int y = -vertical; y <= vertical; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!isSpawnable(pos, minLight)) continue;

                    positions.add(pos);
                    if (positions.size() >= MAX_POSITIONS) return positions;
                }
            }
        }

        return positions;
    }
}
