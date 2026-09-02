---
navigation:
  title: Tools and Upgrades
  icon: configurator
  parent: index.md
  position: 4
item_ids:
  - skylogistics:configurator
  - skylogistics:filter_list
  - skylogistics:tag_filter_list
  - skylogistics:speed_upgrade
  - skylogistics:dimension_upgrade
  - skylogistics:sky_necklace
  - skylogistics:kleis_dominion_wand
---

# Tools and Upgrades

The configurator is the main tool for copying, pasting, and editing line settings. Filters and upgrades refine how a node behaves once the line is running.

<ItemGrid>
  <ItemIcon id="configurator" />
  <ItemIcon id="filter_list" />
  <ItemIcon id="tag_filter_list" />
  <ItemIcon id="speed_upgrade" />
  <ItemIcon id="dimension_upgrade" />
  <ItemIcon id="sky_necklace" />
  <ItemIcon id="kleis_dominion_wand" />
</ItemGrid>

Read the detailed pages:

- [Sky Configurator](configurator.md)
- [Configurator and Upgrades](logistics_configurator_upgrades.md)
- [Vaults and Filters](logistics_vaults_filters.md)
- [Sky Necklace](sky_necklace.md)

## Filters

Use <ItemLink id="filter_list" /> for explicit item or fluid filters, and <ItemLink id="tag_filter_list" /> when an entire tag should match.

## Upgrades

<ItemLink id="speed_upgrade" /> helps nodes process resources more quickly. <ItemLink id="dimension_upgrade" /> can reach same-line endpoints in other loaded dimensions.

## Sky Necklace

The <ItemLink id="sky_necklace" /> supports extract, insert, and maintain modes. See [Sky Necklace](sky_necklace.md) for whitelist requirements, upgrades, and quantity behavior.

## Kleis Dominion Wand

Hold the <ItemLink id="kleis_dominion_wand" /> in your main hand and a configured Sky Configurator in your offhand to create or remove wireless virtual faces. Swap hands to edit them: sneak-right-click a highlighted face to copy its single-face configuration, then right-click another highlighted face or a real logistics node to paste. Configurations copied from real nodes can likewise be pasted onto highlighted virtual faces.

<RecipeFor id="configurator" fallbackText="The configurator recipe is unavailable." />
<RecipeFor id="sky_necklace" fallbackText="The sky necklace recipe is unavailable." />
<RecipeFor id="kleis_dominion_wand" fallbackText="The Kleis Dominion Wand offering recipe is unavailable." />
