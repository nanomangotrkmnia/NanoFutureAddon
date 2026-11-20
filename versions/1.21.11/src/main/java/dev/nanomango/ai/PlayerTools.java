package dev.nanomango.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWaypointHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class PlayerTools {
    private static final int MAX_PLAYERS = 100;
    private static final int MAX_WAYPOINTS = 50;

    private PlayerTools() {
    }

    public static String players(JsonObject arguments) {
        if (mc.player == null || mc.getNetworkHandler() == null) return AiTools.error("Not in a world.");

        Collection<PlayerListEntry> listed = mc.getNetworkHandler().getListedPlayerListEntries();
        List<PlayerListEntry> entries = new ArrayList<>(listed);
        entries.sort(Comparator.comparing(entry -> entry.getProfile().name(), String.CASE_INSENSITIVE_ORDER));

        JsonArray array = new JsonArray();
        UUID self = mc.player.getUuid();
        int count = Math.min(MAX_PLAYERS, entries.size());

        for (int i = 0; i < count; i++) {
            PlayerListEntry entry = entries.get(i);

            JsonObject object = new JsonObject();
            object.addProperty("name", entry.getProfile().name());
            object.addProperty("self", self.equals(entry.getProfile().id()));
            details(object, entry);
            array.add(object);
        }

        JsonObject out = new JsonObject();
        out.addProperty("count", entries.size());
        out.addProperty("truncated", entries.size() > MAX_PLAYERS);
        out.add("players", array);
        return out.toString();
    }

    public static JsonObject self() {
        if (mc.player == null || mc.getNetworkHandler() == null) return new JsonObject();

        JsonObject out = new JsonObject();
        out.addProperty("name", mc.player.getGameProfile().name());

        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (entry != null) details(out, entry);

        return out;
    }

    public static String locator(JsonObject arguments) {
        if (mc.player == null || mc.getNetworkHandler() == null) return AiTools.error("Not in a world.");

        ClientWaypointHandler handler = mc.getNetworkHandler().getWaypointHandler();

        JsonObject out = new JsonObject();
        out.addProperty("enabled", handler.hasWaypoint());

        List<Entry> found = new ArrayList<>();

        if (handler.hasWaypoint()) {
            Entity viewer = mc.getCameraEntity();
            if (viewer == null) viewer = mc.player;

            final Entity camera = viewer;
            handler.forEachWaypoint(camera, waypoint -> {
                UUID source = waypoint.getSource().left().orElse(null);
                if (source != null && source.equals(camera.getUuid())) return;

                double yaw = waypoint.getRelativeYaw(mc.world, mc.gameRenderer.getCamera(), entity -> 0.0f);
                if (!Double.isFinite(yaw) || yaw <= -60.0 || yaw > 60.0) return;

                double distance = Math.sqrt(waypoint.squaredDistanceTo(camera));
                if (!Double.isFinite(distance)) distance = 0.0;
                found.add(new Entry(source, waypoint.getSource().right().orElse(null), yaw, distance));
            });
        }

        found.sort(Comparator.comparingDouble(Entry::yaw));

        JsonArray array = new JsonArray();
        int count = Math.min(MAX_WAYPOINTS, found.size());

        for (int i = 0; i < count; i++) array.add(locatorEntry(found.get(i)));

        out.addProperty("count", found.size());
        out.addProperty("truncated", found.size() > MAX_WAYPOINTS);
        out.add("entries", array);
        return out.toString();
    }

    private static JsonObject locatorEntry(Entry entry) {
        JsonObject out = new JsonObject();
        out.addProperty("type", entry.uuid() == null ? "waypoint" : "player");

        if (entry.uuid() != null) {
            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(entry.uuid());

            if (info != null) {
                out.addProperty("name", info.getProfile().name());
                details(out, info);
                out.addProperty("resolved", true);
            } else {
                PlayerEntity entity = loaded(entry.uuid());

                if (entity != null) {
                    out.addProperty("name", entity.getName().getString());
                    out.addProperty("resolved", true);
                } else {
                    out.addProperty("name", "unknown");
                    out.addProperty("resolved", false);
                }
            }
        } else {
            out.addProperty("name", entry.label() == null ? "unknown" : entry.label());
            out.addProperty("resolved", true);
        }

        out.addProperty("yaw", round(entry.yaw()));
        out.addProperty("side", side(entry.yaw()));
        out.addProperty("distance", round(entry.distance()));
        return out;
    }

    private static void details(JsonObject out, PlayerListEntry entry) {
        out.addProperty("ping", entry.getLatency());

        GameMode mode = entry.getGameMode();
        if (mode != null) out.addProperty("game_mode", mode.asString());

        Team team = entry.getScoreboardTeam();
        if (team != null) {
            out.addProperty("team", team.getName());
            out.addProperty("team_color", team.getColor().getName());
        }

        Text display = entry.getDisplayName();
        if (display != null && !display.getString().isBlank()) {
            out.addProperty("display_name", display.getString());
        }
    }

    private static PlayerEntity loaded(UUID uuid) {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getUuid().equals(uuid)) return player;
        }

        return null;
    }

    private static String side(double yaw) {
        double angle = Math.abs(yaw);

        if (angle <= 15.0) return "front";
        if (angle <= 45.0) return yaw < 0 ? "front-left" : "front-right";
        return yaw < 0 ? "left" : "right";
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record Entry(UUID uuid, String label, double yaw, double distance) {
    }
}
