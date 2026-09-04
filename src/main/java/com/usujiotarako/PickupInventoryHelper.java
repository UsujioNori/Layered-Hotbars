package com.usujiotarako;

import com.usujiotarako.access.ExtraStorageAccess;
import net.minecraft.core.component.DataComponents;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Layered Hotbars' custom insertion order for genuine world-item pickups.
 *
 * This helper is deliberately NOT installed on Inventory.add globally.
 * Inventory-management/container mods (including IPN) must be allowed to use
 * vanilla Inventory.add semantics during their own click/transfer sequences.
 */
public final class PickupInventoryHelper {
	private PickupInventoryHelper() {}

	public static boolean addWorldPickup(Inventory inventory, ItemStack incoming) {
		if (incoming.isEmpty()) return false;

		ExtraStorageInventory extra = BehaviorPolicy.hasExtraRow(inventory.player)
				? ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage() : null;
		int originalCount = incoming.getCount();
		boolean physicalMode = !BehaviorPolicy.modeFor(inventory.player).usesVirtualHotbars();
		boolean physicalCasual = physicalMode && !PickupPolicy.isAggressive();

		// In the physical modes Casual follows vanilla's hotbar-first pickup order.
		// Aggressive does not create new ordinary-item stacks on the hotbar, but it
		// must still top up an already-present compatible hotbar stack before routing
		// the remainder into storage. This keeps Vanilla/Vanilla+ feeling vanilla
		// without changing Partial/Full's virtual-hotbar rules.
		if (physicalCasual) {
			for (int slot = 0; slot < 9 && !incoming.isEmpty(); slot++) merge(incoming, inventory.getItem(slot));
			while (!incoming.isEmpty() && placeInEmptyHotbar(inventory, incoming)) { }
			for (int slot = 9; slot < Inventory.INVENTORY_SIZE && !incoming.isEmpty(); slot++) merge(incoming, inventory.getItem(slot));
			while (!incoming.isEmpty() && placeInEmptyStorage(inventory, null, incoming)) { }
			return incoming.getCount() < originalCount;
		}

		if (prefersPhysicalHotbar(incoming)) {
			for (int slot = 0; slot < 9 && !incoming.isEmpty(); slot++) {
				merge(incoming, inventory.getItem(slot));
			}
			while (!incoming.isEmpty() && placeInEmptyHotbar(inventory, incoming)) {
			}
			for (int slot = 9; slot < Inventory.INVENTORY_SIZE && !incoming.isEmpty(); slot++) {
				merge(incoming, inventory.getItem(slot));
			}
			for (int slot = 0; extra != null && slot < 9 && !incoming.isEmpty(); slot++) {
				merge(incoming, extra.getItem(slot));
			}
			while (!incoming.isEmpty() && placeInEmptyStorage(inventory, extra, incoming)) {
			}
			return incoming.getCount() < originalCount;
		}

		// In Vanilla/Vanilla+ Aggressive mode, preserve an existing physical hotbar
		// stack by filling it first. We deliberately do NOT place the item into an
		// empty hotbar slot; only an already-matching stack is eligible here.
		if (physicalMode) {
			for (int slot = 0; slot < 9 && !incoming.isEmpty(); slot++) {
				merge(incoming, inventory.getItem(slot));
			}
		}

		// Stackable blocks/items are real inventory contents, not physical-hotbar
		// contents. Fill the vanilla three storage rows first, then Layered Hotbars'
		// fourth row. In Partial/Full the virtual hotbar is only a reference to these
		// backing stacks.
		for (int slot = 9; slot < Inventory.INVENTORY_SIZE && !incoming.isEmpty(); slot++) {
			merge(incoming, inventory.getItem(slot));
		}
		for (int slot = 0; extra != null && slot < 9 && !incoming.isEmpty(); slot++) {
			merge(incoming, extra.getItem(slot));
		}
		while (!incoming.isEmpty() && placeInEmptyStorage(inventory, extra, incoming)) {
		}
		return incoming.getCount() < originalCount;
	}

	private static boolean prefersPhysicalHotbar(ItemStack stack) {
		return stack.has(DataComponents.TOOL)
				|| stack.has(DataComponents.WEAPON)
				|| stack.has(DataComponents.EQUIPPABLE)
				|| stack.has(DataComponents.BLOCKS_ATTACKS)
				|| stack.has(DataComponents.PIERCING_WEAPON)
				|| stack.has(DataComponents.KINETIC_WEAPON);
	}

	private static void merge(ItemStack source, ItemStack target) {
		if (target.isEmpty() || !ItemStack.isSameItemSameComponents(source, target) || !target.isStackable()) return;
		int moved = Math.min(source.getCount(), target.getMaxStackSize() - target.getCount());
		if (moved > 0) {
			target.grow(moved);
			source.shrink(moved);
		}
	}

	private static boolean placeInEmptyStorage(Inventory inventory, ExtraStorageInventory extra, ItemStack source) {
		for (int slot = 9; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, source.split(Math.min(source.getCount(), source.getMaxStackSize())));
				return true;
			}
		}
		if (extra == null) return false;
		for (int slot = 0; slot < 9; slot++) {
			if (extra.getItem(slot).isEmpty()) {
				extra.setItem(slot, source.split(Math.min(source.getCount(), source.getMaxStackSize())));
				return true;
			}
		}
		return false;
	}

	private static boolean placeInEmptyHotbar(Inventory inventory, ItemStack source) {
		for (int slot = 0; slot < 9; slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, source.split(Math.min(source.getCount(), source.getMaxStackSize())));
				return true;
			}
		}
		return false;
	}
}
