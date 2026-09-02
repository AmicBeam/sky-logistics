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

The Sky Configurator selects a line and the resource types to move. Right-click air to open its screen; right-click a node to open the node screen. Place a node while holding the configurator in your offhand to apply its line and toggles automatically.

Sneak-right-click a node with the configurator to copy its line and resource toggles and enter paste mode. Right-click other nodes to paste those settings. Sneak-right-click again, open the configurator screen, or stop holding it to leave paste mode.

<RecipeFor id="configurator" fallbackText="The Sky Configurator recipe is unavailable." />

## Slot Parallel Upgrade

Slot Parallel Upgrades stack in one node upgrade slot. Each card lets the node handle more slots at once, which helps devices that move many different items.

<RecipeFor id="speed_upgrade" fallbackText="The Slot Parallel Upgrade recipe is unavailable." />

## Dimension Upgrade

A Dimension Upgrade is only needed on an extract face. It can send resources to insert faces on the same line in other loaded dimensions, but it does not load dimensions or chunks.

Make a Dimension Upgrade with a tier 2 offering: put a Nether Star on the altar and place 4 Eyes of Ender and 1 Chora Nectar on offering tables.

## Force Extraction Upgrade

A Force Extraction Upgrade works only on item extract faces. It lets supported devices provide more items at once. The upgrade is hidden when no supported device mod is installed. Its tier 2 offering uses a Slot Parallel Upgrade on the altar with 4 Blaze Rods, 4 Magma Creams, 4 Crying Obsidian, and 1 Netherite Scrap on offering tables.

## Ordered Matching Upgrade

Ordered Matching Upgrades switch between Per Slot and Per Item. Right-click air while holding one to change mode; Per Slot is the default.

Per Slot works on item extract or insert faces. Extract faces match local slots to receiving endpoints on the line; insert faces match source endpoints to local slots. Sneak-scroll while holding the upgrade to shift the matching order forward or backward.

Per Item works only on extract faces and divides one item type as evenly as possible among every target that can accept it. On an insert face, it behaves like normal insertion without the upgrade.

<RecipeFor id="ordered_matching_upgrade" fallbackText="The Ordered Matching Upgrade recipe is unavailable." />
