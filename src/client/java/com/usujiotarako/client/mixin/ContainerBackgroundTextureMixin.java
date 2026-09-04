package com.usujiotarako.client.mixin;

import com.usujiotarako.client.BetterHotbarsGuiTextures;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.usujiotarako.BehaviorPolicy;

@Mixin(targets = {
		"net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen",
		"net.minecraft.client.gui.screens.inventory.AnvilScreen",
		"net.minecraft.client.gui.screens.inventory.BeaconScreen",
		"net.minecraft.client.gui.screens.inventory.BrewingStandScreen",
		"net.minecraft.client.gui.screens.inventory.CartographyTableScreen",
		"net.minecraft.client.gui.screens.inventory.CrafterScreen",
		"net.minecraft.client.gui.screens.inventory.CraftingScreen",
		"net.minecraft.client.gui.screens.inventory.DispenserScreen",
		"net.minecraft.client.gui.screens.inventory.EnchantmentScreen",
		"net.minecraft.client.gui.screens.inventory.ContainerScreen",
		"net.minecraft.client.gui.screens.inventory.GrindstoneScreen",
		"net.minecraft.client.gui.screens.inventory.HopperScreen",
		"net.minecraft.client.gui.screens.inventory.HorseInventoryScreen",
		"net.minecraft.client.gui.screens.inventory.LoomScreen",
		"net.minecraft.client.gui.screens.inventory.MerchantScreen",
		"net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen",
		"net.minecraft.client.gui.screens.inventory.SmithingScreen",
		"net.minecraft.client.gui.screens.inventory.StonecutterScreen"
})
public abstract class ContainerBackgroundTextureMixin {
	@ModifyArg(
			method = "extractBackground",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
			),
			index = 1,
			require = 0
	)
	private Identifier betterHotbars$useExtendedContainerTexture(Identifier original) {
		return BehaviorPolicy.hasExtraRow() ? BetterHotbarsGuiTextures.replaceVanillaContainerTexture(original) : original;
	}
}
