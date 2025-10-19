package net.mattwhyy.onemace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OneMace implements ModInitializer {

    public static final String MOD_ID = "onemace";
    private static final String OFFLINE_FILE_NAME = "onemace_offline.properties";
    private static final int SCAN_INTERVAL_TICKS = 40; // 2 seconds @ 20 TPS

    public static boolean maceCrafted = false;
    public static UUID maceOwner = null;
    public static final Map<UUID, Boolean> offlineInventory = new ConcurrentHashMap<>();

    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        OneMaceCommand.register();

        ServerLifecycleEvents.SERVER_STARTING.register(this::loadOfflineData);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::saveOfflineData);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getPlayerManager() == null || server.getPlayerManager().getPlayerList().isEmpty()) return;
            blockCraftResults(server);
            yankFromOpenContainers(server);
            enforceOneMace(server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.player;
            offlineInventory.put(p.getUuid(), hasMaceAnywhere(p));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            offlineInventory.remove(p.getUuid());
            if (!anyMaceExists(server)) resetMaceCrafting(server, false);
            else maceCrafted = true;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> onUseEntity(player, world, hand, entity));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> onUseBlock(player, world, hand, hit));
    }

    // Prevent placing the mace into item frames
    private static ActionResult onUseEntity(PlayerEntity player, net.minecraft.world.World world, Hand hand, net.minecraft.entity.Entity entity) {
        if (world.isClient()) return ActionResult.PASS;
        if ((isMace(player.getMainHandStack()) || isMace(player.getOffHandStack()))
                && entity instanceof ItemFrameEntity) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    // Allow anvils, block other containers
    private static ActionResult onUseBlock(PlayerEntity player, net.minecraft.world.World world, Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.PASS;

        ItemStack heldMain = player.getMainHandStack();
        ItemStack heldOff = player.getOffHandStack();
        if (!isMace(heldMain) && !isMace(heldOff)) return ActionResult.PASS;

        BlockEntity be = world.getBlockEntity(hit.getBlockPos());
        if (world.getBlockState(hit.getBlockPos()).getBlock() instanceof AnvilBlock) return ActionResult.PASS;
        if (be instanceof Inventory) return ActionResult.FAIL;

        return ActionResult.PASS;
    }

    // Remove mace from non-anvil inventories
    private static void yankFromOpenContainers(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ScreenHandler open = p.currentScreenHandler;
            if (open == null || open == p.playerScreenHandler) continue;
            if (open instanceof AnvilScreenHandler) continue;

            try {
                for (Slot slot : open.slots) {
                    boolean isPlayerInv = slot.inventory == p.getInventory();
                    boolean isEnder = slot.inventory == p.getEnderChestInventory();
                    if (isPlayerInv || isEnder) continue;
                    if (slot.inventory instanceof CraftingResultInventory) continue;

                    ItemStack s = slot.getStack();
                    if (isMace(s)) {
                        ItemStack copy = s.copy();
                        slot.setStack(ItemStack.EMPTY);
                        slot.markDirty();
                        if (!p.getInventory().insertStack(copy)) p.dropItem(copy, false);
                        // Play pling sound when returning item
                        p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), 1.0f, 1.0f);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    // Prevent crafting another mace if one exists
    private static void blockCraftResults(MinecraftServer server) {
        boolean locked = anyMaceExists(server);
        if (!locked) return;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ScreenHandler open = p.currentScreenHandler;
            if (open == null) continue;
            try {
                for (Slot slot : open.slots) {
                    if (slot.inventory instanceof CraftingResultInventory) {
                        ItemStack out = slot.getStack();
                        if (isMace(out)) {
                            slot.setStack(ItemStack.EMPTY);
                            slot.markDirty();
                            p.playSound(SoundEvents.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                            p.sendMessage(Text.literal("§eA Mace has already been crafted!"), true);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    // Track mace existence
    private void enforceOneMace(MinecraftServer server) {
        if (++tickCounter % SCAN_INTERVAL_TICKS != 0) return;

        boolean found = false;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (hasMaceAnywhere(player)) { found = true; break; }
        }

        if (!found) {
            for (var world : server.getWorlds()) {
                // Big bounding box for searching dropped items
                Box worldBox = new Box(-30000, -64, -30000, 30000, 320, 30000);
                List<ItemEntity> maces = world.getEntitiesByClass(ItemEntity.class, worldBox,
                        e -> isMace(e.getStack()));
                if (!maces.isEmpty()) { found = true; break; }
            }
        }

        if (!found && offlineInventory.values().stream().noneMatch(Boolean::booleanValue)) {
            if (maceCrafted) resetMaceCrafting(server, true);
        } else maceCrafted = true;
    }

    public static boolean isMace(ItemStack s) {
        return s != null && s.isOf(Items.MACE) && s.getCount() > 0;
    }

    private static boolean hasMaceAnywhere(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) if (isMace(inv.getStack(i))) return true;
        for (int i = 0; i < p.getEnderChestInventory().size(); i++)
            if (isMace(p.getEnderChestInventory().getStack(i))) return true;
        return false;
    }

    private static boolean anyMaceExists(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            if (hasMaceAnywhere(p)) return true;
        return offlineInventory.values().stream().anyMatch(Boolean::booleanValue);
    }

    private static void announce(MinecraftServer server, String msg) {
        if (server.getPlayerManager() != null)
            server.getPlayerManager().broadcast(Text.literal("§e" + msg), false);
    }

    // ✅ Combined logic: plays global sound like 1.21.8 version but in modern readable mappings
    public static void resetMaceCrafting(MinecraftServer server, boolean announce) {
        maceCrafted = false;
        maceOwner = null;
        offlineInventory.clear();

        if (announce) {
            announce(server, "§eThe Mace has been destroyed! Crafting is re-enabled.");

            // Play the loud lightning thunder sound globally in every world
            for (var world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    world.playSound(
                            null, // null = broadcast to all nearby players
                            player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, // ⚡ loud thunder sound
                            SoundCategory.WEATHER,
                            20.0f,  // 🔊 very loud
                            1.0f   // normal pitch
                    );
                }
            }
        }
    }


    private void loadOfflineData(MinecraftServer server) {
        try {
            File f = server.getSavePath(WorldSavePath.ROOT).resolve(OFFLINE_FILE_NAME).toFile();
            if (!f.exists()) return;
            Properties p = new Properties();
            try (FileReader r = new FileReader(f)) { p.load(r); }
            offlineInventory.clear();
            for (String k : p.stringPropertyNames())
                offlineInventory.put(UUID.fromString(k), Boolean.parseBoolean(p.getProperty(k, "false")));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveOfflineData(MinecraftServer server) {
        try {
            File f = server.getSavePath(WorldSavePath.ROOT).resolve(OFFLINE_FILE_NAME).toFile();
            Properties p = new Properties();
            for (var e : offlineInventory.entrySet())
                p.setProperty(e.getKey().toString(), Boolean.toString(e.getValue()));
            try (FileWriter w = new FileWriter(f)) { p.store(w, "OneMace offline data"); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void saveConfig() {} // stub for OneMaceCommand
}
