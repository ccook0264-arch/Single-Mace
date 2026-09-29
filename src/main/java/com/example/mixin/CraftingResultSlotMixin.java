package com.example.mixin;

import com.example.SingleMaceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {
	@Inject(method = "safeClone", at = @At("HEAD"), cancellable = true)
	private void singleMace$blockShiftClone(Player player, CallbackInfoReturnable<ItemStack> cir) {
		if (player instanceof ServerPlayer serverPlayer
				&& ((ResultSlot) (Object) this).getItem().is(Items.MACE)
				&& SingleMaceManager.isMaceAlreadyCrafted(serverPlayer)) {
			SingleMaceManager.notifyAlreadyCrafted(serverPlayer, false);
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void singleMace$onTakeItem(Player player, ItemStack stack, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer && stack.is(Items.MACE)) {
			SingleMaceManager.onMaceCrafted(serverPlayer);
		}
	}
}