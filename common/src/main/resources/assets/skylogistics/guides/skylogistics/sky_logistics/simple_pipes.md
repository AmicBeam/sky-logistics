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
  - skylogistics:eulogia_companion_stone
---

# Simple Pipes

Simple pipes provide a wired alternative to wireless logistics nodes. Item, fluid, and energy pipes connect only to pipes of the same type. Every connected group forms one line, runs continuously without a screen, and automatically connects to usable adjacent machines or containers.

<ItemGrid>
  <ItemIcon id="simple_item_pipe" />
  <ItemIcon id="simple_fluid_pipe" />
  <ItemIcon id="simple_energy_pipe" />
  <ItemIcon id="eulogia_companion_stone" />
</ItemGrid>

## Placement and Endpoints

Place normally to make the container on the clicked side an insert endpoint. Sneak-place to make that side extract instead. Other usable adjacent containers are connected automatically in insert mode.

Right-click a pipe connection with the Sky Wrench to disconnect or reconnect that side. Sneak-right-click a pipe to dismantle it quickly; its drops are placed in your inventory when possible.

Right-click a machine-facing endpoint with the Sky Configurator to switch it between extract and insert without opening the configurator screen. An extract endpoint is shown with a wider collar.

Aim at a machine-facing item or fluid pipe endpoint and right-click with a Filter List or Tag Filter List to copy its rules without consuming the list. Both extract and insert use the filter, and later edits to the original do not update the copied rules. Sneak-right-click with an empty hand to clear it; energy pipes do not accept filters. Jade can show endpoint status and a filter summary when installed.

## Default Throughput

- Item: 64 items per extracting endpoint per tick. One transfer uses at most one source slot and one target slot.
- Fluid: 10,000 mB per extracting endpoint per tick.
- Energy: 100,000 FE per extracting endpoint per tick.
- Botania mana: 50 mana per extracting endpoint per tick, matching the rounded average throughput of a nearby basic Mana Spreader.
- Ars Nouveau Source: 50 Source per extracting endpoint per tick, matching a basic Source Relay's average of 1,000 Source every 20 ticks.

One line normally contains at most 1024 pipe blocks. If a placement or reconnection would exceed the limit, the new edge stays disconnected and a message appears.

## Recipes

Each pipe recipe produces 2 pipes and requires a charged Eulogia Companion Stone. A wooden chest, bucket, or redstone block distinguishes the item, fluid, and energy variants.

<RecipeFor id="eulogia_companion_stone" fallbackText="The Eulogia Companion Stone recipe is unavailable." />
<RecipeFor id="sky_wrench" fallbackText="The Sky Wrench recipe is unavailable." />

<RecipeFor id="simple_item_pipe" fallbackText="The simple item pipe recipe is unavailable." />
<RecipeFor id="simple_fluid_pipe" fallbackText="The simple fluid pipe recipe is unavailable." />
<RecipeFor id="simple_energy_pipe" fallbackText="The simple energy pipe recipe is unavailable." />
