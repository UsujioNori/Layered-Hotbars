package com.usujiotarako.mixin;

import com.usujiotarako.access.ExtraStorageAccess;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin implements ExtraStorageAccess {
	@Unique private final ExtraStorageInventory betterHotbars$extraStorage = new ExtraStorageInventory((Player)(Object)this);

	@Override
	public ExtraStorageInventory betterHotbars$getExtraStorage() {
		return this.betterHotbars$extraStorage;
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void betterHotbars$loadExtraStorage(ValueInput input, CallbackInfo ci) {
		this.betterHotbars$extraStorage.clearContent();
		for (ItemStackWithSlot entry : input.listOrEmpty("BetterHotbarsStorage", ItemStackWithSlot.CODEC)) {
			if (entry.isValidInContainer(ExtraStorageInventory.SLOT_COUNT)) this.betterHotbars$extraStorage.setItem(entry.slot(), entry.stack());
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void betterHotbars$saveExtraStorage(ValueOutput output, CallbackInfo ci) {
		ValueOutput.TypedOutputList<ItemStackWithSlot> list = output.list("BetterHotbarsStorage", ItemStackWithSlot.CODEC);
		for (int slot = 0; slot < ExtraStorageInventory.SLOT_COUNT; slot++) {
			ItemStack stack = this.betterHotbars$extraStorage.getItem(slot);
			if (!stack.isEmpty()) list.add(new ItemStackWithSlot(slot, stack));
		}
	}

	@Inject(method = "dropEquipment", at = @At("TAIL"))
	private void betterHotbars$dropExtraStorage(ServerLevel level, CallbackInfo ci) {
		if (!level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			Player player = (Player)(Object)this;
			for (int slot = 0; slot < ExtraStorageInventory.SLOT_COUNT; slot++) {
				ItemStack stack = this.betterHotbars$extraStorage.removeItemNoUpdate(slot);
				if (!stack.isEmpty()) player.drop(stack, true, false);
			}
		}
	}
}
