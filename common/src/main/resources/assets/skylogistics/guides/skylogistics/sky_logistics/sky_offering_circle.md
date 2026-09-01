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
  - skylogistics:chora_nectar_block
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

## Tier 3

Tier 3 keeps the tier 2 outer pillars and glass caps, but replaces the four corners of the inner 5x5 frame with Chora Nectar Blocks. It processes offerings at the configured base multiplier (4x by default), while ritual particles continue at normal speed.

<GameScene zoom={0.82} interactive={true} fullWidth={true}>
  <ImportStructure src="/structures/offering_circle_tier3.snbt" />
  <IsometricCamera yaw={35} pitch={30} />
</GameScene>

Celestial Glass is made through a tier 1 offering: place a charged Eulogia Crystal on the altar, then place 8 of any glass and 8 Glowstone Dust on two offering tables. After about 8 seconds, the ritual produces 8 Celestial Glass.
