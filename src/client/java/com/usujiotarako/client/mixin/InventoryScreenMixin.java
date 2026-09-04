package com.usujiotarako.client.mixin;

import com.usujiotarako.client.access.SlotPositionAccess;
import com.usujiotarako.client.VirtualHotbarManager;
import com.usujiotarako.client.IPNCompat;
import com.usujiotarako.client.BetterHotbarsGuiTextures;
import com.usujiotarako.client.BetterHotbarsClient;
import com.usujiotarako.BehaviorPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	private static final int VIRTUAL_ROW_Y = 160;
	private static final int ROW_X = 8;

	@Inject(method = "init", at = @At("TAIL"))
	private void betterHotbars$moveFormerHotbarIntoInventory(CallbackInfo ci) {
		if (!BehaviorPolicy.hasExtraRow()) return;
		InventoryMenu menu = ((InventoryScreen)(Object)this).getMenu();
		for (int menuSlot = 36; menuSlot < 45; menuSlot++) {
			((SlotPositionAccess)menu.slots.get(menuSlot)).betterHotbars$setY(VIRTUAL_ROW_Y);
		}
	}

	@Inject(
			method = "extractBackground",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V",
					shift = At.Shift.BEFORE
			)
	)
	private void betterHotbars$drawTexturedExtendedInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!BehaviorPolicy.hasExtraRow()) return;
		AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor)this;
		BetterHotbarsGuiTextures.Texture texture = BetterHotbarsGuiTextures.inventory();
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture.id(),
				screen.betterHotbars$getLeftPos(),
				screen.betterHotbars$getTopPos(),
				0.0F,
				0.0F,
				176,
				184,
				texture.width(),
				texture.height()
		);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void betterHotbars$drawExtendedInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor)this;
		int left = screen.betterHotbars$getLeftPos();
		int top = screen.betterHotbars$getTopPos();
		int activeProfile = VirtualHotbarManager.getActiveProfile();
		BetterHotbarsGuiTextures.Texture activeTexture = BetterHotbarsGuiTextures.hotbarActive();
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				activeTexture.id(),
				left - 18,
				top + (BehaviorPolicy.hasExtraRow() ? 152 : 134),
				0.0F,
				activeProfile * 32.0F,
				16,
				32,
				activeTexture.width(),
				activeTexture.height()
		);
		BetterHotbarsGuiTextures.Texture sortTexture = BetterHotbarsGuiTextures.sortButton();
		if (!IPNCompat.isLoaded()) graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				sortTexture.id(),
				left + 153,
				top + 67,
				0.0F,
				VirtualHotbarManager.isSortButtonPressed() ? 16.0F : 0.0F,
				16,
				16,
				sortTexture.width(),
				sortTexture.height()
		);

		if (BehaviorPolicy.isVirtual()) for (int slot = 0; slot < 9; slot++) {
			boolean selected = Minecraft.getInstance().player.getInventory().getSelectedSlot() == slot;
			int x = left + ROW_X + slot * 18;
			int y = top + (BehaviorPolicy.hasExtraRow() ? VIRTUAL_ROW_Y : 142);
			betterHotbars$drawIndicator(graphics, x, y, selected, VirtualHotbarManager.isLocked(slot));
		}

		if (BehaviorPolicy.keepsPhysicalProfiles() && BetterHotbarsClient.isProfilePreviewDown() && Minecraft.getInstance().player != null) {
			betterHotbars$drawPhysicalProfilePreview(graphics, mouseX, mouseY, left, top);
		}
	}

	private static void betterHotbars$drawPhysicalProfilePreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int left, int top) {
		// Keep the vanilla inventory unobstructed. Very narrow GUI scales retain the
		// normal profile indicator and omit this optional side preview.
		int panelX = left + 184;
		if (panelX + 174 > graphics.guiWidth()) return;
		int panelY = top + 61;
		Minecraft minecraft = Minecraft.getInstance();
		int active = VirtualHotbarManager.getActiveProfile();
		for (int profile = 0; profile < 3; profile++) {
			int rowY = panelY + profile * 22;
			int border = profile == active ? 0xFFFFD75E : 0xFF777777;
			graphics.fill(panelX, rowY, panelX + 174, rowY + 20, 0xCC151515);
			graphics.outline(panelX, rowY, 174, 20, border);
			graphics.centeredText(minecraft.font, Integer.toString(profile + 1), panelX + 6, rowY + 6, border);
			for (int slot = 0; slot < 9; slot++) {
				int x = panelX + 11 + slot * 18;
				graphics.outline(x, rowY + 1, 18, 18, 0xFF555555);
				ItemStack stack = VirtualHotbarManager.getPhysicalProfilePreviewStack(
						minecraft.player.getInventory(), profile, slot);
				if (!stack.isEmpty()) {
					graphics.item(stack, x + 1, rowY + 2);
					graphics.itemDecorations(minecraft.font, stack, x + 1, rowY + 2);
					if (mouseX >= x && mouseX < x + 18 && mouseY >= rowY + 1 && mouseY < rowY + 19) {
						graphics.setTooltipForNextFrame(minecraft.font, stack, mouseX, mouseY);
					}
				}
			}
		}
	}

	private static void betterHotbars$drawIndicator(GuiGraphicsExtractor graphics, int x, int y, boolean selected, boolean locked) {
		if (!selected && !locked) return;
		int color = locked ? 0xFF48BDE8 : 0xFFFFD75E;
		graphics.fill(x - 1, y - 1, x + 17, y, color);
		graphics.fill(x - 1, y + 16, x + 17, y + 17, color);
		graphics.fill(x - 1, y, x, y + 16, color);
		graphics.fill(x + 16, y, x + 17, y + 16, color);
	}

}
