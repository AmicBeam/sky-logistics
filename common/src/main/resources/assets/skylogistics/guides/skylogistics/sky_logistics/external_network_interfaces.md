---
navigation:
  title: External Network Interfaces
  icon: sky_me_interface
  parent: logistics.md
  position: 3
item_ids:
  - skylogistics:sky_me_interface
  - skylogistics:sky_rs_interface
  - skylogistics:sky_dimension_interface
---

# External Network Interfaces

External network interfaces connect large storage networks from other mods to a Sky Logistics line. All three use the node-style screen for line, mode, resources, upgrades, redstone, priority, and filters. Enable the resources you want to move after placing an interface; placement with a configurator applies its saved toggles. The interface stores no resources, so rejected resources remain in the original network.

## Sky ME Interface

The <ItemLink id="sky_me_interface" /> is available with Applied Energistics 2. It moves items and fluids in an ME network. With AppFlux, Applied Mekanistics, Applied Botanics, or Ars Énergistique installed, it can also access FE, chemicals, mana, or Source. On Minecraft 1.21.1, Soulplied Energistics additionally enables Warden Soul access, including souls stored in AppliedSoul cells.

<RecipeFor id="sky_me_interface" fallbackText="Install AE2 to view the Sky ME Interface recipe." />

## Sky RS Interface

The <ItemLink id="sky_rs_interface" /> is available with Refined Storage and exposes items and fluids in an RS network. Item and fluid paths can be disabled independently.

<RecipeFor id="sky_rs_interface" fallbackText="Install Refined Storage to view the Sky RS Interface recipe." />

## Sky Dimension Interface

The <ItemLink id="sky_dimension_interface" /> is available with Beyond Dimensions and accesses items, fluids, and FE in its network. With Mekanism or Ars Nouveau installed, it can also access chemicals or Source. On Minecraft 1.21.1, Industrial Foregoing: Souls additionally enables Warden Soul access. Warden Souls use fluid-enabled faces, pipes, transfer limits, and filters. It connects third-party storage and is not the same as a Dimension Upgrade for ordinary nodes.

<RecipeFor id="sky_dimension_interface" fallbackText="Install Beyond Dimensions to view the interface recipe." />

## Filters and Limits

Extracting items from an external network requires a whitelist containing at least one concrete item or it will not work. Tag and mod filters cannot replace it. Dimension Upgrades do not load chunks, so unloaded targets are skipped.
