---
navigation:
  title: Configurator and Upgrades
  icon: speed_upgrade
  parent: tools.md
  position: 2
item_ids:
  - skylogistics:configurator
  - skylogistics:speed_upgrade
  - skylogistics:dimension_upgrade
  - skylogistics:force_extraction_upgrade
  - skylogistics:ordered_matching_upgrade
---

# Configurator and Upgrades

The Sky Configurator stores the selected line and resource settings. Right-click air to open the configurator screen; right-click a node to open its node screen. Holding the configurator in the offhand while placing a node makes the new node inherit its line and resource toggles.

Sneak-right-click a node with the configurator to copy that node and enter paste mode. While in paste mode, right-click another node to write the stored line and resource toggles to it. Sneak-right-click again, open the configurator screen, or stop holding the configurator to leave paste mode.

Speed upgrades stack in one node upgrade slot. Each card adds one slot check per tick. By default, up to 8 cards raise the base rate from 1 to 9 slots/t; the server can configure this limit. Actual transfer speed also depends on the receivers.

<RecipeFor id="speed_upgrade" fallbackText="The slot parallel upgrade recipe is unavailable." />

Per Slot Ordered Matching works on item extract or insert nodes, while Per Item affects extract nodes only; on an insert node Per Item falls back to normal insertion. Right-click air while holding one to switch between Per Slot and Per Item; Per Slot is the default and the tooltip shows the saved mode. Switching to Per Item clears the order offset, so switching back to Per Slot starts at 0. Extraction still selects the lowest extractable source slot allowed by the filter.

Per Slot follows `local slot + order offset = network position`. Sneak-scroll up/down raises/lowers the offset without changing the hotbar. Positive values skip leading network positions: offset `2` maps slot 0 to position 2. Negative values skip leading local slots: offset `-2` maps slot 2 to position 0. Extract nodes use this to match receiving endpoints; insert nodes reverse the same relation to match sources with inventory slots. Receivers cycle within the remaining positions by default; Server settings can disable that cycle.

Per Item works on extract nodes only. It shares the current item among receivers that can accept it, skipping full or unavailable targets. Any leftovers stay at the source for the next transfer. Receivers with equal priorities still participate separately.

Check the device order in the Sky Configurator. Higher-priority devices come first, and devices with equal priority each keep their own position. Full inventories and filter settings do not change that matching order. Ordered Matching Upgrades affect items only.

<RecipeFor id="ordered_matching_upgrade" fallbackText="The ordered matching upgrade recipe is unavailable." />

Force Extraction Upgrades work only on item extract nodes. For devices allowed by the server, they can move more than one stack at a time, up to the amount the target can accept. Items that cannot be accepted remain at the source. By default, compatible devices from Mekanism Extras are supported. Servers may change the supported devices. Without the required mods or server support for this upgrade, it is hidden from creative inventory and JEI and cannot be made through an offering. The offering uses a Slot Parallel Upgrade on the altar plus 4 Blaze Rods, 4 Magma Creams, 4 Crying Obsidian, and 1 Netherite Scrap on offering tables.

Dimension upgrades also go into node upgrade slots, but only affect extract faces. An extract face with a dimension upgrade can send to same-line insert faces in other loaded dimensions. Insert faces do not need dimension upgrades. This is not a chunk loader; unloaded dimensions or chunks are skipped.

Dimension upgrades are made through a tier 2 sky offering: place a Nether Star on the altar, then place 4 Eyes of Ender and 1 Chora Nectar on offering tables. The ritual takes about 12 seconds, and the upgrade only needs to be installed on the extracting node.
