package com.usujiotarako.client.mixin;

import com.usujiotarako.client.VirtualHotbarManager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional Mouse Tweaks compatibility.
 *
 * Mouse Tweaks' own RMB state machine is left completely intact. We only make
 * its getCarried() reads see the Layered Hotbars virtual cursor. The returned
 * stack is a read-only representative; it is never installed into the real
 * menu, so vanilla/server carried-stack synchronisation remains untouched.
 */
@Pseudo
@Mixin(targets = "yalter.mousetweaks.Main", remap = false)
public abstract class MouseTweaksMainMixin {
	@Redirect(
			method = {"onMouseClicked", "onMouseDrag"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getCarried()Lnet/minecraft/world/item/ItemStack;",
					remap = true
			),
			remap = false,
			require = 0
	)
	private static ItemStack betterHotbars$showVirtualCursorToMouseTweaks(AbstractContainerMenu menu) {
		return VirtualHotbarManager.getMouseTweaksCarriedStack(menu);
	}
}
