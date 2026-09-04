package com.usujiotarako.client.mixin;

import com.usujiotarako.client.VirtualHotbarManager;
import com.usujiotarako.client.BetterHotbarsGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.usujiotarako.BehaviorPolicy;

@Mixin(Hud.class)
public abstract class HudMixin {

	@Inject(method = "extractItemHotbar", at = @At("TAIL"))
	private void betterHotbars$drawActiveProfileIndicator(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null
				|| minecraft.player.getAbilities().instabuild
				|| minecraft.gui.screen() instanceof AbstractContainerScreen<?>) return;
		int x = graphics.guiWidth() / 2 - 91 - 18;
		int y = graphics.guiHeight() - 32;
		BetterHotbarsGuiTextures.Texture texture = BetterHotbarsGuiTextures.hotbarActive();
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture.id(),
				x,
				y,
				0.0F,
				VirtualHotbarManager.getActiveProfile() * 32.0F,
				16,
				32,
				texture.width(),
				texture.height()
		);
	}

	@Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack betterHotbars$displayVirtualAssignment(Inventory inventory, int slot) {
		if (inventory.player.getAbilities().instabuild) return inventory.getItem(slot);
		return VirtualHotbarManager.getDisplayedStack(inventory, slot);
	}

	@Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack betterHotbars$displayVirtualOffhandAssignment(Player player) {
		if (player.getAbilities().instabuild) return player.getOffhandItem();
		return VirtualHotbarManager.getDisplayedOffhandStack(player.getInventory());
	}

	@ModifyArg(
			method = "extractItemHotbar",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
					ordinal = 2
			),
			index = 2
	)
	private int betterHotbars$moveLeftOffhandBackground(int x) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.getAbilities().instabuild) return x;
		return x - 13;
	}

	@ModifyArg(
			method = "extractItemHotbar",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Hud;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
					ordinal = 1
			),
			index = 1
	)
	private int betterHotbars$moveLeftOffhandItem(int x) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.getAbilities().instabuild) return x;
		return x - 13;
	}

	@Redirect(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
	private void betterHotbars$showZeroForLockedAssignment(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
		Inventory inventory = Minecraft.getInstance().player.getInventory();
		if (inventory.player.getAbilities().instabuild) {
			graphics.itemDecorations(font, stack, x, y);
			return;
		}
		if (VirtualHotbarManager.isUnavailableLockedStack(inventory, stack)) {
			graphics.fill(x, y, x + 16, y + 16, 0x88000000);
			graphics.itemDecorations(font, stack, x, y, "0");
		} else {
			graphics.itemDecorations(font, stack, x, y);
		}
	}
}
