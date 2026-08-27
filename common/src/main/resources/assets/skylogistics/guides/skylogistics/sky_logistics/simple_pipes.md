---
navigation:
  title: Simple Pipes
  icon: simple_item_pipe
  parent: index.md
  position: 1
item_ids:
  - skylogistics:simple_item_pipe
  - skylogistics:simple_fluid_pipe
  - skylogistics:simple_energy_pipe
  - skylogistics:sky_wrench
---

# Simple Pipes

Simple pipes provide a wired alternative to wireless logistics nodes. Item, fluid, and energy pipes connect only to pipes of the same type. Every connected group forms one line, runs continuously without a screen, and automatically connects to usable adjacent machines or containers.

<ItemGrid>
  <ItemIcon id="simple_item_pipe" />
  <ItemIcon id="simple_fluid_pipe" />
  <ItemIcon id="simple_energy_pipe" />
  <ItemIcon id="sky_wrench" />
</ItemGrid>

## Placement and Endpoints

Place normally to make the container on the clicked side an insert endpoint. Sneak-place to make that side extract instead. Other usable adjacent containers are connected automatically in insert mode.

Right-click a pipe connection with the Sky Wrench to disconnect or reconnect that side. Sneak-right-click a pipe to dismantle it quickly; its drops are placed in your inventory when possible.

Right-click a machine-facing endpoint with the Sky Configurator to switch it between extract and insert without opening the configurator screen. An extract endpoint is shown with a wider collar.

## Default Throughput

- Item: 64 items per extracting endpoint per tick. One transfer uses at most one source slot and one target slot.
- Fluid: 10,000 mB per extracting endpoint per tick.
- Energy: 100,000 FE per extracting endpoint per tick.
- Botania mana: 50 mana per extracting endpoint per tick, matching the rounded average throughput of a nearby basic Mana Spreader.
- Ars Nouveau Source: 50 Source per extracting endpoint per tick, matching a basic Source Relay's average of 1,000 Source every 20 ticks.

One line contains at most 1024 pipe blocks by default. If a placement or reconnection would exceed the limit, the new edge stays disconnected and a message appears. Server settings may change the default transfer rates and line limit, or disable the connection-count check.

## Recipes

Each pipe recipe produces 8 pipes.

<RecipeFor id="simple_item_pipe" fallbackText="The simple item pipe recipe is unavailable." />
<RecipeFor id="simple_fluid_pipe" fallbackText="The simple fluid pipe recipe is unavailable." />
<RecipeFor id="simple_energy_pipe" fallbackText="The simple energy pipe recipe is unavailable." />
<RecipeFor id="sky_wrench" fallbackText="The Sky Wrench recipe is unavailable." />
