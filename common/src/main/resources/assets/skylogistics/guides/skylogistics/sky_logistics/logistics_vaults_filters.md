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

Both vaults remain normally mineable while resisting explosions, lava, Withers, and the Ender Dragon. Sneak-right-click either vault with the Celestial Wrench or a compatible wrench to recover it instantly.

Vault terminals support display-name search and quantity, name, or mod sorting. In an Item Vault, empty-hand left-click takes a stack, right-click takes one, and Shift sends it directly to inventory; with a carried stack, left-click inserts all and right-click inserts one. A Fluid Vault uses fluid-capable containers for filling and draining.

The <ItemLink id="filter_list" /> stores exact item, fluid, and supported chemical samples in 18 ghost slots. It supports whitelist or blacklist, data/component matching, and durability matching. Placing a configured list into a node face filter slot, or right-clicking a simple-pipe endpoint with it, copies the rules without consuming the item. Later edits to the original do not update copied rules.

Use <ItemLink id="tag_filter_list" /> when a whole item or fluid tag should match. It also has a mod mode that matches resource ID namespaces; FE uses the virtual mod ID `forge`. External item extraction still requires a whitelist containing concrete items, so tag or mod filters cannot replace it.

<RecipeFor id="filter_list" fallbackText="The filter list recipe is unavailable." />
