# 空域物流模组设计方案

> 工作名：`Sky Logistics` / `Celestial Logistics`
>
> MVP 目标版本：Minecraft 1.20.1 Forge
>
> 版本策略：后续 1.21.1、NeoForge 或其他 MC 版本作为独立 Git 分支维护，不在 MVP 主线里同时兼容。
>
> 核心概念：借助天界/空域的力量进行存储、传输和线路化物流；前期通过供奉获取关键材料。

## 1. 设计目标

本模组要解决的不是“管道速度不够快”，而是传统管道模组在大规模自动化里暴露出的三个根本问题：

1. 大量管道方块、方块实体或每 tick 扫描带来的服务器负担。
2. 后期铺线、装插件、调输入输出、调过滤的重复劳动。
3. 在复杂机器、移动结构、存档重载等环境下，连接状态不稳定或行为不透明。

因此本模组采用“空域端点 + 线路 ID + 无中间缓存 + 聚合存储”的路线。玩家仍然能用“管道系列”的方式理解和布置，但服务端不做逐格管道寻路，也不在管道中暂存资源；真正传输由网络调度器按线路直接完成。

## 2. 参考项目与可借鉴点

### Pipez

参考仓库：[henkelmax/pipez](https://github.com/henkelmax/pipez)

本次核对版本：`eacbe01ddca14bbe7ebc822b606a2cc8ce485702`。

可借鉴：

- 轻量、便宜、易理解的“抽取端 + 升级”玩法。
- 物品、流体、能量和通用管道的统一体验。
- 过滤、分配、速率升级等玩家已经熟悉的概念。
- 连接列表和 capability handler 使用缓存，避免每 tick 重新沿管道寻路。
- 物品管道使用固定速度间隔：无升级 20t，基础 15t，强化 10t，高级 5t，终极 1t；这保证单槽高频机器不会因为长退避睡过头。

暴露问题：

- 主动运输模型如果目标不可输入、源端不可抽取或 capability 状态异常，容易产生无效尝试。
- 高速或轮询模式下，如果按槽、按物品数量或按目标反复扫描，会在大吞吐量场景造成明显卡顿。
- 插件和输入输出配置分散在每根管道/每个端口上，后期堆量非常折磨。
- 分配模式每次工作时仍会对目标连接排序或 shuffle；过滤匹配也在实际移动谓词里逐目标执行。多接收方、带过滤、目标接近满时，成本会随目标数和源槽数上升。
- 其优势不是复杂场景算法更强，而是行为很直接：没有很长失败退避，玩家感知延迟稳定。

### PipezLagFix

参考仓库：[Almana-mc/PipezLagFix](https://github.com/Almana-mc/PipezLagFix)

可借鉴：

- “eco mode / 退避模式”方向是正确的：当传输失败或没有必要运行时，降低扫描频率。

我们的取舍：

- 不把退避作为补丁，而是作为核心调度规则。线路、端点和目标都必须有失败冷却、唤醒条件和硬预算。

### fastpipes

参考仓库：[bigenergy/fastpipes](https://github.com/bigenergy/fastpipes)

可借鉴：

- Extractor / Inserter / Void / Sensor 等附件化设计清晰。
- 优先级、轮询、最近/最远、随机等分配模式直观。
- 染色隔离网络、集中终端、Jade 信息展示都适合玩家理解。

我们的取舍：

- 保留“端点有角色、线路有策略”的交互语言。
- 不采用大量可见管道实体承载逻辑；可见管道只作为装饰、占位或低成本视觉连接。

### Create

参考仓库：[Creators-of-Create/Create](https://github.com/Creators-of-Create/Create)

本次核对版本：`mc1.21.1/dev`，`fdfde66c337e3ae36b260459c3b52802c58630f6`。

可借鉴：

- 静态机器和容器仍通过 capability 暴露，适合本模组节点按侧面直接传输。
- 动态结构使用 `Contraption` / `AbstractContraptionEntity`，组装时把方块实体从世界移除，移动中由 contraption-local 坐标和 `MountedStorageManager` 管理存储。
- 移动交互通过 `MovingInteractionBehaviour`，不是普通世界方块右键。
- 可通过 `ContraptionMovementSetting`、`BlockMovementChecks` 或 `create:non_movable` 一类标签控制方块是否可被机械结构搬运。
- Create 的 mounted storage API 适合未来给天仓/天池做专门被动存储适配。
- SophisticatedCore 的 Create 兼容证明了可行路线：为自定义存储实现 Create `MountedItemStorage`，移动中用 contraption local pos 找 mounted storage，内容走独立 SavedData/UUID，GUI 和同步走 contraption entity id + local pos，而不是继续依赖世界方块实体。
- SophisticatedBackpacksCreateIntegration 展示了完整落地：注册 mounted storage type、注册 movement behaviour、移动中 tick 升级和拾取逻辑、打开 mounted GUI、同步移动中 stack 和渲染状态，并在 schematic 写入时移除真实存储 UUID。

暴露问题：

- 只依赖 `BlockPos + Direction` 的端点在 contraption 移动中会失效，因为原方块实体已经不在世界坐标处。
- Create 的 fallback mounted item storage 只接受普通 `ItemStackHandler` 级别的库存；本模组天仓是 `vaultId -> key -> long amount` 的聚合存储，不能指望 fallback 自动正确搬运。
- 流体天池也需要专门 mounted fluid storage，否则移动中不会成为 Create 可交互的流体存储。
- 如果允许天仓/天池被整车拾取成物品或跨世界移动，`vaultId` 指向世界 SavedData 的语义必须重新定义，否则会出现内容丢失、复制或跨维归属不清的问题。

我们的取舍：

- 静态 Create 机器/容器按普通 capability 兼容，不需要专门联动。
- MVP 不承诺移动 contraption 兼容，也不交付额外 Create compat 附属模组。
- 真正的 Create 动态结构支持规划为未来独立附属模组 `skylogistics_create`，不放进核心 `skylogistics` 主线；核心最多提供无硬依赖的默认不可移动保护。
- 在未来 `skylogistics_create` 中，默认应把物流节点标记为不可移动；天仓/天池也应先不可移动，直到有专门 mounted storage 和 SavedData 归属策略。
- 若后续要支持被动移动存储，只给天仓/天池实现 Create mounted storage，不让节点在移动中执行物流。实现应参考 SophisticatedBackpacksCreateIntegration：mounted adapter 代理存储操作，MovementBehaviour 负责移动中 tick/位置/清理 contraption NBT，菜单使用 entity/local pos 校验和同步。
- 若后续要支持移动节点，必须引入 `EntityEndpointAddress`、contraption entity UUID、local pos、动态 side 映射、移动交互 GUI 和组装/解体生命周期同步；这与航空学移动结构是同一类兼容层，不应混入 MVP 主线。

### CreatePrism

参考仓库：[adonis-baffin/CreatePrism](https://github.com/adonis-baffin/CreatePrism)

本次核对版本：临时克隆 `main` 分支，用于参考玻璃 casing 的视觉结构。

可借鉴：

- 玻璃 casing 用透明主体加高亮边缘表达“透亮但仍有工业结构”的外观。
- 同一类玻璃方块相邻时跳过内部面渲染，避免玻璃墙出现重复叠色和内部杂线。
- CreatePrism 通过 Create 的 `CTSpriteShiftEntry` 和 `_connected` 纹理实现 connected texture；透明玻璃 casing 与 illumination casing 都准备普通纹理和 connected 纹理。
- 客户端按方块类型选择 `translucent` 或 `cutout` 渲染层，玻璃 casing 使用半透明渲染。

我们的取舍：

- 核心模组不硬依赖 Create，也不引入 CTM/connected texture 运行时。
- 天穹玻璃采用原生 `BlockState` 六方向连通属性：放置和邻居变化时记录上下南北东西是否接入同类玻璃。
- 资源侧使用 multipart blockstate：透明主体始终渲染，只有两个相邻外侧方向都未连接时才渲染对应高亮边框。因此多块相邻放置时内部边框消失，整体外轮廓保留。
- Java 侧仍继承玻璃语义：透光、亮度高、相邻同类方块跳过内部面渲染；视觉上接近 CreatePrism 的清透玻璃 casing，但实现保持独立、离线构建友好。

### Mekanism

参考仓库：[mekanism/Mekanism](https://github.com/mekanism/Mekanism)

可借鉴：

- 网络化 transmitter、acceptor cache、capability invalidation、simulate 后 execute 的安全习惯。
- 流体和 chemical 网络有缓存，能在短暂断供时提供缓冲。
- 对 Mek 机器、多方块和 gas/chemical API 的兼容经验。

暴露问题：

- 缓存网络可靠，但每 tick 面向多个 acceptor 分配仍会随着端口数增长。
- 缓存带来“管道里还有东西”的复杂状态，玩家排障成本更高。

我们的取舍：

- 默认无缓存、无距离、无管道中暂存。
- 不吸收 Mek 缓存管道的玩法，只借鉴 capability 缓存、事务安全和 API 兼容经验。
- Mek gas/chemical 作为后期联动层，不进入 MVP。

### Flux Networks

参考仓库：[SonarSonic/Flux-Networks](https://github.com/SonarSonic/Flux-Networks)

可借鉴：

- 频率/网络/节点的无线模型。
- 节点可以限速、启停、分组，并通过网络/频率归属管理。
- 传输控制属于网络和节点，而不是属于每一段线。
- 能量输出端先统计需求，输入端按需求限流；排序只在连接变化或优先级变化时重做。

我们的取舍：

- 采用“空域频率/线路”作为玩家可理解的网络容器。
- 物品、流体、FE 共用同一套线路 ID、权限和操作率配置。
- 不引入 Flux 式网络能量池；本模组保持无中间缓存，FE 也必须在同一事务里完成源端抽取和目标接收。
- 吸收 Flux 的性能策略：优先级列表 dirty-only 重排、源驱动调度、目标需求模拟、失败端点跳过、全服/线路/端点多层预算。
- 气体/chemical 传输留到后期 Mek 联动分支。

### Applied Energistics 2

参考仓库：[AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2)

本地源码：`Applied-Energistics-2`，本次核对版本：`b4b08d9941e3faecb520d76be617629bb56661e1`。

可借鉴：

- `MEStorage` 风格：外部 API 不受 MC 单堆限制，内部 `insert/extract` 使用 `long` 数量。
- storage cell 风格：按 key 聚合存储，限制类型数，而不是按槽保存每一组物品。
- tick manager 风格：设备可以休眠、唤醒、加速、降速，而不是永远每 tick 工作。
- network storage 风格：存储提供者挂载到网络，按优先级插入/提取，并避免递归使用。

我们的取舍：

- MVP 不复刻 AE2 复杂终端和自动合成。
- 只借用“聚合存储 + 网络索引 + 调度器”的底层思想。

### LogisticsNetworks

参考仓库：[Almana-mc/LogisticsNetworks](https://github.com/Almana-mc/LogisticsNetworks)

本次核对版本：`e88e00fecf0fd957bd193b98997a45bb761ead41`。

可借鉴：

- 完全无线，节点贴在机器上，没有真实管道方块。
- 一个节点可在多个面上并行配置输入/输出。
- 字段完整：启用、输入/输出、类型、批量、延迟、侧面、红石、分配模式、过滤、优先级、名称。
- 支持物品、流体、FE、Mekanism chemicals。
- 支持跨维升级、节点隐藏、网络管理器、I/O 监控、复制粘贴、批量放置。
- 源码里采用 dirty-only 调度、网络缓存、capability 查询缓存、失败 backoff 等性能策略。
- 节点实体自身不执行物流，只维护附着、存活和配置；真实传输由 `NetworkRegistry` 的 dirty/wake 调度进入 `TransferEngine`。
- 网络缓存按 9 个 channel 分类 import 目标；每次醒来只处理 dirty 网络或到期 wake bucket，不全量扫描全部网络。
- 失败 backoff 默认最大 40t，能量最大 5t；相比“无脑每 tick”更省，但不会把高频产出机器拖到 100t 级别。
- 每次处理会构建一次临时 capability cache、节点 tier cache、红石 signal cache；这是稳定性优先的设计，避免跨 tick handler 失效复杂度。

我们的取舍：

- 采用“线路复制粘贴 + 批量放置”的玩家体验；MVP 不再提供频道号。
- 比 LogisticsNetworks 多一个“存储核心”：MVP 中物品天仓、流体天池是网络的一等公民；气体天罐留到后期 Mek 联动。
- 完全移除传输中间缓存，也不提供缓存升级。
- 进一步强化事务模型：目标不能接收时不从源端抽取，不生成隐藏 payload，不累积无界队列。
- 采用同类 dirty/wake 调度，但保留端点级长驻缓存和热槽缓存，以降低持续运行网络的重复 capability/slot 扫描成本。
- 失败退避必须区分“冷源长期无货”和“热槽刚被抽空”：热槽场景的最大等待不应超过 LogisticsNetworks 默认 40t，优先目标是小于等于 20t。

### Sophisticated Backpacks / Sophisticated Core

参考仓库：

- [P3pp3rF1y/SophisticatedBackpacks](https://github.com/P3pp3rF1y/SophisticatedBackpacks)
- [P3pp3rF1y/SophisticatedCore](https://github.com/P3pp3rF1y/SophisticatedCore)

本地源码：

- `SophisticatedBackpacks`，本次核对版本：`b46011201f424ce8dbdcaf3a06b15bf7c007b322`
- `SophisticatedCore`，本次核对版本：`4f294c0695b84285a5d6fab0e088ca1ebc78dd9b`

可借鉴：

- 大容量/移动存储不应把完整内容无界写入单个物品或方块实体 NBT。
- 通过 UUID 或独立 SavedData 路径承载真实内容，物品/方块只保存引用和少量摘要。
- 极端场景下可以按 UUID 或 hash bucket 分片保存，避免单个 SavedData 文件无限膨胀。
- SophisticatedCore 把 Create 动态结构支持抽成基础设施，而不是在背包方块实体里硬写特殊逻辑。
- `MountedStorageBase` 继承 Create `MountedItemStorage` 并实现 `SyncedMountedStorage`，把移动结构中的库存操作代理到自身 `IStorageWrapper#getInventoryForInputOutput()`。
- 移动中的内容可以进入 `MountedStorageData`，SavedData 路径为 `sophisticatedcore_mounted/<uuid>`；保存时会按需创建目录，删除时通过标记删除对应文件。客户端还有 10 分钟访问过期的副本缓存。
- GUI 基类不再依赖世界方块 `BlockPos`。`MountedStorageContainerMenuBase` 保存 contraption entity id 和 local pos，`stillValid` 通过 `contraption.toGlobalVector(localPos)` 计算玩家距离，设置界面也通过 contraption entity/local pos 打开。
- 客户端同步基类分两类 payload：内容 NBT 同步到 `MountedStorageData`，以及通过 contraption entity id + local pos 找到 mounted storage 后更新移动中的 storage stack 和渲染。
- `ContraptionHelper` 从 `contraption.getStorage().getAllItemStorages().get(localPos)` 取回 mounted storage；对客户端 `CarriageContraption` 还用反射访问 `Contraption.storage`，说明 Create 移动结构兼容需要明确面对客户端/服务端存储路径差异。

我们的取舍：

- 天仓/天池只在方块实体 NBT 保存 `vaultId`、类型上限和 UI 摘要；完整内容进入世界级 SavedData。
- 首发格式直接使用按 `vaultId` hash bucket 分片的世界级 SavedData + 内容索引缓存，不保留未发布开发期旧格式迁移路径。
- 当前固定 16 个分片，避免单个 SavedData 文件在大型整合包中过度膨胀；如果后续真实存档规模需要，再改为更多 bucket 或按 vaultId 独立文件。
- 后续 Create 兼容如果允许天仓/天池移动，应复用同类基础设施：组装时进入独立 moving-vault SavedData 或明确的移动态 UUID，移动中只暴露 mounted storage 代理，解体时再写回世界 vault。不能让同一个 `vaultId` 同时被世界方块和 contraption 读写。

### Sophisticated Backpacks Create Integration

参考仓库：[P3pp3rF1y/SophisticatedBackpacksCreateIntegration](https://github.com/P3pp3rF1y/SophisticatedBackpacksCreateIntegration)

本次核对版本：`1.21.x`，`9c9c764b4b034855caf9b27972e8789a80347741`。项目描述为 1.21.1 NeoForge 上的完整 Sophisticated Backpacks on Create Contraptions 实现，并支持 Create schematics。

可借鉴：

- 集成模组独立于 SophisticatedBackpacks 主仓库，依赖 Create、SophisticatedCore 和 SophisticatedBackpacks；这说明复杂动态结构兼容适合放到独立 compat 模块，而不是污染 MVP 主线。
- `MountedSophisticatedBackpackType` 继承 `MountedItemStorageType<MountedSophisticatedBackpack>`，`mount` 时只接受 `BackpackBlockEntity`，并从方块实体复制出背包 `ItemStack` 作为移动存储的权威入口。
- `ModContent` 注册 `SOPHISTICATED_MOUNTED_BACKPACK_TYPE`，并在 common setup 中对所有 `BackpackBlock` 同时注册 `MountedItemStorageType.REGISTRY` 和 `MovementBehaviour.REGISTRY`。也就是：Create 能否把它作为存储搬起来、移动中是否 tick，是两条独立注册。
- `MountedSophisticatedBackpack` 继承 SophisticatedCore 的 `MountedStorageBase`，用 `ItemStack` codec 序列化移动存储，懒加载 `BackpackWrapper.fromStack(getStorageStack())`，并在内容变化时标记 `stackDirty`。
- `getExternalItemHandler()` 返回 `getStorageWrapper().getInventoryForInputOutput()`，因此 Create 或其他移动结构访问的是背包自己的自动化 IO 视图，而不是临时复制出来的普通 `ItemStackHandler`。
- `unmount` 时如果移动中的 storage stack 仍有 `STORAGE_UUID`，就把 stack 写回落地方块的 `BackpackBlockEntity`；这个流程避免移动期间把真实内容塞进 contraption 方块 NBT。
- `SophisticatedBackpackMovementBehaviour.tick` 每 tick 从 `ContraptionHelper` 找 mounted storage，写入 contraption entity/local pos/level/position，调用 `clearNbt()` 清掉 contraption block info 里的 NBT，再执行 mounted storage 自身 tick。
- 移动中仍运行功能升级：服务端执行 tickable upgrades 和拾取掉落物逻辑；客户端刷新移动中的 backpack render block entity，并渲染升级特效。
- 存储变化时通过 `MountedStorageUpdatePayload(entityId, localPos, storageStack, refreshBlockRender)` 发给追踪 contraption 的客户端；渲染状态变化会更新移动结构里的 block state，如左右流体罐和电池显示。
- 交互不走普通方块右键。`handleInteraction` 用 contraption entity id 和 local pos 打开 `MountedBackpackContainerMenu`；菜单、设置界面和子背包打开都通过 `MountedBackpackContext` 传递 entity/local pos。
- 子背包也被兼容：`MountedBackpackContext.SubBackpack` 从父背包槽位取出子背包 `ItemStack` 并创建 wrapper，支持在移动结构 GUI 中进入嵌套背包。
- Create schematic 安全写入通过 `SophisticatedBackpackSafeNbtWriter` 完成：复制背包 stack 后移除 `STORAGE_UUID`，只保留颜色等外观数据，避免蓝图复制真实存储引用。
- JEI/EMI/REI 和 InventoryTweaks 兼容单独适配 mounted backpack screen，保证移动结构 GUI 仍能用幽灵拖拽、配方转移和排序类体验。

我们的取舍：

- 天仓/天池未来如果支持 Create contraption，被动存储层应按该项目分成三部分：`MountedVaultType` 负责 mount/unmount，`MountedVault` 负责代理 IO 和同步，`VaultMovementBehaviour` 负责移动中位置、tick、清 NBT 和渲染/粒子。
- 这些内容属于未来独立 `skylogistics_create` 附属模组，不进入 MVP；MVP 只记录设计和风险，不实现 mounted type、movement behaviour、移动 GUI 或 schematic safe NBT。
- 组装时不能把完整天仓/天池内容保留在 contraption block info NBT。必须像它清除 `BackpackBlockEntity` NBT 那样，清除或最小化 `vaultId` 相关 NBT，防止 schematic、拾取成物品或客户端缓存复制真实存储引用。
- 对本模组来说，移动天仓/天池只应暴露“被动存储 capability”；移动中的物流节点仍默认不可移动，不随 contraption 继续跑线路调度。
- 若后续支持天仓/天池移动中 GUI，菜单上下文应使用 `contraptionEntityId + localPos`，而不是世界 `BlockPos`；校验距离时使用 contraption 的 local->global 转换。
- 如果将来实现移动中的磁吸/自动拾取类升级，必须像该项目一样由 `MovementBehaviour` 控制 tick 频率和 AABB，而不能交给世界方块实体 tick。

### MEGA Cells

参考仓库：[62832/MEGACells](https://github.com/62832/MEGACells)

可借鉴：

- 面向 AE2 的超大容量存储语义和大数字存储体验。
- 后续 AE2 bridge 可参考其大容量 cell 与 AE2 key/amount 的交互边界。

我们的取舍：

- 天仓/天池不是 AE2 cell，MVP 不把内容塞进物品 NBT，也不依赖 AE2 网络。
- 当前核心仍是 `vaultId -> key -> long amount` 的世界级聚合存储；AE2 联动只作为后续桥接层。

### SuperFactoryManager

参考仓库：[TeamDman/SuperFactoryManager](https://github.com/TeamDman/SuperFactoryManager)

可借鉴：

- 用“程序/计划”表达物流，不让每个管道方块自己思考。
- `EVERY n TICKS` 的最低执行间隔思路：不把所有物流都放到每 tick。
- 过滤表达式和 matcher 编译缓存，能避免每次执行都重新解析规则。
- `slots 0,1,3-4` 这类槽位范围选择适合精确控制机器输入输出，但不进入 MVP。
- 性能图让玩家自己判断某个配置是否昂贵。
- 对象池和测试专用容器说明：大量库存自动化时，临时对象分配也是性能敌人。

我们的取舍：

- MVP 不给玩家暴露完整 DSL，避免上手门槛过高。
- 内部采用“编译后的线路计划”：线路 ID、节点模式、面级资源类型、过滤列表会被编译成运行时计划。
- 借鉴 SFM 的计划编译和性能可视化，但保持右键/配置器为主的轻量交互；槽位/tank 范围留到后续高级版。

### LaserIO

参考仓库：[Direwolf20-MC/LaserIO](https://github.com/Direwolf20-MC/LaserIO)

可借鉴：

- 节点贴在机器上，卡片决定输入、输出、资源类型和隔离逻辑。
- 基础过滤器、标签过滤器、模组过滤器、NBT 过滤器、计数过滤器等“过滤物品”非常直观。
- 线路隔离、优先级、轮询、抽取数量、tick speed 和超频器的 GUI 语言成熟。
- 过滤器使用幽灵槽，不真实持有物品，适合与 JEI/REI/EMI 拖拽交互。

我们的取舍：

- 不采用“每个功能都插一张卡”的复杂度；节点抽取/存入模式由普通放置/潜行放置决定，线路和资源类型由配置器批量设置。
- 保留独立过滤列表物品，玩家能像使用 Create 过滤列表一样复用过滤规则。
- 传输速度不以“物品数/t”为主要语言，而以“格/t、tank/t 或端点操作/t”为主要限制。

## 3. 竞品痛点与我们的优化策略

| 玩家差评/问题 | 本质原因 | 优化策略 | 本模组采用方式 |
| --- | --- | --- | --- |
| 源端无东西、目标满、仍持续尝试，甚至导致泄漏或无效堆积 | 主动抽取没有严格事务边界，失败后继续排队或继续扫描 | 所有传输必须先模拟源端和目标端，再执行最小可转移量 | `simulate source -> simulate target -> execute source -> execute target`；任一步失败都不生成 payload |
| 插件要一个个装、输入输出要一个个调 | 配置粒度太低，配置跟每段管道绑定 | 主要配置放到手持工具和线路；升级只装在输入源 | 工具复制/粘贴、副手工具放置时自动链接线路 |
| 有缓存时排障复杂、无缓存时又担心吞吐 | 缓存把资源藏在管道中，玩家很难确认资源到底在哪 | 完全移除中间缓存；传输必须源端和目标端同时成功 | 没有缓存升级，没有管道内物品/流体/能量；失败就不抽取 |
| 流体/复杂资源每 tick 扫描大量端口很卡 | 每 tick 全量扫描 acceptor 或按目标反复尝试 IO | dirty-only 调度、capability 缓存、失败 backoff、每 tick 预算 | 网络只处理活跃线路；失败端点退避；单 tick 有操作上限 |
| 轮询模式一次运输超大量物品导致巨大卡顿 | 轮询按数量或按目标循环，没有硬上限 | 轮询只移动游标，槽/tank/端点操作数被硬限制 | 任何升级都不能让单 tick 循环次数无限增长 |
| 存档重载后管道连接断开 | 运行时 handler 被持久化或重载后没有延迟重绑 | 只持久化端点地址和配置，handler 运行时重建 | 世界加载后延迟重绑，失败进入 `MISSING_CAP`，不假装在线 |
| 和移动结构不兼容，实体化后无法配置 | 端点地址只支持固定 BlockPos | MVP 先保证工具配置体验；移动结构作为后续兼容层 | 后续可增加 EntityEndpoint；MVP 不把航空学兼容作为硬要求 |
| 扳手误拆 Mek/AE2/Create 等机器 | 使用通用 Forge ToolAction，交互语义冲突 | 本模组配置器优先处理本模组端点，普通扳手只做兼容 | “空域配置器”是主工具；不会默认触发其他模组拆卸行为 |

## 4. 本模组的核心方式

### 4.1 空域网络不是实体管道网络

玩家看到的是“节点、云脉、配置工具和线路 ID”，服务端看到的是：

```text
ServerSavedData
  SkyNetworkRegistry
    SkyNetwork(lineId, owner, permissions)
      sender endpoints
      receiver endpoints
      storage endpoints
      telemetry
```

真实传输不沿着每一格管道走，而是在同一空域线路内直接从发送端到接收端。可见的管道、云线或星轨只是表现层，不参与寻路，也不持有中间库存。

### 4.2 储存优先

MVP 提供两个存储核心：

1. `物品天仓`
   - 可堆叠物品无限堆叠体验。
   - 限制种类数，初始默认只有 1 个类型。
   - 通过消耗 `星辉甘露` 扩展类型数，每次 +1；潜行右击会尽可能使用手中整组甘露，直到达到配置上限或物品耗尽。
   - 基础合成本身也需要 1 个 `星辉甘露`，让甘露成为天仓/天池路线的核心材料，而不是只用于后续扩容。
   - 内部按 `StackKey -> long amount` 存储。
   - 当某个已存在类型达到 `long` 上限后，继续插入该类型会被视为成功接收，但超过 `long` 上限的部分直接销毁，不返回给源端。这是天仓作为终局汇点的明确例外；类型槽已满的新类型仍会被拒绝。
   - 不可堆叠物品可以存入，但默认不进行人工堆叠。每个不可堆叠实例作为独立类型或独立封印条目，占用类型容量。

2. `流体天池`
   - 内部使用 `long mB`。
   - 对外适配 Forge 1.20.1 fluid capability。
   - 对外调用受单次 `int` 上限限制，内部可分批。
   - 初始默认只有 1 个流体类型，通过 `星辉甘露` 扩展可记录的流体类型数。
   - 基础合成本身也需要 1 个 `星辉甘露`。

后期联动存储：

- `气体天罐`：Mekanism 联动分支中加入，适配 Mek gas/chemical API。
- 其他资源容器：按具体模组 API 单独设计，不进入 MVP。

这些存储核心既可以作为玩家手动存取容器，也可以作为线路目标。这样本模组不是单纯运输模组，而是“存储 x 管道”的组合。

存储持久化策略：

- 天仓/天池方块实体只持久化 `vaultId`、类型上限和少量 UI 摘要，不把完整内容写进方块实体 NBT。
- 实际内容按 `vaultId` hash 写入多个世界级 SavedData 分片，单个分片内部结构为 `vaultId -> key -> long amount`，避免单个方块实体、物品堆或 SavedData 文件 NBT 过长。
- 这与 Sophisticated Backpacks 常规背包的方向一致：背包物品/方块以 UUID 指向服务端 SavedData 中的内容；内容不会全部塞进物品 NBT。
- 当前首发实现使用固定 16 个 hash bucket 分片；如果整合包出现极端数量的天仓/天池，可进一步扩展为更多 bucket 或按 vaultId 独立文件。Sophisticated Core 对 Create 移动存储有类似 `mounted/<uuid>` 的按 UUID SavedData 文件路径，可作为后续参考。

### 4.3 无中间缓存

线路永远没有中间缓存：

- 目标不能接收，源端就不会被抽取。
- 源端没东西，不创建传输任务。
- 目标满了，不保留隐藏 payload。
- 物品天仓同类型达到 `long` 上限后的溢出销毁是存储容器自身规则，不属于线路缓存。
- 世界重载后，没有“半路上的物品/流体”需要恢复。

这条规则也意味着：

- 不提供缓存升级。
- 不提供线路缓存、端点缓存或管道缓存。
- 能量也不在管道中暂存，发送端和接收端必须在同一次传输事务中同时可用。
- 若执行阶段出现极端不一致，优先回滚给源端；无法回滚时记录错误并暂停该线路，而不是把资源塞进隐藏缓冲区。

### 4.4 材料、供奉与多方块

MVP 的前期材料链落为以下实装名：

- `天穹水晶`：工作台合成获得，初始为未充能状态。
- `已充能天穹水晶`：同一物品通过 damage 状态区分图标；在配置的高空仪式高度或更高处的玩家背包中，或放在本模组 `天穹供桌` 中一段时间后完成充能。默认高度为 `Y >= 200`，可通过服务端配置 `rituals.skyRitualMinY` 调整。
- `天穹石`：使用 1 个已充能天穹水晶 + 8 个 `#forge:stone` 合成，提供基础方块、台阶、楼梯和墙变体。
- `天穹玻璃`：使用 1 个已充能天穹水晶 + 8 个 `#forge:glass` 合成，是全亮度光源玻璃方块；保留完整一阶结构后，放在外扩四角天穹石柱顶部，用于将供奉结构提升到二阶。
- `星辉甘露`：通过二阶 `天穹供奉` 配方获得，同时用于天仓/天池基础合成与类型扩展。

供奉结构：

```text
第一层（祭坛/供桌同层）：

..T..
.TAT.
..T..

下层（祭坛下方一层）：

CCCCC
C...C
C...C
C...C
CCCCC

二阶额外外扩四角柱：

G.....G
C.....C

柱子位于 7x7 外角，天穹玻璃在柱顶。

A = 天穹供奉祭坛
T = 天穹供桌
C = 天穹石
G = 天穹玻璃（位于外扩角柱顶部）
. = 不新增方块
```

运行规则：

- 祭坛和供桌都是 1 格物品方块实体，无 GUI，可由玩家直接右键放入/取出，也暴露 Forge item handler 给管道运输。
- 槽内物品在方块上方静态显示。
- 祭坛和供桌单槽最多容纳 64 个物品，实际仍受物品自身最大堆叠数限制。
- 祭坛只在配置的高空仪式高度或更高处，且多方块结构有效时工作。默认高度为 `Y >= 200`，可通过服务端配置 `rituals.skyRitualMinY` 调整。
- 一阶结构使用祭坛下方一层的完整 5x5 天穹石外圈，结构不要求其它位置为空气；二阶结构不替换一阶方块，只要求在 7x7 外扩四角各有天穹石柱，并在柱顶放置天穹玻璃。
- 供奉配方类型为 `skylogistics:sky_offering`，由 data recipe 填写主祭品、最多 4 个供桌材料、产物、duration 和可选 `altar_tier`。
- `星辉甘露` 的默认供奉配方要求 `altar_tier: 2`。
- 玩家摆好所有材料后，祭坛在下一次输入变化唤醒时查找配方；匹配后播放粒子直到 duration 满足，随后瞬间消耗原材料并在祭坛槽位生成产物。祭坛装不下的产物会弹出。
- 空阵、缺材料、缺结构或高度不足时不做配方查询循环；祭坛只在物品输入/供桌变化后设置一次待检查标记，运行中的配方才持续 tick。
- Patchouli 数据提供一本 `天穹物流手册`，并分别提供一阶和二阶多方块预览。JEI 页面作为可选 compat 源码存在，默认无 JEI API jar 时不参与基础构建。

## 5. 线路模型

### 5.1 线路是什么

MVP 使用单层 `线路 ID` 模型：

- `线路 ID`：决定哪些节点属于同一条无线线路，相当于 network/frequency。
- 线路 ID 是持久化 UUID，工具和 GUI 优先显示线路显示名，例如 `Steve-0`、`Steve-1`。
- 对同一玩家生成的线路来说，线路显示名等价于同一条线路：显示名稳定派生 UUID，同一个显示名必须绑定到同一个 UUID。
- 玩家没有副手配置器时，新放置节点默认使用 `玩家名-0` 线路，因此同一玩家直接放下的默认节点天然同线。
- 不再提供频道号。不同流程需要隔离时，玩家创建不同线路。

```text
线路 Steve-0：默认物流
线路 Steve-1：矿物输入
线路 Steve-2：流体供应
线路 Steve-3：主电网
```

玩家通过“线路显示名”识别线路；工具图标、节点 GUI 和 Jade tooltip 显示当前线路名。

线路 ID 的生成和复制：

- 新工具第一次打开 GUI 时生成或选择 `玩家名-0` 线路；后续新建线路按 `玩家名-1`、`玩家名-2` 递增。
- 新建线路的 UUID 由显示名稳定派生，保证同玩家同名线路不会出现“看起来相同但互不连通”的情况。
- 对已有节点使用复制模式时，工具复制该节点的线路 ID、线路显示名和配置模板。
- 工具 GUI 后续可支持手动输入完整 UUID，用于多人共享同一线路；显示名不是权限边界。

线路字段：

| 字段 | 说明 |
| --- | --- |
| `enabled` | 是否启用 |
| `lineId` | 线路 UUID |
| `lineName` | 线路显示名；默认由玩家名和序号组成，并稳定绑定到 `lineId` |
| `types` | 当前面允许物品、流体、FE；资源类型是面级配置，同一节点不同面可以启用不同资源 |
| `mode` | 输入源、输出目标、双向、禁用 |
| `side` | 指定机器侧面或全部侧面 |
| `filter` | 当前面的过滤槽；每面 2 个过滤升级槽，可插入过滤列表物品 |
| `priority` | 当前面的优先级数值；目标选择固定为高优先级接收面先尝试，同优先级组内轮询 |
| `operationRate` | 每 tick 最多处理的槽、tank 或端点操作数 |
| `amountHint` | 可选的单次数量提示，只是上限 hint，不允许驱动循环次数 |
| `tickDelay` | 基础间隔 |
| `redstone` | 当前面的红石条件：忽略、高信号、低信号、禁用 |

### 5.2 线路如何控制

玩家主要通过一个配置工具控制线路。MVP 不提供网络控制台，也不提供网络升级；网络概念尽量弱化为“同线路 ID 的节点互通”。

#### 手持配置器

`空域配置器` 是主要工具，避免和 Mek/AE2/Create/Powah 的扳手行为冲突。

基础操作：

- 主手持工具，右键本模组管道/节点：配置该管道或节点。
- 主手持工具，右键空气：打开工具 GUI。
- 主手持工具，潜行右键：打开工具 GUI 或快速切换工具模式。
- 工具 GUI 中设置当前线路、放置模板的资源类型、红石条件和优先级；抽取/存入不在配置器里切换，而由普通放置/潜行放置决定。
- 工具图标或 tooltip 显示当前线路名。
- 潜行滚轮：切换复制/粘贴模式，或作为后续快捷操作预留。
- 潜行右键节点：复制该节点的线路 ID，以及六个面的模式、资源类型、红石条件、优先级和过滤列表状态。
- 粘贴模式右键节点：把复制到工具中的六面配置粘贴到该节点，包括每面的过滤列表状态。
- 过滤列表复制为配置快照；粘贴到节点时不会消耗玩家背包中的过滤列表物品。

放置联动：

```text
副手持空域配置器
主手放置空域节点
新节点自动读取副手工具的线路 ID、线路显示名，以及工具 GUI 中配置的本面放置模板
普通放置为存入，潜行放置为抽取
节点直接链接到对应线路
```

这样玩家可以一边铺节点，一边自动绑定到当前线路，不需要每放一个节点都打开 GUI。
如果没有副手配置器，新节点使用 `玩家名-0` 默认线路；普通放置仍为存入，潜行放置仍为抽取。

推荐默认操作：

```text
1. 打开工具 GUI，生成或选择线路 Steve-0。
2. 放置模板的资源类型勾选物品 + 流体 + FE。
3. 副手拿工具，主手拿节点，对机器输出侧放置。
4. 潜行放置的新节点自动成为 Steve-0 的抽取面，可同时抽物品、流体和能量。
5. 在另一个容器/机器侧面普通放置节点，新节点自动成为同线路的存入面。
6. 两端线路相同，线路自动工作。
```

#### 节点 GUI

节点 GUI 面向单个机器端点：

- 顶部显示当前线路名和节点状态。
- 中间编辑节点模式：输入源、输出目标、双向、禁用。
- 当前面的资源类型可多选：物品、流体、FE。资源开关按面保存，不同面可以分别负责物品、流体或 FE。
- 侧面可选：当前面、指定方向或全部侧面。
- “更多”按钮切换当前面的高级配置页。
- 每个面可配置 `priority` 数值；目标选择固定为 priority 分组，高 priority 先尝试，同 priority 组内轮询。
- 每个面可配置 `redstone`：忽略、高信号、低信号或禁用；被红石条件挡住的面不计入失败冷却。
- 输入源支持升级槽：速率、低延迟、跨维等只装在输入源。
- 输入源和输出目标都支持面级过滤槽；每个面有 2 个过滤升级槽，可放入过滤列表物品。
- 底部显示状态：在线、无目标、目标满、源为空、缺少 capability、chunk 未加载。

节点 GUI 适合精调某个机器。

### 5.3 过滤列表物品

新增独立物品：`空域过滤列表`。

它的定位类似 Create 的过滤列表和 LaserIO 的过滤器，但服务于本模组的线路计划：

- 可放入输入源节点或输出目标节点的面级过滤槽；每个面有 2 个过滤升级槽。
- 节点面级过滤槽是幽灵引用：放入过滤列表时只复制该列表的状态，不消耗也不真实存储玩家背包里的过滤列表物品。
- 配置器复制/粘贴节点时会复制过滤列表状态快照，粘贴后目标节点拥有同样的过滤规则，但仍不会消耗过滤列表物品。
- 使用幽灵槽，不真实消耗或存储样品物品。
- JEI 物品 ghost 可直接拖入过滤列表，写入物品过滤条目。
- JEI 流体 ghost 可直接拖入过滤列表，写入流体过滤条目。
- 从玩家背包点击或 shift-click 水桶、流体桶等物品时，保留为物品过滤，不自动拆成流体过滤；需要流体过滤时使用 JEI 流体 ghost。
- 支持白名单、黑名单。
- 匹配模式：
  - 精确物品。
  - NBT/组件精确匹配。
  - 可选匹配耐久值。
  - 流体样品基础匹配。

多个过滤升级的组合规则：

- 黑名单过滤：任一黑名单命中即拒绝。
- 白名单过滤：存在白名单时，命中任一白名单即允许。
- 没有对应资源类型过滤条目时，不影响该资源类型。例如只有物品条目的过滤器不会阻止流体传输。

运行时不能每次传输都解析过滤列表。保存或修改过滤列表时，需要编译为：

```text
CompiledFilter
  whitelist: boolean
  exactItems: HashSet<Item>
  exactStacks: HashSet<StackKey>
  itemTags: TagKeySet
  modIds: HashSet<String>
  fluidIds: HashSet<Fluid>
  fluidTags: TagKeySet
  componentPredicates
```

调度器只调用编译后的 predicate：

```text
filter.matches(resourceKey)
```

tag 数据包重载、物品注册表变化或过滤列表被编辑时，标记相关线路计划 dirty 并重新编译。黑名单不要在每个 matcher 里反复判断，编译阶段就把“命中后取反”的逻辑收束到一个最终 predicate。

### 5.4 升级

MVP 不提供网络升级。升级只安装在输入源节点或存储容器上。

输入源节点升级：

- `传输速率升级`：提高该输入源每 tick 可处理的槽、tank 或端点操作数，但不增加按数量循环。
- `低延迟升级`：降低该输入源的 tickDelay 下限。
- `过滤列表`：不是输入源速率升级，而是插入当前面的 2 个过滤槽中；输入源和输出目标都可使用。
- `跨维升级`：允许该输入源向其他维度的同线路目标传输。

存储容器升级：

- `类型扩展升级`：提高物品天仓或流体天池的可存类型数。
- `容量升级`：提高流体天池内部容量。物品天仓主要受类型数限制，单类型数量使用 `long` 上限。

输入源和输出目标都支持过滤，但只有输入源吃性能/速率相关升级。输出目标的过滤只决定“能不能接收”，不主动 tick。

## 6. 性能设计

### 6.1 没有管道方块 tick

空域端点可以是实体、方块附着数据或轻量 BE，但不应该让每个端点每 tick 主动扫描。

服务器只有一个调度入口：

```text
ServerTick
  SkyNetworkRegistry.processActiveNetworks()
  TelemetryManager.tick()
```

网络只在以下情况下进入活跃队列：

- 节点配置变更。
- 红石状态可能变更。
- capability invalidation。
- 存储内容变更。
- 上次传输成功，需要继续传输。
- backoff 到期。

### 6.2 能力缓存

端点不持久化 handler，只持久化地址：

```text
EndpointAddress
  dimension
  blockPos 或 entityUuid + localPos
  side
```

这里的“能力缓存”只缓存 capability/handler 查询结果，不缓存资源本身：

- item handler
- fluid handler
- energy handler
- lastValidTick
- failureReason

缓存失效时只标记 dirty，不在失效回调里做重活。

### 6.3 状态预检

输入源和输出目标节点都应有运行时状态预检，但预检结果只是“提示和调度依据”，不是最终传输依据。

预检目标：

- 避免明知道源端为空、目标满、capability 缺失时仍然频繁尝试。
- 在节点 GUI / Jade tooltip 中显示可读状态。
- 给调度器提供排序、退避和跳过依据。

输入源节点可预读：

- 当前侧面是否存在 item/fluid/FE capability。
- 是否可能抽取物品、流体或 FE。
- 对物品源，最多扫描有限槽数，记录“可能存在可抽取资源”的粗略状态。
- 对本模组聚合容器，直接读取 key 计数和类型表，成本为 map 查询。

输出目标节点可预读：

- 当前侧面是否存在 item/fluid/FE capability。
- 是否可能接收某类资源。
- 对具体资源 key，当前实现按目标端点维护短 TTL 的拒收缓存，避免同一资源在目标满、类型槽满或过滤拒绝时反复 simulate。
- 对本模组聚合容器，可直接判断类型容量和剩余容量。

预检限制：

- 不全量扫描大型库存，不每 tick 扫所有槽。
- 不对所有可能物品提前计算可接受量，只对当前传输候选 key 做小范围预检。
- 预检状态必须有 TTL 或 invalidation，外部机器被其他模组改变后不能长期相信旧状态。
- 最终传输仍必须执行 `simulateExtract` 和 `simulateInsert`。预检说“能传”也可能在执行前被其他系统改变，事务模拟才是权威。

推荐状态枚举：

```text
READY
SOURCE_EMPTY
TARGET_FULL
NO_CAPABILITY
FILTER_BLOCKED
CHUNK_UNLOADED
BACKING_OFF
UNKNOWN
```

### 6.4 事务传输

所有资源都必须遵守同一条规则：

```text
候选源 = select up to operationRate slots/tanks/keys from source
请求量 = amountHint 或该候选源能表达的最大安全数量
可抽取量 = simulateExtract(source, 候选源, 请求量)
可插入量 = simulateInsert(target, 可抽取量)
实际量 = min(可抽取量, 可插入量, tickBudget)
执行抽取 source
执行插入 target
若目标实际少收，必须尝试回滚 source
若回滚失败，记录错误并暂停该线路
```

物品尤其不能按“数量”循环。即使一次请求 640000 个物品，也只能作为一个 `long requestedAmount` 进入算法；实际可转移量由源端、目标端和 adapter 能力决定，但不能被拆成 640000 次操作。

`operationRate` 控制本 tick 最多看多少个槽、tank 或聚合 key；`amountHint` 控制单次事务的数量上限。两者必须分离，否则“提高速度”会不小心变成“增加循环次数”。

### 6.5 格/t 与标量吞吐

本模组的默认限速语言不是“物品数/t”，而是“格/t”或“操作/t”。

对物品来说：

- `1 格/t` 表示每 tick 最多处理 1 个源槽或 1 个聚合资源 key。
- 原版箱子的一格普通物品最多 64 个，因此箱子到箱子的默认表现会自然接近 `64/t`。
- 本模组物品天仓没有传统槽位，同一种物品类型视作一个“虚拟格”。天仓到天仓传输圆石时，`1 格/t` 可以在一次事务里移动非常大的 `long amount`。

对流体来说：

- 默认可理解为 `tank/t` 或 `流体类型/t`。
- 外部机器按 tank handler 处理，本模组流体天池按流体 key 处理。
- `mB/t` 可以作为兼容性限额或整合包平衡配置，但不应该成为算法循环次数。

对 FE 来说：

- 默认是端点操作数/t。
- 一次 `receiveEnergy/extractEnergy` 调用能移动多少，由双方 capability 和配置上限决定。

因此，本模组的性能目标是：传输数量是标量，不是循环次数。

在同一种资源、同一个 key、同一对聚合端点之间，`1/t` 和 `2.1B/t` 应该消耗接近相同的运算量。区别只是传给 `insert/extract` 的数值不同：

```text
物品天仓 A: cobblestone = 10_000_000_000
物品天仓 B: cobblestone = 0

move(cobblestone, 1)
move(cobblestone, 2_147_483_647)

两者都应是：
  查一次 key
  模拟一次源端
  模拟一次目标端
  更新两个 long 数值
```

这只在“聚合端点”成立，例如：

- 本模组物品天仓到物品天仓。
- 本模组流体天池到流体天池。
- 能量端点双方都能在一次 capability 调用中接受大数值。
- 后期联动中的资源 handler 本身支持 long 数量时。

这不代表所有目标都能无成本吞吐：

- 原版箱子按槽存储，一个槽最多 64 个普通物品。若算法每 tick 只处理一个源槽和一个目标槽，那么箱子到箱子自然只有约 `64/t`，但运算量仍是常数级。
- 若要把一个大箱子所有槽都搬完，成本会按槽数增长，而不是按物品总数增长。
- Forge 1.20.1 的很多流体和 FE capability 使用 `int` 参数，单次调用理论上最多约 `2.1B`，实际还会被目标容量和实现 clamp。
- 多 tank、多 slot、带复杂过滤的机器会增加少量查询成本，但必须受每 tick 操作预算限制。

因此本模组的“原则上无上限”是指：当源端和目标端都能用聚合数量表达资源时，吞吐量不应额外增加循环成本。外部容器如果只能按槽/按 tank 表达容量，就按外部容器的自然结构限速。

### 6.6 如何尝试超过 LogisticsNetworks

LogisticsNetworks 已经有 dirty-only 调度、网络缓存、capability 查询缓存和失败 backoff。想在性能上超过它，不能只说“我们也缓存”，而要在常见场景减少更核心的工作量。

我们的优势目标：

1. 更窄的 MVP 范围。
   - MVP 不做 Mek chemical、不做网络控制台、不做频道号、不做中间缓存。
   - 功能少不是目的，目的是减少每 tick 需要维护的状态面。

2. 源驱动调度。
   - 线路不主动轮询所有目标。
   - 只有输入源 dirty、上次成功、红石变化、capability 失效或 backoff 到期时才入队。
   - 输出目标默认不 tick，只提供索引、过滤和短 TTL 接收状态。
   - 运行时维护 ready-line 集合和 wake bucket；睡眠线路不进入每 tick 遍历。
   - 跨维目标按 `lineId -> priorityOutputs` 在 dirty rebuild 后缓存，传输 tick 不临时聚合和排序。

3. 运行时索引更细。
   - 直接按 `(lineId, resourceType, role)` 建索引。
   - 输入源寻找目标时，不遍历同线路所有节点。
   - 过滤列表、侧面和资源类型都编译进 `TransferPlan`。

4. 格/t 限速。
   - 每 tick 限制槽、tank、端点操作数，而不是物品数量。
   - 超大吞吐只改变 `long amount`，不制造超大循环。
   - 目标选择只移动游标，不能按物品数重复遍历目标。

5. 聚合存储直连快路径。
   - 物品天仓到物品天仓、流体天池到流体天池不走 Forge slot adapter。
   - 直接用 `StackKey/FluidKey -> long` 做事务。
   - 这是最有机会显著超过通用无线管道的场景。

6. 过滤预编译。
   - 精确物品、tag、mod id、NBT/组件 matcher 在编辑时编译。
   - 传输时只做 HashSet 查询或少量 predicate 判断。
   - tag 重载时统一失效，不在每次传输时解析 tag。

7. 目标状态短 TTL。
   - 对 `canAccept(key)` 的否定结果做短 TTL 缓存；当前实现为每个目标端点 item/fluid 各 8 个 key，默认 20t。
   - 目标满、过滤拒绝、chunk 未加载会快速进入 backoff。
   - TTL 到期或 capability invalidation 后再尝试，避免长期相信旧状态。

8. 少分配。
   - 热路径使用可复用 `TransferContext`、游标和候选列表。
   - 避免每 tick 创建大量临时 `ItemStack`、列表、lambda 或排序副本。
   - 借鉴 SFM 的对象池思路，但只用于已证明高频的热路径。

9. 可见性能反馈。
   - 节点 GUI/Jade 可显示最近 tick 成本、尝试次数、失败原因和 backoff。
   - 后期提供线路性能页，帮助玩家发现昂贵过滤或大量满目标。

建议基准测试：

| 场景 | 目标 |
| --- | --- |
| 1000 个空闲节点 | 接近 0 tick 成本 |
| 500 个目标满的输出节点 | 快速 backoff，后续 tick 尝试数接近 0 |
| 100 条线路，只有 1 条线路活跃 | 只处理活跃线路 |
| 天仓到天仓传输 2.1B 圆石/t | 调用次数接近传 1 个/t |
| 原版箱子到原版箱子，`1 格/t` | 每 tick 固定少量 slot/capability 调用 |
| exact/tag/mod/NBT 过滤混合 | 过滤耗时随条目数次线性或接近常数，不能每 tick 解析 |
| 640000 物品请求 | 单 tick 目标尝试数受硬预算限制 |

结论：我们最应该超过 LogisticsNetworks 的地方，是“空闲网络”“目标满网络”“过滤重网络”和“本模组聚合容器互传”。对任意第三方机器的大规模 I/O，LogisticsNetworks 已经做了不少正确优化，我们的目标是靠更少功能面、更强计划编译和格/t 限速取得更低常数成本。

### 6.7 硬预算

必须存在多层硬预算，但预算限制的是操作次数，而不是聚合资源数量：

- 全服每 tick 最大线路操作数。
- 单线路每 tick 最大操作数。
- 单输入端每 tick 最大目标尝试数。
- 单输入端每 tick 最大外部 tank 扫描数。
- 节点自身的操作率继续限制每 tick 源槽、tank 或端点操作数。

示例默认值：

```text
serverOpsPerTick = 256
lineOpsPerTick = 32
endpointTargetAttempts = 8
externalTankScansPerEndpoint = 4
```

配置可以提高数值，但不能允许无限循环。所谓“无限速”只能代表“聚合数量很大”，不能代表 `Integer.MAX_VALUE` 次操作。

### 6.8 退避

失败场景要降频：

- 源为空。
- 目标满。
- 无匹配过滤。
- 目标 chunk 未加载。
- capability 缺失。
- 跨维缺升级。

退避建议：

```text
baseDelay = line.tickDelay
maxBackoff = 40t
onSuccess: backoff = max(baseDelay, backoff / 3)
onFailure: backoff = min(maxBackoff, max(baseDelay + 1, backoff * 1.3))
```

通用失败退避最多 40t，避免长时间错过新产物。能量线路可以特殊处理，maxBackoff 更小，因为电网需要更连续的平滑供给。

退避必须区分热源与冷源：

- 冷源长期无货、目标长期满、capability 缺失等场景允许逐步退避，减少服务器压力。
- 最近成功过的源槽/tank 是热源。热源刚被抽空时，不应立即进入长退避；这类场景常见于机器每 1t-20t 高频产出并立刻被抽走。
- 热槽场景的最大额外等待应不高于 20t；即使采用 LogisticsNetworks 类似的通用 backoff，上限也不应超过其默认 40t。
- 当前实现已具备热槽缓存、空槽短 TTL 和 `5t -> 20t -> 40t` 退避阶梯；刚成功后的短时间补货场景不应进入 40t，40t 只用于连续较久无货或不可达的冷源。
- Pipez 的固定速度间隔说明玩家对物流延迟非常敏感：性能优化不能用“睡太久”换来表面低占用。

## 7. 后期兼容设计

### Mekanism 后期联动

目标：

- MVP 不做 Mekanism 联动。
- 后期分支支持 Mek gas/chemical。
- 遵守机器侧面配置，不绕过 Mek 的自动化语义。

策略：

- 在 Mek 联动分支中，`mekanism` 不存在时，气体传输和相关物品隐藏或禁用。
- 气体传输要求安装 Mek 联动升级。
- MVP 先不要求处理 Mek 多方块结构重载后的特殊断连问题。
- 后期普通 Mek 机器仍按 capability 当前状态工作：能找到 handler 就传输，找不到就退避并显示 `MISSING_CAP`。

### AE2

目标：

- 不在 MVP 中直接接入 AE2 网络自动合成。
- 可把物品天仓作为普通 item handler 被 AE2 storage bus 访问。
- 后续可提供专门 AE2 bridge，按 AEKey/long amount 做高效对接。

策略：

- 核心存储 key 设计向 AE2 靠拢。
- 内部数量使用 long。
- 对外 item handler 只暴露正常 ItemStack 数量，避免破坏其他模组预期。

### 移动结构/航空学/Create 动态结构

目标：

- MVP 不把航空学或 Create 动态结构兼容作为硬要求。
- 后续需要兼容时，端点在移动结构实体化后仍应能通过工具 GUI 查看和编辑。

策略：

- 地址层预留 `EntityEndpointAddress`，不能把所有端点永久绑定为 `BlockPos + Direction`。
- 工具 GUI 可脱离右键侧面的方式编辑端点。
- 端点绑定失败时进入 `MOVED_OR_UNBOUND` 状态。
- 不把配置唯一入口绑定到 BlockPos 右键。
- Create 静态机器/容器按普通 capability 兼容；痛点只发生在 contraption 组装后。
- Create 组装期间，方块实体会从世界移除，存储进入 `MountedStorageManager`，玩家交互进入 `MovingInteractionBehaviour`；本模组节点当前的注册、菜单和目标解析都依赖世界 `BlockPos`，移动中会自动断开。
- MVP 不额外交付 Create compat 附属模组；未来真正支持 Create 动态结构时，新建独立 `skylogistics_create`，依赖 Create 并跟随对应 MC/Create API 分支维护。
- `skylogistics_create` 的默认策略是：物流节点不可被 contraption 移动；天仓/天池先不可移动，直到有专门 mounted storage 和 SavedData 归属策略。
- 若允许天仓/天池作为被动移动存储，需要实现 Create `MountedItemStorageType` / `MountedFluidStorageType`，并明确组装、移动、解体、拾取成物品、跨世界移动时的 `vaultId` 规则。
- SophisticatedCore 的参考实现给出的生命周期是：组装时用 storage stack/UUID 创建 mounted storage，移动中通过 mounted storage 代理库存和 GUI，内容存入 `sophisticatedcore_mounted/<uuid>` 这类独立 SavedData，客户端用 payload 同步内容和渲染，关闭/设置界面都按 contraption entity + local pos 定位。
- SophisticatedBackpacksCreateIntegration 的完整实现进一步说明：mounted type 负责从方块实体 mount，movement behaviour 负责每 tick 定位、清理 contraption NBT、驱动移动中升级逻辑，safe NBT writer 负责 schematic 时移除真实存储 UUID。
- 本模组若实现可移动天仓/天池，应采用“世界 vault 暂停绑定 -> moving vault 接管 -> 解体写回或迁移 UUID -> 删除 moving 数据”的事务，必须保证任一时刻只有一个权威存储位置，避免复制、丢失和跨维归属不清。Create schematic/蓝图写入时必须移除真实 `vaultId`，只保留外观和空引用。
- 若允许移动节点继续传输，需要把端点地址升级为 `contraption entity UUID + local pos + transformed side`，并提供移动交互 GUI；这不是简单加 capability cache 能解决的问题。

## 8. 玩家进程与材料

前期材料来自高空充能和供奉：

- `天穹水晶`：工作台合成，未充能时不能用于高阶结构材料。
- `已充能天穹水晶`：在配置的高空仪式高度或更高处的玩家背包或天穹供桌中等待充能完成；用于合成天穹石和祭坛。默认高度为 `Y >= 200`，可通过服务端配置 `rituals.skyRitualMinY` 调整。
- `天穹石`：1 个已充能天穹水晶 + 8 个 `#forge:stone` 合成，是供奉法阵的建筑材料，并提供台阶、楼梯、墙变体。
- `天穹玻璃`：1 个已充能天穹水晶 + 8 个 `#forge:glass` 合成，是全亮度光源方块；保留供奉法阵一阶外圈后，放在外扩四角天穹石柱顶部时构成二阶祭坛。
- `天穹供奉祭坛`：需要消耗 1 个已充能天穹水晶合成；负责读取 data 驱动的供奉配方。
- `天穹供桌`：不消耗天穹水晶合成；提供四个供奉输入位，也可作为容器内水晶充能点。
- `星辉甘露`：通过二阶天穹供奉获得，用于天仓/天池基础合成，也保留右击扩展天仓/天池类型上限的能力。
- `空域配置器`、`天穹过滤列表`、速度升级：进入常规工作台合成路线，部分升级消耗星辉甘露；跨维升级通过二阶天穹供奉获得。

推荐进程：

1. 前期：工作台合成天穹水晶 -> 高空充能 -> 合成天穹石 -> 搭建祭坛/供桌法阵。
2. 中期：通过天穹供奉获得星辉甘露 -> 合成物品天仓/流体天池和基础节点。
3. 后期：配置工具设置线路 ID -> 过滤升级 -> FE 传输 -> 跨维升级。
4. 联动分支：Mek 气体升级 -> 气体天罐。

## 9. MVP 范围

MVP 必做：

- 物品天仓。
- 流体天池。
- 空域节点。
- 物品、流体、FE 三类资源传输。
- 线路基础字段：启用、发送/接收、面级资源类型、侧面、每面 2 个过滤槽、格/t 操作率、延迟、优先级。
- 空域配置器。
- 空域过滤列表物品。
- 天穹水晶、星辉甘露、天穹石及台阶/楼梯/墙变体、天穹玻璃。
- 天穹供奉祭坛和天穹供桌。
- data 驱动的 `skylogistics:sky_offering` 配方类型。
- 星辉甘露供奉配方。
- 祭坛/供桌/天穹石/天穹玻璃组成的供奉多方块结构，支持下层一阶框架和外扩角柱二阶判断。
- Patchouli 手册数据和一阶/二阶多方块预览条目。
- 复制粘贴配置。
- 失败 backoff。
- 存档重载后的普通端点重绑。

MVP 可选：

- Jade tooltip。
- JEI/EMI 信息页；当前实现提供可选 JEI 源码，需构建时提供 JEI API jar。
- 基础 I/O 监控。

MVP 不做：

- 自动合成。
- 复杂可视化管道网络。
- 中间缓存和缓存升级。
- 网络控制台。
- 网络升级。
- 每 tick 全端点扫描。
- 额外 Create compat 附属模组 `skylogistics_create`。
- Create contraption 的 mounted storage、movement behaviour、移动中 GUI 或 schematic safe NBT。
- 直接兼容所有移动结构 API。
- Create contraption 移动节点或 mounted vault 兼容。
- Mek gas/chemical 联动。
- Mek 多方块结构重载专项修复。

## 10. 与参考模组的定位差异

| 模组 | 核心定位 | 我们的差异 |
| --- | --- | --- |
| Pipez | 轻量实体管道 + 主动抽取 | 不做逐格逻辑管道；默认无缓存；严格事务 |
| PipezLagFix | 给 Pipez 加 eco mode | 从设计上内置 backoff 和预算 |
| fastpipes | 高性能有形管道 + 附件 | 借鉴附件/优先级/分配，但服务端无线化 |
| Create | 机械动力、动态结构和 mounted storage | 静态 capability 自然兼容；移动 contraption 作为后续独立兼容层，MVP 默认不移动本模组节点/仓池 |
| Mekanism | 可靠缓存网络 | 借鉴 capability 处理和 API 兼容，不采用中间缓存 |
| Flux Networks | 无线能量网络 | 扩展为物品/流体/FE 的线路模型，气体留到后期联动 |
| AE2 | 网络化存储和自动化系统 | 借鉴聚合存储和调度，不复刻复杂 ME 体系 |
| LogisticsNetworks | 无线节点 + 多频道物流 | 借鉴复制粘贴和批量放置；去掉控制台与频道号，增加存储核心 |
| Sophisticated Backpacks/Core | 大容量背包与移动存储基础设施 | 借鉴 UUID/SavedData 间接存储，避免单个方块实体或物品 NBT 爆长 |
| Sophisticated Backpacks Create Integration | 背包在 Create contraption 上完整工作 | 借鉴 mounted type + movement behaviour + entity/local pos GUI + safe schematic NBT；仅用于后续 Create 分支 |
| MEGA Cells | AE2 超大容量存储扩展 | 借鉴大容量语义和后续 AE2 bridge 边界；MVP 不依赖 AE2 cell |
| SuperFactoryManager | 程序化物流调度 | 借鉴编译计划、matcher 缓存和性能反馈，不暴露完整 DSL；槽位/tank 范围不进 MVP |
| LaserIO | 节点 + 卡片 + 过滤器 | 借鉴过滤列表、优先级和速度 GUI；用配置器批量化替代逐卡配置 |

## 11. 实现结构建议

```text
sky-logistics/
  common-core/
    storage/
      StackKey
      LongKeyedStorage
      FluidVaultStorage
    network/
      SkyNetwork
      SkyLine
      SkyEndpoint
      EndpointAddress
      TransferPlanner
      TransferBudget
      BackoffState
      CompiledTransferPlan
    filter/
      ItemFilter
      FluidFilter
      TagMatcher
      FilterListItemData
      CompiledFilter
      SlotRange
    api/
      TransactionalEndpoint
      TransferAdapter
  forge-1.20.1/
    ForgeItemAdapter
    ForgeFluidAdapter
    ForgeEnergyAdapter
  client/
    NodeScreen
    ConfigToolScreen
    WrenchHud
```

关键原则：

- MVP 主线只实现 Forge 1.20.1。
- 后续 1.21.1 / NeoForge / 其他 MC 版本以独立 Git 分支维护。
- 版本差异集中在 capability / transfer adapter，不在同一 MVP 分支里堆兼容层。
- Mek 联动作为后期分支或独立 compat 包处理。

## 12. 验收标准

性能验收：

- 1000 个节点空闲时几乎不产生 tick 成本。
- 目标满时，线路在短时间内进入 backoff。
- 640000 物品请求不会让单 tick 调用次数线性增长。
- 天仓到天仓传输 `1/t` 与 `2.1B/t` 的调用次数接近一致。
- 原版箱子到原版箱子的速度由 `格/t` 和槽容量自然限制。
- 过滤列表修改后编译一次，传输热路径不解析过滤配置。
- 单 tick 操作数受配置硬限制。

稳定性验收：

- 存档重载后端点能自动恢复或明确显示失败原因。
- 外部机器 capability 失效后不会继续使用旧 handler。
- 目标满时源端物品不会丢失、复制或进入隐藏无界队列；物品天仓同类型达到 `long` 上限后的溢出销毁是明确例外。
- 跨维缺升级时不尝试传输，并显示原因。

玩家体验验收：

- 前期两三次右键即可完成“箱子 -> 机器”的简单自动化。
- 副手持工具放置节点时，可以自动绑定到当前线路。
- 后期可以通过工具复制/粘贴批量复用一组机器的节点配置。
- 线路名、节点状态和异常原因可视。
- 复制粘贴能复用整套节点配置。
