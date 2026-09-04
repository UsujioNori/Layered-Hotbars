package com.usujiotarako.client;

import com.usujiotarako.PickupPolicy;
import com.usujiotarako.BehaviorPolicy;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import com.usujiotarako.access.ExtraStorageAccess;
import com.usujiotarako.inventory.ExtraStorageInventory;

public final class BetterHotbarsConfigScreen extends Screen {
	private final Screen parent;
	private PickupPolicy.Mode selectedMode;
	private BehaviorPolicy.Mode selectedBehaviorMode;

	public BetterHotbarsConfigScreen(Screen parent) {
		super(Component.translatable("config.better-hotbars.title"));
		this.parent = parent;
		this.selectedMode = PickupPolicy.getMode();
		this.selectedBehaviorMode = BehaviorPolicy.getConfiguredMode();
	}

	@Override
	protected void init() {
		super.init();
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		this.addRenderableWidget(
				CycleButton.builder(BetterHotbarsConfigScreen::modeName, this.selectedMode)
						.withValues(List.of(PickupPolicy.Mode.AGGRESSIVE, PickupPolicy.Mode.CASUAL))
						.create(
								centerX - 100,
								centerY - 22,
								200,
								20,
								Component.translatable("config.better-hotbars.pickup_mode"),
								(button, value) -> this.selectedMode = value
						)
		);
		this.addRenderableWidget(
				CycleButton.builder(BetterHotbarsConfigScreen::behaviorModeName, this.selectedBehaviorMode)
						.withValues(List.of(BehaviorPolicy.Mode.values()))
						.create(centerX - 100, centerY + 2, 200, 20,
								Component.translatable("config.better-hotbars.behavior_mode"),
								(button, value) -> this.selectedBehaviorMode = value)
		);
		this.addRenderableWidget(
				Button.builder(CommonComponents.GUI_DONE, button -> this.saveAndClose())
						.bounds(centerX - 100, centerY + 38, 200, 20)
						.build()
		);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 45, -1);
	}

	@Override
	public void onClose() {
		this.saveAndClose();
	}

	private void saveAndClose() {
		if (this.minecraft.player != null && hidesCurrentExtraStorage(this.selectedBehaviorMode)) {
			this.minecraft.player.sendOverlayMessage(Component.translatable("message.better-hotbars.mode_storage_not_empty"));
			return;
		}
		PickupPolicy.setMode(this.selectedMode);
		BehaviorPolicy.setConfiguredMode(this.selectedBehaviorMode);
		this.minecraft.gui.setScreen(this.parent);
	}

	private boolean hidesCurrentExtraStorage(BehaviorPolicy.Mode selected) {
		if (selected == BehaviorPolicy.getConfiguredMode()) return false;
		ExtraStorageInventory extra = ((ExtraStorageAccess)this.minecraft.player).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < ExtraStorageInventory.SLOT_COUNT; slot++) if (!extra.getItem(slot).isEmpty()) return true;
		return false;
	}

	private static Component modeName(PickupPolicy.Mode mode) {
		return Component.translatable("config.better-hotbars.pickup_mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static Component behaviorModeName(BehaviorPolicy.Mode mode) {
		return Component.translatable("config.better-hotbars.behavior_mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
	}
}
