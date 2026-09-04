package com.usujiotarako.mixin;

import com.usujiotarako.access.ExtraStorageAccess;
import com.usujiotarako.inventory.ExtraStorageSlot;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.usujiotarako.BehaviorPolicy;

@Pseudo
@Mixin(targets = "com.tiviacz.travelersbackpack.inventory.menu.BackpackBaseMenu", remap = false)
public abstract class TravelersBackpackMenuMixin {
	@Inject(method = "<init>", at = @At("TAIL"), remap = false)
	private void betterHotbars$addExtraStorageToBackpack(CallbackInfo ci) {
		AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
		Inventory inventory = null;
		int hotbarY = Integer.MAX_VALUE;
		int hotbarX = Integer.MAX_VALUE;
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory found) {
				inventory = found;
				if (!(slot instanceof ExtraStorageSlot) && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 9) {
					hotbarY = Math.min(hotbarY, slot.y);
					hotbarX = Math.min(hotbarX, slot.x);
				}
			}
		}
		if (inventory == null || !BehaviorPolicy.hasExtraRow(inventory.player) || hotbarY == Integer.MAX_VALUE) return;
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		if (menu.slots.stream().anyMatch(slot -> slot instanceof ExtraStorageSlot)) return;
		AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor)this;
		for (int slot = 0; slot < 9; slot++) {
			accessor.betterHotbars$addSlot(new ExtraStorageSlot(inventory, extra, slot, hotbarX + slot * 18, hotbarY - 4));
		}
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, remap = false)
	private void betterHotbars$quickMoveExtraStorage(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
		if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;
		ExtraStorageInventory extra = ((ExtraStorageAccess)player).betterHotbars$getExtraStorage();
		Slot source = menu.slots.get(slotIndex);
		if (!(source instanceof ExtraStorageSlot) || !source.hasItem()) return;

		ItemStack sourceStack = source.getItem();
		ItemStack original = sourceStack.copy();
		int before = sourceStack.getCount();
		Inventory inventory = player.getInventory();
		for (Slot target : menu.slots) {
			if (sourceStack.isEmpty()) break;
			if (target == source || target.container == inventory || target instanceof ExtraStorageSlot) continue;
			target.safeInsert(sourceStack);
		}
		if (sourceStack.getCount() == before) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		if (sourceStack.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
		else source.setChanged();
		cir.setReturnValue(original);
	}
}
