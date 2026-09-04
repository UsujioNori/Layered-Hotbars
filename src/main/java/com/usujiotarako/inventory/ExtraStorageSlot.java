package com.usujiotarako.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Fourth-row slot that presents itself as part of the player's real Inventory
 * while storing its contents in Layered Hotbars' separate nine-slot store.
 *
 * IPN therefore sees the exact same backing Inventory object for rows 1-4.
 * Minecraft's real Inventory list is not expanded, so equipment/offhand indices
 * remain untouched. Vanilla sees a safe backing slot 0-8; only IPN sees the
 * logical extended player-storage id 43-51.
 */
public class ExtraStorageSlot extends Slot {
    private final ExtraStorageInventory extraStorage;
    private final int localSlot;

    public ExtraStorageSlot(Inventory playerInventory, ExtraStorageInventory extraStorage, int localSlot, int x, int y) {
        super(playerInventory, localSlot, x, y);
        this.extraStorage = extraStorage;
        this.localSlot = localSlot;
    }

    public int getLocalStorageSlot() {
        return this.localSlot;
    }

    /**
     * Logical player-inventory id exposed specifically to IPN's Slot accessor.
     * Vanilla still sees this Slot's real backing index as 0-8, which keeps all
     * normal Slot/container operations inside the valid Inventory range.
     */
    public int getInvSlot() {
        return ExtraStorageInventory.toContainerSlot(this.localSlot);
    }

    public int getLogicalInventorySlot() {
        return ExtraStorageInventory.toContainerSlot(this.localSlot);
    }

    @Override
    public ItemStack getItem() {
        return this.extraStorage.getItem(this.localSlot);
    }

    @Override
    public boolean hasItem() {
        return !this.getItem().isEmpty();
    }

    @Override
    public void set(ItemStack stack) {
        this.extraStorage.setItem(this.localSlot, stack);
        this.setChanged();
    }

    @Override
    public ItemStack remove(int amount) {
        return this.extraStorage.removeItem(this.localSlot, amount);
    }

    @Override
    public void setChanged() {
        this.extraStorage.setChanged();
    }
}
