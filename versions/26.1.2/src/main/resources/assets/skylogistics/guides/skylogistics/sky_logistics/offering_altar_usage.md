---
navigation:
  title: Using the Offering Altar
  icon: offering_altar
  parent: offerings.md
  position: 2
item_ids:
  - skylogistics:offering_altar
  - skylogistics:offering_table
---

# Using the Offering Altar

The <ItemLink id="offering_altar" /> and <ItemLink id="offering_table" /> each have one offering slot that can hold up to 64 items, with no GUI. Right-click with an item to insert it, or right-click empty-handed to extract. Pipes and hopper-like automation can access the item slot.

<RecipeFor id="offering_altar" fallbackText="The offering altar recipe is unavailable." />
<RecipeFor id="offering_table" fallbackText="The offering table recipe is unavailable." />

The altar consumes a charged Eulogia Crystal, while the offering table only requires Celestial Stone.

The altar must be at the configured sky height or higher and inside a valid offering circle. Put the main ingredient on the altar and the offerings on the four side tables; table order does not matter as long as every ingredient matches.

Once the ingredients match, the altar starts its particles and counts the recipe duration. When the duration completes, inputs vanish at once and the result appears in the altar slot. If it cannot fit, the remainder is dropped above the altar.

The altar does not scan constantly when no recipe is present. It wakes when its own item or a neighboring table changes, and after world loading. Jade shows a non-empty offering slot; empty slots are left unlisted.
