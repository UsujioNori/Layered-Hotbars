package com.usujiotarako.client.mixin;

import com.usujiotarako.inventory.ExtraStorageSlot;
import com.usujiotarako.access.ExtraStorageAccess;
import com.usujiotarako.client.VirtualHotbarManager;
import com.usujiotarako.client.IPNCompat;
import com.usujiotarako.client.BetterHotbarsGuiTextures;
import com.usujiotarako.PickupPolicy;
import com.usujiotarako.BehaviorPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenRenderMixin<T extends AbstractContainerMenu> {
	@Shadow @Final protected T menu;
	@Shadow protected int imageHeight;
	@Shadow protected int imageWidth;
	@Shadow protected int leftPos;
	@Shadow protected int topPos;
	@Unique private boolean betterHotbars$hasExtraRow;

	@Inject(method = "init", at = @At("HEAD"))
	private void betterHotbars$makeRoomForExtraRow(CallbackInfo ci) {
		if ((Object)this instanceof InventoryScreen) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		ExtraStorageInventory extraStorage = ((ExtraStorageAccess)minecraft.player).betterHotbars$getExtraStorage();
		if (!betterHotbars$hasExtraRow && this.menu.slots.stream().anyMatch(slot -> slot instanceof ExtraStorageSlot)) {
			betterHotbars$hasExtraRow = true;
			this.imageHeight += 18;
			int extraY = this.menu.slots.stream().filter(slot -> slot instanceof ExtraStorageSlot).mapToInt(slot -> slot.y).min().orElse(Integer.MIN_VALUE);
			for (Slot slot : this.menu.slots) {
				if (slot.container == minecraft.player.getInventory()
						&& !(slot instanceof ExtraStorageSlot)
						&& slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 9
						&& slot.y < extraY + 22) {
					((com.usujiotarako.client.access.SlotPositionAccess)slot).betterHotbars$setY(extraY + 22);
				}
			}
		}
	}

	@Inject(method = "extractContents", at = @At("HEAD"))
	private void betterHotbars$drawExtraInventoryRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!betterHotbars$hasExtraRow) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;

		// Traveler's Backpack renders its background with its own dynamically sliced
		// texture routine. TravelersBackpackBackgroundMixin replaces that routine
		// with the 4-row-aware version, so no generic inventory strip belongs here.
		if (betterHotbars$isTravelersBackpackScreen()) return;

		ExtraStorageInventory extraStorage = ((ExtraStorageAccess)minecraft.player).betterHotbars$getExtraStorage();
		Slot firstExtraSlot = this.menu.slots.stream().filter(slot -> slot instanceof ExtraStorageSlot).findFirst().orElse(null);
		if (firstExtraSlot == null) return;
		java.util.Optional<BetterHotbarsGuiTextures.Texture> dedicated = BetterHotbarsGuiTextures.containerForScreen(this);

		// Vanilla/container screens with a dedicated Layered Hotbars texture already
		// draw the complete expanded GUI through ContainerBackgroundTextureMixin.
		// Do not paint the inventory strip a second time over that background.
		if (dedicated.isPresent()) return;

		// Fallback for unsupported/modded container screens that do not have a
		// dedicated full-screen texture. These still need a small backing panel
		// behind Layered Hotbars' added storage row.
		BetterHotbarsGuiTextures.Texture texture = BetterHotbarsGuiTextures.inventory();
		float sourceY = 134.0F;
		int panelX = firstExtraSlot.x - 8;
		int panelYAdjustment = 0;
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture.id(),
				this.leftPos + panelX,
				this.topPos + firstExtraSlot.y - 4 + panelYAdjustment,
				0.0F,
				sourceY,
				176,
				50,
				texture.width(),
				texture.height()
		);
	}

	@Inject(method = "extractCarriedItem", at = @At("HEAD"))
	private void betterHotbars$drawVirtualCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (!BehaviorPolicy.isVirtual()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.player.getAbilities().instabuild || !this.menu.getCarried().isEmpty()) return;
		ItemStack cursor = VirtualHotbarManager.getVirtualCursorDisplayStack(minecraft.player.getInventory());
		if (cursor.isEmpty()) return;
		int total = VirtualHotbarManager.getVirtualCursorCount(minecraft.player.getInventory());
		graphics.nextStratum();
		graphics.item(cursor, mouseX - 8, mouseY - 8);
		graphics.itemDecorations(minecraft.font, cursor, mouseX - 8, mouseY - 8, Integer.toString(total));
	}
	@Redirect(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack betterHotbars$renderHybridHotbarStack(Slot slot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || slot.container != minecraft.player.getInventory() || slot instanceof ExtraStorageSlot) return slot.getItem();
		if (minecraft.player.getAbilities().instabuild) return slot.getItem();
		int inventorySlot = slot.getContainerSlot();
		if (inventorySlot >= 0 && inventorySlot < 9) {
			return VirtualHotbarManager.getDisplayedStack(minecraft.player.getInventory(), inventorySlot);
		}
		if (inventorySlot == 40) return VirtualHotbarManager.getDisplayedOffhandStack(minecraft.player.getInventory());
		return slot.getItem();
	}

	@Redirect(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
	private void betterHotbars$showZeroForLockedSlot(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, @Nullable String countText) {
		if ((Object)this instanceof InventoryScreen && VirtualHotbarManager.isUnavailableLockedStack(Minecraft.getInstance().player.getInventory(), stack)) {
			graphics.fill(x, y, x + 16, y + 16, 0x88000000);
			graphics.itemDecorations(font, stack, x, y, "0");
		} else {
			graphics.itemDecorations(font, stack, x, y, countText);
		}
	}
	@Inject(method = "extractContents", at = @At("TAIL"))
	private void betterHotbars$drawPickupModeButtonOnInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!((Object)this instanceof InventoryScreen)) return;

		BetterHotbarsGuiTextures.Texture texture = BetterHotbarsGuiTextures.pickupMode();
		boolean aggressive = PickupPolicy.isAggressive();
		boolean pressed = VirtualHotbarManager.isPickupModeButtonPressed();
		float v = aggressive
				? (pressed ? 16.0F : 0.0F)
				: (pressed ? 48.0F : 32.0F);

		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture.id(),
				this.leftPos - 18,
				this.topPos + (BehaviorPolicy.hasExtraRow() ? 132 : 114),
				0.0F,
				v,
				16,
				16,
				texture.width(),
				texture.height()
		);
	}

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void betterHotbars$drawSortButtonOnContainers(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (IPNCompat.isLoaded() || (Object)this instanceof InventoryScreen || (Object)this instanceof CreativeModeInventoryScreen) return;
		BetterHotbarsGuiTextures.Texture texture = BetterHotbarsGuiTextures.sortButton();
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture.id(),
				betterHotbars$getSortButtonX(),
				betterHotbars$getSortButtonY(),
				0.0F,
				VirtualHotbarManager.isSortButtonPressed() ? 16.0F : 0.0F,
				16,
				16,
				texture.width(),
				texture.height()
		);
	}

	@Unique
	private int betterHotbars$getSortButtonX() {
		return this.leftPos + this.imageWidth - 23;
	}

	@Unique
	private int betterHotbars$getSortButtonY() {
		if (betterHotbars$isTravelersBackpackScreen()) return this.topPos - 18;
		return this.topPos + 2;
	}

	@Unique
	private boolean betterHotbars$isTravelersBackpackScreen() {
		return this.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("travelersbackpack");
	}

}
