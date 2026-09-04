package com.usujiotarako.client.mixin;

import com.usujiotarako.client.VirtualHotbarManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void betterHotbars$switchDirectlyWithAltNumber(long window, int action, KeyEvent event, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (window != minecraft.getWindow().handle()
				|| action != GLFW.GLFW_PRESS
				|| minecraft.player == null
				|| minecraft.player.getAbilities().instabuild
				|| minecraft.gui.screen() != null
				|| !event.hasAltDown()) return;

		int profile = switch (event.key()) {
			case GLFW.GLFW_KEY_1 -> 0;
			case GLFW.GLFW_KEY_2 -> 1;
			case GLFW.GLFW_KEY_3 -> 2;
			default -> -1;
		};
		if (profile < 0) return;
		VirtualHotbarManager.switchToProfile(minecraft, profile);
		ci.cancel();
	}
}
