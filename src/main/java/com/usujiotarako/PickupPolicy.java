package com.usujiotarako;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class PickupPolicy {
	public enum Mode { AGGRESSIVE, CASUAL }
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("better-hotbars.properties");
	private static Mode mode = Mode.AGGRESSIVE;
	private PickupPolicy() {}

	public static void load() {
		Properties properties = new Properties();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) { properties.load(reader); }
			catch (IOException exception) { BetterHotbars.LOGGER.error("Could not read {}", PATH, exception); }
		}
		try { mode = Mode.valueOf(properties.getProperty("pickup_mode", "aggressive").trim().toUpperCase(Locale.ROOT)); }
		catch (IllegalArgumentException exception) { mode = Mode.AGGRESSIVE; }
		BehaviorPolicy.load(properties);
		save();
	}

	private static void save() {
		Properties properties = new Properties();
		properties.setProperty("pickup_mode", mode.name().toLowerCase(Locale.ROOT));
		properties.setProperty("behavior_mode", BehaviorPolicy.getConfiguredMode().name().toLowerCase(Locale.ROOT));
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				properties.store(writer, "Layered Hotbars: aggressive uses manual virtual assignments; casual automatically assigns newly received stackable items.");
			}
		} catch (IOException exception) { BetterHotbars.LOGGER.error("Could not write {}", PATH, exception); }
	}

	public static Mode getMode() { return mode; }

	public static void setMode(Mode newMode) {
		if (newMode == null || newMode == mode) return;
		mode = newMode;
		save();
	}

	public static boolean isAggressive() { return mode == Mode.AGGRESSIVE; }
}
