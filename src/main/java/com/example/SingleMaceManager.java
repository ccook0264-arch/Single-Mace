package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class SingleMaceManager {
	private static final int DROP_ANNOUNCEMENT_DELAY_TICKS = 5 * 60 * 20;
	private static final Map<UUID, PendingMaceDrop> PENDING_MACE_DROPS = new HashMap<>();
	private static final Set<Container> ALLOWED_MENU_CONTAINERS = Collections.synchronizedSet(
		Collections.newSetFromMap(new WeakHashMap<>())
	);

	private static final SavedDataType<MaceState> STATE_TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(SingleMace.MOD_ID, "mace_state"),
		MaceState::new,
		Codec.STRING.xmap(UUID::fromString, UUID::toString)
			.listOf()
			.fieldOf("craftedPlayers")
			.xmap(MaceState::new, state -> new ArrayList<>(state.craftedPlayers))
			.codec(),
		DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private SingleMaceManager() {
	}

	public static void registerAllowedMenuContainer(Container container) {
		ALLOWED_MENU_CONTAINERS.add(container);
	}

	public static boolean isDisallowedMaceStorage(Object storage) {
		if (storage instanceof Inventory || storage instanceof PlayerEnderChestContainer) {
			return false;
		}
		return !(storage instanceof Container container && ALLOWED_MENU_CONTAINERS.contains(container));
	}

	public static boolean isMaceAlreadyCrafted(ServerPlayer player) {
		return getState(player).craftedPlayers.contains(player.getUUID());
	}

	public static boolean resetMaceCraftedState(ServerPlayer player) {
		return resetMaceCraftedState(player.level().getServer(), player.getUUID());
	}

	private static boolean resetMaceCraftedState(MinecraftServer server, UUID playerId) {
		MaceState state = getState(server);
		if (state.craftedPlayers.remove(playerId)) {
			state.setDirty();
			return true;
		}
		return false;
	}

	public static List<String> getMaceStatus(MinecraftServer server) {
		MaceState state = getState(server);
		List<String> report = new ArrayList<>();
		Set<UUID> onlineRecordedPlayers = new HashSet<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean recorded = state.craftedPlayers.contains(player.getUUID());
			boolean hasMace = hasMaceInAccessibleStorage(player);
			if (recorded) {
				onlineRecordedPlayers.add(player.getUUID());
			}
			if (recorded || hasMace) {
				String status = hasMace
					? "mace found in inventory, Ender Chest, or open menu"
					: "craft recorded, but no mace found in inventory, Ender Chest, or open menu";
				if (!recorded) {
					status = "mace found, but no craft record is saved";
				}
				report.add(player.getName().getString() + ": " + status + ".");
			}
		}

		for (UUID playerId : state.craftedPlayers) {
			if (!onlineRecordedPlayers.contains(playerId)) {
				report.add(playerId + ": craft recorded; player is offline, so inventory was not checked.");
			}
		}

		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity && itemEntity.getItem().is(Items.MACE)) {
					String owner = itemEntity.getOwner() instanceof ServerPlayer player
						? " (owner " + player.getName().getString() + ")"
						: "";
					report.add("Dropped mace at " + level.dimension().identifier() + " "
						+ (int) entity.getX() + " " + (int) entity.getY() + " " + (int) entity.getZ() + owner + ".");
				}
			}
		}

		if (report.isEmpty()) {
			report.add("No saved mace-craft records or maces found in online player storage or loaded ground items.");
		}
		return report;
	}

	private static boolean hasMaceInAccessibleStorage(ServerPlayer player) {
		return player.getInventory().contains(stack -> stack.is(Items.MACE))
			|| containsMace(player.getEnderChestInventory())
			|| player.containerMenu.slots.stream().anyMatch(slot -> slot.getItem().is(Items.MACE));
	}

	private static boolean containsMace(Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).is(Items.MACE)) {
				return true;
			}
		}
		return false;
	}

	public static void onMaceCrafted(ServerPlayer player) {
		MaceState state = getState(player);
		if (state.craftedPlayers.add(player.getUUID())) {
			state.setDirty();
			Component title = Component.literal(player.getName().getString()).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" has crafted the mace").withStyle(ChatFormatting.WHITE));
			announce(player.level().getServer(), title, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
		}
	}

	public static void onMaceDropped(ServerPlayer player, ItemEntity droppedMace) {
		PENDING_MACE_DROPS.put(droppedMace.getUUID(), new PendingMaceDrop(
			droppedMace,
			player.getUUID(),
			player.getName().getString()
		));
	}

	public static void onMacePickedUp(ItemEntity mace) {
		PENDING_MACE_DROPS.remove(mace.getUUID());
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, PendingMaceDrop>> drops = PENDING_MACE_DROPS.entrySet().iterator();
		while (drops.hasNext()) {
			PendingMaceDrop pending = drops.next().getValue();
			if (!pending.item.getItem().is(Items.MACE) && !pending.item.isRemoved()) {
				drops.remove();
				continue;
			}
			if (--pending.ticksRemaining <= 0 && pending.item.isRemoved()) {
				resetMaceCraftedState(server, pending.dropperId);
				Component title = Component.literal("The mace dropped by ").withStyle(ChatFormatting.WHITE)
					.append(Component.literal(pending.dropperName).withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(" has disappeared.").withStyle(ChatFormatting.WHITE));
				announce(server, title, SoundEvents.LIGHTNING_BOLT_THUNDER);
				drops.remove();
			}
		}
	}

	public static void notifyAlreadyCrafted(ServerPlayer player, boolean ignored) {
		player.sendSystemMessage(Component.literal(
			"Your mace craft is already recorded. If this is incorrect, ask an operator to run /singlemace reset "
				+ player.getName().getString() + "."
		));
	}

	private static void announce(MinecraftServer server, Component title, SoundEvent sound) {
		var soundHolder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
		for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
			recipient.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			recipient.connection.send(new ClientboundSetTitleTextPacket(title));
			recipient.connection.send(new ClientboundSoundPacket(
				soundHolder,
				SoundSource.MASTER,
				recipient.getX(),
				recipient.getY(),
				recipient.getZ(),
				1.0F,
				1.0F,
				ThreadLocalRandom.current().nextLong()
			));
		}
	}

	private static MaceState getState(ServerPlayer player) {
		return getState(player.level().getServer());
	}

	private static MaceState getState(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(STATE_TYPE);
	}

	private static final class MaceState extends SavedData {
		private final Set<UUID> craftedPlayers;

		private MaceState() {
			this(Set.of());
		}

		private MaceState(java.util.List<UUID> players) {
			this(new java.util.HashSet<>(players));
		}

		private MaceState(Set<UUID> players) {
			this.craftedPlayers = new java.util.HashSet<>(players);
		}
	}

	private static final class PendingMaceDrop {
		private final ItemEntity item;
		private final UUID dropperId;
		private final String dropperName;
		private int ticksRemaining = DROP_ANNOUNCEMENT_DELAY_TICKS;

		private PendingMaceDrop(ItemEntity item, UUID dropperId, String dropperName) {
			this.item = item;
			this.dropperId = dropperId;
			this.dropperName = dropperName;
		}
	}
}