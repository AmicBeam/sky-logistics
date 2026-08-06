# Sky Logistics

**Read this in other languages: [简体中文](README_CN.md)**

Celestial logistics for Minecraft. Sky Logistics moves items, fluids, energy, and supported third-party resources through named wireless lines or lightweight local pipe runs, adds large aggregate vaults, and provides sky-themed tools for configuring networks, filters, portable transfer, and high-altitude offering recipes.

## Features

- **Wireless by design**: connect machines, vaults, and interfaces through named logistics lines instead of pipe runs. Items, fluids, and energy use the same line model, with optional cross-dimensional transfer.
- **Simple local pipes**: item, fluid, and energy pipes automatically connect to nearby compatible containers. Connected pipes form bounded local lines and use the same transfer engine as logistics nodes without adding GUIs or hidden buffers.
- **High throughput**: normal nodes process 1 stack/t and speed upgrades raise that to 2 stacks/t. Item and energy transfers use a 2.1B-class default per-operation cap, while direct Sky Logistics vault-to-vault transfers use a 9e18-class default cap.
- **Server-friendly performance**: transfer work is scheduled with operation budgets, ready-line queues, hot slot tracking, capability caches, and endpoint backoff so large networks stay responsive without constantly scanning the world.
- **Fast placement and setup**: node placement switches mode based on sneaking, and the Sky Configurator handles line management, copy/paste configuration, and offhand placement presets.
- **Built-in high-stack storage**: Celestial Item Vaults and Celestial Fluid Vaults stack 9e18-class amounts per type in searchable terminal-style views, while type limits stay expandable and configurable.
- **Inventory and backpack interaction**: the Sky Necklace adds portable transfer between logistics lines, the player inventory, and supported backpack inventories.
- **Mod integrations**: optional compatibility is available for Jade, JEI, Patchouli, Curios, Sophisticated Backpacks, Mekanism, Botania on 1.20.1, and Ars Nouveau, plus high-throughput links for AE2, Refined Storage, and Beyond Dimensions depending on the Minecraft version.

## Requirements

This repository keeps the supported Minecraft versions in one branch. Each version directory is independently buildable.

- **Forge (Minecraft 1.20.1)**: use `versions/1.20.1`
  - Minecraft 1.20.1
  - Forge 47.x
  - Mekanism 10.4+ (optional)
  - Botania 1.20.1 (optional)
  - Ars Nouveau 4.x (optional)
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
  - AE2 19+ (optional)
  - Refined Storage 2+ (optional)
  - Beyond Dimensions 0.7.6+ (optional)
- **NeoForge (Minecraft 26.1.2)**: use `versions/26.1.2`
  - Minecraft 26.1.2
  - NeoForge 26.1.2.71+
  - Java 25
  - Jade, JEI, Mekanism, Botania, Ars Nouveau, AE2, Refined Storage, and Beyond Dimensions integrations are optional and enabled only when compatible APIs are present

## Installation

1. Put the Sky Logistics jar for your Minecraft/loader version into the `mods` folder
2. Install any optional integration mods you want to use
3. Start the game

## Usage

1. Charge Eulogia Crystals at the configured sky ritual height, then use them to craft Celestial Stone and sky logistics components
2. Build a Sky Offering Altar setup with Offering Tables to make Chora Nectar and other offering-based components
3. Place Celestial Item Vaults or Celestial Fluid Vaults as aggregate storage endpoints
4. Place Sky Logistics Nodes against machines, vaults, or interfaces; normal placement creates insert mode, sneak placement creates extract mode
5. For short local routes, place Simple Item, Fluid, or Energy Pipes. They inherit the node placement controls, automatically connect to compatible adjacent containers, and join neighboring pipes of the same type.
6. Right-click a machine-facing endpoint with the Sky Wrench to switch between insert and extract; sneak-right-click a pipe connection to disconnect or reconnect that side. Extract sections use the wider connector model.
7. Use the Sky Configurator to create/select lines, rename them, copy node settings, paste settings, and preset newly placed nodes from the offhand
8. Add Sky Filter Lists, Speed Upgrades, and Dimension Upgrades to nodes when needed
9. Use a Sky Necklace with a whitelist filter list for portable extraction/insertion between player inventory and a logistics line

## Notes

- Wireless logistics nodes directly pair loaded extract faces with loaded insert faces on the same named line. Simple pipes provide a separate block-by-block local option and form bounded lines only with adjacent pipes of the same type.
- Lines have no hidden item/fluid/energy buffer. If a target cannot accept a resource, the source is not extracted first.
- Simple pipes have no GUI and are always active when enabled. Their local lines reuse the logistics-node transfer engine with additional per-resource rate limits.
- Mekanism chemicals use fluid-enabled faces. Botania mana and Ars Nouveau Source use energy-enabled faces, but they are moved only to matching resource handlers and are not converted to FE.
- Line ids are stable for their display names, so unchanged/reused line names continue to point at the same line.
- Node transfer work is budgeted and cached with ready-line queues, hot slot tracking, capability caches, and endpoint backoff.
- Sky Necklace work interval is configurable with `skyNecklaceTickInterval` in the server config. The default is 10 ticks. `skyNecklaceTargetAttemptsPerWork` bounds output endpoint visits per interval and defaults to 1.
- Vault type limits, node item/energy transfer limits, direct sky-container transfer limits, operation budgets, hot slot cache size, ritual height, and crystal charge time are configurable.
- Simple pipe limits are configurable independently through `simpleItemPipeTransferRate`, `simpleFluidPipeTransferRate`, `simpleEnergyPipeTransferRate`, `simpleChemicalPipeTransferRate`, `simpleManaPipeTransferRate`, and `simpleSourcePipeTransferRate`. `simplePipeMaxConnectedBlocks` controls the maximum size of one connected pipe line.
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
