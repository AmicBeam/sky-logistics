---
navigation:
  title: Logistics
  icon: sky_node
  parent: index.md
  position: 2
item_ids:
  - skylogistics:sky_node
  - skylogistics:item_vault
  - skylogistics:fluid_vault
  - skylogistics:sky_me_interface
  - skylogistics:sky_rs_interface
  - skylogistics:sky_dimension_interface
---

# Logistics Network

Nodes do not create pipes. The server pairs extract faces with insert faces that share a line id, then moves only what the target can accept. Failed targets do not consume resources first.

Read more:

- [Nodes and Pipe Endpoints](logistics_nodes.md)
- [Vaults and Filters](logistics_vaults_filters.md)
- [Configurator and Upgrades](logistics_configurator_upgrades.md)


## External Networks

When the matching mod is installed, Sky Logistics can expose interfaces for AE2, Refined Storage, and Beyond Dimensions:

<ItemGrid>
  <ItemIcon id="sky_me_interface" />
  <ItemIcon id="sky_rs_interface" />
  <ItemIcon id="sky_dimension_interface" />
</ItemGrid>

<RecipeFor id="item_vault" fallbackText="The item vault recipe is unavailable." />
<RecipeFor id="fluid_vault" fallbackText="The fluid vault recipe is unavailable." />
