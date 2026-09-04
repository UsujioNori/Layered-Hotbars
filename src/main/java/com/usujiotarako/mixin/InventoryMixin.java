package com.usujiotarako.mixin;

import com.usujiotarako.PickupContext;
import com.usujiotarako.PickupInventoryHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies Layered Hotbars' custom insertion order only while ItemEntity.playerTouch
 * is actively processing a genuine world pickup.
 *
 * Outside that narrow scope Inventory.add is untouched. In particular IPN,
 * container clicks, shift transfers, crafting, and other inventory mods get the
 * normal Minecraft insertion semantics.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Inject(
			method = "add(ILnet/minecraft/world/item/ItemStack;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void betterHotbars$usePickupPolicyOnlyForWorldItems(
			int requestedSlot,
			ItemStack incoming,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!PickupContext.isWorldPickup()) return;
		if (requestedSlot != -1 || incoming.isEmpty()) return;

		Inventory inventory = (Inventory)(Object)this;
		if (inventory.player.getAbilities().instabuild) return;

		cir.setReturnValue(PickupInventoryHelper.addWorldPickup(inventory, incoming));
	}
}
