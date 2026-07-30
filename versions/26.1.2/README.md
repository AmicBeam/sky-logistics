# Sky Logistics / 天穹物流

NeoForge 26.1.2 public test build for celestial wireless logistics.

## Current MVP

- `Celestial Item Vault` / `天穹物品库`
  - Aggregate item storage.
  - Starts with 1 item type.
  - Stackable items are stored as `long` amounts internally.
  - Exposes NeoForge item handler capability.
  - Opens a terminal-style UI with searchable, scrollable storage rows and the player inventory below.

- `Celestial Fluid Vault` / `天穹流体库`
  - Aggregate fluid storage.
  - Starts with 1 fluid type.
  - Exposes NeoForge fluid handler capability.
  - Opens a terminal-style UI with searchable, scrollable fluid rows and the player inventory below.

- `Sky Logistics Node` / `天穹物流节点`
  - Placed against a machine/container side.
  - Main-hand node placement blocks the target machine GUI.
  - Normal right-click links in insert mode; sneak right-click links in extract mode.
  - Stores a line id, line name, per-face connection modes, item/fluid/energy toggles, priority, redstone control and operation rate.
  - New line ids are stably derived from their line names; placing without an offhand configurator uses the player's `name-0` line.
  - The node GUI configures all six faces independently as disconnected, extract or insert and shows adjacent block icons.
  - The node GUI has a More page for the selected face: every face can set priority and redstone control.
  - Transfer targets are grouped by priority and round-robined within the same priority.
  - The node GUI owns transfer rate, a filter-list slot and the player inventory below it.
  - The model is smaller than a full block and shows a larger connector ring in extract mode.
  - Server tick dispatcher transfers items, fluids and energy wirelessly between matching extract/insert faces on the same line.
  - Fluid-enabled faces can also transfer Mekanism chemicals; energy-enabled faces can also transfer Botania mana and Ars Nouveau Source when the matching optional mods and server config toggles are enabled.
  - A dimension upgrade on an extract node lets it send to matching insert faces on the same line in other dimensions.
  - The dispatcher keeps a dirty-rebuilt line index, a ready-line wake queue, cached cross-dimensional outputs, target capability caches and idle/failed endpoint backoff.
  - Right-click opens a lightweight node GUI.

- `Simple Celestial Pipes and Sky Wrench` / `天穹简易管道与天穹扳手`
  - Item, fluid and energy pipes are separate blocks with their own blue/orange, blue/deep-blue and blue/red models.
  - Placement inherits the logistics node controls: normal placement prefers insert mode and sneak placement prefers extract mode.
  - Pipes automatically connect to compatible adjacent containers and neighboring pipes of the same type. Connected pipes form a bounded local line.
  - Use any item in the wrench tag, including the Sky Wrench, on a machine-facing endpoint to switch between insert and extract. Sneak-right-click a pipe connection to disconnect or reconnect that side. Extract sections use a wider model.
  - The Sky Wrench is registered only when neither Applied Energistics 2 nor Refined Storage is installed; packs with either mod use their existing wrench-tag tools instead.
  - Pipes have no GUI or hidden buffer and are always active when their type is enabled.
  - They reuse the logistics-node scheduler and add only per-resource rate limits. Defaults are 64 items/t, 10,000 mB/t and 100,000 FE/t; an item transfer touches at most one source slot and one target slot.
  - Fluid pipes also support Mekanism chemicals. Energy pipes also support Botania mana and Ars Nouveau Source when the corresponding integration toggle is enabled and a matching handler exists.
  - Chemical, mana and Source limits have independent server settings. `simplePipeMaxConnectedBlocks` defaults to 256; a connection that would exceed it stays disconnected.
  - Their recipes do not require Sky Crystals.

- `Starlit Nectar` / `星辉甘露`
  - Core sky material, no longer only a capacity item.
  - Right-click a Celestial Item Vault or Fluid Vault to increase its type limit by 1.
  - Sneak-right-click uses as many items from the held stack as possible, up to the configured vault type limit.
  - Required by the base Celestial Item Vault and Fluid Vault recipes.

