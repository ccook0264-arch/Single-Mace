package com.example;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SingleMace implements ModInitializer {
	public static final String MOD_ID = "single-mace";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Single Mace initialized");
		ServerTickEvents.END_SERVER_TICK.register(SingleMaceManager::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("mace")
				.executes(context -> {
					MinecraftServer server = context.getSource().getServer();
					Component report = Component.literal(String.join("\n", SingleMaceManager.getMaceStatus(server)));
					context.getSource().sendSuccess(() -> report, false);
					return 1;
				}));
			dispatcher.register(Commands.literal("singlemace")
				.then(Commands.literal("reset")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> {
							ServerPlayer target = EntityArgument.getPlayer(context, "player");
							boolean reset = SingleMaceManager.resetMaceCraftedState(target);
							String message = reset
								? "Cleared the saved mace-craft record for " + target.getName().getString() + "."
								: target.getName().getString() + " has no saved mace-craft record.";
							context.getSource().sendSuccess(() -> Component.literal(message), true);
							return reset ? 1 : 0;
						}))));
		});
	}
}