package com.usujiotarako.client.mixin;

import com.usujiotarako.client.VirtualHotbarManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(
			method = "handleKeybinds",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"
			)
	)
	private void betterHotbars$rememberVirtualOffhandSwap(CallbackInfo ci) {
		Minecraft minecraft = (Minecraft)(Object)this;
		if (minecraft.player != null) VirtualHotbarManager.prepareOffhandSwap(minecraft.player);
	}
}
