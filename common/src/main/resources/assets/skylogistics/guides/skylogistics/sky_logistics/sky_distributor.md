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

## Purpose and setup

Connect several touching containers to one logistics node or simple pipe. Use it to feed a row of machines or collect their products.

Attach your node or pipe to the distributor, then place chests, tanks, or machines next to one another. The distributor stores nothing itself. Resources that cannot be accepted stay at the source.

## Choose a pipe

Use item pipes for items, fluid pipes for fluids, and energy pipes for FE. With the relevant integrations installed, chemicals use fluid pipes; Mana and Source use energy pipes.

Connect using Sky Logistics nodes or simple pipes. Vanilla hoppers and other mods' pipes cannot connect directly.

## Choose a direction

Right-click with a Sky Configurator or compatible wrench to change direction without opening the configurator screen.

All-sides mode connects nearby touching containers. Directional mode follows a straight row along the arrow. Starting in all-sides mode, clicks on the same face cycle between pointing away from it, pointing toward it, and all sides.

## Connection range

Keep containers touching. Gaps, ordinary blocks, and machines that cannot transfer through the required side break the connection.

The default limit is 32 targets; servers can change it. Directional connections can pass through other distributors on the same straight line. Each of those distributors also counts toward the limit.

## Check machine sides

Machine input and output settings matter. For example, connecting to the top of the distributor also requires access through the top of each target machine.

Hold a Sky Configurator and aim at the distributor to highlight connected targets in cyan. After placing or adjusting machines, allow a moment for the highlights to update.

## Redstone modes

Without redstone, incoming resources are shared as evenly as possible among machines that accept them. Apply redstone to fill machines one after another instead. Remove the signal to restore balanced filling.

Jade can show the current mode. Extraction can draw from all connected targets without taking equal amounts from each.

## Node maintenance

With a maintenance value on the inserting node, balanced mode counts each machine separately. Redstone sequential mode counts all targets together.

For example, maintaining 64 items by count means 64 in each accepting machine in balanced mode, or 64 across all targets in sequential mode.

## Per-slot matching

With an item per-slot matching upgrade, balanced mode uses the matched position as a slot inside each machine. Redstone sequential mode uses it to select a machine.

An out-of-range position prevents insertion. If a machine receives nothing, check its input sides, filters, maintenance value, and matching position.

## Crafting

Craft it with Prismarine in the corners, Lapis Lazuli on all four sides, and a Redstone Comparator in the center.
