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

The Celestial Distributor is a zero-buffer item, fluid, and energy capability proxy. Starting from its six neighbors, it follows only blocks that are themselves valid containers, stopping at air, ordinary blocks, capability-free block entities, and other distributors. It exposes at most 16 targets by default; servers can set `[distributor].maxTargets` from 1 to 64.

Incoming resources are divided as evenly as possible among targets that can accept them. A rotating cursor maintains fairness when node or pipe budgets split a transfer across ticks. Extraction aggregates every target without requiring an even split.

The target list is cached. Neighbor changes invalidate it immediately, and a 100-tick safety validation handles capability changes. Item, fluid, and energy proxying can each be disabled in the server config.

It is made at a tier 2 altar with 1 charged Eulogia Crystal as the main ingredient and offerings of 4 Celestial Stone, 2 Redstone Dust, 2 Amethyst Shards, and 1 Chest.