- `Sky Crystal` / `天穹水晶`
  - Crafted in a vanilla crafting table.
  - Starts uncharged.
  - Charges at or above the configured sky ritual height while in a player inventory or in a Sky Offering Table. The default is `Y >= 200`.
  - Uses item damage state to switch to a charged model.
  - A charged crystal plus eight `#c:stones` items crafts Celestial Stone.

- `Celestial Stone` / `天穹石`
  - Decorative/structure block for the sky offering circle.
  - Includes slab, stairs and wall variants.

- `Celestial Glass` / `天穹玻璃`
  - Full-bright, high-clarity glass block with connected outer-frame rendering.
  - Crafted from charged Sky Crystal and glass.
  - Place on top of the four outer corner pillars to make a tier 2 altar.

- `Sky Offering Altar` / `天穹供奉祭坛`
  - Single-slot block entity with no GUI.
  - Players insert/extract items directly by right-clicking; item handlers allow pipe transport.
  - Displays the stored item on top.
  - Requires the configured sky ritual height and a valid multiblock to work. The default is `Y >= 200`.
  - Runs data-driven `skylogistics:sky_offering` recipes.
  - Starlit Nectar requires a tier 2 altar.

- `Sky Offering Table` / `天穹供桌`
  - Single-slot block entity with no GUI.
  - Displays the stored item on top.
  - Can charge Sky Crystals at or above the configured sky ritual height. The default is `Y >= 200`.
  - Four tables around an altar provide offering inputs.

- `Sky Configurator` / `天穹配置器`
  - Right-click opens its GUI instead of cycling line/mode directly.
  - The GUI configures line name plus item/fluid toggles, and can enter paste mode.
  - In paste mode, right-clicking a node pastes the tool config instead of opening the node GUI.
  - Sneak right-click, opening the configurator GUI or no longer holding the configurator exits paste mode.
  - Right-click a node with the configurator outside paste mode opens the node GUI with a `Copy Config` action.
  - Hold in offhand while placing a node: new node inherits line/type toggles from the tool while preserving placement mode.

- `Sky Filter List` / `天穹过滤列表`
  - Right-click opens a filter GUI.
  - Supports 18 ghost filter entries, whitelist/blacklist mode and optional NBT/durability matching.
  - Node face filter slots copy the filter list state as a ghost reference; inserting or pasting one does not consume the item.
  - Insert a configured filter list into a node's filter slot to filter both extraction and insertion item transfers.

Crafting recipes are included for the current item/block set. Starlit Nectar is produced by the included `skylogistics:sky_offering` recipe and requires a tier 2 altar.

GuideME support adds a data-driven Sky Logistics manual for 26.1.2 when GuideME is installed. Jade and JEI compatibility sources under `src/jade/java` and `src/jei/java` are compiled when matching 26.1.2 NeoForge API jars are available through `SKYLOGISTICS_JADE_API_JAR` / `SKYLOGISTICS_JEI_API_JAR`, `-Dskylogistics.jadeApiJar` / `-Dskylogistics.jeiApiJar`, `/private/tmp/jade-api.jar`, or the local Gradle cache.

## Build Note

Use Java 25:

```bash
env JAVA_HOME=/Users/bytedance/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x/jdk-25.0.3+9/Contents/Home \
  PATH=/Users/bytedance/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x/jdk-25.0.3+9/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ./gradlew --no-daemon build
```

The verified output jar is `build/libs/skylogistics-0.3.0+26.1.2.jar`.

This build uses NeoForge ModDev, a Java 25 toolchain, and NeoForge `26.1.2.76`. Runtime metadata is generated from `src/main/templates/META-INF/neoforge.mods.toml`.

Data resources use 1.21 singular paths such as `data/skylogistics/recipe`, `loot_table`, `advancement`, `tags/block`, and `tags/item`. Crafting outputs use `result.id`, common tags use the `c:` namespace, and optional recipe conditions use `neoforge:conditions`.

The optional Jade and JEI compatibility sources are included when their matching API jars are present.
