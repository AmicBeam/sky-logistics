---
navigation:
  title: Overview
  icon: sky_node
  position: 0
item_ids:
  - skylogistics:sky_node
---

# Sky Logistics Manual

Sky Logistics supports both wired simple pipes and wireless logistics nodes. Simple pipes connect adjacent machines block by block, while nodes link extract and insert faces on the same line. Both systems leave resources at the source when no destination can accept them.

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

- [Simple Pipes](simple_pipes.md): build wired item, fluid, and energy lines; use a wrench for connections and the configurator for endpoints.
- [Sky Offerings](offerings.md): charge crystals, build offering circles, and make Chora Nectar.
- [Logistics Network](logistics.md): connect nodes, vaults, filters, and external networks.
- [Tools and Upgrades](tools.md): configure lines, copy settings, and extend throughput or dimensions.

<RecipeFor id="sky_node" fallbackText="The Sky Node recipe cannot be shown here; check JEI for it." />
