# Sky Logistics

**Read this in other languages: [简体中文](README_CN.md)**

Celestial wireless logistics for Minecraft. Sky Logistics moves items, fluids, and FE through named wireless lines, adds large aggregate vaults, and provides sky-themed tools for configuring networks, filters, portable transfer, and high-altitude offering recipes.

## Features

- **Wireless by design**: connect machines, vaults, and interfaces through named logistics lines instead of pipe runs. Items, fluids, and FE use the same line model, with optional cross-dimensional transfer.
- **High throughput**: normal nodes process 1 stack/t and speed upgrades raise that to 2 stacks/t. Item and energy transfers use a 2.1B-class default per-operation cap, while direct Sky Logistics vault-to-vault transfers use a 9e18-class default cap.
- **Server-friendly performance**: transfer work is scheduled with operation budgets, ready-line queues, hot slot tracking, capability caches, and endpoint backoff so large networks stay responsive without constantly scanning the world.
- **Fast placement and setup**: node placement switches mode based on sneaking, and the Sky Configurator handles line management, copy/paste configuration, and offhand placement presets.
- **Built-in high-stack storage**: Celestial Item Vaults and Celestial Fluid Vaults stack 9e18-class amounts per type in searchable terminal-style views, while type limits stay expandable and configurable.
- **Inventory and backpack interaction**: the Sky Necklace adds portable transfer between logistics lines, the player inventory, and supported backpack inventories.
- **Mod integrations**: optional compatibility is available for Jade, JEI, Patchouli, Curios, Sophisticated Backpacks, and Mekanism, plus high-throughput links for AE2, Refined Storage, and Beyond Dimensions depending on the Minecraft version.

## Requirements

This repository keeps the supported Minecraft versions in one branch. Each version directory is independently buildable.

- **Forge (Minecraft 1.20.1)**: use `versions/1.20.1`
  - Minecraft 1.20.1
  - Forge 47.x
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
  - Curios 9+ (optional)
  - Sophisticated Backpacks 3.25+ (optional)
  - AE2 19+ (optional)
  - Refined Storage 2+ (optional)
  - Beyond Dimensions 0.7.6+ (optional)

## Installation

1. Put the Sky Logistics jar for your Minecraft/loader version into the `mods` folder
2. Install any optional integration mods you want to use
3. Start the game

## Usage

1. Charge Eulogia Crystals at the configured sky ritual height, then use them to craft Celestial Stone and sky logistics components
2. Build a Sky Offering Altar setup with Offering Tables to make Chora Nectar and other offering-based components
3. Place Celestial Item Vaults or Celestial Fluid Vaults as aggregate storage endpoints
4. Place Sky Logistics Nodes against machines, vaults, or interfaces; normal placement creates insert mode, sneak placement creates extract mode
5. Use the Sky Configurator to create/select lines, rename them, copy node settings, paste settings, and preset newly placed nodes from the offhand
6. Add Sky Filter Lists, Speed Upgrades, and Dimension Upgrades to nodes when needed
7. Use a Sky Necklace with a whitelist filter list for portable extraction/insertion between player inventory and a logistics line

## Notes

- Sky Logistics is not a block-by-block pipe network. It directly pairs loaded extract faces with loaded insert faces on the same line.
- Lines have no hidden item/fluid/energy buffer. If a target cannot accept a resource, the source is not extracted first.
- Line ids are stable for their display names, so unchanged/reused line names continue to point at the same line.
- Node transfer work is budgeted and cached with ready-line queues, hot slot tracking, capability caches, and endpoint backoff.
- Sky Necklace work interval is configurable with `skyNecklaceTickInterval` in the server config. The default is 10 ticks.
- Vault type limits, node item/energy transfer limits, direct sky-container transfer limits, operation budgets, hot slot cache size, ritual height, and crystal charge time are configurable.
- Patchouli support is data-only and appears when Patchouli is installed.
- Optional mod integrations are enabled only when the matching mod and compatible version/API are present.

## Build

Build both versions from the repository root:

```bash
./scripts/build_all_versions.sh
```

Build one version directly:

```bash
cd versions/1.21.1
./gradlew --no-daemon clean build

cd ../1.20.1
./gradlew --no-daemon clean build
```

The `common` directory contains shared source and resource files. Version-specific Forge, NeoForge, mapping, dependency, and API code stays under `versions/<minecraft-version>`.

## License

MIT. See the `LICENSE` file in each version directory.
