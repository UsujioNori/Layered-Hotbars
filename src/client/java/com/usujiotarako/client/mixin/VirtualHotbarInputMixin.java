package com.usujiotarako.client.mixin;

import com.usujiotarako.inventory.ExtraStorageSlot;
import com.usujiotarako.PickupPolicy;
import com.usujiotarako.BehaviorPolicy;
import com.usujiotarako.client.VirtualHotbarManager;
import com.usujiotarako.client.InventorySorter;
import com.usujiotarako.client.MouseTweaksCompat;
import com.usujiotarako.client.IPNCompat;
import com.usujiotarako.access.ExtraStorageAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class VirtualHotbarInputMixin<T extends AbstractContainerMenu> {
	@Shadow @Final protected T menu;
	@Shadow protected @Nullable Slot hoveredSlot;
	@Shadow protected int leftPos;
	@Shadow protected int topPos;
	@Shadow protected int imageWidth;
	@Shadow private boolean skipNextRelease;
	@Unique private final java.util.LinkedHashSet<Integer> betterHotbars$virtualDragSlots = new java.util.LinkedHashSet<>();
	@Unique private int betterHotbars$virtualDragButton = -1;
	@Unique private int betterHotbars$lastVirtualDragSlot = -1;
	@Unique private boolean betterHotbars$virtualLeftDragMoved = false;
	@Unique private boolean betterHotbars$virtualRightDragMoved = false;
	@Unique private final java.util.LinkedHashMap<Integer, Integer> betterHotbars$virtualDragBaselines = new java.util.LinkedHashMap<>();

	@Inject(method = "hasClickedOutside", at = @At("RETURN"), cancellable = true)
	private void betterHotbars$includeExtendedRows(double mouseX, double mouseY, int left, int top, CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof InventoryScreen
				&& ((BehaviorPolicy.hasExtraRow() && mouseX >= left + 7 && mouseX < left + 172
				&& mouseY >= top + 136 && mouseY < top + 181)
				|| (mouseX >= left - 18 && mouseX < left - 2
				&& mouseY >= top + (BehaviorPolicy.hasExtraRow() ? 132 : 114)
				&& mouseY < top + (BehaviorPolicy.hasExtraRow() ? 184 : 166)))) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void betterHotbars$assignHoveredItemWithNumber(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object)this instanceof InventoryScreen)) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (event.hasAltDown()) {
			for (int profile = 0; profile < 3; profile++) {
				if (minecraft.options.keyHotbarSlots[profile].matches(event)) {
					VirtualHotbarManager.switchToProfile(minecraft, profile);
					cir.setReturnValue(true);
					return;
				}
			}
		}
		if (!BehaviorPolicy.isVirtual()) return;
		if (this.hoveredSlot != null && this.hoveredSlot.index == 45) {
			for (int virtualSlot = 0; virtualSlot < 9; virtualSlot++) {
				if (minecraft.options.keyHotbarSlots[virtualSlot].matches(event)) {
					VirtualHotbarManager.prepareOffhandSwap(minecraft.player, virtualSlot);
					minecraft.gameMode.handleContainerInput(this.menu.containerId, 45, virtualSlot, ContainerInput.SWAP, minecraft.player);
					cir.setReturnValue(true);
					return;
				}
			}
		}
		if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;
		for (int virtualSlot = 0; virtualSlot < 9; virtualSlot++) {
			if (minecraft.options.keyHotbarSlots[virtualSlot].matches(event)) {
				VirtualHotbarManager.assign(virtualSlot, this.hoveredSlot.getItem());
				minecraft.gameMode.handleContainerInput(this.menu.containerId, this.hoveredSlot.index, virtualSlot, ContainerInput.SWAP, minecraft.player);
				minecraft.player.sendOverlayMessage(Component.translatable("message.better-hotbars.assigned_slot", this.hoveredSlot.getItem().getHoverName(), virtualSlot + 1));
				cir.setReturnValue(true);
				return;
			}
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void betterHotbars$handleVirtualSlotClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.getAbilities().instabuild) return;
		if (!BehaviorPolicy.isVirtual()) {
			betterHotbars$handlePhysicalModeButtons(event, cir, minecraft);
			return;
		}
		if (minecraft.player != null && VirtualHotbarManager.hasVirtualCursor()) {
			betterHotbars$virtualDragSlots.clear();
			betterHotbars$virtualDragButton = -1;
			betterHotbars$lastVirtualDragSlot = -1;
			betterHotbars$virtualLeftDragMoved = false;
			betterHotbars$virtualRightDragMoved = false;
			betterHotbars$virtualDragBaselines.clear();
			if (this.hoveredSlot != null && this.hoveredSlot.container == minecraft.player.getInventory()
					&& !(this.hoveredSlot instanceof ExtraStorageSlot)
					&& this.hoveredSlot.getContainerSlot() >= 0 && this.hoveredSlot.getContainerSlot() < 9
					&& VirtualHotbarManager.moveVirtualCursorToHotbar(this.hoveredSlot.getContainerSlot())) {
				if (!((Object)this instanceof InventoryScreen)) this.skipNextRelease = true;
				cir.setReturnValue(true);
				return;
			}
			if (this.hoveredSlot != null && (event.button() == 0 || event.button() == 1)
					&& VirtualHotbarManager.canUseVirtualCursorTarget(minecraft.player, this.hoveredSlot)) {
				betterHotbars$virtualDragButton = event.button();
				betterHotbars$virtualDragSlots.add(this.hoveredSlot.index);
				betterHotbars$virtualDragBaselines.put(this.hoveredSlot.index, this.hoveredSlot.getItem().isEmpty() ? 0 : this.hoveredSlot.getItem().getCount());
				betterHotbars$lastVirtualDragSlot = this.hoveredSlot.index;
				if (event.button() == 1 && !MouseTweaksCompat.shouldDelegateRmb()) {
					VirtualHotbarManager.placeOneFromVirtualCursor(minecraft, this.menu, this.hoveredSlot, 1, betterHotbars$virtualDragSlots);
				}
				// Left click only arms the virtual drag. Do not place or redistribute anything
				// until the cursor actually enters another slot. This makes a plain click on
				// the inventory a no-op while preserving Mouse Tweaks-style left dragging.
				if (!((Object)this instanceof InventoryScreen)) this.skipNextRelease = true;
				cir.setReturnValue(true);
				return;
			}
			if (event.button() == 0 || event.button() == 1) {
				VirtualHotbarManager.clearVirtualCursor();
				if (!((Object)this instanceof InventoryScreen)) this.skipNextRelease = true;
				cir.setReturnValue(true);
				return;
			}
		}
		if (minecraft.player != null && !event.hasShiftDown() && this.hoveredSlot != null
				&& this.hoveredSlot.container == minecraft.player.getInventory()
				&& !(this.hoveredSlot instanceof ExtraStorageSlot)
				&& this.hoveredSlot.getContainerSlot() >= 0 && this.hoveredSlot.getContainerSlot() < 9
				&& VirtualHotbarManager.moveCarriedStackToVirtualHotbar(
						minecraft, this.menu, this.hoveredSlot.getContainerSlot(), event.button())) {
			if (!((Object)this instanceof InventoryScreen)) this.skipNextRelease = true;
			cir.setReturnValue(true);
			return;
		}
		if (minecraft.player != null && this.menu.getCarried().isEmpty()
				&& !event.hasShiftDown() && !event.hasAltDown() && this.hoveredSlot != null
				&& this.hoveredSlot.container == minecraft.player.getInventory()
				&& !(this.hoveredSlot instanceof ExtraStorageSlot)
				&& this.hoveredSlot.getContainerSlot() >= 0 && this.hoveredSlot.getContainerSlot() < 9
				&& VirtualHotbarManager.beginVirtualCursor(
						minecraft.player.getInventory(), this.hoveredSlot.getContainerSlot(), event.button())) {
			betterHotbars$armVirtualDragFromHotbar(event.button());
			if (!((Object)this instanceof InventoryScreen)) this.skipNextRelease = true;
			cir.setReturnValue(true);
			return;
		}
		if (!IPNCompat.isLoaded() && !((Object)this instanceof InventoryScreen) && !((Object)this instanceof CreativeModeInventoryScreen)) {
			int sortButtonX = betterHotbars$getContainerSortButtonX();
			int sortButtonY = betterHotbars$getContainerSortButtonY();
			if (event.button() == 0
					&& event.x() >= sortButtonX && event.x() < sortButtonX + 16
					&& event.y() >= sortButtonY && event.y() < sortButtonY + 16
					&& InventorySorter.sort(minecraft, this.menu)) {
				VirtualHotbarManager.pressSortButton();
				cir.setReturnValue(true);
			}
			return;
		}
		if ((Object)this instanceof CreativeModeInventoryScreen) return;
		if ((Object)this instanceof InventoryScreen && event.button() == 0
				&& betterHotbars$isPickupModeButton(event.x(), event.y())) {
			PickupPolicy.setMode(PickupPolicy.isAggressive() ? PickupPolicy.Mode.CASUAL : PickupPolicy.Mode.AGGRESSIVE);
			VirtualHotbarManager.pressPickupModeButton();
			cir.setReturnValue(true);
			return;
		}
		if (!IPNCompat.isLoaded() && event.button() == 0
				&& event.x() >= this.leftPos + 153 && event.x() < this.leftPos + 169
				&& event.y() >= this.topPos + 67 && event.y() < this.topPos + 83
				&& InventorySorter.sort(minecraft, this.menu)) {
			VirtualHotbarManager.pressSortButton();
			cir.setReturnValue(true);
			return;
		}
		int profile = this.betterHotbars$getProfileButton(event.x(), event.y());
		if (profile >= 0 && event.button() == 0) {
			VirtualHotbarManager.switchToProfile(Minecraft.getInstance(), profile);
			cir.setReturnValue(true);
			return;
		}
		if (this.hoveredSlot != null && this.hoveredSlot.index == 45 && event.button() >= 0 && event.button() <= 1) {
			ItemStack carried = this.menu.getCarried();
			ItemStack offhand = this.hoveredSlot.getItem();
			if (!carried.isEmpty()) {
				VirtualHotbarManager.setOffhandAssignment(carried);
			} else if (event.hasShiftDown() || event.button() == 0 || offhand.getCount() <= 1) {
				VirtualHotbarManager.setOffhandAssignment(ItemStack.EMPTY);
			}
		}
		int hotbarSlot = this.betterHotbars$getExtendedHotbarSlot(event.x(), event.y());
		if (hotbarSlot < 0 || event.button() < 0 || event.button() > 1) return;
		if (event.hasShiftDown() && event.button() == 0 && betterHotbars$quickMoveVirtualHotbarSlot(this.hoveredSlot)) {
			cir.setReturnValue(true);
			return;
		}
		ItemStack physicalStack = minecraft.player.getInventory().getItem(hotbarSlot);
		if (!event.hasShiftDown() && !event.hasAltDown() && physicalStack.isEmpty() && this.menu.getCarried().isEmpty()
				&& VirtualHotbarManager.beginVirtualCursor(minecraft.player.getInventory(), hotbarSlot, event.button())) {
			betterHotbars$armVirtualDragFromHotbar(event.button());
			cir.setReturnValue(true);
			return;
		}
		if (event.hasAltDown()) {
			boolean locked = VirtualHotbarManager.toggleLocked(hotbarSlot, physicalStack);
			minecraft.player.sendOverlayMessage(Component.translatable(locked ? "message.better-hotbars.locked" : "message.better-hotbars.unlocked", hotbarSlot + 1));
			cir.setReturnValue(true);
			return;
		}
		if (!VirtualHotbarManager.isLocked(hotbarSlot) && !physicalStack.isEmpty()) VirtualHotbarManager.clear(hotbarSlot);
		minecraft.gameMode.handleContainerInput(
				this.menu.containerId,
				36 + hotbarSlot,
				event.button(),
				event.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP,
				minecraft.player
		);
		cir.setReturnValue(true);
	}

	@Unique
	private void betterHotbars$armVirtualDragFromHotbar(int mouseButton) {
		betterHotbars$virtualDragSlots.clear();
		betterHotbars$virtualDragBaselines.clear();
		betterHotbars$virtualDragButton = (mouseButton == 0 || mouseButton == 1) ? mouseButton : -1;
		betterHotbars$lastVirtualDragSlot = -1;
		betterHotbars$virtualLeftDragMoved = false;
		betterHotbars$virtualRightDragMoved = false;
	}

	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void betterHotbars$handleVirtualQuickMove(Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
		if (!BehaviorPolicy.isVirtual()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.getAbilities().instabuild) return;
		// Mouse Tweaks owns the RMB traversal/state machine. When it decides a slot
		// should receive an item it ultimately calls this normal screen slot-click
		// path. Translate that click into a Layered Hotbars virtual transaction instead
		// of letting vanilla act on the intentionally-empty real carried stack.
		if (containerInput == ContainerInput.PICKUP && buttonNum == 1
				&& minecraft.player != null && VirtualHotbarManager.hasVirtualCursor()
				&& MouseTweaksCompat.shouldDelegateRmb() && slot != null) {
			VirtualHotbarManager.placeOneFromVirtualCursor(minecraft, this.menu, slot, 1, betterHotbars$virtualDragSlots);
			ci.cancel();
			return;
		}
		if (containerInput != ContainerInput.QUICK_MOVE) return;
		if (betterHotbars$quickMoveVirtualHotbarSlot(slot)) {
			ci.cancel();
			return;
		}
		if (!((Object)this instanceof InventoryScreen) || slot == null || minecraft.player == null) return;
		ExtraStorageInventory extra = ((ExtraStorageAccess)minecraft.player).betterHotbars$getExtraStorage();
		boolean mainStorage = slot.container == minecraft.player.getInventory()
				&& slot.getContainerSlot() >= 9
				&& slot.getContainerSlot() < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE;
		if ((mainStorage || slot instanceof ExtraStorageSlot)
				&& VirtualHotbarManager.assignToFirstAvailableVirtualHotbarSlot(slot.getItem())) {
			ci.cancel();
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void betterHotbars$handleMouseTweaksVirtualShiftDrag(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
		if (!BehaviorPolicy.isVirtual()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.getAbilities().instabuild) return;
		if (minecraft.player != null && VirtualHotbarManager.hasVirtualCursor()
				&& betterHotbars$virtualDragButton == event.button()
				&& (event.button() == 0 || event.button() == 1)) {
			int currentSlotIndex = this.hoveredSlot == null ? -1 : this.hoveredSlot.index;
			if (currentSlotIndex != betterHotbars$lastVirtualDragSlot) {
				if (event.button() == 1 && MouseTweaksCompat.shouldDelegateRmb()) {
					// Mouse Tweaks has already processed this movement in MouseHandler before
					// AbstractContainerScreen.mouseDragged is invoked. We only record that a
					// drag happened so a simple RMB click can still be handled on release.
					betterHotbars$virtualRightDragMoved = true;
					betterHotbars$lastVirtualDragSlot = currentSlotIndex;
					if (this.hoveredSlot != null && VirtualHotbarManager.canUseVirtualCursorTarget(minecraft.player, this.hoveredSlot)) {
						// Mouse Tweaks has already clicked this destination. Remember it only
						// as a protected destination so later virtual placements cannot pull
						// their backing item back out of an earlier player-inventory slot.
						betterHotbars$virtualDragSlots.add(this.hoveredSlot.index);
					}
					cir.setReturnValue(true);
					return;
				}
				betterHotbars$lastVirtualDragSlot = currentSlotIndex;
				if (this.hoveredSlot != null && VirtualHotbarManager.canUseVirtualCursorTarget(minecraft.player, this.hoveredSlot)) {
					boolean newlyAdded = betterHotbars$virtualDragSlots.add(this.hoveredSlot.index);
					if (newlyAdded) {
						betterHotbars$virtualDragBaselines.put(this.hoveredSlot.index, this.hoveredSlot.getItem().isEmpty() ? 0 : this.hoveredSlot.getItem().getCount());
					}
					if (event.button() == 1) {
					// Match Mouse Tweaks: only the immediately previous slot is suppressed.
					// All destinations remain excluded as backing sources, so A -> B -> A
					// adds another item to A instead of stealing the one placed there earlier.
					VirtualHotbarManager.placeOneFromVirtualCursor(minecraft, this.menu, this.hoveredSlot, 1, betterHotbars$virtualDragSlots);
					} else if (newlyAdded) {
						betterHotbars$virtualLeftDragMoved = true;
						// Vanilla-style left drag should visibly rebalance while slots are added,
						// rather than waiting until release.
						VirtualHotbarManager.redistributeVirtualCursor(minecraft, this.menu, betterHotbars$virtualDragSlots, betterHotbars$virtualDragBaselines);
					}
				}
			}
			cir.setReturnValue(true);
			return;
		}
		if (event.button() == 0 && event.hasShiftDown() && betterHotbars$quickMoveVirtualHotbarSlot(this.hoveredSlot)) {
			cir.setReturnValue(true);
		}
	}

	private boolean betterHotbars$quickMoveVirtualHotbarSlot(@Nullable Slot slot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (slot == null || minecraft.player == null
				|| slot.container != minecraft.player.getInventory()
				|| slot instanceof ExtraStorageSlot) return false;
		int hotbarSlot = slot.getContainerSlot();
		if (hotbarSlot < 0 || hotbarSlot >= 9 || VirtualHotbarManager.isLocked(hotbarSlot)) return false;
		if ((Object)this instanceof InventoryScreen) {
			VirtualHotbarManager.clearForInventoryTransfer(hotbarSlot);
			return slot.getItem().isEmpty();
		}
		return VirtualHotbarManager.transferVirtualHotbarStackToOpenContainer(minecraft, this.menu, hotbarSlot);
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void betterHotbars$preventCountTextDrop(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!BehaviorPolicy.isVirtual()) {
			if ((Object)this instanceof InventoryScreen && (this.betterHotbars$getProfileButton(event.x(), event.y()) >= 0 || this.betterHotbars$isPickupModeButton(event.x(), event.y()))) cir.setReturnValue(true);
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.getAbilities().instabuild) return;
		if (minecraft.player != null && VirtualHotbarManager.hasVirtualCursor()
				&& betterHotbars$virtualDragButton == event.button()) {
			// Releasing LMB after picking up a virtual stack must NOT cancel the virtual
			// cursor. Just like a normal vanilla carried stack, a click-and-release leaves
			// the stack attached to the cursor so the next click can move it to another
			// hotbar slot or begin a new left-drag distribution. The drag bookkeeping is
			// still reset below; only the carried virtual stack persists.
			if (event.button() == 1 && MouseTweaksCompat.shouldDelegateRmb()
					&& !betterHotbars$virtualRightDragMoved && this.hoveredSlot != null) {
				// Mouse Tweaks only takes over once RMB becomes a drag. Preserve a normal
				// single right click by placing one here when no drag ever occurred.
				VirtualHotbarManager.placeOneFromVirtualCursor(minecraft, this.menu, this.hoveredSlot, 1, betterHotbars$virtualDragSlots);
			}
			betterHotbars$virtualDragSlots.clear();
			betterHotbars$virtualDragButton = -1;
			betterHotbars$lastVirtualDragSlot = -1;
			betterHotbars$virtualLeftDragMoved = false;
			betterHotbars$virtualRightDragMoved = false;
			betterHotbars$virtualDragBaselines.clear();
			cir.setReturnValue(true);
			return;
		}
		betterHotbars$virtualDragSlots.clear();
		betterHotbars$virtualDragButton = -1;
		betterHotbars$lastVirtualDragSlot = -1;
		betterHotbars$virtualLeftDragMoved = false;
		betterHotbars$virtualRightDragMoved = false;
		betterHotbars$virtualDragBaselines.clear();
		if ((Object)this instanceof InventoryScreen
				&& (this.betterHotbars$getExtendedHotbarSlot(event.x(), event.y()) >= 0
				|| this.betterHotbars$getProfileButton(event.x(), event.y()) >= 0
				|| this.betterHotbars$isPickupModeButton(event.x(), event.y()))) {
			cir.setReturnValue(true);
		}
	}

	@Unique
	private boolean betterHotbars$isPickupModeButton(double mouseX, double mouseY) {
		return mouseX >= this.leftPos - 18 && mouseX < this.leftPos - 2
				&& mouseY >= this.topPos + (BehaviorPolicy.hasExtraRow() ? 132 : 114)
				&& mouseY < this.topPos + (BehaviorPolicy.hasExtraRow() ? 148 : 130);
	}

	private int betterHotbars$getProfileButton(double mouseX, double mouseY) {
		double relativeX = mouseX - (this.leftPos - 18);
		double relativeY = mouseY - (this.topPos + (BehaviorPolicy.hasExtraRow() ? 152 : 134));
		if (relativeX < 1 || relativeX >= 15 || relativeY < 2 || relativeY >= 30) return -1;
		if (relativeY < 11) return 0;
		if (relativeY < 20) return 1;
		return 2;
	}

	@Unique
	private void betterHotbars$handlePhysicalModeButtons(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir, Minecraft minecraft) {
		if (!((Object)this instanceof InventoryScreen) || event.button() != 0) return;
		if (betterHotbars$isPickupModeButton(event.x(), event.y())) {
			PickupPolicy.setMode(PickupPolicy.isAggressive() ? PickupPolicy.Mode.CASUAL : PickupPolicy.Mode.AGGRESSIVE);
			VirtualHotbarManager.pressPickupModeButton();
			cir.setReturnValue(true);
			return;
		}
		if (!IPNCompat.isLoaded()
				&& event.x() >= this.leftPos + 153 && event.x() < this.leftPos + 169
				&& event.y() >= this.topPos + 67 && event.y() < this.topPos + 83
				&& InventorySorter.sort(minecraft, this.menu)) {
			VirtualHotbarManager.pressSortButton();
			cir.setReturnValue(true);
			return;
		}
		int profile = betterHotbars$getProfileButton(event.x(), event.y());
		if (profile >= 0) {
			VirtualHotbarManager.switchToProfile(minecraft, profile);
			cir.setReturnValue(true);
		}
	}

	@Unique
	private int betterHotbars$getContainerSortButtonX() {
		return this.leftPos + this.imageWidth - 23;
	}

	@Unique
	private int betterHotbars$getContainerSortButtonY() {
		if (this.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("travelersbackpack")) {
			return this.topPos - 18;
		}
		return this.topPos + 2;
	}

	private int betterHotbars$getExtendedHotbarSlot(double mouseX, double mouseY) {
		double relativeX = mouseX - this.leftPos - 7;
		double relativeY = mouseY - this.topPos - 159;
		if (relativeX < 0 || relativeX >= 165 || relativeY < 0 || relativeY >= 22) return -1;
		int slot = (int)(relativeX / 18);
		return slot >= 0 && slot < 9 ? slot : 8;
	}
}
