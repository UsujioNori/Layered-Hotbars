package com.usujiotarako.client.mixin;

import com.usujiotarako.client.BetterHotbarsClient;
import com.usujiotarako.client.VirtualHotbarManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void betterHotbars$switchProfileWithScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();


		if (window != minecraft.getWindow().handle()
				|| minecraft.player == null
				|| minecraft.player.getAbilities().instabuild
				|| minecraft.gui.screen() != null
				|| vertical == 0.0
				|| !BetterHotbarsClient.isProfileScrollModifierDown()) return;
		VirtualHotbarManager.switchProfile(minecraft, vertical > 0.0 ? -1 : 1);
		ci.cancel();
	}
}
