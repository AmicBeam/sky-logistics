---
navigation:
  title: Logistics
  icon: sky_node
  parent: index.md
  position: 3
item_ids:
  - skylogistics:sky_node
  - skylogistics:item_vault
  - skylogistics:fluid_vault
  - skylogistics:sky_me_interface
  - skylogistics:sky_rs_interface
  - skylogistics:sky_dimension_interface
---

# Logistics Network

Logistics nodes provide wireless transport. Set extract and insert faces to the same line, and they can move resources directly between machines. If a destination cannot accept a transfer, the resources remain at the source. For a block-by-block wired network, see [Simple Pipes](simple_pipes.md).

Read more:

- [Nodes and Pipe Endpoints](logistics_nodes.md)
- [Vaults and Filters](logistics_vaults_filters.md)
- [External Network Interfaces](external_network_interfaces.md)
- [Configurator and Upgrades](logistics_configurator_upgrades.md)


## External Networks

When the matching mod is installed, dedicated interfaces can connect Sky Logistics to AE2, Refined Storage, and Beyond Dimensions. See [External Network Interfaces](external_network_interfaces.md) for resource paths, filter requirements, and server switches:

<ItemGrid>
  <ItemIcon id="sky_me_interface" />
  <ItemIcon id="sky_rs_interface" />
  <ItemIcon id="sky_dimension_interface" />
</ItemGrid>

<RecipeFor id="item_vault" fallbackText="The item vault recipe is unavailable." />
<RecipeFor id="fluid_vault" fallbackText="The fluid vault recipe is unavailable." />
