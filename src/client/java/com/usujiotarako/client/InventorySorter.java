package com.usujiotarako.client;

import com.usujiotarako.inventory.ExtraStorageSlot;
import com.usujiotarako.access.ExtraStorageAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.usujiotarako.BehaviorPolicy;

public final class InventorySorter {
	private InventorySorter() {}

	public static boolean sort(Minecraft client, AbstractContainerMenu menu) {
		if (client.player == null || client.gameMode == null || !menu.getCarried().isEmpty()
				|| VirtualHotbarManager.hasVirtualCursor()) return false;

		List<Slot> storage = selectStorage(client, menu);
		if (storage.size() < 2) return false;
		return sortSlots(client, menu, storage);
	}

	private static List<Slot> selectStorage(Minecraft client, AbstractContainerMenu menu) {
		Inventory inventory = client.player.getInventory();
		ExtraStorageInventory extra = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();

		// The inventory screen sorts the player's full backing storage exactly as before.
		if (client.gui.screen() instanceof InventoryScreen) return collectPlayerStorage(menu, inventory, extra);

		// In chests and other storage-style menus, sort the largest non-player container.
		// Requiring at least nine slots deliberately avoids scrambling small functional
		// station inventories such as furnaces, smithing tables and brewing stands.
		Map<Object, List<Slot>> externalGroups = new IdentityHashMap<>();
		for (Slot slot : menu.slots) {
			if (slot.container == inventory || slot instanceof ExtraStorageSlot) continue;
			externalGroups.computeIfAbsent(slot.container, ignored -> new ArrayList<>()).add(slot);
		}
		List<Slot> largest = null;
		for (List<Slot> group : externalGroups.values()) {
			if (largest == null || group.size() > largest.size()) largest = group;
		}
		if (largest != null && largest.size() >= 9) return largest;

		// Stations still get the button; there it sorts the player's backing inventory.
		return collectPlayerStorage(menu, inventory, extra);
	}

	private static List<Slot> collectPlayerStorage(AbstractContainerMenu menu, Inventory inventory, ExtraStorageInventory extra) {
		List<Slot> storage = new ArrayList<>(36);
		for (Slot slot : menu.slots) {
			if (slot.container == inventory
					&& slot.getContainerSlot() >= 9
					&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE) storage.add(slot);
		}
		if (BehaviorPolicy.hasExtraRow()) for (Slot slot : menu.slots) if (slot instanceof ExtraStorageSlot) storage.add(slot);
		return storage;
	}

	private static boolean sortSlots(Minecraft client, AbstractContainerMenu menu, List<Slot> storage) {
		// Consolidate compatible partial stacks before ordering them.
		for (int targetIndex = 0; targetIndex < storage.size(); targetIndex++) {
			Slot target = storage.get(targetIndex);
			if (target.getItem().isEmpty()) continue;
			for (int sourceIndex = targetIndex + 1;
					sourceIndex < storage.size() && target.getItem().getCount() < target.getItem().getMaxStackSize();
					sourceIndex++) {
				Slot source = storage.get(sourceIndex);
				if (!source.getItem().isEmpty() && ItemStack.isSameItemSameComponents(target.getItem(), source.getItem())) {
					click(client, menu, source);
					click(client, menu, target);
					if (!menu.getCarried().isEmpty()) click(client, menu, source);
				}
			}
		}

		List<ItemStack> ordered = storage.stream()
				.map(Slot::getItem)
				.filter(stack -> !stack.isEmpty())
				.map(ItemStack::copy)
				.sorted(Comparator
						.comparing((ItemStack stack) -> stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
						.thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
						.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed()))
				.toList();

		for (int targetIndex = 0; targetIndex < storage.size(); targetIndex++) {
			ItemStack wanted = targetIndex < ordered.size() ? ordered.get(targetIndex) : ItemStack.EMPTY;
			Slot target = storage.get(targetIndex);
			if (sameStack(target.getItem(), wanted)) continue;
			int sourceIndex = -1;
			for (int candidate = targetIndex + 1; candidate < storage.size(); candidate++) {
				if (sameStack(storage.get(candidate).getItem(), wanted)) {
					sourceIndex = candidate;
					break;
				}
			}
			if (sourceIndex < 0) continue;
			Slot source = storage.get(sourceIndex);
			click(client, menu, source);
			click(client, menu, target);
			if (!menu.getCarried().isEmpty()) click(client, menu, source);
		}
		return true;
	}

	private static void click(Minecraft client, AbstractContainerMenu menu, Slot slot) {
		client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, client.player);
	}

	private static boolean sameStack(ItemStack first, ItemStack second) {
		if (first.isEmpty() || second.isEmpty()) return first.isEmpty() && second.isEmpty();
		return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
	}
}
