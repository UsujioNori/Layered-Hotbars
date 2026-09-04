package com.usujiotarako.mixin;

import com.usujiotarako.access.ExtraStorageAccess;
import com.usujiotarako.inventory.ExtraStorageSlot;
import net.minecraft.world.Container;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.usujiotarako.BehaviorPolicy;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Shadow protected abstract void addInventoryHotbarSlots(Container container, int left, int top);

	@Redirect(
			method = "addStandardInventorySlots",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;addInventoryHotbarSlots(Lnet/minecraft/world/Container;II)V"
			)
	)
	private void betterHotbars$addExtraRowBeforeHotbar(AbstractContainerMenu menu, Container container, int left, int top) {
		if (!(container instanceof Inventory inventory) || !BehaviorPolicy.hasExtraRow(inventory.player) || (Object)this instanceof InventoryMenu) {
			this.addInventoryHotbarSlots(container, left, top);
			return;
		}
		ExtraStorageInventory extraStorage = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor)this;
		for (int slot = 0; slot < 9; slot++) {
			accessor.betterHotbars$addSlot(new ExtraStorageSlot(inventory, extraStorage, slot, left + slot * 18, top - 4));
		}
		this.addInventoryHotbarSlots(container, left, top + 18);
	}
}
