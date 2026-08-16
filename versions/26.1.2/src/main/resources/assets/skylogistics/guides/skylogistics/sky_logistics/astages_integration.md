---
navigation:
  title: AStages Rate Integration
  icon: speed_upgrade
  parent: logistics.md
  position: 4
---

# AStages Rate Integration

With AStages installed, a server can limit and progressively unlock per-operation transfer amounts according to stages owned by the line owner. The integration is disabled by default and must be enabled at `transfers.astages.enabled`. It covers wireless nodes, external interfaces, and simple pipes.

Items, fluids, Mekanism chemicals, FE, Botania mana, and Ars Nouveau Source are calculated independently. The system starts with `initialRates`, takes the highest configured value for each resource across every owned stage, then applies the lowest of that value, the device limit, and target acceptance.

The rate only limits how much one successful operation may commit. It does not change node operations per tick, Speed Upgrades, server or line operation budgets, or necklace intervals. A simple pipe's own lower configured limit still wins.

Wireless lines use the player who created or claimed the line as owner. Simple pipe networks use their placer or inherited network owner. If no owner can be found or a query fails, only `initialRates` apply; nearby online players never lend their stages.
