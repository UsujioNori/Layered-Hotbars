package com.usujiotarako.mixin;

import com.usujiotarako.PickupContext;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks only the duration of a real world-item pickup.
 *
 * These HEAD/RETURN injections intentionally do not redirect Inventory.add.
 * That allows Collective and other pickup mods to keep their own redirects
 * without a Mixin conflict.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"))
	private void betterHotbars$beginWorldPickup(Player player, CallbackInfo ci) {
		PickupContext.enterWorldPickup();
	}

	@Inject(method = "playerTouch", at = @At("RETURN"))
	private void betterHotbars$endWorldPickup(Player player, CallbackInfo ci) {
		PickupContext.exitWorldPickup();
	}
}
