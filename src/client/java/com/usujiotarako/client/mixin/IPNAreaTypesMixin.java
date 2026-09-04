package com.usujiotarako.client.mixin;

import com.usujiotarako.inventory.ExtraStorageSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.anti_ad.mc.ipnext.inventory.ItemArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import com.usujiotarako.BehaviorPolicy;

/**
 * Adds Layered Hotbars' fourth storage row to IPN's normal player-storage area.
 *
 * Layered Hotbars does not implement IPN sorting or scrolling here. IPN builds
 * its ordinary 27-slot player-storage ItemArea first; this hook only adds the
 * nine ExtraStorageSlot menu indices to that area.
 */
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.inventory.AreaType$Companion", remap = false)
public abstract class IPNAreaTypesMixin {
    @Inject(
            method = "playerInvSlots$lambda$1",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void betterHotbars$includeFourthStorageRow(CallbackInfoReturnable<ItemArea> cir) {
		if (!BehaviorPolicy.hasExtraRow()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        ItemArea nativeArea = cir.getReturnValue();
        if (nativeArea == null || nativeArea.isEmpty()) return;

        // Only extend IPN's normal 27-slot storage lookup. This keeps hotbar,
        // offhand and other player-area lookups untouched.
        List<Integer> nativeIndices = new ArrayList<>();
        for (Object value : nativeArea.getSlotIndices()) {
            if (value instanceof Number number) nativeIndices.add(number.intValue());
        }
        if (nativeIndices.size() != 27) return;

        List<Integer> fourthRowIndices = new ArrayList<>(9);
        for (int index = 0; index < menu.slots.size(); index++) {
            if (menu.slots.get(index) instanceof ExtraStorageSlot) fourthRowIndices.add(index);
        }
        if (fourthRowIndices.size() != 9) return;

        ItemArea fourthRow = ItemArea.Companion.invoke(menu.slots, fourthRowIndices, false);
        cir.setReturnValue(nativeArea.plus(fourthRow));
    }
}
