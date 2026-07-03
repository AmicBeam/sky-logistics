---
navigation:
  title: Overview
  icon: sky_node
  position: 0
item_ids:
  - skylogistics:sky_node
---

# Sky Logistics Manual

Sky Logistics is not a block-by-block pipe network. Players place Sky Logistics Nodes, and the server directly pairs extract faces with insert faces that share a line ID. Lines have no hidden buffer; if the target cannot accept resources, the source is not extracted first.

<ItemGrid>
  <ItemIcon id="configurator" />
  <ItemIcon id="sky_node" />
  <ItemIcon id="item_vault" />
  <ItemIcon id="fluid_vault" />
  <ItemIcon id="offering_altar" />
</ItemGrid>

A line can carry items, fluids, and energy. Nodes are configured per face: extract faces pull from adjacent machines or containers, and insert faces push into adjacent machines or containers. The node screen lets you edit all six directions independently.

Typical setup: create a line with the configurator, place one node beside a source and set it to extract, place another same-line node beside a destination and set it to insert, then copy and paste settings for repeated machines.

## Topics

- [Sky Offerings](offerings.md): charge crystals, build offering circles, and make Chora Nectar.
- [Logistics Network](logistics.md): connect nodes, vaults, filters, and external networks.
- [Tools and Upgrades](tools.md): configure lines, copy settings, and extend throughput or dimensions.

<RecipeFor id="sky_node" fallbackText="Install the recipe data pack or check JEI for the Sky Node recipe." />
