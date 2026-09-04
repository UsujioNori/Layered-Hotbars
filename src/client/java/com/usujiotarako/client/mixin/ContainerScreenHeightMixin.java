package com.usujiotarako.client.mixin;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.usujiotarako.BehaviorPolicy;

/** Extends the separately-rendered player-inventory section of chest screens. */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenHeightMixin {
	@ModifyArg(
			method = "extractBackground",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V",
					ordinal = 1
			),
			index = 7
	)
	private int betterHotbars$extendPlayerInventoryTextureHeight(int originalHeight) {
		return BehaviorPolicy.hasExtraRow() ? originalHeight + 18 : originalHeight;
	}
}
