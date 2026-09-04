package com.usujiotarako.client;

import com.usujiotarako.inventory.ExtraStorageSlot;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Map;
import com.usujiotarako.BehaviorPolicy;

/** Shared helpers for the optional Inventory Profiles Next integration. */
public final class IPNCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("inventoryprofilesnext");

	private IPNCompat() {}

	public static boolean isLoaded() {
		return LOADED;
	}

	/** Re-baseline IPN after a virtual profile deliberately changes both hands. */
	public static void resetAutoRefillMonitor() {
		if (!LOADED) return;
		try {
			Class<?> handlerClass = Class.forName("org.anti_ad.mc.ipnext.event.autorefill.AutoRefillHandler");
			Object handler = handlerClass.getField("INSTANCE").get(null);
			handlerClass.getMethod("init").invoke(handler);
		} catch (ReflectiveOperationException ignored) {
		}
	}


	public static Map<Object, Object> extendLockSlotLocations(Map<Object, Object> original) {
		if (!LOADED || !BehaviorPolicy.hasExtraRow()) return original;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) return original;
		try {
			Class<?> pointClass = Class.forName("org.anti_ad.mc.common.math2d.Point");
			Constructor<?> pointConstructor = pointClass.getConstructor(int.class, int.class);
			Map<Object, Object> result = new java.util.LinkedHashMap<>(original);
			for (Slot slot : screen.getMenu().slots) {
				if (slot instanceof ExtraStorageSlot extraSlot) result.put(extraSlot.getLogicalInventorySlot(), pointConstructor.newInstance(slot.x, slot.y));
			}
			return result;
		} catch (ReflectiveOperationException ignored) {
			return original;
		}
	}

	public static boolean isExtraSlotLocked(Slot slot) {
		if (!LOADED || !BehaviorPolicy.hasExtraRow() || slot == null || !isExtraStorageSlot(slot)) return false;
		try {
			Class<?> handlerClass = Class.forName("org.anti_ad.mc.ipnext.event.LockSlotsHandler");
			Object handler = handlerClass.getField("INSTANCE").get(null);
			Object locked = handlerClass.getMethod("getLockedInvSlots").invoke(handler);
			int key = ((ExtraStorageSlot)slot).getLogicalInventorySlot();
			if (locked instanceof Collection<?> collection) return collection.contains(key);
			if (locked instanceof Iterable<?> iterable) {
				for (Object value : iterable) if (Integer.valueOf(key).equals(value)) return true;
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return false;
	}

	private static boolean isExtraStorageSlot(Slot slot) {
		return slot instanceof ExtraStorageSlot;
	}
}
