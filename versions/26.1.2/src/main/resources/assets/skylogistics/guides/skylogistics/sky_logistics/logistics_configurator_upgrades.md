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
---

# Configurator and Upgrades

The Sky Configurator stores a line name, line ID, and resource toggles. Right-click air to open the configurator screen; right-click a node to open its node screen. Holding the configurator in the offhand while placing a node makes the new node inherit its line and resource toggles.

Sneak-right-click a node with the configurator to copy that node and enter paste mode. While in paste mode, right-click another node to write the stored line and resource toggles to it. Sneak-right-click again, open the configurator screen, or stop holding the configurator to leave paste mode.

Speed upgrades go into node upgrade slots. Without one, a node processes 1 item stack or fluid tank per tick; with one, it processes 2 stacks or tanks per tick. This improves the node's stack/tank throughput, but it does not add a hidden line buffer.

<RecipeFor id="speed_upgrade" fallbackText="The speed upgrade recipe is unavailable." />

Dimension upgrades also go into node upgrade slots, but only affect extract faces. An extract face with a dimension upgrade can send to same-line insert faces in other loaded dimensions. Insert faces do not need dimension upgrades. This is not a chunk loader; unloaded dimensions or chunks are skipped.

Dimension upgrades are made through a tier 2 sky offering: place a Nether Star on the altar, then place 4 Eyes of Ender and 1 Chora Nectar on offering tables. Duration is 240 ticks. It only needs to be installed on the extracting node.
