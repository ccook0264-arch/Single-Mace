package com.example.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public abstract class ItemFrameEntityMixin {
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void singleMace$blockMaceInFrame(Player player, InteractionHand hand, Vec3 hitPosition,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (player.getItemInHand(hand).is(Items.MACE)) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Maces cannot be placed in item frames."));
			}
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}