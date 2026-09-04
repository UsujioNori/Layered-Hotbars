package com.usujiotarako.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class BetterHotbarsGuiTextures {
	private static final Identifier BUILTIN_INVENTORY =
			Identifier.fromNamespaceAndPath("better-hotbars", "textures/gui/container/inventory.png");
	private static final Identifier BUILTIN_HOTBAR_ACTIVE =
			Identifier.fromNamespaceAndPath("better-hotbars", "textures/gui/hotbar_active.png");
	private static final Identifier BUILTIN_SORT_BUTTON =
			Identifier.fromNamespaceAndPath("better-hotbars", "textures/gui/sort_button.png");
	private static final Identifier BUILTIN_PICKUP_MODE =
			Identifier.fromNamespaceAndPath("better-hotbars", "textures/gui/pickup_mode.png");
	private static ResourceManager cachedResourceManager;
	private static Texture cachedInventory;
	private static Texture cachedHotbarActive;
	private static Texture cachedSortButton;
	private static Texture cachedPickupMode;
	private static final Map<Identifier, Optional<Texture>> CACHED_CONTAINER_TEXTURES = new HashMap<>();

	private BetterHotbarsGuiTextures() {}

	public record Texture(Identifier id, int width, int height) {}

	public static synchronized Texture inventory() {
		refreshCacheIfNeeded();
		if (cachedInventory == null) {
			Identifier override = minecraftBhTexture("textures/gui/container/inventory.png");
			cachedInventory = readTexture(resourceExists(override) ? override : BUILTIN_INVENTORY, 176, 184);
		}
		return cachedInventory;
	}

	public static synchronized Texture hotbarActive() {
		refreshCacheIfNeeded();
		if (cachedHotbarActive == null) cachedHotbarActive = readTexture(BUILTIN_HOTBAR_ACTIVE, 16, 96);
		return cachedHotbarActive;
	}

	public static synchronized Texture sortButton() {
		refreshCacheIfNeeded();
		if (cachedSortButton == null) cachedSortButton = readTexture(BUILTIN_SORT_BUTTON, 16, 32);
		return cachedSortButton;
	}

	public static synchronized Texture pickupMode() {
		refreshCacheIfNeeded();
		if (cachedPickupMode == null) cachedPickupMode = readTexture(BUILTIN_PICKUP_MODE, 16, 64);
		return cachedPickupMode;
	}

	public static synchronized Optional<Texture> containerForScreen(Object screen) {
		refreshCacheIfNeeded();
		String simpleName = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
		String fileName = switch (simpleName) {
			case "merchantscreen" -> "villager";
			case "containerscreen", "genericcontainerscreen" -> "generic_54";
			case "shulkerboxscreen" -> "shulker_box";
			case "horseinventoryscreen" -> "horse";
			case "nautilusscreen", "nautilusinventoryscreen", "nautiluscontainerscreen" -> "nautilus";
			case "craftingscreen" -> "crafting_table";
			case "furnacescreen" -> "furnace";
			case "blastfurnacescreen" -> "blast_furnace";
			case "smokerscreen" -> "smoker";
			case "dispenserscreen" -> "dispenser";
			case "hopperscreen" -> "hopper";
			case "brewingstandscreen" -> "brewing_stand";
			case "beaconscreen" -> "beacon";
			case "anvilscreen" -> "anvil";
			case "smithingscreen" -> "smithing";
			case "grindstonescreen" -> "grindstone";
			case "cartographytablescreen" -> "cartography_table";
			case "stonecutterscreen" -> "stonecutter";
			case "crafterscreen" -> "crafter";
			case "loomscreen" -> "loom";
			case "enchantmentscreen" -> "enchanting_table";
			default -> null;
		};
		if (fileName == null) return Optional.empty();
		Identifier override = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/" + fileName + "_bh.png");
		Identifier builtin = Identifier.fromNamespaceAndPath("better-hotbars", "textures/gui/container/" + fileName + ".png");
		Identifier selected = resourceExists(override) ? override : builtin;
		return CACHED_CONTAINER_TEXTURES.computeIfAbsent(selected, BetterHotbarsGuiTextures::loadOptional);
	}

	public static synchronized Identifier replaceVanillaContainerTexture(Identifier original) {
		refreshCacheIfNeeded();
		if (!original.getNamespace().equals("minecraft")) return original;
		String path = original.getPath();
		String prefix = "textures/gui/container/";
		if (!path.startsWith(prefix) || !path.endsWith(".png")) return original;
		Identifier override = minecraftBhTexture(path);
		if (resourceExists(override)) return override;
		Identifier replacement = Identifier.fromNamespaceAndPath("better-hotbars", path);
		return resourceExists(replacement) ? replacement : original;
	}

	private static Identifier minecraftBhTexture(String path) {
		if (!path.endsWith(".png")) return Identifier.fromNamespaceAndPath("minecraft", path);
		return Identifier.fromNamespaceAndPath("minecraft", path.substring(0, path.length() - 4) + "_bh.png");
	}

	private static boolean resourceExists(Identifier id) {
		return Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
	}

	private static void refreshCacheIfNeeded() {
		ResourceManager current = Minecraft.getInstance().getResourceManager();
		if (current == cachedResourceManager) return;
		cachedResourceManager = current;
		cachedInventory = null;
		cachedHotbarActive = null;
		cachedSortButton = null;
		cachedPickupMode = null;
		CACHED_CONTAINER_TEXTURES.clear();
	}

	private static Optional<Texture> loadOptional(Identifier id) {
		if (Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()) return Optional.empty();
		return Optional.of(readTexture(id, 256, 256));
	}

	private static Texture readTexture(Identifier selected, int fallbackWidth, int fallbackHeight) {
		try (InputStream stream = Minecraft.getInstance().getResourceManager().open(selected);
			 DataInputStream input = new DataInputStream(stream)) {
			if (input.readLong() != 0x89504E470D0A1A0AL || input.readInt() != 13 || input.readInt() != 0x49484452) {
				return new Texture(selected, fallbackWidth, fallbackHeight);
			}
			int width = input.readInt();
			int height = input.readInt();
			return width > 0 && height > 0
					? new Texture(selected, width, height)
					: new Texture(selected, fallbackWidth, fallbackHeight);
		} catch (IOException exception) {
			return new Texture(selected, fallbackWidth, fallbackHeight);
		}
	}
}
