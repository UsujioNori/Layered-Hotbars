package com.usujiotarako.mixin;

import com.usujiotarako.access.ExtraStorageAccess;
import com.usujiotarako.inventory.ExtraStorageSlot;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.usujiotarako.BehaviorPolicy;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void betterHotbars$addFourthStorageRow(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		if (!BehaviorPolicy.hasExtraRow(owner) && !BehaviorPolicy.keepsPhysicalProfiles(owner)) return;
		ExtraStorageInventory extraStorage = ((ExtraStorageAccess)owner).betterHotbars$getExtraStorage();
		AbstractContainerMenuAccessor menu = (AbstractContainerMenuAccessor)this;
		if (BehaviorPolicy.hasExtraRow(owner)) {
			for (int slot = 0; slot < ExtraStorageInventory.VISIBLE_SLOT_COUNT; slot++) menu.betterHotbars$addSlot(new ExtraStorageSlot(inventory, extraStorage, slot, 8 + slot * 18, 138));
		} else {
			// Server-synchronised backing slots for Vanilla+. They intentionally live
			// outside the screen; only profile-switch SWAP packets address them.
			for (int slot = 0; slot < ExtraStorageInventory.PHYSICAL_PROFILE_SLOT_COUNT; slot++) {
				int local = ExtraStorageInventory.PHYSICAL_PROFILE_BASE + slot;
				menu.betterHotbars$addSlot(new ExtraStorageSlot(inventory, extraStorage, local, -10000, -10000));
			}
		}
	}
}
