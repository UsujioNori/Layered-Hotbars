package com.usujiotarako;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.minecraft.world.entity.player.Player;

/** Startup-only feature mode. AUTO stays conservative on remote clients until a server handshake confirms support. */
public final class BehaviorPolicy {
	public enum Mode {
		AUTOMATIC, VANILLA, VANILLA_PLUS, PARTIAL, FULL;

		public boolean usesVirtualHotbars() { return this == PARTIAL || this == FULL; }
		public boolean hasExtraInventoryRow() { return this == FULL; }
		public boolean keepsPhysicalProfiles() { return this == VANILLA_PLUS; }
	}

	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("better-hotbars.properties");
	private static Mode configuredMode = Mode.AUTOMATIC;
	private static Mode effectiveMode = Mode.VANILLA;
	private static Mode activeServerMode = Mode.FULL;

	private BehaviorPolicy() {}

	public static void load(Properties properties) {
		try {
			configuredMode = Mode.valueOf(properties.getProperty("behavior_mode", "automatic").trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			configuredMode = Mode.AUTOMATIC;
		}
		effectiveMode = configuredMode == Mode.AUTOMATIC ? initialAutomaticMode() : configuredMode;
		activeServerMode = configuredMode == Mode.AUTOMATIC ? Mode.FULL : configuredMode;
	}

	private static Mode initialAutomaticMode() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER ? Mode.FULL : Mode.VANILLA;
	}

	public static Mode getConfiguredMode() { return configuredMode; }
	public static Mode getEffectiveMode() { return effectiveMode; }
	public static Mode getServerMode() { return activeServerMode; }
	public static void beginServerConnection() {
		activeServerMode = configuredMode == Mode.AUTOMATIC ? Mode.FULL : configuredMode;
	}
	public static boolean isVirtual() { return effectiveMode.usesVirtualHotbars(); }
	public static boolean hasExtraRow() { return effectiveMode.hasExtraInventoryRow(); }
	public static boolean keepsPhysicalProfiles() { return effectiveMode.keepsPhysicalProfiles(); }
	public static Mode modeFor(Player player) {
		return player.level().isClientSide() ? effectiveMode : getServerMode();
	}
	public static boolean hasExtraRow(Player player) { return modeFor(player).hasExtraInventoryRow(); }
	public static boolean keepsPhysicalProfiles(Player player) { return modeFor(player).keepsPhysicalProfiles(); }

	/** Called only after the remote server advertises the Layered Hotbars protocol. */
	public static void acceptServerMode(String name) {
		try { effectiveMode = Mode.valueOf(name.toUpperCase(Locale.ROOT)); }
		catch (IllegalArgumentException exception) { effectiveMode = Mode.VANILLA; }
	}

	public static void resetAutomaticConnection() {
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) effectiveMode = Mode.VANILLA;
	}

	public static void setConfiguredMode(Mode mode) {
		if (mode == null || mode == configuredMode) return;
		configuredMode = mode;
		writeModeOnly();
	}

	private static void writeModeOnly() {
		Properties properties = new Properties();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) { properties.load(reader); }
			catch (IOException exception) { BetterHotbars.LOGGER.error("Could not read {}", PATH, exception); }
		}
		properties.setProperty("behavior_mode", configuredMode.name().toLowerCase(Locale.ROOT));
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) { properties.store(writer, "Layered Hotbars settings. Behavior mode changes apply after restarting/reconnecting."); }
		} catch (IOException exception) { BetterHotbars.LOGGER.error("Could not write {}", PATH, exception); }
	}
}
