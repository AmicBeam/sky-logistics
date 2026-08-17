# Sky Logistics

![Sky Logistics](docs/media/sky-logistics-header-16x10.png)

**Read this in other languages: [简体中文](README_CN.md)**

Celestial-themed logistics for Minecraft. Sky Logistics moves items, fluids, energy, and supported third-party resources through named wireless lines or lightweight local pipe networks, adds large aggregate vaults, and provides sky-themed tools for network configuration, filtering, portable transfer, and high-altitude offering recipes.

## Documentation

- **[Player Wiki](https://github.com/AmicBeam/sky-logistics/wiki)**: getting started, complete block/item reference, logistics, offerings, integrations, configuration, and troubleshooting.
- The Git-tracked Wiki source is maintained in [`wiki/`](wiki/README.md).

## Features

- **Performance-first design**: transfer work is scheduled with operation budgets, ready-line queues, hot slot tracking, capability caches, and endpoint backoff. These systems reduce unnecessary scanning and keep large logistics networks responsive.
- **Wireless logistics**: connect machines, vaults, and external storage interfaces through named logistics lines without laying physical pipes. Items, fluids, and energy share the same line model, one machine face can transfer multiple resource types simultaneously, and Dimension Upgrades allow transfers between loaded dimensions.
- **Simple local pipes**: affordable item, fluid, and energy pipes automatically connect to compatible adjacent containers and neighboring pipes of the same type. Each connected group forms a bounded local line with no line setup, GUI, or hidden buffer.
- **Fast placement and adjustment**: normal placement creates an insert endpoint on the clicked container, while sneak-placement creates an extract endpoint. The Sky Configurator switches pipe endpoints between insert and extract; the Celestial Wrench and compatible tagged wrenches disconnect or reconnect individual pipe sides.
- **High throughput**: normal nodes process 1 stack/t and Speed Upgrades raise that to 2 stacks/t. Standard item and FE operations use a configurable 2.1B-class limit. Direct transfers between long-capable storage endpoints can use a configurable limit of up to approximately 9.22e18 per operation. By default, each extracting simple pipe can move 64 items, 10,000 mB of fluid, 100,000 FE, 10,000 chemical units, 50 mana, or 50 Source per tick.
- **Multi-container access**: the Celestial Distributor lets one connected logistics node or simple pipe interact with a group of connected compatible containers. Incoming resources are divided as evenly as possible among accepting targets, while extraction aggregates available resources. A distributor supports 16 targets by default, configurable up to 64.
- **Built-in high-stack storage**: Celestial Item Vaults and Celestial Fluid Vaults aggregate resources by type. Each type can hold approximately 9.22e18 units in searchable terminal-style interfaces, while type limits remain expandable and configurable.
- **Inventory and backpack interaction**: the Sky Necklace adds portable extract, insert, and maintain modes between logistics lines, the player inventory, and supported Sophisticated Backpack inventories. Its two upgrade slots accept Dimension and Exact Quantity Upgrades.
- **Precise stock control**: the exact-quantity upgrade replaces matching-slot retention with an editable item total from 1 to `Integer.MAX_VALUE` on nodes and necklaces.
- **Chemical filtering**: on Mekanism-capable versions, chemical ingredients can be dragged from JEI into ordinary filter lists and are enforced at both ends of chemical transfers.
- **Mod integrations**: optional and configurable compatibility is available for Jade, JEI, Patchouli, Curios, Sophisticated Backpacks, Mekanism, Botania, and Ars Nouveau. Dedicated interfaces connect Sky Logistics lines to AE2, Refined Storage, and Beyond Dimensions networks when compatible versions and APIs are present.
- **Extended resources**: Mekanism chemicals use fluid-enabled faces and pipes. Botania mana and Ars Nouveau Source use energy-enabled faces and pipes, but move only between matching handlers and are never converted to FE.
- **AStages progression**: on Minecraft 1.20.1 and 1.21.1, servers can optionally limit and progressively unlock per-operation transfer amounts according to stages owned by the line owner. The integration requires AStages 2.x and is disabled by default.

## Requirements

This repository keeps the supported Minecraft versions in one branch. Each version directory is independently buildable.

- **Forge (Minecraft 1.20.1)**: use `versions/1.20.1`
  - Minecraft 1.20.1
  - Forge 47.x
  - Mekanism 10.4+ (optional)
  - Botania 1.20.1 (optional)
  - Ars Nouveau 4.x (optional)
  - AStages 2.x (optional, disabled by default)
  - AE2 15.2+ (optional)
  - Refined Storage 1.12+ (optional)
  - Beyond Dimensions 0.7.5+ (optional)
  - Jade 11.x / JEI 15.x API jars are optional for compiling the matching compatibility source sets
- **NeoForge (Minecraft 1.21.1)**: use `versions/1.21.1`
  - Minecraft 1.21.x
  - NeoForge 21.1+
  - Jade 15+ (optional)
  - JEI 19+ (optional, client side)
  - Patchouli 1+ (optional)
  - Mekanism 10.7+ (optional)
  - Ars Nouveau 5.x (optional)
  - Curios 9+ (optional)
  - Sophisticated Backpacks 3.25+ (optional)
  - AStages 2.x (optional, disabled by default)
  - AE2 19+ (optional)
  - Refined Storage 2+ (optional)
  - Beyond Dimensions 0.7.6+ (optional)
- **NeoForge (Minecraft 26.1.2)**: use `versions/26.1.2`
  - Minecraft 26.1.2
  - NeoForge 26.1.2.71+
  - Java 25
  - Jade, JEI, Mekanism, Botania, Ars Nouveau, AE2, Refined Storage, and Beyond Dimensions integrations are optional and enabled only when compatible APIs are present
  - AStages is not supported in this version

## Installation

1. Put the Sky Logistics jar for your Minecraft/loader version into the `mods` folder
2. Install any optional integration mods you want to use
3. Start the game

## Usage

1. Craft Simple Item, Fluid, or Energy Pipes for an inexpensive early-game logistics network. Normal placement creates an insert endpoint on the clicked container, while sneak-placement creates an extract endpoint.
2. Use the Sky Configurator to switch machine-facing pipe endpoints between insert and extract. Use the Celestial Wrench or another compatible tagged wrench to disconnect and reconnect pipe sides.
3. Charge Eulogia Crystals above Y 200 by default, or at the configured ritual height, and use them to craft Celestial Stone and advanced Sky Logistics components.
4. Build a multiblock Sky Offering Altar with Offering Tables to produce Chora Nectar and other offering materials.
5. Place Celestial Item Vaults or Celestial Fluid Vaults as aggregate storage endpoints.
6. Place Sky Logistics Nodes against machines, vaults, distributors, or external storage interfaces. Normal placement creates insert mode, while sneak-placement creates extract mode.
7. Use the Sky Configurator to create and manage named lines, copy and paste node settings, and preset newly placed nodes from the offhand.
8. Add Filter Lists, Tag Filter Lists, Speed Upgrades, Dimension Upgrades, and Exact Quantity Upgrades as needed.
9. Use the Sky Necklace with a whitelist filter to extract, insert, or maintain items between a logistics line and the player inventory or supported backpacks.
10. Install the appropriate integration mods to connect Sky Logistics directly to AE2, Refined Storage, or Beyond Dimensions networks. Available resource paths depend on the installed mod version and compatible add-ons.

## Notes

- Wireless logistics nodes directly pair loaded extract faces with loaded insert faces on the same named line. Simple pipes provide a separate block-by-block local option and form bounded lines only with adjacent pipes of the same type.
- Lines have no hidden item/fluid/energy buffer. If a target cannot accept a resource, the source is not extracted first.
- Simple pipes have no GUI and are always active when enabled. Their local lines reuse the logistics-node transfer engine with independent per-resource rate limits. One line contains at most 256 pipe blocks by default.
- The Celestial Distributor is a zero-buffer routing proxy used only by Sky Logistics nodes and simple pipes. Vanilla hoppers and third-party pipes cannot connect to it as a general-purpose inventory or tank.
- Mekanism chemicals use fluid-enabled faces. Botania mana and Ars Nouveau Source use energy-enabled faces, but they are moved only to matching resource handlers and are not converted to FE.
- Direct 9.22e18-class transfers require both endpoints to support long amounts. This is a per-operation limit, not a guaranteed per-tick throughput rate.
- Line IDs are stable for their display names, so unchanged/reused line names continue to point at the same line.
- Node transfer work is budgeted and cached with ready-line queues, hot slot tracking, capability caches, and endpoint backoff.
- Sky Necklace work interval is configurable with `skyNecklaceTickInterval` in the server config. The default is 10 ticks. `skyNecklaceTargetAttemptsPerWork` bounds output endpoint visits per interval and defaults to 1.
- Vault type limits, node item/energy transfer limits, direct sky-container transfer limits, distributor target and operation budgets, hot slot cache size, ritual height, and crystal charge time are configurable.
- Simple pipe limits are configurable independently through `simpleItemPipeTransferRate`, `simpleFluidPipeTransferRate`, `simpleEnergyPipeTransferRate`, `simpleChemicalPipeTransferRate`, `simpleManaPipeTransferRate`, and `simpleSourcePipeTransferRate`. `simplePipeMaxConnectedBlocks` controls the maximum size of one connected pipe line.
- AStages controls per-operation limits rather than operation frequency. It does not increase Speed Upgrade rates or server and line operation budgets.
- Patchouli support is data-only and appears when Patchouli is installed.
- Optional mod integrations are enabled only when the matching mod and compatible version/API are present.

## Build

Build all three supported versions from the repository root:

```bash
./scripts/build_all_versions.sh
```

Build one version directly:

```bash
cd versions/1.21.1
./gradlew --no-daemon clean build

cd ../1.20.1
./gradlew --no-daemon clean build

cd ../26.1.2
./gradlew --no-daemon clean build
```

The `common` directory contains shared source and resource files. Version-specific Forge, NeoForge, mapping, dependency, and API code stays under `versions/<minecraft-version>`.

## License

MIT. See the `LICENSE` file in each version directory.
