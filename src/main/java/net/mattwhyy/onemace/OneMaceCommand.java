package net.mattwhyy.onemace;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OneMaceCommand {

    private OneMaceCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("onemace")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(CommandManager.literal("locate").executes(ctx -> locate(ctx.getSource())))
                .then(CommandManager.literal("fix").executes(ctx -> fix(ctx.getSource())))
        );
        d.register(CommandManager.literal("om")
                .requires(src -> src.hasPermissionLevel(2))
                .redirect(d.getRoot().getChild("onemace")));
    }

    private static int info(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal("§eOneMace — only one mace may exist at any time."), false);
        src.sendFeedback(() -> Text.literal("§7• If a mace exists, duplicates are removed automatically."), false);
        src.sendFeedback(() -> Text.literal("§7• If the mace is gone (including drops), crafting is allowed again."), false);
        src.sendFeedback(() -> Text.literal("§7• Offline tracking keeps crafting blocked if someone logs out with it."), false);
        src.sendFeedback(() -> Text.literal("§7• /onemace locate — search players or offline record."), false);
        src.sendFeedback(() -> Text.literal("§7• /onemace fix — keep one mace, delete extras among online players."), false);
        return 1;
    }

    private static int locate(ServerCommandSource src) {
        MinecraftServer server = src.getServer();

        // Search online players first
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            for (int i = 0; i < p.getInventory().size(); i++) {
                if (OneMace.isMace(p.getInventory().getStack(i))) {
                    src.sendFeedback(() -> Text.literal("§aThe Mace is in §b" + p.getName().getString() + "§a's inventory."), false);
                    return 1;
                }
            }
            for (int i = 0; i < p.getEnderChestInventory().size(); i++) {
                if (OneMace.isMace(p.getEnderChestInventory().getStack(i))) {
                    src.sendFeedback(() -> Text.literal("§aThe Mace is in §b" + p.getName().getString() + "§a's Ender Chest."), false);
                    return 1;
                }
            }
        }

        // If not found, just report UUIDs from offline cache
        if (!OneMace.offlineInventory.isEmpty()) {
            for (UUID uuid : OneMace.offlineInventory.keySet()) {
                if (Boolean.TRUE.equals(OneMace.offlineInventory.get(uuid))) {
                    src.sendFeedback(() -> Text.literal("§eThe Mace is in offline player with UUID §b" + uuid + "§e."), false);
                    return 1;
                }
            }
        }

        src.sendFeedback(() -> Text.literal("§cThe Mace wasn't found among online players or offline records. It might be in an unloaded area."), false);
        return 1;
    }

    private static int fix(ServerCommandSource src) {
        MinecraftServer server = src.getServer();
        List<ItemStack> all = new ArrayList<>();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s != null && s.getItem() == Items.MACE && s.getCount() > 0) all.add(s);
            }
            for (int i = 0; i < p.getEnderChestInventory().size(); i++) {
                ItemStack s = p.getEnderChestInventory().getStack(i);
                if (s != null && s.getItem() == Items.MACE && s.getCount() > 0) all.add(s);
            }
        }

        if (all.isEmpty()) {
            OneMace.maceCrafted = false;
            OneMace.maceOwner = null;
            OneMace.offlineInventory.clear();
            OneMace.saveConfig();
            src.sendFeedback(() -> Text.literal("§aNo maces found among online players. Crafting is unlocked."), false);
            return 1;
        }

        boolean kept = false;
        int removed = 0;
        for (ItemStack s : all) {
            if (!kept) {
                kept = true;
            } else {
                s.setCount(0);
                removed++;
            }
        }

        OneMace.maceCrafted = true;
        ServerPlayerEntity owner = findHolder(server);
        OneMace.maceOwner = owner != null ? owner.getUuid() : null;
        OneMace.saveConfig();

        final int removedCount = removed;
        src.sendFeedback(() -> Text.literal("§aFixed. Kept one mace, removed §c" + removedCount + " §aduplicate(s)."), false);
        return 1;
    }

    private static ServerPlayerEntity findHolder(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            for (int i = 0; i < p.getInventory().size(); i++) {
                if (OneMace.isMace(p.getInventory().getStack(i))) return p;
            }
            for (int i = 0; i < p.getEnderChestInventory().size(); i++) {
                if (OneMace.isMace(p.getEnderChestInventory().getStack(i))) return p;
            }
        }
        return null;
    }
}
