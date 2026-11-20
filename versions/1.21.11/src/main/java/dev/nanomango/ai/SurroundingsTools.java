package dev.nanomango.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nanomango.utils.BaritoneBridge;
import dev.nanomango.utils.ReachHelper;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SurroundingsTools {
    private SurroundingsTools() {
    }

    public static String state(JsonObject arguments) {
        JsonObject out = new JsonObject();
        out.add("position", vec(mc.player.getEntityPos()));
        out.add("block_position", pos(mc.player.getBlockPos()));
        out.addProperty("dimension", mc.world.getRegistryKey().getValue().toString());
        out.addProperty("health", mc.player.getHealth());
        out.addProperty("food", mc.player.getHungerManager().getFoodLevel());
        out.addProperty("on_ground", mc.player.isOnGround());
        out.addProperty("sneaking", mc.player.isSneaking());
        out.addProperty("sprinting", mc.player.isSprinting());
        out.addProperty("yaw", round(mc.player.getYaw()));
        out.addProperty("pitch", round(mc.player.getPitch()));
        out.addProperty("selected_slot", mc.player.getInventory().getSelectedSlot());
        out.add("held", stack(mc.player.getMainHandStack(), -1));
        out.add("offhand", stack(mc.player.getOffHandStack(), -1));
        out.add("user", PlayerTools.self());

        BlockPos below = mc.player.getBlockPos().down();
        out.addProperty("standing_on", blockId(mc.world.getBlockState(below)));
        out.addProperty("reach", round(ReachHelper.serverReach()));
        out.addProperty("baritone", BaritoneBridge.available());
        return out.toString();
    }

    public static String scanArea(JsonObject arguments) {
        int radius = AiTools.integer(arguments, "radius", 12, 1, 32);
        BlockPos center = mc.player.getBlockPos();
        int minY = Math.max(mc.world.getBottomY(), center.getY() - 8);
        int maxY = center.getY() + 4;

        int waterSource = 0;
        int waterFlowing = 0;
        int lavaSource = 0;
        int lavaFlowing = 0;
        int oreCount = 0;
        int containerCount = 0;

        JsonArray fluids = new JsonArray();
        JsonArray ores = new JsonArray();
        JsonArray containers = new JsonArray();

        for (int y = minY; y <= maxY; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.LAVA) {
                        boolean isLava = state.getBlock() == Blocks.LAVA;
                        boolean source = state.getFluidState().isStill();

                        if (isLava) {
                            if (source) lavaSource++;
                            else lavaFlowing++;
                        } else {
                            if (source) waterSource++;
                            else waterFlowing++;
                        }

                        if (fluids.size() < 8) {
                            JsonObject entry = new JsonObject();
                            entry.addProperty("fluid", (isLava ? "lava_" : "water_") + (source ? "source" : "flowing"));
                            entry.add("pos", pos(pos));
                            fluids.add(entry);
                        }
                    }

                    if (state.hasBlockEntity() && state.createScreenHandlerFactory(mc.world, pos) != null) {
                        containerCount++;
                        if (containers.size() < 8) {
                            JsonObject entry = new JsonObject();
                            entry.addProperty("id", blockId(state));
                            entry.add("pos", pos(pos));
                            containers.add(entry);
                        }
                    }

                    String id = blockId(state);
                    if (isOre(id)) {
                        oreCount++;
                        if (ores.size() < 8) {
                            JsonObject entry = new JsonObject();
                            entry.addProperty("id", id);
                            entry.add("pos", pos(pos));
                            ores.add(entry);
                        }
                    }
                }
            }
        }

        Box box = new Box(center.getX() - radius, minY, center.getZ() - radius,
            center.getX() + radius + 1, maxY + 1, center.getZ() + radius + 1);

        List<Entity> found = mc.world.getEntitiesByClass(Entity.class, box,
            entity -> entity != mc.player && entity.isAlive());
        found.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player)));

        JsonArray entities = new JsonArray();
        for (int i = 0; i < Math.min(10, found.size()); i++) entities.add(entity(found.get(i)));

        JsonObject fluidSummary = new JsonObject();
        fluidSummary.addProperty("water_source", waterSource);
        fluidSummary.addProperty("water_flowing", waterFlowing);
        fluidSummary.addProperty("lava_source", lavaSource);
        fluidSummary.addProperty("lava_flowing", lavaFlowing);
        fluidSummary.add("positions", fluids);

        JsonObject oreSummary = new JsonObject();
        oreSummary.addProperty("count", oreCount);
        oreSummary.add("samples", ores);

        JsonObject containerSummary = new JsonObject();
        containerSummary.addProperty("count", containerCount);
        containerSummary.add("samples", containers);

        JsonObject out = new JsonObject();
        out.add("center", pos(center));
        out.addProperty("radius", radius);
        out.add("defaults", AiTools.defaults(arguments, "radius"));
        out.addProperty("floor_y", floor(center));
        out.add("fluids", fluidSummary);
        out.add("ores", oreSummary);
        out.add("containers", containerSummary);
        out.add("entities", entities);
        return out.toString();
    }

    public static String findBlocks(JsonObject arguments) {
        if (!AiTools.present(arguments, "block")) return AiTools.error("missing required argument \"block\"");

        String id = AiTools.string(arguments, "block", "");
        if (id.isBlank()) return AiTools.error("argument \"block\" must be a block id");
        if (!id.contains(":")) id = "minecraft:" + id;

        int radius = AiTools.integer(arguments, "radius", 16, 1, 32);
        int minY = AiTools.integer(arguments, "y_min", -8, -32, 0);
        int maxY = AiTools.integer(arguments, "y_max", 8, 0, 32);
        int limit = AiTools.integer(arguments, "limit", 20, 1, 64);

        BlockPos center = mc.player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();

        for (int y = minY; y <= maxY; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (!state.isAir() && blockId(state).equals(id)) found.add(pos);
                }
            }
        }

        found.sort(Comparator.comparingDouble(pos ->
            mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos())));

        JsonArray positions = new JsonArray();
        for (int i = 0; i < Math.min(limit, found.size()); i++) positions.add(pos(found.get(i)));

        JsonObject out = new JsonObject();
        out.addProperty("block", id);
        out.addProperty("count", found.size());
        out.addProperty("truncated", found.size() > limit);
        out.addProperty("radius", radius);
        out.addProperty("y_min", minY);
        out.addProperty("y_max", maxY);
        out.addProperty("limit", limit);
        out.add("defaults", AiTools.defaults(arguments, "radius", "y_min", "y_max", "limit"));
        out.add("positions", positions);
        return out.toString();
    }

    public static String getBlock(JsonObject arguments) {
        String error = AiTools.intError(arguments, "x", "y", "z");
        if (error != null) return AiTools.error(error);

        int x = AiTools.integer(arguments, "x", 0, -30000000, 30000000);
        int y = AiTools.integer(arguments, "y", 0, -64, 320);
        int z = AiTools.integer(arguments, "z", 0, -30000000, 30000000);
        boolean relative = AiTools.bool(arguments, "relative", true);

        BlockPos pos = relative ? mc.player.getBlockPos().add(x, y, z) : new BlockPos(x, y, z);
        BlockState state = mc.world.getBlockState(pos);

        JsonObject out = new JsonObject();
        out.add("pos", pos(pos));
        out.addProperty("id", blockId(state));
        out.addProperty("air", state.isAir());
        out.addProperty("replaceable", state.isReplaceable());
        out.addProperty("block_entity", state.hasBlockEntity());
        out.addProperty("hardness", round(state.getHardness(mc.world, pos)));
        out.addProperty("fluid", fluidId(state));
        out.addProperty("breakable", !state.isAir() && BlockUtils.canBreak(pos, state));
        out.addProperty("in_reach", ReachHelper.inServerReach(pos));
        out.addProperty("distance", round(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()))));
        out.addProperty("relative", relative);
        out.add("defaults", AiTools.defaults(arguments, "relative"));
        return out.toString();
    }

    public static String inventory(JsonObject arguments) {
        PlayerInventory inventory = mc.player.getInventory();
        boolean main = AiTools.bool(arguments, "main", false);

        JsonArray hotbar = new JsonArray();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty()) hotbar.add(stack(stack, slot));
        }

        String[] armorSlots = {"boots", "leggings", "chestplate", "helmet"};
        JsonArray armor = new JsonArray();
        for (int i = 0; i < armorSlots.length; i++) {
            ItemStack stack = inventory.getStack(36 + i);
            JsonObject entry = stack(stack, 36 + i);
            entry.addProperty("armor_slot", armorSlots[i]);
            armor.add(entry);
        }

        JsonObject out = new JsonObject();
        out.addProperty("selected", inventory.getSelectedSlot());
        out.add("hotbar", hotbar);
        out.add("offhand", stack(mc.player.getOffHandStack(), 40));
        out.add("armor", armor);
        out.addProperty("main", main);
        out.add("defaults", AiTools.defaults(arguments, "main"));

        if (main) {
            JsonArray rest = new JsonArray();
            for (int slot = 9; slot < Math.min(36, inventory.size()); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (!stack.isEmpty()) rest.add(stack(stack, slot));
            }
            out.add("main", rest);
        }

        return out.toString();
    }

    public static String findEntities(JsonObject arguments) {
        String kind = AiTools.string(arguments, "kind", "mob").toLowerCase();

        if (!kind.equals("player") && !kind.equals("hostile") && !kind.equals("animal")
            && !kind.equals("mob") && !kind.equals("all")) {
            return AiTools.error("unknown kind \"" + kind
                + "\"; valid values are player, hostile, animal, mob, all");
        }

        int radius = AiTools.integer(arguments, "radius", 24, 1, 64);
        int limit = AiTools.integer(arguments, "limit", 10, 1, 32);

        BlockPos center = mc.player.getBlockPos();
        Box box = new Box(center.getX() - radius, center.getY() - 8, center.getZ() - radius,
            center.getX() + radius + 1, center.getY() + 8 + 1, center.getZ() + radius + 1);

        List<Entity> found = mc.world.getEntitiesByClass(Entity.class, box,
            entity -> entity != mc.player && entity.isAlive() && matches(entity, kind));
        found.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player)));

        JsonArray entities = new JsonArray();
        for (int i = 0; i < Math.min(limit, found.size()); i++) entities.add(entity(found.get(i)));

        JsonObject out = new JsonObject();
        out.addProperty("kind", kind);
        out.addProperty("count", found.size());
        out.addProperty("radius", radius);
        out.addProperty("limit", limit);
        out.add("defaults", AiTools.defaults(arguments, "kind", "radius", "limit"));
        out.add("entities", entities);
        return out.toString();
    }

    private static boolean matches(Entity entity, String kind) {
        return switch (kind) {
            case "player" -> entity instanceof PlayerEntity;
            case "hostile" -> entity instanceof HostileEntity;
            case "animal" -> entity instanceof LivingEntity
                && !(entity instanceof PlayerEntity) && !(entity instanceof HostileEntity);
            case "all" -> true;
            default -> entity instanceof LivingEntity && !(entity instanceof PlayerEntity);
        };
    }

    private static JsonObject entity(Entity entity) {
        JsonObject out = new JsonObject();
        out.addProperty("type", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        out.addProperty("name", entity.getName().getString());
        out.add("pos", vec(entity.getEntityPos()));
        out.addProperty("distance", round(Math.sqrt(entity.squaredDistanceTo(mc.player))));
        out.addProperty("player", entity instanceof PlayerEntity);
        out.addProperty("hostile", entity instanceof HostileEntity);

        if (entity instanceof LivingEntity living) {
            out.addProperty("health", round(living.getHealth()));
            out.addProperty("max_health", round(living.getMaxHealth()));
        }

        return out;
    }

    private static JsonObject stack(ItemStack stack, int slot) {
        JsonObject out = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            out.addProperty("empty", true);
            return out;
        }

        if (slot >= 0) out.addProperty("slot", slot);
        out.addProperty("item", Registries.ITEM.getId(stack.getItem()).toString());
        out.addProperty("name", stack.getName().getString());
        out.addProperty("count", stack.getCount());

        if (stack.isDamageable()) out.addProperty("durability", stack.getMaxDamage() - stack.getDamage());
        return out;
    }

    private static String blockId(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    private static String fluidId(BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) return "";

        return Registries.FLUID.getId(fluid.getFluid()).toString();
    }

    private static boolean isOre(String id) {
        return id.endsWith("_ore") || id.equals("minecraft:ancient_debris");
    }

    private static int floor(BlockPos center) {
        BlockPos pos = center;
        int bottom = mc.world.getBottomY();

        while (pos.getY() > bottom && mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
            pos = pos.down();
        }

        return pos.getY();
    }

    private static JsonObject pos(BlockPos pos) {
        JsonObject out = new JsonObject();
        out.addProperty("x", pos.getX());
        out.addProperty("y", pos.getY());
        out.addProperty("z", pos.getZ());
        return out;
    }

    private static JsonObject vec(Vec3d vec) {
        JsonObject out = new JsonObject();
        out.addProperty("x", round(vec.x));
        out.addProperty("y", round(vec.y));
        out.addProperty("z", round(vec.z));
        return out;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
