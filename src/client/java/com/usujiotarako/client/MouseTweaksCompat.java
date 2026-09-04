package com.usujiotarako.client;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

/**
 * Small optional compatibility bridge. There is intentionally no hard runtime
 * dependency on Mouse Tweaks: Layered Hotbars still loads when Mouse Tweaks is
 * absent, and uses its internal RMB fallback in that case.
 */
public final class MouseTweaksCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("mousetweaks");
	private static Field configField;
	private static Field rmbTweakField;
	private static boolean reflectionAttempted;

	private MouseTweaksCompat() {
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * Delegate RMB traversal to Mouse Tweaks only while its own RMB tweak is
	 * enabled. If its config cannot be read, fall back to Layered Hotbars rather
	 * than silently disabling RMB dragging.
	 */
	public static boolean shouldDelegateRmb() {
		if (!LOADED) return false;
		try {
			if (!reflectionAttempted) {
				reflectionAttempted = true;
				Class<?> mainClass = Class.forName("yalter.mousetweaks.Main");
				configField = mainClass.getField("config");
				Object config = configField.get(null);
				if (config != null) {
					rmbTweakField = config.getClass().getField("rmbTweak");
				}
			}
			if (configField == null || rmbTweakField == null) return false;
			Object config = configField.get(null);
			return config != null && rmbTweakField.getBoolean(config);
		} catch (ReflectiveOperationException | LinkageError ignored) {
			return false;
		}
	}
}
