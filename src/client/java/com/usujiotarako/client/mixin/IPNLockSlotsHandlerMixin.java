package com.usujiotarako.client.mixin;

import com.usujiotarako.client.IPNCompat;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.event.LockSlotsHandler", remap = false)
public abstract class IPNLockSlotsHandlerMixin {
	@Inject(method = "getSlotLocations", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
	private void betterHotbars$includeExtraLockLocations(CallbackInfoReturnable<Map<Object, Object>> cir) {
		cir.setReturnValue(IPNCompat.extendLockSlotLocations(cir.getReturnValue()));
	}

	@Inject(method = "isMappedSlotLocked", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void betterHotbars$recognizeExtraLock(Slot slot, CallbackInfoReturnable<Boolean> cir) {
		if (IPNCompat.isExtraSlotLocked(slot)) cir.setReturnValue(true);
	}
}
