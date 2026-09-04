package com.usujiotarako.client.mixin;

import com.usujiotarako.BehaviorPolicy;
import com.usujiotarako.PickupPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side pickup destination filtering for remote servers.
 *
 * Minecraft first tries to merge a received stack into an existing compatible
 * stack and only calls Inventory#getFreeSlot when a new stack needs somewhere
 * to live.  Intercepting that empty-slot choice avoids the visible one/two-tick
 * hotbar flash caused by moving a completed server pickup afterward.
 *
 * In physical Aggressive mode ordinary stackables search the normal inventory
 * rows before empty hotbar slots. Existing compatible hotbar stacks are not
 * affected because their merge happens before getFreeSlot. Tools/weapons/etc.
 * and all Casual-mode pickups keep vanilla's normal hotbar-first behaviour.
 */
@Mixin(value = Inventory.class, priority = 1100)
public abstract class ClientInventoryPickupMixin {
    @Shadow @Final public NonNullList<ItemStack> items;

    @Unique
    private ItemStack betterHotbars$addedStack;

    @Inject(method = "addResource", at = @At("HEAD"), require = 0)
    private void betterHotbars$captureAddedStack(ItemStack stack, CallbackInfoReturnable<?> cir) {
        this.betterHotbars$addedStack = stack;
    }

    @Inject(method = "addResource", at = @At("RETURN"), require = 0)
    private void betterHotbars$clearAddedStack(ItemStack stack, CallbackInfoReturnable<?> cir) {
        this.betterHotbars$addedStack = null;
    }

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void betterHotbars$chooseAggressivePickupSlot(CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        Inventory inventory = (Inventory)(Object)this;

        if (minecraft.player == null || inventory.player != minecraft.player) return;
        if (!PickupPolicy.isAggressive()) return;
        if (BehaviorPolicy.getEffectiveMode().usesVirtualHotbars()) return;

        ItemStack incoming = this.betterHotbars$addedStack;
        if (incoming == null || incoming.isEmpty() || betterHotbars$prefersHotbar(incoming)) return;

        // Keep empty hotbar cells out of the candidate set while normal storage
        // still has room. This is the same pre-selection strategy IPN uses for
        // locked slots, rather than correcting the inventory after the pickup.
        for (int slot = 9; slot < this.items.size(); slot++) {
            if (this.items.get(slot).isEmpty()) {
                cir.setReturnValue(slot);
                return;
            }
        }

        // Storage is genuinely full: fall back to vanilla so the hotbar can be
        // used rather than making an otherwise valid pickup impossible.
    }

    @Unique
    private static boolean betterHotbars$prefersHotbar(ItemStack stack) {
        return stack.has(DataComponents.TOOL)
                || stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.EQUIPPABLE)
                || stack.has(DataComponents.BLOCKS_ATTACKS)
                || stack.has(DataComponents.PIERCING_WEAPON)
                || stack.has(DataComponents.KINETIC_WEAPON);
    }
}
