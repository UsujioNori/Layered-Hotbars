package com.usujiotarako.mixin;

import com.usujiotarako.access.ExtraStorageAccess;
import net.minecraft.server.level.ServerPlayer;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void betterHotbars$restoreExtraStorage(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
		if (!restoreAll) return;
		ExtraStorageInventory source = ((ExtraStorageAccess)oldPlayer).betterHotbars$getExtraStorage();
		ExtraStorageInventory target = ((ExtraStorageAccess)this).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < ExtraStorageInventory.SLOT_COUNT; slot++) target.setItem(slot, source.getItem(slot).copy());
	}
}
