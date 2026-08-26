---
navigation:
  title: Sky Configurator
  icon: configurator
  parent: tools.md
  position: 1
item_ids:
  - skylogistics:configurator
---

# Sky Configurator

The <ItemLink id="configurator" /> manages lines and quickly copies logistics node settings. Right-click air to open its screen; doing so exits paste mode. The screen lets you switch lines and choose whether the saved settings carry items, fluids, and energy.

The |<, <, >+, and >| buttons select the first, previous, next-or-new, and last line. Edit the line name and press Enter or unfocus it to rename. × only removes this configurator's reference and does not delete other devices. The lower panel lists loaded faces with mode, resources, priority, redstone, coordinates, and dimension.

<RecipeFor id="configurator" fallbackText="The configurator recipe is unavailable." />

Sneak-right-click a Sky Logistics Node while holding the configurator to copy that node's line and resource toggles, then enter paste mode without opening the node screen. A hotbar message shows the copied line name.

While paste mode is active, right-click a Sky Logistics Node to paste the configurator's line and resource toggles onto that node, also without opening the node screen. Paste mode exits when the configurator is no longer held, when the configurator screen is opened, or when you sneak-right-click air or a non-node block.

Right-clicking a machine-facing simple-pipe endpoint with the configurator does not open its screen. Instead, it switches that endpoint directly between extract and insert.

The item, fluid, energy, auto-detect, redstone, priority, and slot controls form the preset applied when a node is placed with the configurator in the offhand.
