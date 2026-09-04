package com.usujiotarako.client.mixin;

import com.usujiotarako.client.access.SlotPositionAccess;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Slot.class)
public abstract class SlotMixin implements SlotPositionAccess {
	@Shadow @Final @Mutable public int y;

	@Override
	public void betterHotbars$setY(int y) {
		this.y = y;
	}
}
