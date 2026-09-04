# Layered Hotbars

**Layered Hotbars** expands Minecraft's hotbar into three switchable profiles and provides several inventory modes ranging from vanilla-style physical hotbars to fully virtual, refillable hotbar assignments.

The mod is designed to preserve normal item use and server interaction while making frequently used items easier to access. Depending on the selected mode, it can also add a fourth storage row, remember a separate offhand for each profile, display total stored item counts on virtual slots, and provide built-in inventory sorting.

## Requirements

- Minecraft **26.2**
- Fabric Loader **0.19.3 or newer**
- Fabric API for Minecraft 26.2
- Java **25 or newer**
- Mod Menu is optional and provides an in-game settings screen.

For single-player, installing the mod normally is enough because Minecraft's integrated server loads it automatically.

For multiplayer, install Layered Hotbars on both the client and server when using the advanced hotbar modes. A server without the mod safely falls back to vanilla-compatible behavior when Automatic mode is used.

## Hotbar modes

Hotbar Mode can be changed through **Mod Menu → Layered Hotbars → Configure**. Mode changes that alter the inventory layout take effect after reconnecting or restarting the world.

### Automatic

The recommended default.

- Uses **Full** when the connected server advertises Layered Hotbars support.
- Falls back to **Vanilla** when connecting to a server without Layered Hotbars.
- Avoids mismatched client/server inventory layouts automatically.

### Vanilla

Keeps Minecraft's normal three-row inventory and uses three physical hotbar profiles.

When switching profiles, the outgoing hotbar is moved into ordinary inventory storage and the selected profile is restored. If there is not enough room to store the current hotbar, the switch is cancelled and the game displays **“Not enough storage space to switch hotbars.”**

### Vanilla+

Keeps Minecraft's normal three-row inventory but gives each of the three profiles its own server-backed physical hotbar storage.

Hold the **Stored Hotbar Preview** key while the inventory is open to display all three stored hotbars beside the inventory. The default preview key is **Left Control** and can be rebound in Minecraft's Controls menu.

Vanilla+ requires Layered Hotbars on both the client and server in multiplayer.

### Partial

Uses the three-profile virtual hotbar system while retaining Minecraft's normal three-row inventory.

Stackable hotbar entries act as assignments backed by items stored in the inventory instead of requiring a permanent physical stack in every hotbar slot.

### Full

Uses the complete virtual hotbar system and converts the old physical hotbar row into a **fourth inventory storage row**, giving the player 36 ordinary storage slots behind the virtual hotbar.

Full requires Layered Hotbars on both the client and server in multiplayer.

> **Important:** Empty any Layered Hotbars-only stored rows or profiles before changing to a mode that would hide that storage. The settings screen blocks unsafe mode changes when extra stored items are detected.

## Switching hotbars

There are three hotbar profiles.

- Hold **Alt** and scroll the mouse wheel to cycle through profiles.
- Press **Alt + 1**, **Alt + 2**, or **Alt + 3** to switch directly to a profile.
- While the inventory is open, click one of the profile indicators to switch directly.

In modes that need to move physical items between the hotbar and inventory, switching is cancelled rather than dropping or deleting items when there is insufficient storage space.

## Virtual hotbar assignments

Partial and Full use virtual hotbar assignments for stackable items.

- Open the inventory, hover an item, and press **1–9** to assign that item to the corresponding hotbar slot without manually moving the stack.
- Items can also be placed and moved through the hotbar normally from the inventory screen.
- A stackable virtual slot displays the total matching supply available in its backing storage.
- Selecting a virtual slot supplies a real usable stack when needed so normal Minecraft item interaction continues to work.
- Duplicate assignments within the same profile are prevented.

The old standalone **Assign** and **Clear Assignment** keybinds have been removed. Assignment is handled directly through the inventory/hotbar interactions above.

## Locked slots

In virtual modes, **Alt-click** a hotbar slot in the inventory to lock or unlock its assignment.

A locked assignment is remembered even when its supply reaches zero. Empty locked assignments remain visible with a count of **0**, making it easy to keep a permanent place reserved for an item.

## Offhand profiles

The offhand is remembered separately for each hotbar profile.

Using Minecraft's normal swap-hands action or placing an item into the offhand slot updates the current profile's offhand assignment. Stackable offhand assignments can draw from matching stored items while preserving normal physical offhand behavior for actual use.

## Pickup modes

Pickup Mode can be changed instantly from the button shown beside the inventory or through the Mod Menu settings screen.

### Aggressive

Designed for deliberate inventory organization.

- Tools, weapons, armour, and similar equipment prefer useful hotbar placement.
- Ordinary stackable items prefer storage instead of consuming physical hotbar space.
- In virtual modes, stackable hotbar contents are managed as assignments backed by storage.

### Casual

Designed to require less manual assignment.

- Vanilla and Vanilla+ retain more vanilla-like hotbar-first pickup behavior.
- Partial and Full can automatically assign newly received stackable items to the first suitable free virtual hotbar slot while keeping their real stacks in backing storage.

The selected pickup mode is saved in `config/better-hotbars.properties`.

## Sorting

Layered Hotbars includes a built-in sort button in supported inventory and container screens.

The sorter understands the mod's extended player inventory layout and is designed to work without treating virtual hotbar assignments as ordinary movable stacks.

When Inventory Profiles Next is installed, Layered Hotbars delegates compatible inventory behavior where appropriate rather than drawing conflicting duplicate controls.

## Compatibility

The current implementation contains dedicated compatibility handling for:

- **Inventory Profiles Next (IPN)**
- **Mouse Tweaks**
- **Traveller's Backpack**
- Vanilla inventory and container screens, including chests, crafting stations, furnaces, shulker boxes, villagers, horses, and other standard menus supported by the included GUI replacements.

Creative mode keeps Minecraft's normal hotbar behavior rather than running the virtual survival hotbar system.

## Saved data

General settings are stored in:

```text
config/better-hotbars.properties
```

Virtual hotbar assignments, locks, active profiles, and related per-world/per-server state are stored under:

```text
config/better-hotbars/contexts/
```

This allows different worlds and multiplayer servers to keep independent hotbar layouts.

## Resource-pack support

Layered Hotbars includes extended versions of the relevant inventory/container textures for layouts that add the extra player storage row.

Resource packs can provide compatible extended Minecraft GUI textures using the mod's existing `_bh` override convention. The internal namespace remains `better-hotbars` for compatibility with existing packs and saved configuration data.

## License

See the included `LICENSE` file for the project's license terms.
