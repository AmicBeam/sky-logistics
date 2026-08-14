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

The Celestial Distributor is a zero-buffer item, fluid, and energy routing proxy used only by Sky Logistics simple pipes and logistics nodes. It exposes no general Forge/NeoForge capabilities, so vanilla hoppers and third-party pipes do not connect. Starting from its six neighbors, it follows containers only. Every bound container is queried through the same face used by the pipe or node to access the distributor; BFS paths and cursors never change that face. Air, ordinary blocks, blocks without a usable capability on that inherited face, and other distributors stop the search. It exposes at most 16 targets by default; servers can set `[distributor].maxTargets` from 1 to 64.

Incoming resources are divided as evenly as possible among targets that can accept them. A rotating cursor maintains fairness when node or pipe budgets split a transfer across ticks. Extraction aggregates every target without requiring an even split.

Each actually connected face has its own target cache. Placement prewarms only faces that already have an adjacent Sky Logistics pipe or node; other faces scan lazily on first use, avoiding six unconditional BFS runs. Neighbor changes invalidate every face cache, while a 100-tick lazy safety validation handles capability changes. An unfinished scan resumes on later ticks without making the connected face disappear. Changing faces discards face-specific transient plans without resetting the distributor's shared per-tick transfer budget. Item, fluid, and energy proxying can each be disabled in the server config.

Item insertion simulation builds a bounded transfer plan and execution reuses it without scanning again, including when execution shrinks to the amount accepted during simulation. A successful complete item insertion keeps that target's slot cursor on the same hot slot; rejection or a partial insertion advances it. `[distributor].scanOpsPerTick` independently limits BFS positions, with a conservative default of 16 per tick. `[distributor].opsPerTick` limits transfer probes: a target and its first slot count as one probe, while each additional slot in that target counts separately. Thus the default 64 can cover 64 targets whose first slots accept. Neither budget is shared with the logistics network: the network charges one endpoint visit, then the distributor performs bounded internal routing. Unused resources remain at the source and continue from cursors on a later tick.

Hold a Celestial Configurator and aim at the distributor to outline every target in its current cache in cyan. The client requests a snapshot immediately when the aimed distributor changes, then once every 20 ticks while aiming; rendering never scans containers every frame.

Craft it with Prismarine in the four corners, Lapis Lazuli above and below, Amethyst Shards on both sides, and a Redstone Comparator in the center. No altar, Eulogia Crystal, or Celestial Stone is required.
