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
  - skylogistics:exact_quantity_upgrade
  - skylogistics:sky_necklace
---

# Tools and Upgrades

The configurator is the main tool for copying, pasting, and editing line settings. Filters and upgrades refine how a node behaves once the line is running.

<ItemGrid>
  <ItemIcon id="configurator" />
  <ItemIcon id="filter_list" />
  <ItemIcon id="tag_filter_list" />
  <ItemIcon id="speed_upgrade" />
  <ItemIcon id="dimension_upgrade" />
  <ItemIcon id="exact_quantity_upgrade" />
  <ItemIcon id="sky_necklace" />
</ItemGrid>

Read the detailed pages:

- [Sky Configurator](configurator.md)
- [Configurator and Upgrades](logistics_configurator_upgrades.md)
- [Vaults and Filters](logistics_vaults_filters.md)
- [Sky Necklace](sky_necklace.md)

## Filters

Use <ItemLink id="filter_list" /> for explicit item or fluid filters, and <ItemLink id="tag_filter_list" /> when an entire tag should match.

## Upgrades

<ItemLink id="speed_upgrade" /> helps nodes process resources more quickly. <ItemLink id="dimension_upgrade" /> can reach same-line endpoints in other loaded dimensions. <ItemLink id="exact_quantity_upgrade" /> makes a node or necklace keep or fill toward the total number of items matching its filter.

## Sky Necklace

The <ItemLink id="sky_necklace" /> supports extract, insert, and maintain modes. See [Sky Necklace](sky_necklace.md) for whitelist requirements, upgrades, and quantity behavior.

<RecipeFor id="configurator" fallbackText="The configurator recipe is unavailable." />
<RecipeFor id="sky_necklace" fallbackText="The sky necklace recipe is unavailable." />
