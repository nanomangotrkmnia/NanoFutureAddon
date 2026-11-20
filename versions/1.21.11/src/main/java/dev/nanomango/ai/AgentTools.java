package dev.nanomango.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nanomango.modules.AIAssistant;
import dev.nanomango.utils.BaritoneBridge;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class AgentTools {
    private static final int MAX_ENTITY_RESULTS = 5;
    private static final int MAX_ITEM_RESULTS = 20;

    private AgentTools() {
    }

    public static String entity(JsonObject arguments) {
        if (mc.player == null) return AiTools.error("Not in a world.");
        if (!AiTools.present(arguments, "name")) return AiTools.error("missing required argument \"name\"");

        String query = AiTools.string(arguments, "name", "").trim();
        if (query.isEmpty()) return AiTools.error("name must not be blank");

        BlockPos center = mc.player.getBlockPos();
        Box box = new Box(center.getX() - 64, center.getY() - 64, center.getZ() - 64,
            center.getX() + 65, center.getY() + 65, center.getZ() + 65);

        List<Entity> found = mc.world.getEntitiesByClass(Entity.class, box,
            entity -> entity != mc.player && matches(entity, query));
        found.sort(Comparator.comparingDouble(entity -> entity.distanceTo(mc.player)));

        JsonArray array = new JsonArray();
        int count = Math.min(MAX_ENTITY_RESULTS, found.size());

        for (int i = 0; i < count; i++) {
            JsonObject object = AwarenessTools.entity(found.get(i));
            object.addProperty("type", "entity");
            array.add(object);
        }

        JsonObject out = new JsonObject();
        out.addProperty("count", found.size());
        out.add("entities", array);
        return out.toString();
    }

    public static String findItem(JsonObject arguments) {
        if (mc.player == null) return AiTools.error("Not in a world.");
        if (!AiTools.present(arguments, "item")) return AiTools.error("missing required argument \"item\"");

        String id = normalize(AiTools.string(arguments, "item", "").trim());
        if (id.isEmpty()) return AiTools.error("item must not be blank");

        JsonArray items = new JsonArray();
        int found = 0;
        int total = 0;

        PlayerInventory inventory = mc.player.getInventory();

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !itemId(stack).equals(id)) continue;

            found++;
            total += stack.getCount();

            if (items.size() < MAX_ITEM_RESULTS) {
                items.add(item(stack, slot, section(slot)));
            }
        }

        ScreenHandler handler = mc.player.currentScreenHandler;

        if (handler != mc.player.playerScreenHandler) {
            int playerStart = handler.slots.size() - 36;

            for (int slot = 0; slot < Math.max(0, playerStart); slot++) {
                ItemStack stack = handler.slots.get(slot).getStack();
                if (stack.isEmpty() || !itemId(stack).equals(id)) continue;

                found++;
                total += stack.getCount();

                if (items.size() < MAX_ITEM_RESULTS) {
                    items.add(item(stack, slot, "container"));
                }
            }
        }

        JsonArray memory = ContainerMemory.find(id, MAX_ITEM_RESULTS - items.size());
        for (int i = 0; i < memory.size(); i++) {
            JsonObject match = memory.get(i).getAsJsonObject();
            found++;
            total += match.has("count") ? match.get("count").getAsInt() : 0;
            items.add(match);
        }

        JsonObject out = new JsonObject();
        out.addProperty("item", id);
        out.addProperty("count", found);
        out.addProperty("total", total);
        out.addProperty("truncated", found > MAX_ITEM_RESULTS);
        out.add("items", items);
        return out.toString();
    }

    public static String baritoneFollow(JsonObject arguments) {
        if (!BaritoneBridge.available()) return AiTools.error("Baritone is not available.");
        if (!AiTools.present(arguments, "name")) return AiTools.error("missing required argument \"name\"");

        String name = AiTools.string(arguments, "name", "").trim();
        if (name.isEmpty()) return AiTools.error("name must not be blank");

        BaritoneBridge.follow(name);

        JsonObject out = new JsonObject();
        out.addProperty("status", "started");
        out.addProperty("name", name);
        return out.toString();
    }

    public static String baritoneMine(JsonObject arguments) {
        if (!BaritoneBridge.available()) return AiTools.error("Baritone is not available.");

        String error = AiTools.intError(arguments, "x", "y", "z");
        if (error != null) return AiTools.error(error);

        int x = AiTools.integer(arguments, "x", 0, -30000000, 30000000);
        int y = AiTools.integer(arguments, "y", 0, -64, 320);
        int z = AiTools.integer(arguments, "z", 0, -30000000, 30000000);
        BlockPos pos = new BlockPos(x, y, z);

        String block = AiTools.string(arguments, "block", "").trim();
        if (block.isEmpty()) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir()) return AiTools.error("no block at that position");

            block = Registries.BLOCK.getId(state.getBlock()).toString();
        }

        block = normalize(block);
        BaritoneBridge.mine(block);

        JsonObject out = new JsonObject();
        out.addProperty("status", "started");
        out.addProperty("block", block);
        out.addProperty("x", x);
        out.addProperty("y", y);
        out.addProperty("z", z);
        return out.toString();
    }

    public static String lookAt(JsonObject arguments) {
        if (mc.player == null) return AiTools.error("Not in a world.");

        String error = AiTools.intError(arguments, "x", "y", "z");
        if (error != null) return AiTools.error(error);

        int x = AiTools.integer(arguments, "x", 0, -30000000, 30000000);
        int y = AiTools.integer(arguments, "y", 0, -64, 320);
        int z = AiTools.integer(arguments, "z", 0, -30000000, 30000000);

        Vec3d eye = mc.player.getEyePos();
        double dx = x + 0.5 - eye.x;
        double dy = y + 0.5 - eye.y;
        double dz = z + 0.5 - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));

        JsonObject out = new JsonObject();
        out.addProperty("yaw", round(yaw));
        out.addProperty("pitch", round(pitch));
        return out.toString();
    }

    public static String useItem(JsonObject arguments) {
        String error = actionsError();
        if (error != null) return error;

        int slot = AiTools.integer(arguments, "slot", -1, 0, 8);
        if (slot < 0) return AiTools.error("slot must be 0-8");

        mc.player.getInventory().setSelectedSlot(slot);
        String result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).toString();

        JsonObject out = new JsonObject();
        out.addProperty("slot", slot);
        out.addProperty("result", result);
        return out.toString();
    }

    public static String placeBlock(JsonObject arguments) {
        String error = actionsError();
        if (error != null) return error;

        error = AiTools.intError(arguments, "x", "y", "z");
        if (error != null) return AiTools.error(error);

        String itemName = AiTools.string(arguments, "item", "").trim();
        if (itemName.isEmpty()) return AiTools.error("missing required argument \"item\"");

        Identifier identifier = Identifier.tryParse(normalize(itemName));
        if (identifier == null) return AiTools.error("invalid item id \"" + itemName + "\"");

        Item item = Registries.ITEM.get(identifier);
        if (item == null || item == Items.AIR) return AiTools.error("unknown item \"" + itemName + "\"");

        FindItemResult found = InvUtils.findInHotbar(item);
        if (!found.found()) return AiTools.error("item is not in the hotbar or offhand");

        BlockPos pos = new BlockPos(
            AiTools.integer(arguments, "x", 0, -30000000, 30000000),
            AiTools.integer(arguments, "y", 0, -64, 320),
            AiTools.integer(arguments, "z", 0, -30000000, 30000000));

        boolean placed = BlockUtils.place(pos, found, true, 50);

        JsonObject out = new JsonObject();
        out.addProperty("placed", placed);
        out.addProperty("x", pos.getX());
        out.addProperty("y", pos.getY());
        out.addProperty("z", pos.getZ());
        return out.toString();
    }

    public static String mineBlock(JsonObject arguments) {
        String error = actionsError();
        if (error != null) return error;

        error = AiTools.intError(arguments, "x", "y", "z");
        if (error != null) return AiTools.error(error);

        BlockPos pos = new BlockPos(
            AiTools.integer(arguments, "x", 0, -30000000, 30000000),
            AiTools.integer(arguments, "y", 0, -64, 320),
            AiTools.integer(arguments, "z", 0, -30000000, 30000000));

        boolean started = BlockUtils.breakBlock(pos, true);

        JsonObject out = new JsonObject();
        out.addProperty("started", started);
        out.addProperty("x", pos.getX());
        out.addProperty("y", pos.getY());
        out.addProperty("z", pos.getZ());
        return out.toString();
    }

    public static String dropItem(JsonObject arguments) {
        String error = actionsError();
        if (error != null) return error;

        int slot = AiTools.integer(arguments, "slot", -1, 0, 8);
        if (slot < 0) return AiTools.error("slot must be 0-8");

        boolean all = AiTools.bool(arguments, "all", false);
        mc.player.getInventory().setSelectedSlot(slot);

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            all ? PlayerActionC2SPacket.Action.DROP_ALL_ITEMS
                : PlayerActionC2SPacket.Action.DROP_ITEM,
            BlockPos.ORIGIN, Direction.DOWN));

        JsonObject out = new JsonObject();
        out.addProperty("slot", slot);
        out.addProperty("all", all);
        return out.toString();
    }

    public static String sendChat(JsonObject arguments) {
        String error = actionsError();
        if (error != null) return error;

        String message = AiTools.string(arguments, "message", "").trim();
        if (message.isEmpty()) return AiTools.error("message must not be blank");
        if (message.startsWith("/")) return AiTools.error("commands are not allowed");
        if (message.length() > 256) return AiTools.error("message must be 256 characters or fewer");

        mc.getNetworkHandler().sendChatMessage(message);

        JsonObject out = new JsonObject();
        out.addProperty("sent", true);
        return out.toString();
    }

    private static String actionsError() {
        if (mc.player == null) return AiTools.error("Not in a world.");
        if (!AIAssistant.actionsAllowed()) {
            return AiTools.error("Actions are disabled. Turn on allow-actions in ai-assistant.");
        }

        return null;
    }

    private static boolean matches(Entity entity, String query) {
        String name = entity.getName().getString();
        String type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();

        return name.equalsIgnoreCase(query)
            || name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
            || type.equalsIgnoreCase(normalize(query));
    }

    private static JsonObject item(ItemStack stack, int slot, String section) {
        JsonObject object = new JsonObject();
        object.addProperty("slot", slot);
        object.addProperty("section", section);
        object.addProperty("item", itemId(stack));
        object.addProperty("name", stack.getName().getString());
        object.addProperty("count", stack.getCount());
        return object;
    }

    private static String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static String section(int slot) {
        if (slot <= 8) return "hotbar";
        if (slot <= 35) return "main";
        if (slot <= 39) return "armor";
        if (slot == 40) return "offhand";
        return "inventory";
    }

    private static String normalize(String id) {
        if (id.isEmpty()) return id;
        return id.contains(":") ? id.toLowerCase(Locale.ROOT) : "minecraft:" + id.toLowerCase(Locale.ROOT);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
