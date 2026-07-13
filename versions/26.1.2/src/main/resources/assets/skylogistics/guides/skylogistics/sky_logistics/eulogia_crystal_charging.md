---
navigation:
  title: Eulogia Crystal Charging
  icon: eulogia_crystal
  parent: offerings.md
  position: 1
item_ids:
  - skylogistics:eulogia_crystal
  - skylogistics:celestial_stone
  - skylogistics:celestial_glass
---

# Eulogia Crystal Charging

A crafted <ItemLink id="eulogia_crystal" /> starts uncharged. Bring it to the configured sky height or higher to let it absorb high-sky light. The server config `rituals.skyRitualMinY` defaults to Y=200.

<RecipeFor id="eulogia_crystal" fallbackText="The Eulogia Crystal recipe is unavailable." />

The Eulogia Crystal is built around a diamond core and does not consume an Ender Pearl. Newly crafted crystals start uncharged.

Uncharged crystals charge in a player's inventory or on a <ItemLink id="offering_table" />. Ordinary containers do not charge them. The default charge time is 60 seconds, controlled by `rituals.eulogiaCrystalChargeSeconds`.

When charging finishes, the crystal icon changes and its tooltip says it is charged. A charged Eulogia Crystal can be crafted with `#c:stones` into <ItemLink id="celestial_stone" />; <ItemLink id="celestial_glass" /> is made through offering.

<RecipeFor id="celestial_stone" fallbackText="The Celestial Stone recipe is unavailable." />
<RecipeFor id="celestial_stone_slab" fallbackText="The Celestial Stone slab recipe is unavailable." />
<RecipeFor id="celestial_stone_wall" fallbackText="The Celestial Stone wall recipe is unavailable." />
