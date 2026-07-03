---
navigation:
  title: Sky Offering Circle
  icon: offering_table
  parent: offerings.md
  position: 3
item_ids:
  - skylogistics:offering_altar
  - skylogistics:offering_table
  - skylogistics:celestial_stone
  - skylogistics:celestial_glass
---

# Sky Offering Circle

Place the altar at the center with one offering table on each horizontal side. The Celestial Stone outer ring sits one block below the altar and forms a 5x5 lower frame; other empty-looking spaces do not need to be air. Chora Nectar requires tier 2.

## Tier 1

Tier 1 requires the lower outer frame to remain complete. Celestial Stone, slabs, stairs, and walls all count as frame blocks.

<GameScene zoom={1.05} interactive={true} fullWidth={true}>
  <ImportStructure src="/structures/offering_circle_tier1.snbt" />
  <IsometricCamera yaw={35} pitch={30} />
</GameScene>

## Tier 2

Tier 2 adds one Celestial Stone pillar at each corner of the next outer ring, with Celestial Glass on top.

<GameScene zoom={0.82} interactive={true} fullWidth={true}>
  <ImportStructure src="/structures/offering_circle_tier2.snbt" />
  <IsometricCamera yaw={35} pitch={30} />
</GameScene>

Celestial Glass is made through a tier 1 offering: place a charged Eulogia Crystal on the altar, then place 8 `#c:glass_blocks` and 8 Glowstone Dust on two offering tables. Duration is 160 ticks and the result is 8 Celestial Glass.
