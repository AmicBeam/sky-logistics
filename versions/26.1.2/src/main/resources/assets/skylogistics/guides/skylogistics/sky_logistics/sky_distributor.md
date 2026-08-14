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

The Celestial Distributor is a zero-buffer item, fluid, and energy routing proxy used only by Sky Logistics simple pipes and logistics nodes. It exposes no general Forge/NeoForge capabilities, so vanilla hoppers and third-party pipes do not connect. Starting from its six neighbors, it follows containers whose contacting face is valid and inherits the access face used to reach each container. Servers may enable `[distributor].searchAllSides` to scan every face and select any available one. Air, ordinary blocks, capability-free block entities, and other distributors stop the search. It exposes at most 16 targets by default; servers can set `[distributor].maxTargets` from 1 to 64.

Incoming resources are divided as evenly as possible among targets that can accept them. A rotating cursor maintains fairness when node or pipe budgets split a transfer across ticks. Extraction aggregates every target without requiring an even split.

Placement immediately runs one BFS and splits its result into immutable item, fluid, and energy target caches. Neighbor changes invalidate them immediately, while a 100-tick lazy safety validation handles capability changes. Item, fluid, and energy proxying can each be disabled in the server config.

Simulation builds a bounded transfer plan and execution reuses it without scanning again. `[distributor].opsPerTick` limits target and internal-slot probes per distributor; unused resources remain at the source and the network continues from its cursor on a later tick.

Hold a Celestial Configurator and aim at the distributor to outline every target in its current cache in cyan. The client requests a snapshot immediately when the aimed distributor changes, then once every 20 ticks while aiming; rendering never scans containers every frame.

It is made at a tier 2 altar with 1 charged Eulogia Crystal as the main ingredient and offerings of 4 Celestial Stone, 2 Redstone Dust, 2 Amethyst Shards, and 1 Chest.
