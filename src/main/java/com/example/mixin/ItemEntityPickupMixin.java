package com.example.mixin;

import com.example.SingleMaceManager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {
	@Unique
	private boolean singleMace$pickupStarted;

	@Inject(method = "playerTouch", at = @At("HEAD"))
	private void singleMace$rememberMacePickup(Player player, CallbackInfo ci) {
		this.singleMace$pickupStarted = ((ItemEntity) (Object) this).getItem().is(Items.MACE);
	}

	@Inject(method = "playerTouch", at = @At("TAIL"))
	private void singleMace$cancelTimerOnPickup(Player player, CallbackInfo ci) {
		ItemEntity item = (ItemEntity) (Object) this;
		if (this.singleMace$pickupStarted && item.isRemoved()) {
			SingleMaceManager.onMacePickedUp(item);
		}
		this.singleMace$pickupStarted = false;
	}
}