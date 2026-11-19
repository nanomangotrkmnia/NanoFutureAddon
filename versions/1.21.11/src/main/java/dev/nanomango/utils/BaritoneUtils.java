package dev.nanomango.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.BetterBlockPos;

public final class BaritoneUtils {
    private BaritoneUtils() {
    }

    public static void clearArea(int minX, int minZ, int minY, int maxX, int maxZ, int maxY) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getBuilderProcess().clearArea(new BetterBlockPos(minX, minY, minZ), new BetterBlockPos(maxX, maxY, maxZ));
    }

    public static boolean isBusy() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isActive();
    }

    public static void stop() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
    }
}
