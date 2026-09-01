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

The Sky Configurator stores a line name, line ID, and resource toggles. Right-click air to open the configurator screen; right-click a node to open its node screen. Holding the configurator in the offhand while placing a node makes the new node inherit its line and resource toggles.

Sneak-right-click a node with the configurator to copy that node and enter paste mode. While in paste mode, right-click another node to write the stored line and resource toggles to it. Sneak-right-click again, open the configurator screen, or stop holding the configurator to leave paste mode.

Speed upgrades stack in one node upgrade slot. Each card adds one slot check per tick. By default, up to 8 cards raise the base rate from 1 to 9 slots/t; the server can configure this limit. They do not increase line capacity.

<RecipeFor id="speed_upgrade" fallbackText="The slot parallel upgrade recipe is unavailable." />

Per Slot Ordered Matching works on item extract or insert nodes, while Per Item affects extract nodes only; on an insert node Per Item falls back to normal insertion. Right-click air while holding one to switch between Per Slot and Per Item; Per Slot is the default and the tooltip shows the saved mode. Switching to Per Item clears the order offset, so switching back to Per Slot starts at 0. Extraction still selects the lowest extractable source slot allowed by the filter.

Per Slot follows `local slot + order offset = network position`. Sneak-scroll up/down raises/lowers the offset without changing the hotbar. Positive values skip leading network positions: offset `2` maps slot 0 to position 2. Negative values skip leading local slots: offset `-2` maps slot 2 to position 0. Extract nodes use this to match receiving endpoints; insert nodes reverse the same relation to match sources with inventory slots. Receivers cycle within the remaining positions by default; `orderedMatchingUpgrade.wrapTargets` disables that cycle.

Per Item makes extract nodes cycle receivers; insert nodes ignore this mode. Success advances the cursor. An unfinished cross-tick batch resumes its original plan, and an idle source resets its cursor when appropriate. Every visited target consumes its own operation budget. When a target is temporarily unavailable, failed items stay at the source and receive priority retries later without blocking other assignments. Equal-priority nodes remain separate positions.

The network keeps independent source and receiver order lists for items, fluids, FE, chemicals, mana, and source. Membership depends only on whether that resource is enabled, not fullness or filter contents; equal-priority endpoints remain separate. The Sky Configurator displays connections using the same priority and stable-position order. This upgrade currently consumes the item lists.

<RecipeFor id="ordered_matching_upgrade" fallbackText="The ordered matching upgrade recipe is unavailable." />

Force Extraction Upgrades work only on item extract nodes. For devices whose mod ID appears in `transfers.integrations.forceExtractionUpgrade.deviceModIdWhitelist`, they bypass an external interface's 64-item return cap and request the amount the destination can actually accept; rejected moves restore the source slot. The whitelist contains `mekanism_extras` by default. If none of its configured mods are installed, or the list is empty, the item is hidden from the creative inventory and JEI and its tier 2 offering cannot start. The offering uses a Slot Parallel Upgrade on the altar plus 4 Blaze Rods, 4 Magma Creams, 4 Crying Obsidian, and 1 Netherite Scrap on offering tables.

Dimension upgrades also go into node upgrade slots, but only affect extract faces. An extract face with a dimension upgrade can send to same-line insert faces in other loaded dimensions. Insert faces do not need dimension upgrades. This is not a chunk loader; unloaded dimensions or chunks are skipped.

Dimension upgrades are made through a tier 2 sky offering: place a Nether Star on the altar, then place 4 Eyes of Ender and 1 Chora Nectar on offering tables. The ritual takes about 12 seconds, and the upgrade only needs to be installed on the extracting node.
