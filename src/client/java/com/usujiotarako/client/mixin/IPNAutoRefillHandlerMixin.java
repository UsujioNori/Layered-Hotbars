package com.usujiotarako.client.mixin;

import com.usujiotarako.client.VirtualHotbarManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.event.autorefill.AutoRefillHandler", remap = false)
public abstract class IPNAutoRefillHandlerMixin {
	@Inject(method = "onTickInGame", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void betterHotbars$pauseEntireVirtualRefillTick(CallbackInfo ci) {
		if (VirtualHotbarManager.shouldPauseIpnAutoRefill()) ci.cancel();
	}

	@Inject(method = "handleAutoRefill", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void betterHotbars$leaveVirtualSelectionAlone(CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (VirtualHotbarManager.shouldPauseIpnAutoRefill()
				|| VirtualHotbarManager.hasSelectedVirtualAssignment(minecraft.player)) ci.cancel();
	}
}
