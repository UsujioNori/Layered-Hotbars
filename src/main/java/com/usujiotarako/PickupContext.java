package com.usujiotarako;

/**
 * Marks the current thread while Minecraft is processing a genuine
 * ItemEntity -> player pickup.
 *
 * This deliberately does not alter the pickup call itself. Other mods such as
 * Collective are free to redirect/wrap ItemEntity.playerTouch normally. Better
 * Hotbars only consults this marker from Inventory.add so container transfers,
 * IPN scrolling, shift-clicking, crafting, etc. retain vanilla insertion rules.
 */
public final class PickupContext {
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

	private PickupContext() {}

	public static void enterWorldPickup() {
		DEPTH.set(DEPTH.get() + 1);
	}

	public static void exitWorldPickup() {
		int depth = DEPTH.get() - 1;
		if (depth <= 0) {
			DEPTH.remove();
		} else {
			DEPTH.set(depth);
		}
	}

	public static boolean isWorldPickup() {
		return DEPTH.get() > 0;
	}
}
