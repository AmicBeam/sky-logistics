---
navigation:
  title: Celestial Distributor
  icon: sky_distributor
  parent: simple_pipes.md
  position: 1
item_ids:
  - skylogistics:sky_distributor
---

# Celestial Distributor

The Celestial Distributor lets simple pipes or logistics nodes use a group of nearby devices as one target. It connects in all six directions by default. Use a compatible wrench to choose one direction and connect devices along a straight line. Other distributors on that line can extend the connection.

Incoming resources are divided as evenly as possible among devices that can accept them. A redstone signal switches to sequential insertion, filling one device before continuing; removing the signal restores balanced insertion. Jade shows the current mode when installed.

When a node maintains an amount, balanced mode applies that target to each device separately, while redstone sequential mode counts all devices together. Per Slot matching addresses slots on each device in balanced mode and addresses devices by order in sequential mode.

Extraction can read from every connected device. The distributor stores no resources of its own. Only Sky Logistics simple pipes and logistics nodes connect to it; vanilla hoppers and pipes from other mods do not.

Hold a Sky Configurator and aim at the distributor to outline every currently connected device in cyan, making its direction and reach easy to check.

<RecipeFor id="sky_distributor" fallbackText="The Celestial Distributor recipe is unavailable." />
