package dev.nanomango.utils;

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SpawnScanner {
    private SpawnScanner() {
    }

    public static boolean isSpawnable(BlockPos pos, int minLight) {
        return isSpawnable(pos, minLight, mc.player.getBlockPos());
    }

    public static boolean isSpawnable(BlockPos pos, int minLight, BlockPos player) {
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isAir()) return false;
        if (!BlockUtils.canPlace(pos)) return false;

        if (pos.equals(player) || pos.equals(player.up())) return false;

        BlockUtils.MobSpawn spawn = BlockUtils.isValidMobSpawn(pos, state, minLight);
        return spawn == BlockUtils.MobSpawn.Always || spawn == BlockUtils.MobSpawn.Potential;
    }
}
