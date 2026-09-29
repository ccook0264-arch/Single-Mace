package com.example.mixin;

import com.example.SingleMaceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerDropMixin {
	@Inject(
		method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
		at = @At("RETURN")
	)
	private void singleMace$announceMaceDrop(ItemStack stack, boolean throwRandomly, Prediction prediction,
			CallbackInfoReturnable<ItemEntity> cir) {
		if (stack.is(Items.MACE) && cir.getReturnValue() != null) {
			SingleMaceManager.onMaceDropped((ServerPlayer) (Object) this, cir.getReturnValue());
		}
	}
}