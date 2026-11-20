package dev.nanomango.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class AwarenessTools {
    private static final int MAX_SCORES = 20;
    private static final int MAX_CONTAINER = 54;
    private static final int MAX_EFFECTS = 10;
    private static final int MAX_EQUIPMENT = 8;

    private AwarenessTools() {
    }

    public static String scoreboard(JsonObject arguments) {
        if (mc.world == null) return AiTools.error("Not in a world.");

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        JsonObject out = new JsonObject();
        out.addProperty("present", objective != null);

        if (objective == null) {
            out.add("entries", new JsonArray());
            return out.toString();
        }

        out.addProperty("name", objective.getName());
        out.addProperty("title", objective.getDisplayName().getString());

        List<ScoreboardEntry> scores = new ArrayList<>(scoreboard.getScoreboardEntries(objective));
        scores.sort(Comparator.comparingInt(ScoreboardEntry::value).reversed());

        JsonArray entries = new JsonArray();
        int count = Math.min(MAX_SCORES, scores.size());

        for (int i = 0; i < count; i++) {
            ScoreboardEntry score = scores.get(i);

            JsonObject entry = new JsonObject();
            entry.addProperty("owner", score.owner());
            entry.addProperty("value", score.value());
            if (score.display() != null) entry.addProperty("display", score.display().getString());
            entries.add(entry);
        }

        out.addProperty("count", scores.size());
        out.addProperty("truncated", scores.size() > MAX_SCORES);
        out.add("entries", entries);
        return out.toString();
    }

    public static String container(JsonObject arguments) {
        if (mc.player == null) return AiTools.error("Not in a world.");

        ScreenHandler handler = mc.player.currentScreenHandler;

        JsonObject out = new JsonObject();
        if (handler == mc.player.playerScreenHandler) {
            out.addProperty("open", false);
            return out.toString();
        }

        out.addProperty("open", true);

        String title = "";
        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            title = screen.getTitle().getString();
            out.addProperty("title", title);
        }

        JsonArray items = new JsonArray();
        JsonArray storedItems = new JsonArray();
        int count = 0;
        int playerStart = handler.slots.size() - 36;

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            count++;

            if (items.size() < MAX_CONTAINER) {
                JsonObject item = new JsonObject();
                item.addProperty("slot", i);
                item.addProperty("section", i < playerStart ? "container" : "player");
                item.addProperty("item", Registries.ITEM.getId(stack.getItem()).toString());
                item.addProperty("name", stack.getName().getString());
                item.addProperty("count", stack.getCount());
                items.add(item);

                if (i < playerStart) storedItems.add(item.deepCopy());
            }
        }

        String dimension = mc.world.getRegistryKey().getValue().toString();
        BlockPos containerPos = ContainerTracker.lastPos();
        if (containerPos == null || !dimension.equals(ContainerTracker.lastDimension())) containerPos = null;

        out.addProperty("stored", ContainerMemory.record(containerPos, dimension, title, storedItems));
        out.addProperty("count", count);
        out.addProperty("truncated", count > MAX_CONTAINER);
        out.add("items", items);
        return out.toString();
    }

    public static String target(JsonObject arguments) {
        if (mc.player == null) return AiTools.error("Not in a world.");

        HitResult hit = mc.crosshairTarget;

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return "{\"type\":\"none\"}";
        }

        if (hit instanceof EntityHitResult entityHit) {
            JsonObject out = entity(entityHit.getEntity());
            out.addProperty("type", "entity");
            return out.toString();
        }

        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();

            JsonObject out = new JsonObject();
            out.addProperty("type", "block");
            out.addProperty("x", pos.getX());
            out.addProperty("y", pos.getY());
            out.addProperty("z", pos.getZ());
            return out.toString();
        }

        return "{\"type\":\"none\"}";
    }

    public static JsonObject entity(Entity entity) {
        JsonObject out = new JsonObject();
        out.addProperty("name", entity.getName().getString());
        out.addProperty("entity_type", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        out.addProperty("distance", round(entity.distanceTo(mc.player)));
        out.addProperty("player", entity instanceof PlayerEntity);

        if (entity instanceof LivingEntity living) {
            out.addProperty("health", round(living.getHealth()));
            out.addProperty("max_health", round(living.getMaxHealth()));
            out.add("effects", effects(living.getStatusEffects()));
            out.add("equipment", equipment(living));
        }

        return out;
    }

    public static void state(JsonObject out) {
        out.addProperty("xp_level", mc.player.experienceLevel);
        out.addProperty("xp_progress", round(mc.player.experienceProgress));
        out.addProperty("difficulty", mc.world.getDifficulty().name().toLowerCase(Locale.ROOT));
        out.addProperty("weather", mc.world.isThundering() ? "thunder"
            : mc.world.isRaining() ? "rain" : "clear");
        out.addProperty("biome", mc.world.getBiome(mc.player.getBlockPos())
            .getKey().map(key -> key.getValue().toString()).orElse("unknown"));
        out.addProperty("on_fire", mc.player.isOnFire());
        out.addProperty("world_age", mc.world.getTime());

        Entity vehicle = mc.player.getVehicle();
        if (vehicle != null) out.addProperty("vehicle", vehicle.getName().getString());

        WorldBorder border = mc.world.getWorldBorder();
        double half = border.getSize() / 2.0;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        double distance = Math.min(
            Math.min(x - (border.getCenterX() - half), (border.getCenterX() + half) - x),
            Math.min(z - (border.getCenterZ() - half), (border.getCenterZ() + half) - z));

        JsonObject worldBorder = new JsonObject();
        worldBorder.addProperty("size", round(border.getSize()));
        worldBorder.addProperty("distance", round(distance));
        out.add("world_border", worldBorder);

        out.add("effects", effects(mc.player.getStatusEffects()));
    }

    public static JsonArray effects(Collection<StatusEffectInstance> active) {
        JsonArray array = new JsonArray();
        int count = 0;

        for (StatusEffectInstance effect : active) {
            if (count++ >= MAX_EFFECTS) break;

            JsonObject object = new JsonObject();
            object.addProperty("effect", effect.getEffectType().getKey()
                .map(key -> key.getValue().toString()).orElse("unknown"));
            object.addProperty("amplifier", effect.getAmplifier());
            object.addProperty("duration", effect.getDuration());
            array.add(object);
        }

        return array;
    }

    private static JsonArray equipment(LivingEntity living) {
        JsonArray array = new JsonArray();
        int count = 0;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = living.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            if (count++ >= MAX_EQUIPMENT) break;

            JsonObject object = new JsonObject();
            object.addProperty("slot", slot.getName());
            object.addProperty("item", Registries.ITEM.getId(stack.getItem()).toString());
            object.addProperty("name", stack.getName().getString());
            object.addProperty("count", stack.getCount());
            array.add(object);
        }

        return array;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
