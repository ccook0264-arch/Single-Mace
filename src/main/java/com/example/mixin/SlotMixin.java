package com.example.mixin;

import com.example.SingleMaceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void singleMace$canInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.is(Items.MACE) && SingleMaceManager.isDisallowedMaceStorage(container)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "safeTake", at = @At("HEAD"), cancellable = true)
	private void singleMace$blockDuplicateResultTake(int amount, int decrement, Player player,
			CallbackInfoReturnable<ItemStack> cir) {
		if (player instanceof ServerPlayer serverPlayer && (Object) this instanceof ResultSlot
				&& ((Slot) (Object) this).getItem().is(Items.MACE)
				&& SingleMaceManager.isMaceAlreadyCrafted(serverPlayer)) {
			SingleMaceManager.notifyAlreadyCrafted(serverPlayer, false);
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}

	@Inject(method = "getItem", at = @At("RETURN"), cancellable = true)
	private void singleMace$hideDuplicateMaceResult(CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof ResultSlot resultSlot
				&& resultSlot instanceof ResultSlotAccessor accessor
				&& accessor.singleMace$getPlayer() instanceof ServerPlayer serverPlayer
				&& cir.getReturnValue().is(Items.MACE)
				&& SingleMaceManager.isMaceAlreadyCrafted(serverPlayer)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}