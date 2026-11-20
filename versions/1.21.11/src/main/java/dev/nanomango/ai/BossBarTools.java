package dev.nanomango.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class BossBarTools {
    private BossBarTools() {
    }

    public static String bars(JsonObject arguments) {
        JsonObject out = new JsonObject();
        out.addProperty("available", false);

        try {
            BossBarHud hud = mc.inGameHud.getBossBarHud();

            Field field = null;
            for (Field candidate : BossBarHud.class.getDeclaredFields()) {
                if (Modifier.isStatic(candidate.getModifiers())) continue;
                if (!Map.class.isAssignableFrom(candidate.getType())) continue;
                field = candidate;
                break;
            }

            if (field == null) return out.toString();

            field.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) field.get(hud);

            JsonArray array = new JsonArray();

            for (Object value : map.values()) {
                if (!(value instanceof BossBar bar)) continue;

                JsonObject object = new JsonObject();
                object.addProperty("name", bar.getName().getString());
                object.addProperty("percent", round(bar.getPercent() * 100.0));
                object.addProperty("color", bar.getColor().name().toLowerCase(Locale.ROOT));
                object.addProperty("darken_sky", bar.shouldDarkenSky());
                array.add(object);
            }

            out.addProperty("available", true);
            out.addProperty("count", array.size());
            out.add("bars", array);
        } catch (Exception ignored) {
        }

        return out.toString();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
