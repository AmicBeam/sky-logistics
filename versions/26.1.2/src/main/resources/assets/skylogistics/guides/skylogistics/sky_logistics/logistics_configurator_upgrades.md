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
  - skylogistics:exact_quantity_upgrade
---

# Configurator and Upgrades

The Sky Configurator stores a line name, line ID, and resource toggles. Right-click air to open the configurator screen; right-click a node to open its node screen. Holding the configurator in the offhand while placing a node makes the new node inherit its line and resource toggles.

Sneak-right-click a node with the configurator to copy that node and enter paste mode. While in paste mode, right-click another node to write the stored line and resource toggles to it. Sneak-right-click again, open the configurator screen, or stop holding the configurator to leave paste mode.

Speed upgrades go into node upgrade slots. They let a node check more source slots or tanks each tick, making busy extract and insert endpoints work more quickly. They do not increase line capacity.

<RecipeFor id="speed_upgrade" fallbackText="The speed upgrade recipe is unavailable." />

Dimension upgrades also go into node upgrade slots, but only affect extract faces. An extract face with a dimension upgrade can send to same-line insert faces in other loaded dimensions. Insert faces do not need dimension upgrades. This is not a chunk loader; unloaded dimensions or chunks are skipped.

Dimension upgrades are made through a tier 2 sky offering: place a Nether Star on the altar, then place 4 Eyes of Ender and 1 Chora Nectar on offering tables. The ritual takes about 12 seconds, and the upgrade only needs to be installed on the extracting node.

Exact Quantity Upgrades work in nodes and Sky Necklaces. They replace slot controls with an item count from 1 to 2147483647. A node extract face keeps the configured total of items matching its filter, while an insert face only fills up to that total. Necklace extract, insert, and maintain modes likewise count every item matching the whitelist.

<RecipeFor id="exact_quantity_upgrade" fallbackText="The exact quantity upgrade recipe is unavailable." />
