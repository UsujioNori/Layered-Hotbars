package com.usujiotarako.client;

import com.usujiotarako.inventory.ExtraStorageSlot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.usujiotarako.BetterHotbars;
import com.usujiotarako.PickupPolicy;
import com.usujiotarako.BehaviorPolicy;
import com.usujiotarako.access.ExtraStorageAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import com.usujiotarako.inventory.ExtraStorageInventory;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class VirtualHotbarManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("better-hotbars").resolve("contexts");
	private static VirtualHotbarConfig config = new VirtualHotbarConfig();
	private static String loadedContext;
	private static Path configPath;
	private static int swapCooldown;
	private static int pendingProfile = -1;
	private static int pendingProfileDelay;
	private static boolean pendingVirtualSwitch;
	private static boolean[] pendingKeptHotbar = new boolean[9];
	private static boolean pendingKeptOffhand;
	private static boolean pendingVanillaOffhand;
	private static boolean offhandCleanupPending;
	private static boolean clearOffhandAfterSwap;
	private static Item virtualCursorItem;
	private static int virtualCursorSourceHotbarSlot = -1;
	private static int sortButtonPressedTicks;
	private static int pickupModeButtonPressedTicks;
	private static final Item[] suppressedHotbarAssignments = new Item[9];
	private static final int[] suppressedHotbarAssignmentTicks = new int[9];
	private static final Container[] materializedSourceContainers = new Container[9];
	private static final int[] materializedSourceSlots = new int[9];
	private static final java.util.IdentityHashMap<Item, Integer> observedStackableCounts = new java.util.IdentityHashMap<>();
	private static boolean observedStackableCountsInitialized;
	private static final ItemStack[] physicalPickupSnapshot = new ItemStack[Inventory.INVENTORY_SIZE];
	private static boolean physicalPickupSnapshotInitialized;
	private static BehaviorPolicy.Mode physicalPickupSnapshotMode;
	private static int physicalPickupReconcileCooldown;

	private VirtualHotbarManager() {}

	public static void load() {
		config = new VirtualHotbarConfig();
		config.validate();
		loadedContext = null;
		configPath = null;
		offhandCleanupPending = false;
		pendingProfile = -1;
		pendingProfileDelay = 0;
		pendingVirtualSwitch = false;
		java.util.Arrays.fill(pendingKeptHotbar, false);
		pendingKeptOffhand = false;
		pendingVanillaOffhand = false;
		clearOffhandAfterSwap = false;
		resetVirtualCursor();
		sortButtonPressedTicks = 0;
		pickupModeButtonPressedTicks = 0;
		java.util.Arrays.fill(suppressedHotbarAssignments, null);
		java.util.Arrays.fill(suppressedHotbarAssignmentTicks, 0);
		clearMaterializedSources();
		observedStackableCounts.clear();
		observedStackableCountsInitialized = false;
		resetPhysicalPickupSnapshot();
	}

	private static void ensureContext(Minecraft client) {
		String context = getContext(client);
		if (context == null || context.equals(loadedContext)) return;
		loadedContext = context;
		String contextId = UUID.nameUUIDFromBytes(context.getBytes(StandardCharsets.UTF_8)).toString();
		configPath = CONFIG_DIRECTORY.resolve(contextId + ".json");
		config = new VirtualHotbarConfig();
		if (Files.exists(configPath)) {
			try (Reader reader = Files.newBufferedReader(configPath)) {
				VirtualHotbarConfig loaded = GSON.fromJson(reader, VirtualHotbarConfig.class);
				if (loaded != null && context.equals(loaded.context)) config = loaded;
			} catch (IOException | RuntimeException exception) {
				BetterHotbars.LOGGER.error("Could not read {}", configPath, exception);
			}
		}
		config.context = context;
		config.validate();
		java.util.Arrays.fill(suppressedHotbarAssignments, null);
		java.util.Arrays.fill(suppressedHotbarAssignmentTicks, 0);
		clearMaterializedSources();
		resetVirtualCursor();
		observedStackableCounts.clear();
		observedStackableCountsInitialized = false;
		resetPhysicalPickupSnapshot();
	}

	private static String getContext(Minecraft client) {
		if (client.player == null || client.level == null) return null;
		if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
			Path worldPath = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			return "singleplayer:" + worldPath;
		}
		ServerData server = client.getCurrentServer();
		return server == null ? null : "server:" + server.ip.toLowerCase(Locale.ROOT);
	}

	private static void ensureCurrentContext() {
		ensureContext(Minecraft.getInstance());
	}

	private static void save() {
		if (configPath == null || loadedContext == null) return;
		try {
			Files.createDirectories(configPath.getParent());
			try (Writer writer = Files.newBufferedWriter(configPath)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException exception) {
			BetterHotbars.LOGGER.error("Could not save {}", configPath, exception);
		}
	}

	public static boolean assignSelected(Player player) {
		if (!BehaviorPolicy.isVirtual()) return false;
		Inventory inventory = player.getInventory();
		return assign(inventory.getSelectedSlot(), inventory.getSelectedItem());
	}

	public static boolean assign(int hotbarSlot, ItemStack stack) {
		if (!BehaviorPolicy.isVirtual()) return false;
		ensureCurrentContext();
		if (hotbarSlot < 0 || hotbarSlot >= 9 || stack.isEmpty()) return false;
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		boolean changed = false;
		if (stack.getMaxStackSize() > 1) {
			if (id.equals(config.offhandAssignments[config.activeProfile])) {
				config.offhandAssignments[config.activeProfile] = null;
				config.offhandAssignmentExplicit[config.activeProfile] = false;
				clearOffhandAfterSwap = true;
				changed = true;
			}
			for (int slot = 0; slot < 9; slot++) {
				if (slot != hotbarSlot && id.equals(config.assignments[config.activeProfile][slot])) {
					config.assignments[config.activeProfile][slot] = null;
					config.assignmentSignatures[config.activeProfile][slot] = null;
					config.locked[config.activeProfile][slot] = false;
					changed = true;
				}
			}
		}
		String signature = getExactStackSignature(stack);
		if (stack.getMaxStackSize() <= 1
				&& id.equals(config.assignments[config.activeProfile][hotbarSlot])) {
			changed |= propagateEvolvedNonStackableSignature(
					id,
					config.assignmentSignatures[config.activeProfile][hotbarSlot],
					signature
			);
		}
		if (id.equals(config.assignments[config.activeProfile][hotbarSlot])
				&& java.util.Objects.equals(signature, config.assignmentSignatures[config.activeProfile][hotbarSlot])) {
			if (changed) save();
			return true;
		}
		config.assignments[config.activeProfile][hotbarSlot] = id;
		config.assignmentSignatures[config.activeProfile][hotbarSlot] = signature;
		save();
		return true;
	}

	public static void clearSelected(Player player) {
		if (!BehaviorPolicy.isVirtual()) return;
		clear(player.getInventory().getSelectedSlot());
	}

	public static void clear(int hotbarSlot) {
		if (!BehaviorPolicy.isVirtual()) return;
		ensureCurrentContext();
		if (hotbarSlot < 0 || hotbarSlot >= 9) return;
		config.assignments[config.activeProfile][hotbarSlot] = null;
		config.assignmentSignatures[config.activeProfile][hotbarSlot] = null;
		config.locked[config.activeProfile][hotbarSlot] = false;
		save();
	}

	public static void clearForInventoryTransfer(int hotbarSlot) {
		ensureCurrentContext();
		if (hotbarSlot < 0 || hotbarSlot >= 9) return;
		suppressedHotbarAssignments[hotbarSlot] = getAssignedItem(hotbarSlot);
		suppressedHotbarAssignmentTicks[hotbarSlot] = 40;
		clear(hotbarSlot);
	}

	public static boolean toggleLocked(int hotbarSlot, ItemStack currentStack) {
		if (!BehaviorPolicy.isVirtual()) return false;
		ensureCurrentContext();
		if (hotbarSlot < 0 || hotbarSlot >= 9) return false;
		if (!currentStack.isEmpty()) assign(hotbarSlot, currentStack);
		if (config.assignments[config.activeProfile][hotbarSlot] == null) return false;
		boolean locked = !config.locked[config.activeProfile][hotbarSlot];
		config.locked[config.activeProfile][hotbarSlot] = locked;
		save();
		return locked;
	}

	public static boolean isLocked(int hotbarSlot) {
		if (!BehaviorPolicy.isVirtual()) return false;
		ensureCurrentContext();
		return hotbarSlot >= 0 && hotbarSlot < 9 && config.locked[config.activeProfile][hotbarSlot];
	}

	public static int nextProfile() {
		ensureCurrentContext();
		return Math.floorMod(config.activeProfile + 1, 3);
	}

	public static boolean switchProfile(Minecraft client, int direction) {
		if (client.player == null || client.gameMode == null || client.player.getAbilities().instabuild || direction == 0) return false;
		ensureContext(client);
		return switchToProfile(client, config.activeProfile + Integer.signum(direction));
	}

	public static boolean switchToProfile(Minecraft client, int requestedProfile) {
		if (client.player == null || client.gameMode == null || client.player.getAbilities().instabuild) return false;
		ensureContext(client);
		if (!BehaviorPolicy.isVirtual()) return switchPhysicalProfile(client, requestedProfile);
		Inventory inventory = client.player.getInventory();
		int outgoingProfile = config.activeProfile;
		int incomingProfile = Math.floorMod(requestedProfile, 3);
		if (incomingProfile == outgoingProfile) return true;

		// Do not snapshot another profile while the previous physical swap is still
		// settling. Temporary stacks must never become assignments for the next
		// profile just because the player switched profiles quickly.
		if (swapCooldown > 0 || pendingProfile >= 0) return false;

		for (int slot = 0; slot < 9; slot++) {
			ItemStack physical = inventory.getItem(slot);
			// Stackable hotbar entries are virtual assignments and are updated only by
			// explicit assignment actions. Only genuine physical tools/equipment are
			// learned from the outgoing hotbar here.
			if (!physical.isEmpty() && physical.getMaxStackSize() <= 1) {
				setAssignmentFromStack(outgoingProfile, slot, physical);
			}
		}
		ItemStack physicalOffhand = inventory.getItem(40);

		// Legacy builds could copy an offhand assignment into another profile without
		// the player ever assigning it there. There was no provenance stored in those
		// configs, so validate the outgoing profile against the real physical offhand
		// before entering another profile. From this build onward explicit placement
		// also records this flag directly.
		String outgoingOffhandAssignment = config.offhandAssignments[outgoingProfile];
		if (!physicalOffhand.isEmpty()
				&& outgoingOffhandAssignment != null
				&& outgoingOffhandAssignment.equals(getItemId(physicalOffhand))) {
			config.offhandAssignmentExplicit[outgoingProfile] = true;
		}

		// A stackable offhand assignment is already recorded explicitly. Never learn
		// it from transient slot-40 contents during a profile change. Genuine physical
		// non-stackables can still update the outgoing assignment.
		if (!physicalOffhand.isEmpty() && physicalOffhand.getMaxStackSize() <= 1) {
			config.offhandAssignments[outgoingProfile] = getItemId(physicalOffhand);
			config.offhandAssignmentExplicit[outgoingProfile] = true;
		}

		// One-time cleanup for old inherited/ghost offhand assignments. If an old
		// profile claims an offhand item but has never been validated by actually
		// holding it or by an explicit assignment action, do not let that stale value
		// keep the outgoing profile's physical stack.
		if (config.offhandAssignments[incomingProfile] != null
				&& !config.offhandAssignmentExplicit[incomingProfile]) {
			config.offhandAssignments[incomingProfile] = null;
		}

		boolean[] keep = new boolean[9];
		int requiredEmptySlots = 0;
		for (int slot = 0; slot < 9; slot++) {
			Item incomingItem = getAssignedItem(incomingProfile, slot);
			ItemStack physical = inventory.getItem(slot);
			keep[slot] = incomingItem != null && matchesAssignment(physical, incomingItem, getAssignedSignature(incomingProfile, slot));
			if (!physical.isEmpty() && !keep[slot]) requiredEmptySlots++;
		}
		Item incomingOffhandItem = getAssignedOffhandItem(incomingProfile);
		boolean keepOffhand = incomingOffhandItem != null && physicalOffhand.is(incomingOffhandItem);
		if (!physicalOffhand.isEmpty() && !keepOffhand) requiredEmptySlots++;

		int[] emptyStorage = getEmptyStorageMenuSlots(client.player);
		if (emptyStorage.length < requiredEmptySlots) {
			client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.better-hotbars.profile_no_space"));
			return false;
		}

		// Mark the virtual transition before sending its first inventory action so
		// IPN cannot observe the temporary empty hand and auto-refill it mid-switch.
		beginPendingSwitch(incomingProfile, true, keep, keepOffhand, false);
		int emptyIndex = 0;
		for (int slot = 0; slot < 9; slot++) {
			if (!inventory.getItem(slot).isEmpty() && !keep[slot]) {
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, emptyStorage[emptyIndex++], slot, ContainerInput.SWAP, client.player);
			}
		}
		if (!physicalOffhand.isEmpty() && !keepOffhand) {
			client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, emptyStorage[emptyIndex++], 40, ContainerInput.SWAP, client.player);
		}

		save();
		return true;
	}

	private static boolean switchPhysicalProfile(Minecraft client, int requestedProfile) {
		Inventory inventory = client.player.getInventory();
		int outgoingProfile = config.activeProfile;
		int incomingProfile = Math.floorMod(requestedProfile, 3);
		if (incomingProfile == outgoingProfile) return true;
		if (swapCooldown > 0 || pendingProfile >= 0) return false;

		if (BehaviorPolicy.keepsPhysicalProfiles()) {
			ExtraStorageInventory extra = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();
			for (int slot = 0; slot < 9; slot++) {
				if (!extra.getItem(ExtraStorageInventory.physicalProfileHotbarSlot(outgoingProfile, slot)).isEmpty()) {
					client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.better-hotbars.profile_storage_busy"));
					return false;
				}
			}
			if (!extra.getItem(ExtraStorageInventory.physicalProfileOffhandSlot(outgoingProfile)).isEmpty()) {
				client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.better-hotbars.profile_storage_busy"));
				return false;
			}

			// InventoryMenu appends all Vanilla+ private slots after vanilla's 46 slots.
			// The legacy 27 hotbar positions stay exactly where they were; the three
			// profile offhands are appended after them so existing saves remain valid.
			for (int slot = 0; slot < 9; slot++) {
				int outgoingLocal = ExtraStorageInventory.physicalProfileHotbarSlot(outgoingProfile, slot);
				int incomingLocal = ExtraStorageInventory.physicalProfileHotbarSlot(incomingProfile, slot);
				int outgoingMenuSlot = 46 + (outgoingLocal - ExtraStorageInventory.PHYSICAL_PROFILE_BASE);
				int incomingMenuSlot = 46 + (incomingLocal - ExtraStorageInventory.PHYSICAL_PROFILE_BASE);
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, outgoingMenuSlot, slot, ContainerInput.SWAP, client.player);
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, incomingMenuSlot, slot, ContainerInput.SWAP, client.player);
			}

			int outgoingOffhandLocal = ExtraStorageInventory.physicalProfileOffhandSlot(outgoingProfile);
			int incomingOffhandLocal = ExtraStorageInventory.physicalProfileOffhandSlot(incomingProfile);
			int outgoingOffhandMenuSlot = 46 + (outgoingOffhandLocal - ExtraStorageInventory.PHYSICAL_PROFILE_BASE);
			int incomingOffhandMenuSlot = 46 + (incomingOffhandLocal - ExtraStorageInventory.PHYSICAL_PROFILE_BASE);
			client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, outgoingOffhandMenuSlot, 40, ContainerInput.SWAP, client.player);
			client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, incomingOffhandMenuSlot, 40, ContainerInput.SWAP, client.player);
		} else {
			for (int slot = 0; slot < 9; slot++) {
				ItemStack physical = inventory.getItem(slot);
				config.vanillaAssignments[outgoingProfile][slot] = physical.isEmpty() ? null : getItemId(physical);
				config.vanillaAssignmentSignatures[outgoingProfile][slot] = physical.isEmpty() ? null : getExactStackSignature(physical);
			}
			ItemStack physicalOffhand = inventory.getItem(40);
			config.vanillaOffhandAssignments[outgoingProfile] = physicalOffhand.isEmpty() ? null : getItemId(physicalOffhand);
			config.vanillaOffhandAssignmentSignatures[outgoingProfile] = physicalOffhand.isEmpty() ? null : getExactStackSignature(physicalOffhand);
			int required = 0;
			for (int slot = 0; slot < 9; slot++) if (!inventory.getItem(slot).isEmpty()) required++;
			if (!physicalOffhand.isEmpty()) required++;
			int[] emptyStorage = getEmptyStorageMenuSlots(client.player);
			if (emptyStorage.length < required) {
				client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.better-hotbars.profile_no_space"));
				return false;
			}
			int emptyIndex = 0;
			for (int slot = 0; slot < 9; slot++) {
				if (!inventory.getItem(slot).isEmpty()) client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, emptyStorage[emptyIndex++], slot, ContainerInput.SWAP, client.player);
			}
			beginPendingSwitch(incomingProfile, false, new boolean[9], true, true);
			if (!physicalOffhand.isEmpty()) {
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, emptyStorage[emptyIndex], 40, ContainerInput.SWAP, client.player);
			}
		}
		if (!BehaviorPolicy.keepsPhysicalProfiles()) {
			save();
			return true;
		}
		config.activeProfile = incomingProfile;
		swapCooldown = 4;
		save();
		client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.better-hotbars.profile", incomingProfile + 1));
		return true;
	}

	private static void beginPendingSwitch(int profile, boolean virtual, boolean[] keptHotbar, boolean keptOffhand, boolean vanillaOffhand) {
		pendingProfile = profile;
		pendingVirtualSwitch = virtual;
		pendingKeptHotbar = java.util.Arrays.copyOf(keptHotbar, 9);
		pendingKeptOffhand = keptOffhand;
		pendingVanillaOffhand = vanillaOffhand;
		// The outgoing swaps are sent during the input event and this pending stage is
		// processed from the client tick handler, so there is already a natural
		// one-tick handoff before the incoming profile is materialised. Older builds
		// added another three ticks for Partial/Full, which made virtual profile
		// switching visibly sluggish without adding useful protection. Keep the
		// staged swap, IPN pause, and offhand protection, but restore every mode on
		// the next client tick.
		pendingProfileDelay = 0;
		swapCooldown = 0;
	}

	public static boolean isVirtualProfileSwitchInProgress() {
		return BehaviorPolicy.isVirtual() && pendingProfile >= 0 && pendingVirtualSwitch;
	}

	/** IPN may restock normally except while Layered Hotbars intentionally changes a managed hand. */
	public static boolean shouldPauseIpnAutoRefill() {
		return pendingProfile >= 0 && (pendingVirtualSwitch || pendingVanillaOffhand);
	}

	/**
	 * Give the outgoing SWAP packet its own short stage before loading the next
	 * profile. Do not wait for the rendered physical slot to look empty: virtual
	 * materialisation and network acknowledgement can legitimately keep that view
	 * populated, which previously deadlocked the switch until the player moved it.
	 */
	private static boolean tickPendingSwitch(Minecraft client) {
		if (pendingProfile < 0) return false;
		if (pendingProfileDelay > 0) {
			pendingProfileDelay--;
			return true;
		}

		int incoming = pendingProfile;
		config.activeProfile = incoming;
		for (int slot = 0; slot < 9; slot++) {
			if (pendingKeptHotbar[slot]) continue;
			Item wanted = pendingVirtualSwitch ? getAssignedItem(incoming, slot)
					: itemFromId(config.vanillaAssignments[incoming][slot]);
			String signature = pendingVirtualSwitch ? getAssignedSignature(incoming, slot)
					: config.vanillaAssignmentSignatures[incoming][slot];
			if (wanted == null) continue;
			int source = findBestSourceMenuSlot(client.player, wanted, signature);
			if (source >= 0) {
				if (pendingVirtualSwitch) rememberMaterializedSource(client.player, slot, source);
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, source, slot, ContainerInput.SWAP, client.player);
			}
		}
		if (pendingVirtualSwitch && !pendingKeptOffhand) {
			Item wanted = getAssignedOffhandItem(incoming);
			if (wanted != null) {
				int source = findBestSourceMenuSlot(client.player, wanted);
				if (source >= 0) client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, source, 40, ContainerInput.SWAP, client.player);
			}
		}
		if (!pendingVirtualSwitch && pendingVanillaOffhand) {
			Item wanted = itemFromId(config.vanillaOffhandAssignments[incoming]);
			if (wanted != null) {
				int source = findBestSourceMenuSlot(client.player, wanted, config.vanillaOffhandAssignmentSignatures[incoming]);
				if (source >= 0) client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, source, 40, ContainerInput.SWAP, client.player);
			}
		}
		boolean resetIpnAutoRefill = pendingVirtualSwitch || pendingVanillaOffhand;
		pendingProfile = -1;
		pendingProfileDelay = 0;
		pendingVirtualSwitch = false;
		java.util.Arrays.fill(pendingKeptHotbar, false);
		pendingKeptOffhand = false;
		pendingVanillaOffhand = false;
		if (resetIpnAutoRefill) IPNCompat.resetAutoRefillMonitor();
		swapCooldown = 4;
		save();
		client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.better-hotbars.profile", incoming + 1));
		return true;
	}

	private static Item itemFromId(String value) {
		Identifier id = value == null ? null : Identifier.tryParse(value);
		return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
	}

	/**
	 * Both pickup modes keep real stackable items out of the physical hotbar.
	 * Casual mode additionally watches the authoritative backing totals so a newly
	 * received stackable can claim one empty virtual hotbar position. The first
	 * snapshot is baseline-only, preventing items that were already in the
	 * inventory when joining a world from being assigned unexpectedly.
	 */
	private static void updateReceivedStackableAssignments(Inventory inventory, boolean allowAssignments) {
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		java.util.IdentityHashMap<Item, Integer> currentCounts = new java.util.IdentityHashMap<>();
		java.util.IdentityHashMap<Item, ItemStack> representatives = new java.util.IdentityHashMap<>();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || stack.getMaxStackSize() <= 1) continue;
			currentCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			representatives.putIfAbsent(stack.getItem(), stack);
		}
		// Include the physical offhand in the same total. Profile switching moves the
		// outgoing offhand stack into backing storage; excluding slot 40 made that
		// count-neutral move look like a brand-new Casual-mode pickup.
		ItemStack offhandStack = inventory.getItem(40);
		if (!offhandStack.isEmpty() && offhandStack.getMaxStackSize() > 1) {
			currentCounts.merge(offhandStack.getItem(), offhandStack.getCount(), Integer::sum);
			representatives.putIfAbsent(offhandStack.getItem(), offhandStack);
		}
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = extra.getItem(slot);
			if (stack.isEmpty() || stack.getMaxStackSize() <= 1) continue;
			currentCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			representatives.putIfAbsent(stack.getItem(), stack);
		}

		if (allowAssignments && observedStackableCountsInitialized && !PickupPolicy.isAggressive()) {
			for (java.util.Map.Entry<Item, Integer> entry : currentCounts.entrySet()) {
				Item item = entry.getKey();
				int previous = observedStackableCounts.getOrDefault(item, 0);
				if (entry.getValue() <= previous || findAssignedHotbarSlot(item) >= 0 || getAssignedOffhandItem() == item) continue;
				ItemStack representative = representatives.get(item);
				for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
					if (getAssignedItem(hotbarSlot) == null) {
						assign(hotbarSlot, representative);
						break;
					}
				}
			}
		}

		observedStackableCounts.clear();
		observedStackableCounts.putAll(currentCounts);
		observedStackableCountsInitialized = true;
	}

	private static void resetPhysicalPickupSnapshot() {
		java.util.Arrays.fill(physicalPickupSnapshot, ItemStack.EMPTY);
		physicalPickupSnapshotInitialized = false;
		physicalPickupSnapshotMode = null;
		physicalPickupReconcileCooldown = 0;
	}

	private static void capturePhysicalPickupSnapshot(Inventory inventory) {
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			physicalPickupSnapshot[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
		}
		physicalPickupSnapshotInitialized = true;
		physicalPickupSnapshotMode = BehaviorPolicy.getEffectiveMode();
	}

	/**
	 * Remote servers own ItemEntity pickup insertion, so the common-side
	 * Inventory.add hook cannot choose the destination slots there.  Reconcile
	 * only the physical modes after the authoritative server inventory update,
	 * using normal vanilla container clicks that the server validates.
	 *
	 * Casual already matches vanilla's hotbar-first free-slot behaviour.
	 * Aggressive only corrects a newly-created ordinary stack in the hotbar; an
	 * existing matching hotbar stack is deliberately allowed to fill first.
	 */
	private static boolean reconcileRemotePhysicalPickup(Minecraft client, Inventory inventory) {
		BehaviorPolicy.Mode mode = BehaviorPolicy.getEffectiveMode();
		if (client.hasSingleplayerServer() || mode.usesVirtualHotbars()) {
			resetPhysicalPickupSnapshot();
			return false;
		}

		// GUI moves, profile swaps and their synchronisation echoes are not world
		// pickups. Re-baseline around those operations instead of interpreting them.
		if (client.gui.screen() != null || pendingProfile >= 0 || swapCooldown > 0) {
			capturePhysicalPickupSnapshot(inventory);
			return false;
		}

		if (!physicalPickupSnapshotInitialized || physicalPickupSnapshotMode != mode) {
			capturePhysicalPickupSnapshot(inventory);
			return false;
		}

		// Casual deliberately follows the remote server's vanilla hotbar-first
		// behaviour. Keep the baseline fresh and do nothing else.
		if (!PickupPolicy.isAggressive()) {
			capturePhysicalPickupSnapshot(inventory);
			return false;
		}

		AbstractContainerMenu menu = client.player.inventoryMenu;
		if (!menu.getCarried().isEmpty()) {
			capturePhysicalPickupSnapshot(inventory);
			return false;
		}

		boolean movedAny = false;

		// This deliberately keys off a slot that WAS EMPTY. A partial matching
		// stack already present on the hotbar therefore remains untouched and can
		// be topped up by the server, which is the intended Aggressive behaviour.
		for (int hotbar = 0; hotbar < 9; hotbar++) {
			ItemStack previous = physicalPickupSnapshot[hotbar];
			if (previous != null && !previous.isEmpty()) continue;

			ItemStack current = inventory.getItem(hotbar);
			if (current.isEmpty() || prefersPhysicalHotbarClient(current)) continue;

			int destination = -1;
			for (int storage = 9; storage < Inventory.INVENTORY_SIZE; storage++) {
				ItemStack oldStorage = physicalPickupSnapshot[storage];
				if (oldStorage != null && !oldStorage.isEmpty()) continue;
				if (!inventory.getItem(storage).isEmpty()) continue;
				destination = storage;
				break;
			}

			if (destination >= 0) {
				// InventoryMenu uses 36..44 for player hotbar slots and 9..35 for
				// normal player storage. IPN's locked-slot keeper performs explicit
				// click-to-click moves for the same reason: QUICK_MOVE lets vanilla
				// choose a destination and can route straight back to the hotbar.
				client.gameMode.handleContainerInput(
						menu.containerId, 36 + hotbar, 0, ContainerInput.PICKUP, client.player);
				client.gameMode.handleContainerInput(
						menu.containerId, destination, 0, ContainerInput.PICKUP, client.player);
				movedAny = true;
				continue;
			}

			// If every storage slot is occupied but there is merge capacity, use a
			// shift move as a last resort. The explicit empty-slot path above is the
			// normal path and is what removes the hotbar-first behaviour.
			if (hasPhysicalStorageRoom(inventory, current)) {
				client.gameMode.handleContainerInput(
						menu.containerId, 36 + hotbar, 0, ContainerInput.QUICK_MOVE, client.player);
				movedAny = true;
			}
		}

		// handleContainerInput predicts the click locally, so capturing here records
		// the post-move state immediately and allows several pickup slots to be
		// corrected in the same tick without a later watcher fighting them.
		capturePhysicalPickupSnapshot(inventory);
		return movedAny;
	}

	/**
	 * Enforces physical-mode pickup policy for the dedicated player-inventory
	 * packet used by remote servers. This runs from the packet handler itself,
	 * so the corrective click is applied locally before the next frame rather
	 * than one or more client ticks later.
	 */
	public static void onRemotePlayerInventorySlotUpdated(int inventorySlot, ItemStack previous, ItemStack received) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gameMode == null) return;
		if (client.hasSingleplayerServer()) return;
		if (client.player.getAbilities().instabuild) return;
		if (BehaviorPolicy.getEffectiveMode().usesVirtualHotbars()) return;
		if (!PickupPolicy.isAggressive()) return;
		if (client.gui.screen() != null) return;
		if (pendingProfile >= 0 || swapCooldown > 0) return;
		if (inventorySlot < 0 || inventorySlot >= 9) return;
		if (received == null || received.isEmpty()) return;
		if (prefersPhysicalHotbarClient(received)) return;

		// Aggressive deliberately allows a matching stack that was already on the
		// physical hotbar to fill before inventory storage is considered.
		if (previous != null && !previous.isEmpty()
				&& ItemStack.isSameItemSameComponents(previous, received)) {
			return;
		}

		Inventory inventory = client.player.getInventory();
		if (!hasPhysicalStorageRoom(inventory, received)) return;

		// InventoryMenu maps player hotbar inventory slots 0..8 to menu slots
		// 36..44. QUICK_MOVE performs the same server-validating shift-click IPN's
		// multiplayer locked-slot keeper ultimately relies on for corrections.
		client.gameMode.handleContainerInput(
				client.player.inventoryMenu.containerId,
				36 + inventorySlot,
				0,
				ContainerInput.QUICK_MOVE,
				client.player
		);
	}

	private static boolean hasPhysicalStorageRoom(Inventory inventory, ItemStack incoming) {
		for (int slot = 9; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack existing = inventory.getItem(slot);
			if (existing.isEmpty()) return true;
			if (ItemStack.isSameItemSameComponents(existing, incoming)
					&& existing.getCount() < existing.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	private static boolean prefersPhysicalHotbarClient(ItemStack stack) {
		return stack.has(DataComponents.TOOL)
				|| stack.has(DataComponents.WEAPON)
				|| stack.has(DataComponents.EQUIPPABLE)
				|| stack.has(DataComponents.BLOCKS_ATTACKS)
				|| stack.has(DataComponents.PIERCING_WEAPON)
				|| stack.has(DataComponents.KINETIC_WEAPON);
	}

	public static String getSelectedAssignmentName(Player player) {
		ItemStack stack = getDisplayedStack(player.getInventory(), player.getInventory().getSelectedSlot());
		return stack.isEmpty() ? "" : stack.getHoverName().getString();
	}

	public static void tick(Minecraft client) {
		if (swapCooldown > 0) swapCooldown--;
		if (sortButtonPressedTicks > 0) sortButtonPressedTicks--;
		if (pickupModeButtonPressedTicks > 0) pickupModeButtonPressedTicks--;
		if (client.player == null || client.gameMode == null) {
			resetVirtualCursor();
			return;
		}
		if (client.player.getAbilities().instabuild) {
			resetVirtualCursor();
			observedStackableCounts.clear();
			observedStackableCountsInitialized = false;
			return;
		}
		ensureContext(client);
		if (tickPendingSwitch(client)) return;
		Inventory inventory = client.player.getInventory();
		if (!BehaviorPolicy.isVirtual()) {
			resetVirtualCursor();
			// On a remote server the server owns the actual pickup insertion. Mirror
			// IPN's multiplayer locked-slot keeper: remember which physical hotbar
			// slots were empty, then immediately move newly-created ordinary stacks
			// into a known-empty storage slot on the next client tick. Existing
			// matching hotbar stacks are never part of the empty-slot baseline, so
			// they are still allowed to fill normally in Aggressive mode.
			reconcileRemotePhysicalPickup(client, inventory);
			return;
		}
		// Physical-mode snapshots must not leak into a later mode switch.
		if (physicalPickupSnapshotInitialized) resetPhysicalPickupSnapshot();
		// Inventory mods implement a single visible move as a sequence of clicks and
		// synchronisation packets. Keep the baseline current while a screen is open,
		// but never mistake those temporary count changes for a newly collected item.
		updateReceivedStackableAssignments(inventory, client.gui.screen() == null);
		if (clearOffhandAfterSwap && swapCooldown == 0) {
			AbstractContainerMenu activeMenu = client.gui.screen() instanceof AbstractContainerScreen<?> screen
					? screen.getMenu() : client.player.inventoryMenu;
			if (movePhysicalOffhandToStorage(client, activeMenu, inventory)) return;
			clearOffhandAfterSwap = false;
		}
		if (offhandCleanupPending && swapCooldown == 0 && client.gui.screen() instanceof InventoryScreen) {
			moveAssignedOffhandStacksOutOfHotbar(client);
			return;
		}
		if (client.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
			// Offhand assignments are changed by explicit GUI placement/swap-hands
			// actions. Do not infer them from physical slot 40 here; that transient state
			// is what allowed one profile's offhand item to leak into another profile.
			if (cleanupStackableHotbarWhileMenuOpen(client, containerScreen.getMenu(), inventory)) return;
			return;
		}
		if (client.gui.screen() != null) {
			resetVirtualCursor();
			return;
		}
		resetVirtualCursor();
		if (offhandCleanupPending && swapCooldown == 0) {
			moveAssignedOffhandStacksOutOfHotbar(client);
			return;
		}
		if (swapCooldown > 0) return;

		for (int slot = 0; slot < 9; slot++) {
			ItemStack physical = inventory.getItem(slot);
			Item suppressedItem = suppressedHotbarAssignments[slot];
			if (suppressedItem != null) {
				if (suppressedHotbarAssignmentTicks[slot] > 0) suppressedHotbarAssignmentTicks[slot]--;
				if (!physical.isEmpty() && !physical.is(suppressedItem)) {
					suppressedHotbarAssignments[slot] = null;
					suppressedHotbarAssignmentTicks[slot] = 0;
				} else if (suppressedHotbarAssignmentTicks[slot] == 0) {
					suppressedHotbarAssignments[slot] = null;
				} else {
					continue;
				}
			}
			if (!physical.isEmpty() && physical.getMaxStackSize() > 1) {
				int existingSlot = findAssignedHotbarSlot(physical.getItem());
				if (existingSlot < 0 || existingSlot == slot) assign(slot, physical);
				if (slot != inventory.getSelectedSlot() || (existingSlot >= 0 && existingSlot != slot)) {
					if (!restoreMaterializedSource(client, client.player.inventoryMenu, slot)) {
						client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, 36 + slot, 0, ContainerInput.QUICK_MOVE, client.player);
						clearMaterializedSource(slot);
					}
					swapCooldown = 2;
					return;
				}
			} else if (!physical.isEmpty()) {
				Item assignedHere = getAssignedItem(slot);
				if (assignedHere != null && !physical.is(assignedHere)) {
					int freeHotbarSlot = findFreePhysicalHotbarSlot(inventory, slot);
					if (freeHotbarSlot >= 0) {
						// The world-pickup insertion code can only see the real vanilla hotbar,
						// so an empty physical slot may still be occupied by a virtual
						// assignment. Preserve that virtual assignment and move the newly
						// received tool/weapon/armor piece to the next genuinely free
						// hotbar position using a normal vanilla SWAP operation.
						assign(freeHotbarSlot, physical);
						client.gameMode.handleContainerInput(
								client.player.inventoryMenu.containerId,
								36 + slot,
								freeHotbarSlot,
								ContainerInput.SWAP,
								client.player
						);
						swapCooldown = 2;
						return;
					}
					// If every other hotbar position is already reserved, do not destroy
					// the existing virtual assignment. Leave the physical item in place
					// until a position becomes available instead of replacing the user's
					// configured slot.
					continue;
				}
				assign(slot, physical);
			}
		}

		int selected = inventory.getSelectedSlot();
		Item assignedItem = getAssignedItem(selected);
		if (assignedItem != null && !inventory.getSelectedItem().is(assignedItem)) {
			int menuSlot = findBestSourceMenuSlot(client.player, assignedItem, getAssignedSignature(config.activeProfile, selected));
			if (menuSlot < 0) {
				if (inventory.getSelectedItem().isEmpty() && !isLocked(selected)) clear(selected);
			} else {
				rememberMaterializedSource(client.player, selected, menuSlot);
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, menuSlot, selected, ContainerInput.SWAP, client.player);
				swapCooldown = 2;
				return;
			}
		}

		ItemStack offhand = inventory.getItem(40);
		Item assignedOffhand = getAssignedOffhandItem();

		// The offhand is intentionally physical: keep a complete normal vanilla
		// stack in slot 40 at all times. Never infer a profile assignment from the
		// physical slot here, though; explicit placement/swap input owns assignment
		// changes and prevents cross-profile contamination.
		if (assignedOffhand != null && offhand.isEmpty()) {
			int menuSlot = findBestSourceMenuSlot(client.player, assignedOffhand);
			if (menuSlot >= 0) {
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, menuSlot, 40, ContainerInput.SWAP, client.player);
				swapCooldown = 2;
			}
		}
	}

	private static int findFreePhysicalHotbarSlot(Inventory inventory, int excludedSlot) {
		for (int candidate = 0; candidate < 9; candidate++) {
			if (candidate == excludedSlot || isLocked(candidate)) continue;
			if (getAssignedItem(candidate) != null) continue;
			if (!inventory.getItem(candidate).isEmpty()) continue;
			return candidate;
		}
		return -1;
	}

	private static boolean cleanupStackableHotbarWhileMenuOpen(Minecraft client, AbstractContainerMenu menu, Inventory inventory) {
		if (client.gameMode == null || client.player == null) return false;
		ExtraStorageInventory extra = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();
		for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
			ItemStack stack = inventory.getItem(hotbarSlot);
			if (stack.isEmpty() || stack.getMaxStackSize() <= 1) continue;
			// A stackable hotbar entry is only an assignment while a menu is open. Keep an
			// existing assignment in its original position; otherwise record this slot,
			// then return the real stack to backing storage. This also removes duplicate
			// physical stacks without moving the virtual entry between hotbar positions.
			int existingSlot = findAssignedHotbarSlot(stack.getItem());
			if (existingSlot < 0 || existingSlot == hotbarSlot) assign(hotbarSlot, stack);
			if (restoreMaterializedSource(client, menu, hotbarSlot)) {
				swapCooldown = 2;
				return true;
			}
			Slot emptyStorage = null;
			for (Slot slot : menu.slots) {
				boolean mainStorage = slot.container == inventory && slot.getContainerSlot() >= 9 && slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
				if ((mainStorage || slot instanceof ExtraStorageSlot) && slot.getItem().isEmpty()) {
					emptyStorage = slot;
					break;
				}
			}
			if (emptyStorage != null) {
				client.gameMode.handleContainerInput(menu.containerId, emptyStorage.index, hotbarSlot, ContainerInput.SWAP, client.player);
				clearMaterializedSource(hotbarSlot);
				swapCooldown = 2;
				return true;
			}
		}
		return false;
	}

	public static boolean pickUpVirtualHotbarStackFromStorage(Minecraft client, AbstractContainerMenu menu, int hotbarSlot, int mouseButton) {
		if (client.player == null || client.gameMode == null || !menu.getCarried().isEmpty()
				|| hotbarSlot < 0 || hotbarSlot >= 9 || (mouseButton != 0 && mouseButton != 1)) return false;
		ensureContext(client);
		Item assignedItem = getAssignedItem(hotbarSlot);
		if (assignedItem == null) return false;

		Inventory inventory = client.player.getInventory();
		ExtraStorageInventory extra = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();
		String signature = bindLegacyExactAssignment(inventory, hotbarSlot, assignedItem);
		Slot bestSource = null;
		int bestCount = -1;
		for (Slot slot : menu.slots) {
			boolean mainStorage = slot.container == inventory
					&& slot.getContainerSlot() >= 9
					&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
			if ((mainStorage || slot instanceof ExtraStorageSlot)
					&& matchesAssignment(slot.getItem(), assignedItem, signature)
					&& slot.getItem().getCount() > bestCount) {
				bestSource = slot;
				bestCount = slot.getItem().getCount();
			}
		}
		if (bestSource == null) return false;
		client.gameMode.handleContainerInput(menu.containerId, bestSource.index, mouseButton, ContainerInput.PICKUP, client.player);
		return true;
	}

	public static boolean beginVirtualCursor(Inventory inventory, int hotbarSlot, int mouseButton) {
		ensureCurrentContext();
		if ((mouseButton != 0 && mouseButton != 1) || hotbarSlot < 0 || hotbarSlot >= 9) return false;
		Item assigned = getAssignedItem(hotbarSlot);
		if (assigned == null) return false;
		ItemStack displayed = getDisplayedStack(inventory, hotbarSlot);
		if (displayed.isEmpty() || displayed.getMaxStackSize() <= 1) return false;
		virtualCursorItem = assigned;
		virtualCursorSourceHotbarSlot = hotbarSlot;
		return true;
	}

	public static boolean hasVirtualCursor() {
		return virtualCursorItem != null;
	}

	public static void pressSortButton() {
		sortButtonPressedTicks = 3;
	}

	public static boolean isSortButtonPressed() {
		return sortButtonPressedTicks > 0;
	}

	public static void pressPickupModeButton() {
		pickupModeButtonPressedTicks = 3;
	}

	public static boolean isPickupModeButtonPressed() {
		return pickupModeButtonPressedTicks > 0;
	}

	public static void clearVirtualCursor() {
		if (virtualCursorItem != null
				&& virtualCursorSourceHotbarSlot >= 0
				&& virtualCursorSourceHotbarSlot < 9
				&& getAssignedItem(virtualCursorSourceHotbarSlot) == virtualCursorItem) {
			clear(virtualCursorSourceHotbarSlot);
		}
		resetVirtualCursor();
	}

	private static void resetVirtualCursor() {
		virtualCursorItem = null;
		virtualCursorSourceHotbarSlot = -1;
	}

	public static boolean moveVirtualCursorToHotbar(int hotbarSlot) {
		ensureCurrentContext();
		if (virtualCursorItem == null || hotbarSlot < 0 || hotbarSlot >= 9 || isLocked(hotbarSlot)) return false;
		assign(hotbarSlot, new ItemStack(virtualCursorItem));
		resetVirtualCursor();
		return true;
	}

	public static ItemStack getVirtualCursorDisplayStack(Inventory inventory) {
		if (virtualCursorItem == null) return ItemStack.EMPTY;
		ItemStack representative = new ItemStack(virtualCursorItem);
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(virtualCursorItem)) {
				representative = stack.copyWithCount(1);
				break;
			}
		}
		return representative;
	}

	public static int getVirtualCursorCount(Inventory inventory) {
		if (virtualCursorItem == null) return 0;
		int total = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) if (inventory.getItem(slot).is(virtualCursorItem)) total += inventory.getItem(slot).getCount();
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < 9; slot++) if (extra.getItem(slot).is(virtualCursorItem)) total += extra.getItem(slot).getCount();
		return total;
	}

	/**
	 * Mouse Tweaks decides whether its RMB drag tweak is active by looking at the
	 * menu's carried stack. Layered Hotbars deliberately keeps the real carried
	 * stack empty, so its compatibility mixin redirects ONLY Mouse Tweaks' read
	 * here. This representative stack is never installed into the menu and is
	 * never sent to the server.
	 */
	public static ItemStack getMouseTweaksCarriedStack(AbstractContainerMenu menu) {
		if (virtualCursorItem == null) return menu.getCarried();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return menu.getCarried();
		ItemStack representative = getVirtualCursorDisplayStack(minecraft.player.getInventory());
		if (representative.isEmpty()) return menu.getCarried();
		int virtualCount = getVirtualCursorCount(minecraft.player.getInventory());
		if (virtualCount <= 0) return ItemStack.EMPTY;
		int count = Math.min(virtualCount, representative.getMaxStackSize());
		return representative.copyWithCount(count);
	}

	public static boolean placeOneFromVirtualCursor(Minecraft client, AbstractContainerMenu menu, Slot target, int mouseButton) {
		return placeOneFromVirtualCursor(client, menu, target, mouseButton, java.util.Set.of(target.index));
	}

	public static boolean placeOneFromVirtualCursor(Minecraft client, AbstractContainerMenu menu, Slot target, int mouseButton,
			java.util.Collection<Integer> excludedTargetSlots) {
		if (virtualCursorItem == null || client.player == null || client.gameMode == null
				|| target == null || (mouseButton != 0 && mouseButton != 1)) return false;
		if (!isVirtualCursorTarget(client.player, target)) return false;
		java.util.HashSet<Integer> excluded = new java.util.HashSet<>(excludedTargetSlots);
		excluded.add(target.index);
		return placeVirtualCursorAmount(client, menu, target, 1, excluded) >= 0;
	}

	public static boolean canUseVirtualCursorTarget(Player player, Slot target) {
		return virtualCursorItem != null && player != null && target != null && isVirtualCursorTarget(player, target);
	}


	public static boolean redistributeVirtualCursor(Minecraft client, AbstractContainerMenu menu,
			java.util.Collection<Integer> targetSlotIndexes, java.util.Map<Integer, Integer> baselineCounts) {
		if (virtualCursorItem == null || client.player == null || client.gameMode == null
				|| targetSlotIndexes == null || targetSlotIndexes.isEmpty()) return false;

		Inventory inventory = client.player.getInventory();
		ExtraStorageInventory extra = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();
		ItemStack one = getVirtualCursorDisplayStack(inventory).copyWithCount(1);
		if (one.isEmpty()) return false;

		java.util.LinkedHashSet<Integer> requested = new java.util.LinkedHashSet<>(targetSlotIndexes);
		java.util.ArrayList<Slot> targets = new java.util.ArrayList<>();
		java.util.HashMap<Integer, Integer> baselineByIndex = new java.util.HashMap<>();
		for (int index : requested) {
			if (index < 0 || index >= menu.slots.size()) continue;
			Slot slot = menu.slots.get(index);
			if (!isVirtualCursorTarget(client.player, slot) || !slot.mayPlace(one)) continue;
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, one)) continue;
			int baseline = baselineCounts == null ? 0 : Math.max(0, baselineCounts.getOrDefault(index, 0));
			baseline = Math.min(baseline, stack.isEmpty() ? baseline : stack.getCount());
			targets.add(slot);
			baselineByIndex.put(index, baseline);
		}
		if (targets.isEmpty()) return true;

		// The virtual pool is made of two parts:
		//  1) every matching stack still living in the player's backing storage, and
		//  2) only the items THIS drag already placed into selected destination slots.
		//
		// The second part is why chest dragging can rebalance 200 -> 100/100 ->
		// 64/64/64/8 -> 50/50/50/50 instead of "losing" each 64 as soon as it
		// enters the chest. Baselines keep pre-existing chest contents out of that pool.
		int virtualPool = 0;
		for (Slot slot : menu.slots) {
			boolean mainStorage = slot.container == inventory
					&& slot.getContainerSlot() >= 9
					&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
			if ((mainStorage || slot instanceof ExtraStorageSlot) && slot.getItem().is(virtualCursorItem)) {
				virtualPool += slot.getItem().getCount();
			}
		}
		for (Slot target : targets) {
			// Player backing slots were already counted above, so only add selected
			// destinations that are outside the backing inventory (chests/stations/etc.).
			boolean mainStorage = target.container == inventory
					&& target.getContainerSlot() >= 9
					&& target.getContainerSlot() < Inventory.INVENTORY_SIZE;
			if (mainStorage || target instanceof ExtraStorageSlot) continue;
			int current = target.getItem().is(virtualCursorItem) ? target.getItem().getCount() : 0;
			virtualPool += Math.max(0, current - baselineByIndex.getOrDefault(target.index, 0));
		}
		if (virtualPool <= 0) return true;

		// Match vanilla LMB quick-craft math. Every eligible slot receives the same
		// floor(total / slotCount) share; the remainder stays on the virtual cursor.
		// Vanilla does NOT hand the remainder out one-by-one, so 200 over 27 slots is
		// 7 in every slot with 11 still held, rather than a mix of 7s and 8s.
		int[] desiredAdded = new int[targets.size()];
		int sharePerSlot = virtualPool / targets.size();
		for (int i = 0; i < targets.size(); i++) {
			Slot target = targets.get(i);
			int baseline = baselineByIndex.getOrDefault(target.index, 0);
			int capacityForDrag = Math.max(0, target.getMaxStackSize(one) - baseline);
			desiredAdded[i] = Math.min(sharePerSlot, capacityForDrag);
		}

		java.util.HashMap<Integer, Integer> desiredFinalByIndex = new java.util.HashMap<>();
		for (int i = 0; i < targets.size(); i++) {
			Slot target = targets.get(i);
			desiredFinalByIndex.put(target.index, baselineByIndex.getOrDefault(target.index, 0) + desiredAdded[i]);
		}

		// Fill each deficit. A source may be normal backing storage, or a previously
		// selected chest slot that is now above its new fair share. That latter case is
		// the actual dynamic rebalance that was missing in the screencast.
		for (Slot target : targets) {
			int desiredFinal = desiredFinalByIndex.get(target.index);
			while ((target.getItem().isEmpty() ? 0 : target.getItem().getCount()) < desiredFinal) {
				Slot source = null;
				int sourceAvailable = 0;
				for (Slot candidate : menu.slots) {
					if (candidate == target || !candidate.getItem().is(virtualCursorItem)) continue;

					Integer selectedDesired = desiredFinalByIndex.get(candidate.index);
					if (selectedDesired != null) {
						int excess = candidate.getItem().getCount() - selectedDesired;
						if (excess > 0) {
							source = candidate;
							sourceAvailable = excess;
							break;
						}
						continue;
					}

					boolean mainStorage = candidate.container == inventory
							&& candidate.getContainerSlot() >= 9
							&& candidate.getContainerSlot() < Inventory.INVENTORY_SIZE;
					if (mainStorage || candidate instanceof ExtraStorageSlot) {
						source = candidate;
						sourceAvailable = candidate.getItem().getCount();
						break;
					}
				}
				if (source == null) break;

				int currentTarget = target.getItem().isEmpty() ? 0 : target.getItem().getCount();
				int needed = desiredFinal - currentTarget;
				int capacity = target.getMaxStackSize(one) - currentTarget;
				int amount = Math.min(needed, Math.min(sourceAvailable, capacity));
				if (amount <= 0 || !moveExactAmount(client, menu, source, target, amount)) break;
			}
		}
		return true;
	}

	private static boolean moveExactAmount(Minecraft client, AbstractContainerMenu menu, Slot source, Slot target, int amount) {
		if (amount <= 0 || source == null || target == null || source == target || source.getItem().isEmpty()) return false;
		int beforeSource = source.getItem().getCount();
		int beforeTarget = target.getItem().isEmpty() ? 0 : target.getItem().getCount();

		client.gameMode.handleContainerInput(menu.containerId, source.index, 0, ContainerInput.PICKUP, client.player);
		for (int i = 0; i < amount && !menu.getCarried().isEmpty(); i++) {
			client.gameMode.handleContainerInput(menu.containerId, target.index, 1, ContainerInput.PICKUP, client.player);
		}
		if (!menu.getCarried().isEmpty()) {
			client.gameMode.handleContainerInput(menu.containerId, source.index, 0, ContainerInput.PICKUP, client.player);
		}

		int afterSource = source.getItem().isEmpty() ? 0 : source.getItem().getCount();
		int afterTarget = target.getItem().isEmpty() ? 0 : target.getItem().getCount();
		return afterSource < beforeSource && afterTarget > beforeTarget;
	}

	public static boolean distributeVirtualCursor(Minecraft client, AbstractContainerMenu menu, java.util.Collection<Integer> targetSlotIndexes) {
		if (virtualCursorItem == null || client.player == null || client.gameMode == null
				|| targetSlotIndexes == null || targetSlotIndexes.isEmpty()) return false;

		java.util.LinkedHashSet<Integer> uniqueIndexes = new java.util.LinkedHashSet<>(targetSlotIndexes);
		java.util.ArrayList<Slot> targets = new java.util.ArrayList<>();
		for (int index : uniqueIndexes) {
			if (index < 0 || index >= menu.slots.size()) continue;
			Slot slot = menu.slots.get(index);
			if (!isVirtualCursorTarget(client.player, slot)) continue;
			ItemStack targetStack = slot.getItem();
			ItemStack one = getVirtualCursorDisplayStack(client.player.getInventory()).copyWithCount(1);
			if (!slot.mayPlace(one)) continue;
			if (!targetStack.isEmpty() && !ItemStack.isSameItemSameComponents(targetStack, one)) continue;
			if (targetStack.getCount() >= slot.getMaxStackSize(one)) continue;
			targets.add(slot);
		}
		if (targets.isEmpty()) return true;

		java.util.Set<Integer> excludedSources = new java.util.HashSet<>();
		for (Slot target : targets) excludedSources.add(target.index);
		int available = countVirtualCursorSources(client.player, menu, excludedSources);
		if (available <= 0) return true;

		int[] additions = new int[targets.size()];
		int[] finalCounts = new int[targets.size()];
		int[] maxCounts = new int[targets.size()];
		ItemStack one = getVirtualCursorDisplayStack(client.player.getInventory()).copyWithCount(1);
		for (int i = 0; i < targets.size(); i++) {
			ItemStack stack = targets.get(i).getItem();
			finalCounts[i] = stack.isEmpty() ? 0 : stack.getCount();
			maxCounts[i] = targets.get(i).getMaxStackSize(one);
		}

		for (int remaining = available; remaining > 0; remaining--) {
			int best = -1;
			int bestCount = Integer.MAX_VALUE;
			for (int i = 0; i < targets.size(); i++) {
				if (finalCounts[i] >= maxCounts[i]) continue;
				if (finalCounts[i] < bestCount) {
					best = i;
					bestCount = finalCounts[i];
				}
			}
			if (best < 0) break;
			finalCounts[best]++;
			additions[best]++;
		}

		for (int i = 0; i < targets.size(); i++) {
			if (additions[i] <= 0) continue;
			placeVirtualCursorAmount(client, menu, targets.get(i), additions[i], excludedSources);
		}
		return true;
	}

	private static boolean isVirtualCursorTarget(Player player, Slot target) {
		Inventory inventory = player.getInventory();
		ExtraStorageInventory extra = ((ExtraStorageAccess)player).betterHotbars$getExtraStorage();
		if (target.container == inventory) {
			int inventorySlot = target.getContainerSlot();
			return inventorySlot >= 9 && inventorySlot < Inventory.INVENTORY_SIZE;
		}
		return target instanceof ExtraStorageSlot || target.container != inventory;
	}

	private static int countVirtualCursorSources(Player player, AbstractContainerMenu menu, java.util.Set<Integer> excludedMenuSlots) {
		Inventory inventory = player.getInventory();
		ExtraStorageInventory extra = ((ExtraStorageAccess)player).betterHotbars$getExtraStorage();
		int total = 0;
		for (Slot slot : menu.slots) {
			if (excludedMenuSlots.contains(slot.index)) continue;
			boolean mainStorage = slot.container == inventory
					&& slot.getContainerSlot() >= 9
					&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
			if ((mainStorage || slot instanceof ExtraStorageSlot) && slot.getItem().is(virtualCursorItem)) {
				total += slot.getItem().getCount();
			}
		}
		return total;
	}

	private static int placeVirtualCursorAmount(Minecraft client, AbstractContainerMenu menu, Slot target, int requestedAmount,
			java.util.Set<Integer> excludedMenuSlots) {
		if (virtualCursorItem == null || requestedAmount <= 0) return 0;
		Inventory inventory = client.player.getInventory();
		ExtraStorageInventory extra = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();
		ItemStack one = getVirtualCursorDisplayStack(inventory).copyWithCount(1);
		ItemStack targetStack = target.getItem();
		if (!target.mayPlace(one)
				|| (!targetStack.isEmpty() && !ItemStack.isSameItemSameComponents(targetStack, one))
				|| targetStack.getCount() >= target.getMaxStackSize(one)) return 0;

		int placed = 0;
		while (placed < requestedAmount) {
			Slot source = null;
			for (Slot slot : menu.slots) {
				if (excludedMenuSlots.contains(slot.index)) continue;
				boolean mainStorage = slot.container == inventory
						&& slot.getContainerSlot() >= 9
						&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
				if ((mainStorage || slot instanceof ExtraStorageSlot) && slot.getItem().is(virtualCursorItem)) {
					source = slot;
					break;
				}
			}
			if (source == null) break;

			int capacity = target.getMaxStackSize(one) - target.getItem().getCount();
			if (capacity <= 0) break;
			int amountThisSource = Math.min(requestedAmount - placed, Math.min(source.getItem().getCount(), capacity));
			if (amountThisSource <= 0) break;

			client.gameMode.handleContainerInput(menu.containerId, source.index, 0, ContainerInput.PICKUP, client.player);
			int before = target.getItem().isEmpty() ? 0 : target.getItem().getCount();
			for (int i = 0; i < amountThisSource; i++) {
				client.gameMode.handleContainerInput(menu.containerId, target.index, 1, ContainerInput.PICKUP, client.player);
			}
			if (!menu.getCarried().isEmpty()) {
				client.gameMode.handleContainerInput(menu.containerId, source.index, 0, ContainerInput.PICKUP, client.player);
			}
			int after = target.getItem().isEmpty() ? 0 : target.getItem().getCount();
			int moved = Math.max(0, after - before);
			placed += moved;
			if (moved <= 0) break;
		}
		return placed;
	}

	public static boolean assignToFirstAvailableVirtualHotbarSlot(ItemStack stack) {
		ensureCurrentContext();
		if (stack.isEmpty() || stack.getMaxStackSize() <= 1) return false;
		// Shift-clicking another stack of an item that is already represented should
		// not create a duplicate physical hotbar stack.
		if (findAssignedHotbarSlot(stack.getItem()) >= 0) return true;
		for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
			if (getAssignedItem(hotbarSlot) == null) {
				assign(hotbarSlot, stack);
				return true;
			}
		}
		// Every virtual position is occupied, so consume the attempted transfer
		// instead of allowing vanilla to pile items into the first physical slot.
		return true;
	}

	public static boolean moveCarriedStackToVirtualHotbar(Minecraft client, AbstractContainerMenu menu, int hotbarSlot, int mouseButton) {
		if (client.player == null || client.gameMode == null || mouseButton != 0
				|| hotbarSlot < 0 || hotbarSlot >= 9 || isLocked(hotbarSlot)) return false;
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty() || carried.getMaxStackSize() <= 1) return false;

		Inventory inventory = client.player.getInventory();
		ExtraStorageInventory extra = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();
		Slot destination = null;
		for (Slot slot : menu.slots) {
			boolean mainStorage = slot.container == inventory
					&& slot.getContainerSlot() >= 9
					&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
			if ((mainStorage || slot instanceof ExtraStorageSlot) && slot.getItem().isEmpty()) {
				destination = slot;
				break;
			}
		}
		if (destination == null) return false;

		// Assigning a stackable item automatically clears the same item from its old
		// virtual position. Put the real cursor stack back into the empty backing slot.
		assign(hotbarSlot, carried);
		client.gameMode.handleContainerInput(menu.containerId, destination.index, 0, ContainerInput.PICKUP, client.player);
		return true;
	}

	private static ItemStack getMatchingCarriedStack(Inventory inventory, Item item, String signature) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != inventory.player || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) return ItemStack.EMPTY;
		ItemStack carried = screen.getMenu().getCarried();
		return matchesAssignment(carried, item, signature) ? carried : ItemStack.EMPTY;
	}

	public static void prepareOffhandSwap(Player player) {
		prepareOffhandSwap(player, player.getInventory().getSelectedSlot());
	}

	public static void prepareOffhandSwap(Player player, int hotbarSlot) {
		ensureCurrentContext();
		Inventory inventory = player.getInventory();
		if (hotbarSlot < 0 || hotbarSlot >= 9) return;
		ItemStack selectedStack = inventory.getItem(hotbarSlot);
		ItemStack offhandStack = inventory.getItem(40);
		if (!selectedStack.isEmpty() && selectedStack.getMaxStackSize() > 1 && ItemStack.isSameItemSameComponents(selectedStack, offhandStack)) {
			config.offhandAssignments[config.activeProfile] = null;
			config.offhandAssignmentExplicit[config.activeProfile] = false;
			setAssignmentFromStack(config.activeProfile, hotbarSlot, selectedStack);
			clearOffhandAfterSwap = true;
			offhandCleanupPending = false;
		} else {
			config.offhandAssignments[config.activeProfile] = selectedStack.isEmpty() ? null : getItemId(selectedStack);
			config.offhandAssignmentExplicit[config.activeProfile] = !selectedStack.isEmpty();
			if (offhandStack.isEmpty()) clearAssignment(config.activeProfile, hotbarSlot);
			else setAssignmentFromStack(config.activeProfile, hotbarSlot, offhandStack);
			offhandCleanupPending = !selectedStack.isEmpty();
		}
		if (offhandStack.isEmpty()) config.locked[config.activeProfile][hotbarSlot] = false;
		swapCooldown = 4;
		save();
	}

	private static boolean movePhysicalOffhandToStorage(Minecraft client, AbstractContainerMenu menu, Inventory inventory) {
		if (inventory.getItem(40).isEmpty() || client.gameMode == null || client.player == null) return false;
		ExtraStorageInventory extra = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();
		Slot offhandSlot = null;
		Slot emptyStorage = null;
		for (Slot slot : menu.slots) {
			if (slot.container == inventory && slot.getContainerSlot() == 40) offhandSlot = slot;
			boolean mainStorage = slot.container == inventory && slot.getContainerSlot() >= 9 && slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
			if (emptyStorage == null && (mainStorage || slot instanceof ExtraStorageSlot) && slot.getItem().isEmpty()) emptyStorage = slot;
		}
		if (offhandSlot == null || emptyStorage == null) return false;
		client.gameMode.handleContainerInput(menu.containerId, emptyStorage.index, 40, ContainerInput.SWAP, client.player);
		clearOffhandAfterSwap = false;
		swapCooldown = 2;
		return true;
	}

	public static void setOffhandAssignment(ItemStack stack) {
		ensureCurrentContext();
		String assignment = stack.isEmpty() ? null : getItemId(stack);
		boolean explicit = assignment != null;
		if (!java.util.Objects.equals(config.offhandAssignments[config.activeProfile], assignment)
				|| config.offhandAssignmentExplicit[config.activeProfile] != explicit) {
			config.offhandAssignments[config.activeProfile] = assignment;
			config.offhandAssignmentExplicit[config.activeProfile] = explicit;
			offhandCleanupPending = assignment != null;
			save();
		}
	}

	public static boolean transferVirtualHotbarStackToOpenContainer(Minecraft client, AbstractContainerMenu menu, int hotbarSlot) {
		if (client.player == null || client.gameMode == null || hotbarSlot < 0 || hotbarSlot >= 9) return false;
		ensureContext(client);
		Item assignedItem = getAssignedItem(hotbarSlot);
		if (assignedItem == null) return false;

		Inventory inventory = client.player.getInventory();
		ExtraStorageInventory extraStorage = ((ExtraStorageAccess)client.player).betterHotbars$getExtraStorage();
		java.util.ArrayList<Integer> sourceMenuSlots = new java.util.ArrayList<>();
		for (Slot slot : menu.slots) {
			boolean playerStorage = slot.container == inventory
					&& slot.getContainerSlot() >= 0
					&& slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
			boolean addedStorage = slot instanceof ExtraStorageSlot;
			if ((playerStorage || addedStorage)
					&& matchesAssignment(slot.getItem(), assignedItem, getAssignedSignature(config.activeProfile, hotbarSlot))) sourceMenuSlots.add(slot.index);
		}
		if (sourceMenuSlots.isEmpty()) return false;
		for (int sourceMenuSlot : sourceMenuSlots) {
			client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, 0, ContainerInput.QUICK_MOVE, client.player);
		}
		swapCooldown = 2;
		return true;
	}

	private static void syncOffhandAssignmentFromPhysical(Inventory inventory) {
		ItemStack offhand = inventory.getItem(40);
		if (offhand.isEmpty()) return;
		String assignment = getItemId(offhand);
		if (!java.util.Objects.equals(config.offhandAssignments[config.activeProfile], assignment)
				|| !config.offhandAssignmentExplicit[config.activeProfile]) {
			config.offhandAssignments[config.activeProfile] = assignment;
			config.offhandAssignmentExplicit[config.activeProfile] = true;
			offhandCleanupPending = true;
			save();
		}
	}

	private static void moveAssignedOffhandStacksOutOfHotbar(Minecraft client) {
		Item offhandItem = getAssignedOffhandItem();
		offhandCleanupPending = false;
		if (offhandItem == null || client.player == null || client.gameMode == null) return;

		Inventory inventory = client.player.getInventory();
		boolean changed = false;
		for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
			Item assigned = getAssignedItem(hotbarSlot);
			ItemStack physical = inventory.getItem(hotbarSlot);
			if (assigned == offhandItem) {
				clearAssignment(config.activeProfile, hotbarSlot);
				config.locked[config.activeProfile][hotbarSlot] = false;
				changed = true;
			}
			if (physical.is(offhandItem)) {
				client.gameMode.handleContainerInput(
						client.player.inventoryMenu.containerId,
						36 + hotbarSlot,
						0,
						ContainerInput.QUICK_MOVE,
						client.player
				);
				changed = true;
			}
		}
		if (changed) {
			swapCooldown = 2;
			save();
		}
	}

	private static void assignOffhand(ItemStack stack) {
		if (stack.isEmpty()) return;
		String id = getItemId(stack);
		if (!id.equals(config.offhandAssignments[config.activeProfile])
				|| !config.offhandAssignmentExplicit[config.activeProfile]) {
			config.offhandAssignments[config.activeProfile] = id;
			config.offhandAssignmentExplicit[config.activeProfile] = true;
			save();
		}
	}

	private static String getItemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static Item getAssignedItem(int hotbarSlot) {
		return getAssignedItem(config.activeProfile, hotbarSlot);
	}

	private static int findAssignedHotbarSlot(Item item) {
		for (int slot = 0; slot < 9; slot++) if (getAssignedItem(slot) == item) return slot;
		return -1;
	}

	private static Item getAssignedItem(int profile, int hotbarSlot) {
		String assignment = config.assignments[profile][hotbarSlot];
		Identifier id = assignment == null ? null : Identifier.tryParse(assignment);
		return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
	}

	private static String getAssignedSignature(int profile, int hotbarSlot) {
		return config.assignmentSignatures[profile][hotbarSlot];
	}

	private static boolean propagateEvolvedNonStackableSignature(String itemId, String oldSignature, String newSignature) {
		if (itemId == null
				|| oldSignature == null
				|| java.util.Objects.equals(oldSignature, newSignature)) return false;

		boolean changed = false;
		for (int profile = 0; profile < config.assignments.length; profile++) {
			for (int slot = 0; slot < config.assignments[profile].length; slot++) {
				if (itemId.equals(config.assignments[profile][slot])
						&& oldSignature.equals(config.assignmentSignatures[profile][slot])) {
					config.assignmentSignatures[profile][slot] = newSignature;
					changed = true;
				}
			}
		}
		return changed;
	}

	private static void setAssignmentFromStack(int profile, int hotbarSlot, ItemStack stack) {
		config.assignments[profile][hotbarSlot] = getItemId(stack);
		config.assignmentSignatures[profile][hotbarSlot] = getExactStackSignature(stack);
	}

	private static void clearAssignment(int profile, int hotbarSlot) {
		config.assignments[profile][hotbarSlot] = null;
		config.assignmentSignatures[profile][hotbarSlot] = null;
	}

	private static boolean matchesAssignment(ItemStack stack, Item item, String signature) {
		return stack.is(item) && (signature == null || signature.equals(getExactStackSignature(stack)));
	}

	private static String getExactStackSignature(ItemStack stack) {
		if (stack.isEmpty() || stack.getMaxStackSize() > 1) return null;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return stack.getComponentsPatch().toString();
		ItemStack normalized = stack.copyWithCount(1);
		return ItemStack.CODEC
				.encodeStart(RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess()), normalized)
				.result()
				.map(JsonElement::toString)
				.orElseGet(() -> stack.getComponentsPatch().toString());
	}

	private static Item getAssignedOffhandItem() {
		return getAssignedOffhandItem(config.activeProfile);
	}

	private static Item getAssignedOffhandItem(int profile) {
		String assignment = config.offhandAssignments[profile];
		Identifier id = assignment == null ? null : Identifier.tryParse(assignment);
		return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
	}

	private static int findBestSourceMenuSlot(Player player, Item item, String signature) {
		Inventory inventory = player.getInventory();
		int bestMenuSlot = -1;
		int bestCount = -1;
		for (int slot = 9; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (matchesAssignment(stack, item, signature) && stack.getCount() > bestCount) {
				bestMenuSlot = slot;
				bestCount = stack.getCount();
			}
		}
		ExtraStorageInventory extra = ((ExtraStorageAccess)player).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = extra.getItem(slot);
			if (matchesAssignment(stack, item, signature) && stack.getCount() > bestCount) {
				bestMenuSlot = 46 + slot;
				bestCount = stack.getCount();
			}
		}
		return bestMenuSlot;
	}

	private static int findBestSourceMenuSlot(Player player, Item item) {
		return findBestSourceMenuSlot(player, item, null);
	}

	private static void rememberMaterializedSource(Player player, int hotbarSlot, int sourceMenuSlot) {
		if (hotbarSlot < 0 || hotbarSlot >= 9 || sourceMenuSlot < 0 || sourceMenuSlot >= player.inventoryMenu.slots.size()) return;
		Slot source = player.inventoryMenu.slots.get(sourceMenuSlot);
		materializedSourceContainers[hotbarSlot] = source.container;
		materializedSourceSlots[hotbarSlot] = source instanceof ExtraStorageSlot extraSlot
				? extraSlot.getLogicalInventorySlot()
				: source.getContainerSlot();
	}

	private static boolean restoreMaterializedSource(Minecraft client, AbstractContainerMenu menu, int hotbarSlot) {
		if (hotbarSlot < 0 || hotbarSlot >= 9 || materializedSourceContainers[hotbarSlot] == null) return false;
		for (Slot slot : menu.slots) {
			int logicalSlot = slot instanceof ExtraStorageSlot extraSlot
					? extraSlot.getLogicalInventorySlot()
					: slot.getContainerSlot();
			if (slot.container == materializedSourceContainers[hotbarSlot]
					&& logicalSlot == materializedSourceSlots[hotbarSlot]
					&& slot.getItem().isEmpty()) {
				client.gameMode.handleContainerInput(menu.containerId, slot.index, hotbarSlot, ContainerInput.SWAP, client.player);
				clearMaterializedSource(hotbarSlot);
				return true;
			}
		}
		return false;
	}

	private static void clearMaterializedSource(int hotbarSlot) {
		if (hotbarSlot < 0 || hotbarSlot >= 9) return;
		materializedSourceContainers[hotbarSlot] = null;
		materializedSourceSlots[hotbarSlot] = -1;
	}

	private static void clearMaterializedSources() {
		java.util.Arrays.fill(materializedSourceContainers, null);
		java.util.Arrays.fill(materializedSourceSlots, -1);
	}

	private static int[] getEmptyStorageMenuSlots(Player player) {
		java.util.ArrayList<Integer> slots = new java.util.ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int slot = 9; slot < Inventory.INVENTORY_SIZE; slot++) if (inventory.getItem(slot).isEmpty()) slots.add(slot);
		if (BehaviorPolicy.hasExtraRow()) {
			ExtraStorageInventory extra = ((ExtraStorageAccess)player).betterHotbars$getExtraStorage();
			for (int slot = 0; slot < 9; slot++) if (extra.getItem(slot).isEmpty()) slots.add(46 + slot);
		}
		return slots.stream().mapToInt(Integer::intValue).toArray();
	}

	public static ItemStack getDisplayedStack(Inventory inventory, int hotbarSlot) {
		if (!BehaviorPolicy.isVirtual()) return inventory.getItem(hotbarSlot);
		ensureCurrentContext();
		Item item = getAssignedItem(hotbarSlot);
		if (virtualCursorItem != null
				&& hotbarSlot == virtualCursorSourceHotbarSlot
				&& item == virtualCursorItem) return ItemStack.EMPTY;
		if (item == null && !inventory.getItem(hotbarSlot).isEmpty()) item = inventory.getItem(hotbarSlot).getItem();
		if (item == null) return ItemStack.EMPTY;
		String signature = bindLegacyExactAssignment(inventory, hotbarSlot, item);
		ItemStack representative = ItemStack.EMPTY;
		int total = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (matchesAssignment(stack, item, signature)) {
				if (representative.isEmpty()) representative = stack.copy();
				total += stack.getCount();
			}
		}
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = extra.getItem(slot);
			if (matchesAssignment(stack, item, signature)) {
				if (representative.isEmpty()) representative = stack.copy();
				total += stack.getCount();
			}
		}
		ItemStack carried = getMatchingCarriedStack(inventory, item, signature);
		if (!carried.isEmpty()) {
			if (representative.isEmpty()) representative = carried.copy();
			total += carried.getCount();
		}
		if (representative.isEmpty()) return isLocked(hotbarSlot) ? new ItemStack(item) : ItemStack.EMPTY;
		representative.setCount(signature == null ? Math.max(1, total) : 1);
		return representative;
	}

	private static String bindLegacyExactAssignment(Inventory inventory, int hotbarSlot, Item item) {
		String signature = getAssignedSignature(config.activeProfile, hotbarSlot);
		if (signature != null) return signature;
		ItemStack candidate = ItemStack.EMPTY;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE && candidate.isEmpty(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item) && stack.getMaxStackSize() <= 1) candidate = stack;
		}
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < 9 && candidate.isEmpty(); slot++) {
			ItemStack stack = extra.getItem(slot);
			if (stack.is(item) && stack.getMaxStackSize() <= 1) candidate = stack;
		}
		if (!candidate.isEmpty()) {
			signature = getExactStackSignature(candidate);
			config.assignmentSignatures[config.activeProfile][hotbarSlot] = signature;
			save();
		}
		return signature;
	}

	public static int getAvailableCount(Inventory inventory, int hotbarSlot) {
		ensureCurrentContext();
		Item item = getAssignedItem(hotbarSlot);
		if (item == null && !inventory.getItem(hotbarSlot).isEmpty()) item = inventory.getItem(hotbarSlot).getItem();
		if (item == null) return -1;
		String signature = bindLegacyExactAssignment(inventory, hotbarSlot, item);
		int total = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) if (matchesAssignment(inventory.getItem(slot), item, signature)) total += inventory.getItem(slot).getCount();
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < 9; slot++) if (matchesAssignment(extra.getItem(slot), item, signature)) total += extra.getItem(slot).getCount();
		total += getMatchingCarriedStack(inventory, item, signature).getCount();
		return total;
	}

	public static boolean hasSelectedVirtualAssignment(Player player) {
		if (!BehaviorPolicy.isVirtual()) return false;
		ensureCurrentContext();
		return player != null && getAssignedItem(player.getInventory().getSelectedSlot()) != null;
	}

	public static ItemStack getDisplayedOffhandStack(Inventory inventory) {
		if (!BehaviorPolicy.isVirtual()) return inventory.getItem(40);
		ensureCurrentContext();
		Item item = getAssignedOffhandItem();
		if (item == null) return ItemStack.EMPTY;
		ItemStack representative = ItemStack.EMPTY;
		int total = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				if (representative.isEmpty()) representative = stack.copy();
				total += stack.getCount();
			}
		}
		ItemStack physicalOffhand = inventory.getItem(40);
		if (physicalOffhand.is(item)) {
			if (representative.isEmpty()) representative = physicalOffhand.copy();
			total += physicalOffhand.getCount();
		}
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = extra.getItem(slot);
			if (stack.is(item)) {
				if (representative.isEmpty()) representative = stack.copy();
				total += stack.getCount();
			}
		}
		if (representative.isEmpty() || total <= 0) return ItemStack.EMPTY;
		representative.setCount(total);
		return representative;
	}

	public static int getOffhandAvailableCount(Inventory inventory) {
		Item item = getAssignedOffhandItem();
		if (item == null) return -1;
		int total = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) if (inventory.getItem(slot).is(item)) total += inventory.getItem(slot).getCount();
		if (inventory.getItem(40).is(item)) total += inventory.getItem(40).getCount();
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		for (int slot = 0; slot < 9; slot++) if (extra.getItem(slot).is(item)) total += extra.getItem(slot).getCount();
		return total;
	}

	public static int getActiveProfile() {
		ensureCurrentContext();
		return config.activeProfile;
	}

	/** Read-only stack used by Vanilla+'s inventory-side profile preview. */
	public static ItemStack getPhysicalProfilePreviewStack(Inventory inventory, int profile, int hotbarSlot) {
		ensureCurrentContext();
		if (profile < 0 || profile >= 3 || hotbarSlot < 0 || hotbarSlot >= 9) return ItemStack.EMPTY;
		if (profile == config.activeProfile) return inventory.getItem(hotbarSlot);
		ExtraStorageInventory extra = ((ExtraStorageAccess)inventory.player).betterHotbars$getExtraStorage();
		return extra.getItem(ExtraStorageInventory.physicalProfileHotbarSlot(profile, hotbarSlot));
	}

	public static boolean isUnavailableLockedStack(Inventory inventory, ItemStack stack) {
		if (!BehaviorPolicy.isVirtual() || stack.isEmpty()) return false;
		Item offhandItem = getAssignedOffhandItem();
		if (offhandItem != null && stack.is(offhandItem) && getOffhandAvailableCount(inventory) == 0) return true;
		for (int slot = 0; slot < 9; slot++) {
			Item assigned = getAssignedItem(slot);
			if (assigned != null
					&& matchesAssignment(stack, assigned, getAssignedSignature(config.activeProfile, slot))
					&& isLocked(slot) && getAvailableCount(inventory, slot) == 0) return true;
		}
		return false;
	}
}
