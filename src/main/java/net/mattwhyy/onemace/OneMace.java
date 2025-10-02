package net.mattwhyy.onemace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OneMace implements ModInitializer {

    public static final String MOD_ID = "onemace";
    public static final Identifier MACE_RECIPE_ID = Identifier.of("minecraft", "mace");

    public static boolean maceCrafted = false;
    public static UUID maceOwner = null;
    public static final Map<UUID, Boolean> offlineInventory = new ConcurrentHashMap<>();

    private static final String OFFLINE_FILE_NAME = "onemace_offline.properties";

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static int tickGuard = 0;

    @Override
    public void onInitialize() {
        OneMaceCommand.register();

        ServerLifecycleEvents.SERVER_STARTING.register(this::loadOfflineData);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::saveOfflineData);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            blockCraftResults(server);
            yankFromOpenContainers(server);
            enforceOneMaceLight(server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            ServerPlayerEntity p = h.player;
            offlineInventory.put(p.getUuid(), hasMaceAnywhere(p));
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
            ServerPlayerEntity p = h.player;
            offlineInventory.remove(p.getUuid());
            if (!anyMaceInPlayersOrOffline(server)) {
                resetMaceCrafting(server, false);
            } else {
                maceCrafted = true;
            }
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> onUseEntity(player, world, hand, entity, hit));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> onUseBlock(player, world, hand, hit));
    }

    private static ActionResult onUseEntity(PlayerEntity player, net.minecraft.world.World world, Hand hand, Entity entity, EntityHitResult hit) {
        if (world.isClient()) return ActionResult.PASS;
        if (isMace(player.getMainHandStack()) || isMace(player.getOffHandStack())) {
            if (entity instanceof ItemFrameEntity) return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private static ActionResult onUseBlock(PlayerEntity player, net.minecraft.world.World world, Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.PASS;
        if (!(isMace(player.getMainHandStack()) || isMace(player.getOffHandStack()))) return ActionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof Inventory) return ActionResult.FAIL;
        return ActionResult.PASS;
    }

    private static void yankFromOpenContainers(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ScreenHandler open = p.currentScreenHandler;
            if (open == null || open == p.playerScreenHandler) continue;

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

                        if (!p.getInventory().insertStack(copy)) {
                            p.dropItem(copy, false);
                        }
                        p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8f, 1.6f);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void blockCraftResults(MinecraftServer server) {
        boolean locked = anyMaceInPlayersOrOffline(server);
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

    private static void enforceOneMaceLight(MinecraftServer server) {
        if (++tickGuard % SCAN_INTERVAL_TICKS != 0) return;

        boolean found = false;
        UUID foundOwner = null;
        boolean announcedDuplicateThisPass = false;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerInventory inv = player.getInventory();

            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (isMace(s)) {
                    if (!found) {
                        found = true;
                        foundOwner = player.getUuid();
                    } else {
                        s.setCount(0);
                        if (!announcedDuplicateThisPass) {
                            announcedDuplicateThisPass = true;
                            announceAndSound(server, "A Mace has already been crafted!", SoundEvents.BLOCK_ANVIL_LAND);
                        }
                        refundRecipeMaterials(player);
                    }
                }
            }
            for (int i = 0; i < player.getEnderChestInventory().size(); i++) {
                ItemStack s = player.getEnderChestInventory().getStack(i);
                if (isMace(s)) {
                    if (!found) {
                        found = true;
                        foundOwner = player.getUuid();
                    } else {
                        s.setCount(0);
                        if (!announcedDuplicateThisPass) {
                            announcedDuplicateThisPass = true;
                            announceAndSound(server, "A Mace has already been crafted!", SoundEvents.BLOCK_ANVIL_LAND);
                        }
                        refundRecipeMaterials(player);
                    }
                }
            }
        }

        boolean offlineHas = offlineInventory.values().stream().anyMatch(Boolean::booleanValue);
        if (!found && !offlineHas) {
            if (maceCrafted) resetMaceCrafting(server, true);
            return;
        }

        if (found) {
            maceCrafted = true;
            if (foundOwner != null) maceOwner = foundOwner;
        }
    }

    public static boolean isMace(ItemStack stack) {
        return stack != null && stack.isOf(Items.MACE) && stack.getCount() > 0;
    }

    private static boolean hasMaceAnywhere(ServerPlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++)
            if (isMace(p.getInventory().getStack(i))) return true;
        for (int i = 0; i < p.getEnderChestInventory().size(); i++)
            if (isMace(p.getEnderChestInventory().getStack(i))) return true;
        return false;
    }

    private static boolean anyMaceInPlayersOrOffline(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            for (int i = 0; i < p.getInventory().size(); i++)
                if (p.getInventory().getStack(i).isOf(Items.MACE)) return true;
            for (int i = 0; i < p.getEnderChestInventory().size(); i++)
                if (p.getEnderChestInventory().getStack(i).isOf(Items.MACE)) return true;
        }
        return offlineInventory.values().stream().anyMatch(Boolean::booleanValue);
    }

    private static void refundRecipeMaterials(ServerPlayerEntity player) {
        ItemStack core = new ItemStack(Items.HEAVY_CORE);
        ItemStack rod = new ItemStack(Items.BREEZE_ROD);
        if (!player.getInventory().insertStack(core)) player.dropItem(core, false);
        if (!player.getInventory().insertStack(rod)) player.dropItem(rod, false);
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 1.8f);
    }

    private static void announceAndSound(MinecraftServer server, String msg, SoundEvent sound) {
        server.getPlayerManager().broadcast(Text.literal("§e" + msg), false);
        // Loop through all dimensions
        for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity p : world.getPlayers()) {
                world.playSound(null, p.getBlockPos(), sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        }
    }

    public static void resetMaceCrafting(MinecraftServer server, boolean announce) {
        maceCrafted = false;
        maceOwner = null;
        offlineInventory.clear();
        if (announce) {
            announceAndSound(server, "The Mace has been lost! Crafting is re-enabled.", SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER);
        }
    }

    private void loadOfflineData(MinecraftServer server) {
        try {
            File file = server.getSavePath(WorldSavePath.ROOT).resolve(OFFLINE_FILE_NAME).toFile();
            if (!file.exists()) return;
            Properties props = new Properties();
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
            }
            offlineInventory.clear();
            for (String k : props.stringPropertyNames()) {
                boolean val = Boolean.parseBoolean(props.getProperty(k, "false"));
                offlineInventory.put(UUID.fromString(k), val);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveOfflineData(MinecraftServer server) {
        try {
            File file = server.getSavePath(WorldSavePath.ROOT).resolve(OFFLINE_FILE_NAME).toFile();
            Properties props = new Properties();
            for (Map.Entry<UUID, Boolean> e : offlineInventory.entrySet()) {
                props.setProperty(e.getKey().toString(), Boolean.toString(e.getValue()));
            }
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, "OneMace offline holder flags");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {}
}
