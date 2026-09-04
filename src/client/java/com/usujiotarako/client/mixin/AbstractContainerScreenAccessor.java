package com.usujiotarako.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos") int betterHotbars$getLeftPos();
	@Accessor("topPos") int betterHotbars$getTopPos();
	@Accessor("hoveredSlot") Slot betterHotbars$getHoveredSlot();
}
