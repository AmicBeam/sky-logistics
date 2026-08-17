---
navigation:
  title: Sky Necklace
  icon: sky_necklace
  parent: tools.md
  position: 3
item_ids:
  - skylogistics:sky_necklace
---

# Sky Necklace

The <ItemLink id="sky_necklace" /> connects player-side inventory to a selected Sky line through an item whitelist. It requires a whitelist Filter List containing at least one concrete item; it does nothing without a filter or with a blacklist. It works in a Curios slot when available, or from the player's main inventory otherwise.

## Three Modes

- **Extract** sends matching player-side items to line targets.
- **Insert** takes matching items from line sources and puts them on the player side.
- **Maintain** targets the player's main inventory, inserting shortages and sending away excess. It requires a slot or quantity target above zero.

## Upgrades

The necklace has two upgrade slots and accepts one Dimension Upgrade and one Exact Quantity Upgrade. Dimension allows access to same-line endpoints in other loaded dimensions but does not load chunks. Exact Quantity replaces the slot buttons with a `1` to `2147483647` count and makes all three modes count every item matching the whitelist.

By default it works every 10 ticks and tries 1 line endpoint per cycle. The server can tune the interval, scanned slots, and endpoint attempts.

<RecipeFor id="sky_necklace" fallbackText="The Sky Necklace is obtained through a sky offering by default." />
