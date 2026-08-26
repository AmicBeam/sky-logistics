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
  - skylogistics:ordered_matching_upgrade
---

# Configurator and Upgrades

The Sky Configurator stores a line name, line ID, and resource toggles. Right-click air to open the configurator screen; right-click a node to open its node screen. Holding the configurator in the offhand while placing a node makes the new node inherit its line and resource toggles.

Sneak-right-click a node with the configurator to copy that node and enter paste mode. While in paste mode, right-click another node to write the stored line and resource toggles to it. Sneak-right-click again, open the configurator screen, or stop holding the configurator to leave paste mode.

Speed upgrades stack in one node upgrade slot. Each card adds one slot check per tick. By default, up to 8 cards raise the base rate from 1 to 9 slots/t; the server can configure this limit. They do not increase line capacity.

<RecipeFor id="speed_upgrade" fallbackText="The speed upgrade recipe is unavailable." />

Ordered Matching Upgrades work on item extract or insert nodes. Right-click air while holding one to switch between Per Slot and Per Item; Per Slot is the default and the tooltip shows the saved mode. Switching to Per Item clears the order offset, so switching back to Per Slot starts at 0. Extraction still selects the lowest extractable source slot allowed by the filter.

Per Slot follows `local slot + order offset = network position`. Sneak-scroll up/down raises/lowers the offset without changing the hotbar. Positive values skip leading network positions: offset `2` maps slot 0 to position 2. Negative values skip leading local slots: offset `-2` maps slot 2 to position 0. Extract nodes use this to match receiving endpoints; insert nodes reverse the same relation to match sources with inventory slots. Receivers cycle within the remaining positions by default; `orderedMatchingUpgrade.wrapTargets` disables that cycle.

Per Item persists a receiver cursor for every extract face and an inventory-slot cursor for every insert face, advancing only after success; order offsets do not apply. When the source count does not exceed the target count, one item moves; larger counts are batched into complete rounds plus a remainder. Every visited target consumes its own operation budget. `continueAfterTargetFailure` defaults to true: a failed one-item extraction assignment enters a persistent per-face detention queue, remains in the source inventory, is excluded from fresh dispatch, and receives priority round-robin retries. `perItemDetentionQueueLength` defaults to 1; a full queue prevents passing another failed target.

The network keeps independent source and receiver order lists for items, fluids, FE, chemicals, mana, and source. Membership depends only on whether that resource is enabled, not fullness or filter contents; equal-priority endpoints remain separate. The Sky Configurator displays connections using the same priority and stable-position order. This upgrade currently consumes the item lists.

<RecipeFor id="ordered_matching_upgrade" fallbackText="The ordered matching upgrade recipe is unavailable." />

Dimension upgrades also go into node upgrade slots, but only affect extract faces. An extract face with a dimension upgrade can send to same-line insert faces in other loaded dimensions. Insert faces do not need dimension upgrades. This is not a chunk loader; unloaded dimensions or chunks are skipped.

Dimension upgrades are made through a tier 2 sky offering: place a Nether Star on the altar, then place 4 Eyes of Ender and 1 Chora Nectar on offering tables. The ritual takes about 12 seconds, and the upgrade only needs to be installed on the extracting node.
