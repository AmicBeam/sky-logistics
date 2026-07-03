---
navigation:
  title: Vaults and Filters
  icon: item_vault
  parent: logistics.md
  position: 2
item_ids:
  - skylogistics:item_vault
  - skylogistics:fluid_vault
  - skylogistics:filter_list
  - skylogistics:tag_filter_list
  - skylogistics:chora_nectar
---

# Vaults and Filters

The <ItemLink id="item_vault" /> stores items by type, and the <ItemLink id="fluid_vault" /> stores fluids by type. They begin with a small type limit. Right-click them with <ItemLink id="chora_nectar" /> to increase that limit; sneak-right-click uses as much of the held stack as possible.

<RecipeFor id="item_vault" fallbackText="The item vault recipe is unavailable." />
<RecipeFor id="fluid_vault" fallbackText="The fluid vault recipe is unavailable." />

Vaults can be opened by players and can also be accessed by logistics nodes, hoppers, and other automation. As line targets, they are ideal for collecting large amounts of repeated items or fluids.

The <ItemLink id="filter_list" /> limits node transfers. Right-click it to edit ghost items, whitelist or blacklist mode, NBT matching, durability matching, and other rules. Placing a configured filter list into a node face filter slot does not consume the item; the node copies that list state. Copying and pasting node settings also copies these filter states.

Use <ItemLink id="tag_filter_list" /> when a whole item or fluid tag should match.

<RecipeFor id="filter_list" fallbackText="The filter list recipe is unavailable." />
