package com.usujiotarako.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory-typed backing store for Layered Hotbars' fourth player-storage row.
 *
 * IPN classifies player storage partly by the concrete backing Container type.
 * Using an Inventory subclass here makes row 4 genuinely player-inventory-backed
 * from inventory mods' point of view, while keeping Mojang's real Inventory
 * object and its equipment/offhand indices completely untouched.
 *
 * The nine physical row-4 slots use extended container indices 43..51. Better
 * Hotbars' own code may continue using local indices 0..8; both address the same
 * nine ItemStacks.
 */
public final class ExtraStorageInventory extends Inventory {
	public static final int SLOT_BASE = 43;
	/** Nine visible Full-mode row slots plus Vanilla+'s private profile storage. */
	public static final int VISIBLE_SLOT_COUNT = 9;
	public static final int PHYSICAL_PROFILE_BASE = 9;
	/** Keep the original 27 hotbar backing slots at their old indices for save compatibility. */
	public static final int PHYSICAL_PROFILE_HOTBAR_SLOT_COUNT = 27;
	/** Three new profile-specific offhand backing slots live after the legacy hotbar block. */
	public static final int PHYSICAL_PROFILE_OFFHAND_BASE = PHYSICAL_PROFILE_BASE + PHYSICAL_PROFILE_HOTBAR_SLOT_COUNT;
	public static final int PHYSICAL_PROFILE_OFFHAND_SLOT_COUNT = 3;
	public static final int PHYSICAL_PROFILE_SLOT_COUNT = PHYSICAL_PROFILE_HOTBAR_SLOT_COUNT + PHYSICAL_PROFILE_OFFHAND_SLOT_COUNT;
	public static final int SLOT_COUNT = VISIBLE_SLOT_COUNT + PHYSICAL_PROFILE_SLOT_COUNT;
	public static final int SLOT_END_EXCLUSIVE = SLOT_BASE + SLOT_COUNT;

	private final NonNullList<ItemStack> storage = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

	public ExtraStorageInventory(Player player) {
		super(player, new EntityEquipment());
	}

	public static int physicalProfileHotbarSlot(int profile, int hotbarSlot) {
		if (profile < 0 || profile >= 3 || hotbarSlot < 0 || hotbarSlot >= 9) {
			throw new IndexOutOfBoundsException("Physical profile hotbar slot " + profile + ":" + hotbarSlot);
		}
		return PHYSICAL_PROFILE_BASE + profile * 9 + hotbarSlot;
	}

	public static int physicalProfileOffhandSlot(int profile) {
		if (profile < 0 || profile >= 3) {
			throw new IndexOutOfBoundsException("Physical profile offhand slot " + profile);
		}
		return PHYSICAL_PROFILE_OFFHAND_BASE + profile;
	}

	public static int toContainerSlot(int localSlot) {
		if (localSlot < 0 || localSlot >= SLOT_COUNT) {
			throw new IndexOutOfBoundsException("Extra storage slot " + localSlot);
		}
		return SLOT_BASE + localSlot;
	}

	public static boolean isExtendedSlot(int slot) {
		return slot >= SLOT_BASE && slot < SLOT_END_EXCLUSIVE;
	}

	public static int toLocalSlot(int slot) {
		if (slot >= 0 && slot < SLOT_COUNT) return slot;
		if (isExtendedSlot(slot)) return slot - SLOT_BASE;
		return -1;
	}

	@Override
	public ItemStack getItem(int slot) {
		int local = toLocalSlot(slot);
		return local >= 0 ? storage.get(local) : ItemStack.EMPTY;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		int local = toLocalSlot(slot);
		if (local < 0) return;
		storage.set(local, stack);
		setChanged();
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		int local = toLocalSlot(slot);
		if (local < 0 || amount <= 0) return ItemStack.EMPTY;
		ItemStack current = storage.get(local);
		if (current.isEmpty()) return ItemStack.EMPTY;
		ItemStack removed = current.split(amount);
		if (current.isEmpty()) storage.set(local, ItemStack.EMPTY);
		if (!removed.isEmpty()) setChanged();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		int local = toLocalSlot(slot);
		if (local < 0) return ItemStack.EMPTY;
		ItemStack current = storage.get(local);
		storage.set(local, ItemStack.EMPTY);
		return current;
	}

	@Override
	public int getContainerSize() {
		// Must cover the extended Slot.slot values used by row 4.
		return SLOT_END_EXCLUSIVE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : storage) if (!stack.isEmpty()) return false;
		return true;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < storage.size(); i++) storage.set(i, ItemStack.EMPTY);
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return player == this.player;
	}

	@Override
	public void setChanged() {
		// This inventory is persisted by PlayerMixin. No vanilla inventory packet
		// bookkeeping is needed for the adapter itself.
	}
}
