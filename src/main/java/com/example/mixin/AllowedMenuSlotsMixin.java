package com.example.mixin;

import com.example.SingleMaceManager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class AllowedMenuSlotsMixin {
	@Inject(method = "addSlot", at = @At("TAIL"))
	private void singleMace$registerAllowedMenuSlots(Slot slot, CallbackInfoReturnable<Slot> cir) {
		if ((Object) this instanceof AnvilMenu || (Object) this instanceof EnchantmentMenu) {
			SingleMaceManager.registerAllowedMenuContainer(slot.container);
		}
	}
}