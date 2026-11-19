package dev.nanomango.utils;

import java.lang.reflect.Method;

public final class BaritoneBridge {
    private static Method clearArea;
    private static Method isBusy;
    private static Method stop;

    static {
        try {
            Class<?> type = Class.forName("dev.nanomango.utils.BaritoneUtils");
            clearArea = type.getMethod("clearArea", int.class, int.class, int.class, int.class, int.class, int.class);
            isBusy = type.getMethod("isBusy");
            stop = type.getMethod("stop");
        } catch (Throwable ignored) {
            clearArea = null;
            isBusy = null;
            stop = null;
        }
    }

    private BaritoneBridge() {
    }

    public static void clearArea(int minX, int minZ, int minY, int maxX, int maxZ, int maxY) {
        if (clearArea == null) return;

        try {
            clearArea.invoke(null, minX, minZ, minY, maxX, maxZ, maxY);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static boolean isBusy() {
        if (isBusy == null) return false;

        try {
            return (boolean) isBusy.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static void stop() {
        if (stop == null) return;

        try {
            stop.invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
