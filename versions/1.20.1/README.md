# Sky Logistics / 天穹物流

Forge 1.20.1 public test build for celestial wireless logistics.

## Current MVP

- `Celestial Item Vault` / `天穹物品库`
  - Aggregate item storage.
  - Starts with 1 item type.
  - Stackable items are stored as `long` amounts internally.
  - Exposes Forge item handler capability.
  - Opens a terminal-style UI with searchable, scrollable storage rows and the player inventory below.

- `Celestial Fluid Vault` / `天穹流体库`
  - Aggregate fluid storage.
  - Starts with 1 fluid type.
  - Exposes Forge fluid handler capability.
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
  - A dimension upgrade on an extract node lets it send to matching insert faces on the same line in other dimensions.
  - The dispatcher keeps a dirty-rebuilt line index, a ready-line wake queue, cached cross-dimensional outputs, target capability caches and idle/failed endpoint backoff.
  - Right-click opens a lightweight node GUI.

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
  - A charged crystal plus eight `#forge:stone` items crafts Celestial Stone.

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

Patchouli support is data-only and adds one guide book plus one multiblock preview when Patchouli is installed. JEI support is optional source code: provide `-Dskylogistics.jeiApiJar=/path/to/jei-api.jar` to compile the Sky Offering category.

## Build Note

Use the cached JDK 17 explicitly, matching the Recipe Linkage 1.20.1 build style. Prefer offline mode in this workspace:

```bash
env JAVA_HOME=/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home \
  PATH=/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ./gradlew --no-daemon --offline \
  -Dskylogistics.offlineRepo=/private/tmp/skylogistics-offline-maven \
  -Dskylogistics.jadeApiJar=/path/to/Jade-1.20.1-Forge-11.x.x.jar \
  clean build
```

`javac` source compilation passes against the local Forge mapped jar. Full Gradle build also passes with `--offline` when `skylogistics.offlineRepo` points at a local Maven-style repository containing cached Forge dependency jars. The produced public test jar is `build/libs/skylogistics-0.0.3+1.20.1.jar`.

Jade support is optional and lives under `src/jade/java`. To include it in a local build, provide a real Jade 1.20.1 11.x API/mod jar with `-Dskylogistics.jadeApiJar=/path/to/Jade-1.20.1-Forge-11.x.x.jar`. Do not use the old minimal `/private/tmp/jade-api.jar` stub: it lacks `snownee.jade.api.Accessor`, compiles an incompatible `IServerDataProvider` bridge, and causes Jade data requests to fail at runtime.

JEI support is optional and lives under `src/jei/java`. To include the Sky Offering recipe category in a local build, provide a JEI API jar with `-Dskylogistics.jeiApiJar=/path/to/jei-api.jar`.

Online builds in this workspace can still print or fail on SSL certificate validation when ForgeGradle resolves `libraries.minecraft.net`.

Recipe Linkage is not a single comparable build path:

- The current `recipe-linkage` checkout is the `1.21.1` branch and uses NeoForge ModDev with Java 21 toolchains.
- The `recipe-linkage` `1.20.1` branch uses ForgeGradle 6.0.54, Forge 47.1.3 and the same JDK 17 startup command as this project.
- A clean online ForgeGradle 1.20.1 build can hit the same `libraries.minecraft.net` certificate problem in this workspace, so prefer cached/offline builds until the network/JDK trust state is fixed.
