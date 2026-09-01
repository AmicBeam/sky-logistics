# 克莱斯支配之杖（Kleis Dominion Wand）技术规格

> 状态：设计冻结前草案；仅记录需求与实现约束，不代表功能已实现。
> 更新日期：2026-09-01
> 覆盖版本：Minecraft 1.20.1 Forge、1.21.1 NeoForge、26.1.2 NeoForge

## 1. 目标

克莱斯支配之杖是一件独立的物流工具。玩家主手持有它，并在副手放置天穹配置器时，可以把设备的一个具体方块侧面注册为“虚拟物流节点”。虚拟节点不放置方块、不占用空间，但在传输、线路配置、过滤和优先级等方面应尽量表现得像一个只拥有单一连接面的天穹物流节点。

核心体验：

- 普通右击设备侧面：创建或切换为蓝色的存入端点。
- 潜行右击设备侧面：创建或切换为橙色的抽取端点。
- 左击已绑定侧面：打开单端点节点 GUI。
- 左击方块时绝不进入方块破坏流程。
- 手持克莱斯支配之杖时可看见已绑定面的常驻边框与方向动画。
- 物品栏中的杖端具有类似发光的低频呼吸动画。
- 虚拟端点天然允许跨维度传输，不需要维度升级卡。

## 2. 非目标

本功能不应：

- 放置隐形方块、空气方块或伪造 BlockEntity。
- 强加载区块或维度。
- 允许在未加载设备上持续读写 capability。
- 替代天穹配置器的线路管理功能。
- 在 GUI 中提供六面切换栏。
- 用整面半透明色块遮挡设备贴图。
- 让客户端直接决定端点配置或写入 SavedData。
- 在本规格阶段实现任何 Java、JSON 注册或数据迁移代码。

## 3. 命名与资源标识

暂定：

- 中文名：`克莱斯支配之杖`
- 英文名：`Kleis Dominion Wand`
- 物品 ID：`skylogistics:kleis_dominion_wand`
- Java 类名：`KleisDominionWandItem`
- SavedData 名称：`skylogistics_kleis_dominion_wand_endpoints`

当前仓库只有美术资产，没有对应物品类、注册项、模型、语言条目、配方或交互逻辑。已有资产包括：

- `common/src/main/resources/assets/skylogistics/textures/item/chora_kleis.png`
- `assets/item-icon-sources/chora_kleis.svg`
- `assets/item-icon-sources/chora-kleis-pixel/`
- `assets/generated/chora-kleis-pixel/`

这些是命名冻结前生成的美术源文件。实施阶段应将最终选中的运行时资源复制或重命名为 `kleis_dominion_wand`；在此之前不得把这些资产描述为“物品已经接入游戏”。

## 4. 术语

### 4.1 目标方块

玩家点击的设备方块。坐标记为 `targetPos`。

### 4.2 目标面

命中结果 `BlockHitResult#getDirection()` 返回的方块外表面，记为 `targetFace`。

### 4.3 虚拟端点键

唯一标识一个虚拟节点：

```text
EndpointKey = dimension + targetPos + targetFace
```

同一维度、同一方块、不同侧面是不同端点。相同的 `EndpointKey` 最多存在一条记录。

### 4.4 抽取与存入

沿用现有 `NodeFaceMode` 语义：

- 抽取：`NodeFaceMode.INPUT`，资源从设备进入物流网络，显示橙色。
- 存入：`NodeFaceMode.OUTPUT`，资源从物流网络进入设备，显示蓝色。

注意：`INPUT`/`OUTPUT` 是相对于物流网络命名，UI 文案必须使用“抽取／存入”，避免玩家误解。

## 5. 激活条件

### 5.1 主手

所有世界交互只认主手中的克莱斯支配之杖。副手持杖不触发绑定、GUI 或动画。

### 5.2 副手配置器

创建、改模式和打开 GUI 时，副手必须是 `ConfiguratorItem`。

推荐行为：

- 主手有杖、副手有配置器：完整功能可用。
- 主手有杖、副手无配置器：仍禁止用杖破坏方块；不创建端点；显示“副手需要天穹配置器”。
- 仅副手有杖：按普通物品处理。

### 5.3 可视化条件

只要主手持有克莱斯支配之杖，就可以显示当前维度内已同步的虚拟端点覆盖层。可视化不依赖副手配置器，方便只检查线路而不修改。

## 6. 世界交互状态机

### 6.1 普通右击

条件：主手克莱斯支配之杖、副手配置器、命中方块侧面、服务器验证通过。

行为：

1. 计算 `EndpointKey`。
2. 若端点不存在，创建存入端点。
3. 若端点已存在且是抽取，将其切换为存入。
4. 若端点已是存入，保持幂等，不重复创建。
5. 新建时从副手配置器读取当前线路及 placement 配置。
6. 已有端点切换模式时保留线路、过滤、优先级、红石和升级配置。
7. 返回 `CONSUME`，阻止目标方块和副手配置器继续处理本次右击。

### 6.2 潜行右击

与普通右击相同，但目标模式为抽取。

潜行右击不能把事件继续传给设备自身，避免打开设备 GUI、旋转方块或触发其它模组的潜行交互。

### 6.3 左击

左击是纯配置交互，不能造成破坏进度。

命中方块时：

- 主手是克莱斯支配之杖：立即取消原版攻击／挖掘输入。
- 若副手是配置器且命中面已有虚拟端点：向服务端请求打开该端点 GUI。
- 若副手是配置器但命中面未绑定：显示“该侧面没有柯拉端点”。
- 若副手不是配置器：显示副手要求，但仍取消方块破坏。

不命中方块时：

- 左击空气保持原版挥手表现。
- 左击实体不在本功能范围内；默认不取消实体攻击。若后续决定克莱斯支配之杖完全无攻击能力，应单独追加产品决策。

### 6.4 移除端点

为避免误删，不使用第二种世界左击手势移除端点。

推荐在单端点 GUI 中提供“移除虚拟节点”按钮：

1. 第一次点击进入确认状态。
2. 五秒内再次点击确认移除。
3. 服务端校验所有者／权限后删除 SavedData 记录。
4. 从运行时网络索引注销。
5. 向附近客户端广播删除增量。
6. 关闭当前菜单。

## 7. 左键防破坏

必须同时在客户端输入层和服务端破坏层拦截，不能只依赖其中一层。

### 7.1 客户端主拦截

参考 Beyond Craftlines 的 Network Linker：

- 公开仓库：`https://github.com/AmicBeam/beyond_craftlines`
- 参考版本：`4af2d54afe0701796965666dd0435b06e96eba05`
- 参考类：`src/main/java/com/amicbeam/beyondcraftlines/client/CraftlinesClientEvents.java`
- 参考方法：`openBoundMachineConfig(InputEvent.InteractionKeyMappingTriggered)`

要求：

1. 监听 `InputEvent.InteractionKeyMappingTriggered`，优先级 `HIGHEST`。
2. 只处理 `event.isAttack()`。
3. 客户端当前命中必须是 `BlockHitResult` 且类型为 `BLOCK`。
4. 主手必须是克莱斯支配之杖。
5. 在任何打开 GUI 的网络请求之前先 `event.setCanceled(true)`。
6. 建议关闭本次手臂挥动，避免视觉上像在敲击设备；若保留挥动，也不能开始裂纹动画。
7. 每次按下只发一个打开请求，不能在长按期间每 tick 重复发包。

客户端取消对于创造模式尤其重要，因为创造模式可能在普通服务端判定前直接破坏方块。

### 7.2 服务端交互兜底

监听 `PlayerInteractEvent.LeftClickBlock`：

- 主手是克莱斯支配之杖时取消事件。
- 将 block usage 与 item usage 都设为拒绝（对应版本 API 支持时）。
- 只在 `START` 动作处理一次打开请求语义；`CLIENT_HOLD`、`STOP`、`ABORT` 不重复打开。

### 7.3 BreakEvent 最终兜底

参考 Beyond Craftlines：

- 1.20.1：`versions/1.20.1/src/main/java/com/amicbeam/beyondcraftlines/common/event/CraftlinesEvents.java`
- 1.21.1：`src/main/java/com/amicbeam/beyondcraftlines/common/event/CraftlinesEvents.java`
- 26.1.2：`versions/26.1.2/src/main/java/com/amicbeam/beyondcraftlines/common/event/CraftlinesEvents.java`

监听 `BlockEvent.BreakEvent`：

- 玩家主手是克莱斯支配之杖时取消方块破坏。
- 此层不负责打开 GUI，只负责安全兜底。
- 必须覆盖生存、创造和服务端收到异常破坏包的情况。

### 7.4 验收条件

- 单击、长按左键均不出现裂纹。
- 生存模式破坏进度始终为零。
- 创造模式不会瞬间删除方块。
- 高延迟下不会先破坏再回滚。
- GUI 每次点击最多打开一次。
- 未绑定面不会被破坏。
- 没有副手配置器时也不会被杖破坏。

NeoForge 对 `PlayerInteractEvent.LeftClickBlock` 的官方说明明确指出，取消事件会阻止 `Block#attack` 和物品采掘路径；但创造模式仍要求客户端提前取消。实现时以对应版本事件 API 为准。

## 8. 新建端点时复制的配置

复用 `ConfiguratorItem.ToolConfig` 和 `ConfiguratorItem.FaceConfig` 的语义，不复制粘贴模式状态。

从副手配置器复制：

- 当前 `lineId`
- 当前线路显示名与 assigned name
- placement 的红石控制
- placement 的优先级
- placement 的槽位／维持量限制
- placement 的过滤器
- 资源自动检测设置
- 配置器中允许复制到节点的升级配置

不复制：

- `PasteMode`
- 配置器自己的线路浏览光标
- 与其它面有关的 `Faces` 全量配置

模式由本次手势强制决定：普通右击存入，潜行右击抽取；配置器中保存的 placement mode 不覆盖手势。

## 9. 资源能力与目标侧

虚拟端点直接访问 `targetPos`，访问方向为 `targetFace`。

这与真实节点位于设备外侧时从该面访问设备的语义一致。不能使用 `targetFace.getOpposite()`，否则会访问设备的错误 capability 面。

新建时自动探测：

- 物品
- 流体
- 能量
- Mekanism chemical（对应配置开关启用时）
- Botania mana 或其它现有能量类兼容资源（对应配置开关启用时）
- Ars Nouveau Source（对应配置开关启用时）

必须复用现有兼容层与配置开关。克莱斯支配之杖不是新的第三方存储类型，不得绕过已有类型禁用开关。

若该面没有任何支持能力：

- 服务端拒绝创建。
- 不写 SavedData。
- 客户端显示“该侧面没有可用的物流能力”。

设备能力暂时不可用但区块仍加载时，不立即删除配置；按“失效与清理”规则处理。

## 10. 持久化数据模型

### 10.1 SavedData

新增服务器级 `KleisDominionWandSavedData`，存放在主世界 DataStorage 中，覆盖所有维度。

推荐结构：

```text
KleisDominionWandSavedData
  schemaVersion
  endpoints: Map<EndpointKey, EndpointRecord>

EndpointKey
  dimensionId
  targetPos
  targetFace

EndpointRecord
  ownerId
  lineId
  assignedLineName
  displayLineName
  mode
  resourceFlags
  redstoneControl
  priority
  itemSlotLimit
  faceFilters
  upgrades
  boundBlockId
  createdGameTime
  modifiedGameTime
```

### 10.2 不持久化的运行时字段

以下字段只保存在服务器运行时缓存：

- 每资源传输游标
- 最近传输时间
- 最近红石状态／脉冲边沿
- capability 句柄
- 当前注册到哪些线路索引
- 上次有效性检查 tick

服务重启后允许这些游标复位，不影响配置正确性。

### 10.3 版本化

SavedData 必须包含显式 `schemaVersion`。未知的新版本数据不得静默降级覆盖；应记录一次警告并跳过加载。

建议首版为 `1`。

### 10.4 三版本 SavedData API

- 1.20.1：参考 `SkyPlayerLines#get` 的 `computeIfAbsent(load, constructor, name)`。
- 1.21.1：参考 `SavedData.Factory`。
- 26.1.2：参考 `SavedDataType`、`CompoundTag.CODEC.xmap` 与 `computeIfAbsent(TYPE)`。

三版本保存的逻辑字段和 schema 必须一致，即使 API 写法不同。

## 11. 运行时端点抽象

虚拟端点不能继承 `NetworkEndpointBlockEntity`，因为它没有实际方块位置和 BlockEntity 生命周期。

推荐引入调度器可见的端点接口，例如 `LogisticsEndpoint`：

```text
identity()
level()
lineId()
endpointKey()
targetPos()
accessSide()
faceMode()
resourceFlags()
redstoneControl()
priority()
filters()
upgrades()
allowsCrossDimension()
resolveItemHandler()
resolveFluidHandler()
resolveEnergyHandler()
...
```

适配关系：

- `NetworkEndpointBlockEntity` 实现或由适配器实现 `LogisticsEndpoint`。
- `KleisDominionWandRuntimeEndpoint` 从 `EndpointRecord` 和当前 `ServerLevel` 实现相同接口。
- `SkyNetworkRegistry.CachedEndpoint` 不再假设端点一定是 BlockEntity。
- `SkyNetworkTicker` 只依赖端点接口，不通过强制类型转换访问节点。

不得通过创建假的 `SkyNodeBlockEntity`、假的 `BlockState` 或占用 `targetPos.relative(face)` 来兼容现有调度器。

## 12. 网络索引与区块生命周期

### 12.1 注册

服务器启动或 SavedData 加载后，不一次性加载所有目标区块。

只有满足以下条件的虚拟端点进入活动索引：

- 目标维度已加载。
- 目标区块已加载。
- 目标方块仍与绑定记录一致。
- 至少一种启用资源能力可解析。

### 12.2 区块加载

`ChunkEvent.Load` 时查询该维度、该区块对应的 SavedData 记录，验证后注册到 `SkyNetworkRegistry`。

### 12.3 区块卸载

`ChunkEvent.Unload` 时只从运行时索引注销，不删除 SavedData。

### 12.4 维度卸载

清除该维度所有运行时适配器与 capability 缓存，保留 SavedData。

### 12.5 配置变更

线路、模式、优先级、资源开关、过滤器或升级变化时：

1. 更新 SavedData 并 `setDirty()`。
2. 从旧线路／资源索引注销。
3. 注册到新索引。
4. 标记相关线路拓扑脏。
5. 广播客户端增量。

## 13. 默认跨维度规则

克莱斯支配之杖端点的 `allowsCrossDimension()` 恒为 `true`。

语义等同于抽取端安装了维度升级卡，但不实际存放卡片：

- 抽取端可把资源发送到其它已加载维度的同线路存入端。
- 存入端可接收其它已加载维度的同线路资源。
- 不强加载目标维度或区块。
- 仍遵守线路所有权、过滤、优先级、AStages 限制和资源类型开关。

GUI 中：

- 不显示可插入的维度升级槽。
- 若保留通用升级槽，必须拒绝放入 `dimension_upgrade`。
- 显示只读提示“跨维度：内置”。

现有可复用路径：

- `SkyNodeBlockEntity#hasDimensionUpgrade`
- `SkyNetworkRegistry` 的 global outputs
- `SkyNetworkTicker` 的 `dimensionUpgrade` 路由
- `ConfiguratorLineDetailsPacket.Entry#dimension`

实现时应抽象成端点能力，而不是在 ticker 中追加大量 `instanceof KleisDominionWand...` 分支。

## 14. 红石语义

真实节点读取节点方块附近红石；虚拟节点没有自身方块。统一规定：虚拟端点的红石状态读取 `targetPos` 的邻居信号。

推荐：

```text
powered = level.hasNeighborSignal(targetPos)
```

脉冲模式的上一次 powered 状态保存在运行时端点中。区块卸载或服务重启后状态复位为当前值，避免重新加载时误产生一次脉冲。

## 15. GUI

### 15.1 总体

左击已绑定面打开专用 `KleisDominionWandMenu`／`KleisDominionWandScreen`，视觉与节点 GUI 一致，但只管理一个端点。

不能把 `targetPos` 伪装成 `SkyNodeMenu` 的 BlockEntity 位置。当前 `SkyNodeMenu` 和 `SkyNodeScreen` 会直接从世界取 `SkyNodeBlockEntity`，虚拟端点需要独立后端。

### 15.2 可复用视觉组件

- `ConfigPanel`
- 节点 GUI 的资源按钮、模式按钮、红石按钮、优先级步进器
- 过滤槽绘制与 tooltip
- 线路翻页／命名语义
- 通用 `MenuAction` 编码，或抽成共享动作定义

### 15.3 明确移除

- 左侧六面选择面板
- `FACE_ORDER` 六面按钮
- 切换 selected face 的网络动作
- 无关面的 filter 容器

### 15.4 显示内容

- 目标方块名称
- 维度与坐标
- 目标面名称
- 当前线路
- 模式：抽取／存入
- 物品／流体／能量开关
- 红石控制
- 优先级
- 槽位／维持量限制
- 当前面的过滤器
- 支持的非原版资源提示
- “跨维度：内置”
- 移除虚拟节点按钮

### 15.5 菜单打开数据

菜单不能只传 `BlockPos`。至少传输：

```text
dimensionId
targetPos
targetFace
endpointRevision
```

客户端屏幕从菜单同步快照读取配置，不直接信任客户端世界能力。

### 15.6 服务端仍为真相源

每个按钮动作都携带菜单 container id 和预期 endpoint revision。服务端拒绝：

- 菜单已失效
- 端点被删除
- revision 过期
- 玩家不再持有正确物品
- 玩家离目标过远
- 玩家无权限

## 16. 网络包

推荐最小包集合：

### C2S

- `KleisDominionWandUpsertEndpointPacket`
  - 右击创建／改模式
  - 字段：dimension、pos、face、requestedMode
- `KleisDominionWandOpenMenuPacket`
  - 左击请求打开菜单
  - 字段：dimension、pos、face
- `KleisDominionWandMenuActionPacket`
  - 修改单端点配置
  - 字段：containerId、endpointKey、revision、action、value
- `KleisDominionWandRemoveEndpointPacket`
  - GUI 确认移除
- `KleisDominionWandOverlayRequestPacket`
  - 客户端手持期间请求当前维度附近端点

### S2C

- `KleisDominionWandEndpointSnapshotPacket`
  - 菜单配置快照
- `KleisDominionWandOverlaySnapshotPacket`
  - 当前维度附近可见端点列表
- `KleisDominionWandOverlayDeltaPacket`
  - 创建、修改、删除单条端点
- `KleisDominionWandActionResultPacket`
  - 失败原因或成功反馈

### 16.1 包上限

- overlay snapshot 必须限制最大条数，建议默认 `512`。
- 请求范围建议默认 `64` 方块，可配置。
- 字符串长度、过滤器数量和 ItemStack 数量沿用现有节点上限。
- 客户端不得请求任意维度的全量端点。

### 16.2 三版本网络 API

- 1.20.1：沿用 `SimpleChannel`、显式 encode/decode、`NetworkEvent.Context`。
- 1.21.1：`CustomPacketPayload`、`StreamCodec`、payload registrar。
- 26.1.2：使用当前版本 `CustomPacketPayload`／`StreamCodec` API 与 `Identifier` 命名。

所有版本必须使用相同字段顺序、相同限制和相同验证语义。

## 17. 覆盖层可见性与同步

### 17.1 客户端缓存键

```text
OverlayKey = dimension + targetPos + targetFace
```

缓存条目只需要：

- key
- mode
- revision
- 可选 resource mask

### 17.2 请求策略

主手首次切换到克莱斯支配之杖时立即请求快照；之后：

- 玩家跨越一定距离或进入新区块时刷新。
- 默认最多每 20 tick 请求一次。
- 登录、退出、切维度时清空缓存。
- 服务端变更通过 delta 主动更新已订阅玩家。

### 17.3 服务端筛选

只发送：

- 玩家当前维度
- 已加载区块
- 距玩家小于配置半径
- 玩家有权查看的端点

## 18. 方块面覆盖层

### 18.1 颜色

- 存入：蓝色。
- 抽取：橙色。

建议从现有像素资产取色：

- 蓝主色：`#79DCE9`
- 蓝深色：`#3C91C5`
- 橙主色：`#FFD56F`
- 橙深色：`#E89A36`

最终 RGBA 应通过游戏内浅色、深色、发光和透明方块背景测试后冻结。

### 18.2 常驻外框

覆盖层可见期间，每个虚拟端点面始终绘制一个一纹素宽的矩形边框。

逻辑面为 `16 × 16`：

```text
上边：x=[0,16], y=[0,1]
下边：x=[0,16], y=[15,16]
左边：x=[0,1],  y=[1,15]
右边：x=[15,16], y=[1,15]
```

内部完全透明，不绘制整面色块。

建议外框 alpha：`0.80–0.90`。

### 18.3 动画框

动画层也只绘制一纹素宽的半透明矩形边框，内部始终透明。

尺寸是离散像素帧：

```text
2×2, 4×4, 6×6, 8×8, 10×10, 12×12, 14×14, 16×16
```

对任意尺寸 `S`：

```text
min = (16 - S) / 2
max = (16 + S) / 2
```

绘制四条一纹素宽的边。`2×2` 因边宽占满，视觉上是四个半透明单元；从 `4×4` 开始有明确透明内孔。

不得对矩形内部填色。

### 18.4 抽取动画

橙色，由内向外：

```text
2 → 4 → 6 → 8 → 10 → 12 → 14 → 16 → 消失／重置
```

`16×16` 与常驻框重合；动画框在该步末尾淡出，常驻框不消失。

### 18.5 存入动画

蓝色，由外向内：

```text
16 → 14 → 12 → 10 → 8 → 6 → 4 → 2 → 消失／重置
```

空间尺寸必须逐级跳变，不做连续缩放插值。

### 18.6 时间

建议初始参数：

- 每个尺寸保持 `3 tick`
- 八个尺寸共 `24 tick`
- 重置间隔 `6 tick`
- 完整周期 `30 tick`（1.5 秒）
- 动画框常规 alpha `0.45–0.60`
- 只允许在最后一个尺寸的 3 tick 内做 alpha 淡出；位置仍保持离散

动画相位使用客户端当前 level game time，保证同一客户端内稳定，不依赖帧率。建议所有端点共用相位，方向由 mode 反转，便于玩家直接比较流向。

### 18.7 几何与深度

- 覆盖层沿目标面法线向外偏移约 `0.002` 方块，避免 Z-fighting。
- 开启深度测试。
- 关闭面剔除或为六个方向生成正确顶点顺序。
- 半透明动画层不写深度。
- 边框不可越过方块面的 `[0,1] × [0,1]` 范围。
- 不受 GUI scale 影响。

矩形关于中心对称，因此六个面的 UV 旋转不会改变动画语义；仍须验证法线方向和相机背面剔除。

### 18.8 渲染事件

- 1.20.1／1.21.1：新增世界阶段渲染监听，不能只依赖 `RenderHighlightEvent.Block`，因为需要同时绘制多个已绑定面。
- 26.1.2：使用对应版本的 render-state／level-stage API；现有 `ExtractBlockOutlineRenderStateEvent` 只适合抽取选中方块轮廓，不足以承担多面动画。

现有 `ClientDistributorHighlights` 可参考缓存和请求节流，但不能直接复用它只包含 `BlockPos` 的数据结构。

### 18.9 物品栏杖端呼吸动画

克莱斯支配之杖的物品贴图应具有低频、循环的“能量发光”呼吸效果。它是贴图颜色动画，不是附魔 glint，也不产生世界光照。

#### 逻辑尺寸

推荐以当前重点设计的 `32 × 32` 像素版本作为游戏运行时物品贴图。动画图集为纵向帧条：

```text
单帧：32 × 32
唯一帧数：6
图集：32 × 192
```

16×16 和 64×64 版本保留为缩略验收与美术参考，不应与运行时图集同时被同一个 item model 引用。

#### 发光掩码

只有杖端能量结构参与呼吸：

- 蓝色半环的发光内缘
- 蓝色节点宝石
- 橙色半环的发光内缘
- 橙色节点宝石
- 必要时允许节点尖端最靠近宝石的一格参与弱高光

以下部分必须在全部帧中逐像素相同：

- 杖柄与皮革缠绕
- 长杖金属杆
- 金色结构件
- 环的外轮廓与几何形状
- 所有透明像素

呼吸动画不得改变 alpha 轮廓、增加外部半透明光晕或扩大物品包围盒。这样可避免物品栏中出现边缘抖动。

#### 亮度级别

六张唯一帧代表从基准到峰值的离散亮度：

```text
L0：基准亮度
L1：约 15%
L2：约 30%
L3：约 50%
L4：约 75%
L5：峰值
```

百分比表示相对于 `L0 → L5` 色阶变化的进度，不是 alpha。所有参与像素保持原有不透明度，只改变 RGB 色阶。

建议峰值颜色：

- 蓝色峰值：`#B9FFF4`
- 橙色峰值：`#FFF0A2`

中间帧必须使用有序离散色阶，不能通过缩放、模糊或抗锯齿生成。

#### 播放序列

使用六张唯一帧组成往返序列：

```text
0, 1, 2, 3, 4, 5, 4, 3, 2, 1
```

建议每项保持 `3 tick`：

```text
10 个播放项 × 3 tick = 30 tick = 1.5 秒／周期
```

建议 `.png.mcmeta` 语义：

```json
{
  "animation": {
    "frametime": 3,
    "interpolate": false,
    "frames": [0, 1, 2, 3, 4, 5, 4, 3, 2, 1]
  }
}
```

`interpolate` 必须为 `false`，保持 Minecraft 像素帧的离散感。呼吸感来自有序调色板，而不是空间插值。

#### 渲染范围限制

原版 animated texture metadata 会在所有引用该纹理的模型中播放，因此默认会同时出现在：

- 物品栏
- 手持第一／第三人称
- 掉落物
- 物品展示框等普通 item model 场景

如果产品要求“只在物品栏中动画、手持和掉落时静止”，不能只使用 `.png.mcmeta`，需要自定义物品渲染器或按显示上下文选择纹理。该需求当前未确认，本规格默认接受所有 item model 场景同步播放。

#### 客户端配置

建议提供 `animateKleisDominionWandItemIcon`，默认 `true`。关闭时应使用 `L0` 或中间亮度的静态帧。具体如何在资源 metadata 动画与客户端配置间切换需要实施阶段选型；不能承诺仅靠 `.png.mcmeta` 可以运行时关闭动画。

## 19. GUI 与覆盖层的一致性

- 蓝色必须在 GUI、世界覆盖层、tooltip 和线路详情中始终表示存入。
- 橙色必须始终表示抽取。
- 改模式后先等待服务端确认，再更新 GUI 与覆盖层；可做客户端预测，但失败必须回滚。
- GUI 打开期间目标面被删除时，屏幕显示失效提示并自动关闭。

## 20. 失效与清理

### 20.1 正常破坏

设备被玩家正常破坏时：

1. 查询该维度、该坐标的六个 EndpointKey。
2. 删除全部虚拟端点。
3. 注销运行时端点。
4. 标记线路拓扑脏。
5. 广播 overlay 删除增量。

### 20.2 爆炸、命令替换和活塞

不能只依赖玩家 BreakEvent。还需覆盖：

- BlockEvent.BreakEvent
- 爆炸后的方块移除回调
- 方块被替换／放置事件
- 区块加载时的惰性校验

记录 `boundBlockId`。若同坐标方块 ID 与绑定时不同，删除端点。普通 blockstate 属性变化不删除。

### 20.3 capability 暂时失效

当方块 ID 未变但 capability 暂时返回空：

- 当 tick 跳过传输。
- 不立即删配置。
- 连续多个检查周期仍无任何支持能力时标记 dormant。
- 区块重新加载或邻居／能力失效通知后重新探测。

不得因为区块卸载而删除端点。

## 21. 权限与安全

所有 C2S 请求必须验证：

- 玩家仍在服务器上。
- 玩家当前维度等于请求维度。
- 主手仍是克莱斯支配之杖。
- 需要修改／打开时副手仍是配置器。
- 命中位置在正常方块交互距离内。
- 服务端重新射线或至少验证 pos／face 与玩家视线合理。
- 目标区块已加载；不得由请求触发加载。
- 玩家有权使用该线路。
- 玩家有权修改该端点。
- endpoint revision 与服务端一致。

建议所有者规则：创建者成为端点 owner；线路 owner 规则沿用 `SkyPlayerLines`。管理员权限和团队共享若已有统一实现，应复用而不是另建名单。

## 22. 配置项

建议服务器配置：

- `enableKleisDominionWand`：总开关，默认 `true`
- `choraKleisMaxEndpointsPerPlayer`：默认 `256`
- `choraKleisMaxEndpointsPerChunk`：默认 `64`
- `choraKleisOverlayRange`：默认 `64`
- `choraKleisOverlayMaxEntries`：默认 `512`
- `choraKleisValidationIntervalTicks`：默认 `20`

建议客户端配置：

- `renderKleisDominionWandOverlays`：默认 `true`
- `choraKleisOverlayAnimation`：默认 `true`
- `choraKleisOverlayAlpha`：默认值按最终视觉测试冻结
- `animateKleisDominionWandItemIcon`：默认 `true`；实现方式见 18.9

关闭动画时仍显示常驻边框。

## 23. 现有代码复用地图

### 配置器

三版本：

- `versions/*/src/main/java/com/skylogistics/item/ConfiguratorItem.java`
- `ToolConfig`
- `FaceConfig`
- `readOrCreate`
- `writeConfig`
- `applyPlacementToolConfig`
- `applyCopiedToolConfig`

1.20.1 使用 ItemStack NBT；1.21.1／26.1.2 使用 `StackData`，26.1.2 最终落在 `DataComponents.CUSTOM_DATA`。

### 节点与调度

- `versions/*/src/main/java/com/skylogistics/block/entity/NetworkEndpointBlockEntity.java`
- `versions/*/src/main/java/com/skylogistics/block/entity/SkyNodeBlockEntity.java`
- `versions/*/src/main/java/com/skylogistics/network/SkyNetworkRegistry.java`
- `versions/*/src/main/java/com/skylogistics/network/SkyNetworkTicker.java`

### 单端点行为

- `versions/*/src/main/java/com/skylogistics/block/entity/ExternalNetworkInterfaceBlockEntity.java`
- `usesSingleEndpoint()`
- `getSingleEndpointDirection()`
- `canConfigureFace()`

### 菜单与屏幕

- `common/src/main/java/com/skylogistics/menu/SkyNodeMenu.java`
- `versions/*/src/main/java/com/skylogistics/client/SkyNodeScreen.java`
- `common/src/main/java/com/skylogistics/menu/MenuAction.java`
- `versions/*/src/main/java/com/skylogistics/client/ConfigPanel.java`

26.1.2 的 `build.gradle` 排除了 common 的 `SkyNodeMenu` 等菜单类，使用 `versions/26.1.2/src/main/java/com/skylogistics/menu/` 下的版本实现。菜单相关修改不能只改 common。

### SavedData

- `versions/*/src/main/java/com/skylogistics/network/SkyPlayerLines.java`
- `versions/*/src/main/java/com/skylogistics/network/SkyLineNames.java`

### 高亮与客户端缓存

- 1.20.1／1.21.1：`versions/*/src/main/java/com/skylogistics/client/ClientRuntimeEvents.java`
- 26.1.2：`versions/26.1.2/src/main/java/com/skylogistics/client/ClientModEvents.java`
- `versions/*/src/main/java/com/skylogistics/client/ClientDistributorHighlights.java`

### 包

- `versions/*/src/main/java/com/skylogistics/network/ModNetworking.java`
- `MenuActionPacket`
- `DistributorTargetsRequestPacket`
- `DistributorTargetsPacket`
- `ConfiguratorLineDetailsPacket`

## 24. 预计新增／修改文件清单

这里只列实施范围，不代表本规格提交会创建这些文件。

三版本都需要对应实现：

- `item/KleisDominionWandItem.java`
- `registry/ModItems.java`
- `network/KleisDominionWandSavedData.java`
- `network/KleisDominionWandRuntimeEndpoint.java`
- `network/KleisDominionWand*Packet.java`
- `network/ModNetworking.java`
- `client/ClientKleisDominionWandEndpoints.java`
- `client/KleisDominionWandOverlayRenderer.java`
- `client/ClientRuntimeEvents.java` 或 `ClientModEvents.java`
- `menu/KleisDominionWandMenu.java`
- `client/KleisDominionWandScreen.java`
- `registry/ModMenus.java`
- `config/SkyLogisticsConfig.java`
- `SkyNetworkRegistry.java`
- `SkyNetworkTicker.java`
- 必要的 endpoint interface／adapter

共享资源：

- `models/item/kleis_dominion_wand.json`
- `textures/item/kleis_dominion_wand.png` 的 32×192 动画帧条
- `textures/item/kleis_dominion_wand.png.mcmeta`
- 26.1.2 对应 item definition
- `lang/en_us.json`
- `lang/zh_cn.json`
- 三版本 recipe
- 创造模式物品栏
- 手册／GuideME／Patchouli 条目

## 25. 多版本差异

### 1.20.1 Forge

- `RegistryObject`
- `NetworkHooks.openScreen`
- `SimpleChannel`
- `RenderLevelStageEvent`／`RenderHighlightEvent`
- Forge capability `LazyOptional`／`ForgeCapabilities`
- 旧 SavedData `computeIfAbsent`
- ItemStack NBT

### 1.21.1 NeoForge

- `DeferredHolder`
- `ServerPlayer.openMenu`
- `CustomPacketPayload`／`StreamCodec`
- NeoForge block capability API
- `SavedData.Factory`
- `RegisterMenuScreensEvent`

### 26.1.2 NeoForge

- `Identifier`
- `SavedDataType`／Codec
- 当前 `Capabilities.Item.BLOCK`、`Capabilities.Fluid.BLOCK`、`Capabilities.Energy.BLOCK`
- `ExtractBlockOutlineRenderStateEvent` 与当前 render-state API
- 版本专用菜单覆盖 common 菜单
- `ConfiguratorStackPacket`／`ClientConfiguratorStack` 的现有同步模式

功能语义、数据字段、网络限制和验收标准必须三版一致。

## 26. 测试计划

### 26.1 单元测试

- EndpointKey 编解码与相等性
- SavedData 三版本 round-trip
- schemaVersion 拒绝未知新版本
- 普通右击映射存入／潜行右击映射抽取
- 从配置器复制 placement 配置
- 已有端点改模式不覆盖其它配置
- intrinsic cross-dimension 恒为 true
- capability side 使用 targetFace
- 红石读取 targetPos
- 动画尺寸序列
- 存入序列是抽取序列的反向
- 2×2／4×4 等边框像素掩码，确保内部不填充

### 26.2 GameTest／集成测试

- 单维度物品、流体、能量抽取和存入
- 跨维度已加载端点传输
- 未加载区块不传输且不强加载
- 区块重载后恢复注册
- 设备破坏后清理六面绑定
- 方块替换后按 boundBlockId 清理
- 过滤、优先级、红石、限量
- 多个面绑定同一设备
- 同一线路混合真实节点与虚拟节点
- Mekanism／Botania／Ars 开关开启与关闭

### 26.3 手工输入验收

- 生存单击左键
- 生存长按左键
- 创造单击／长按
- 高延迟服务器
- 无副手配置器
- 未绑定面
- 已绑定面
- 左击实体
- 左击空气
- 与其它模组扳手／工具同装
- 方块带自身左键行为

### 26.4 渲染验收

- 六个方向
- 浅色／深色／透明／发光设备
- 同一方块多面绑定
- 多个相邻设备
- F1、第三人称、不同 FOV
- 动画关闭时只保留外框
- 2→4→…→16 和反向序列无连续缩放
- 动画框内部始终透明
- 16×16 到达时只与常驻框叠加，不替换常驻框
- 无 Z-fighting、穿墙或深度写入残影
- 物品贴图六张唯一帧的透明轮廓逐像素一致
- 呼吸动画只改变杖端能量掩码
- `0→1→2→3→4→5→4→3→2→1` 顺序正确
- 30 tick 周期无首尾跳闪
- 物品栏、手持、掉落物和展示框中的默认动画行为一致
- 禁用物品动画时显示稳定静态帧

### 26.5 GUI 回归

- 原天穹节点六面 GUI 不变
- 外部网络接口单端点 GUI 不变
- 柯拉 GUI 无左侧面板
- 过滤槽与升级槽边界
- 维度升级卡不可插入
- 删除确认
- endpoint revision 冲突

## 27. 性能预算

- 不每帧向服务端请求 overlay。
- 不每 tick 扫描全服 SavedData。
- 运行时按维度和 chunk 索引 EndpointKey。
- 渲染只遍历客户端当前缓存范围。
- 每个面每帧固定绘制 8 个 quad：常驻框四条边＋动画框四条边。
- 可将相同 RenderType 的所有面批量写入同一 VertexConsumer。
- capability 解析只在区块／邻居变化或定期失效检查时刷新，不每次列表遍历都重新发现。
- 跨维度只合并已加载维度的活动线路索引。

## 28. 失败反馈文案

至少需要以下可本地化结果：

- 副手需要天穹配置器
- 该侧面没有可用的物流能力
- 已创建存入端点
- 已创建抽取端点
- 已切换为存入
- 已切换为抽取
- 该侧面没有柯拉端点
- 无权修改该端点
- 目标区块未加载
- 端点已失效
- 已移除虚拟节点
- 端点数量达到上限

## 29. 三阶祭坛配方

### 29.1 已确认规则

克莱斯支配之杖必须通过三阶天穹供奉祭坛制作，不提供工作台替代配方。

已确认配方骨架：

```text
配方类型：skylogistics:sky_offering
最低祭坛等级：3
祭坛中央：柯拉甘露
供桌核心材料：维度升级卡
其它供桌材料：原版常见高级材料
结果：1 × 克莱斯支配之杖
```

“祭坛中央是甘露”在数据上解释为 `1 × skylogistics:chora_nectar`，不是柯拉甘露块，也不要求额外充能状态。

三阶祭坛结构本身需要四个柯拉甘露块，并按 `tierThreeAltarWorkSpeedMultiplier` 加速供奉；该结构成本和速度倍率不计入配方材料。

### 29.2 配方格式约束

现有 `OfferingRecipe.MAX_OFFERINGS = 4`，因此供桌最多出现四种 counted ingredient。每种材料可以要求多个数量，但不能定义第五种材料。

三个版本应分别提供语义相同的配方文件：

- 1.20.1：`data/skylogistics/recipes/kleis_dominion_wand.json`
- 1.21.1：`data/skylogistics/recipe/kleis_dominion_wand.json`
- 26.1.2：`data/skylogistics/recipe/kleis_dominion_wand.json`

固定字段：

```json
{
  "type": "skylogistics:sky_offering",
  "altar_tier": 3,
  "main": {
    "ingredient": {
      "item": "skylogistics:chora_nectar"
    },
    "count": 1
  },
  "offerings": [],
  "result": {
    "id": "skylogistics:kleis_dominion_wand",
    "count": 1
  }
}
```

本段 JSON 只冻结结构；`offerings` 和 `duration` 在最终材料确认后补齐。

### 29.3 推荐候选

若没有其它平衡要求，推荐使用四种供桌材料：

| 材料 | 建议数量 | 意象／成本 |
| --- | ---: | --- |
| 维度升级卡 | 4 | 对应四个方向与内置跨维度能力 |
| 下界合金锭 | 4 | 高阶、稳定的杖身材料 |
| 回响碎片 | 8 | 对应跨空间定位与无形节点 |
| 末地水晶 | 4 | 对应空间能量与蓝橙呼吸光 |

建议基础 `duration = 960 tick`。在默认三阶 `4×` 工作倍率下，实际耗时约为 `240 tick = 12 秒`；服务器调整三阶倍率后，实际耗时按现有祭坛逻辑变化。

这套候选尚未冻结。若希望降低成本，优先把维度升级卡从 4 张改为 2 张；不建议移除维度升级卡，因为它是功能意象最直接的核心供物。

### 29.4 配方验收

- 一阶／二阶祭坛不能启动配方。
- 中央不是柯拉甘露时不能启动。
- 供桌缺少维度升级卡时不能启动。
- 多余供物会导致匹配失败，沿用现有严格匹配语义。
- 完成后只产出一根克莱斯支配之杖。
- 三版本 JEI／手册展示的材料、数量和时长一致。
- 服务器三阶速度倍率只改变实际耗时，不改变材料消耗。

## 30. 开放决策

以下内容尚未由产品需求明确，实施前应确认：

1. 是否允许克莱斯支配之杖攻击实体；本规格默认允许原版实体攻击。
2. 端点删除是否采用 GUI 二次确认；本规格推荐采用。
3. 动画每步 3 tick、周期 30 tick 是否合适。
4. 外框与动画框最终 alpha。
5. 主手持杖、副手无配置器时是否显示所有覆盖层；本规格默认显示。
6. 已有端点再次右击是否只改模式；本规格默认只改模式、不覆盖其它设置。
7. 方块 ID 不变但 BlockEntity 实例被替换时是否保留端点；本规格默认保留，正常 BreakEvent 会优先清理。
8. 杖端呼吸是否允许在手持、掉落物和展示框中同步播放；本规格默认允许。若必须仅限物品栏，需要自定义渲染路径。
9. 呼吸周期是否采用 30 tick／1.5 秒；本规格暂定采用。
10. 三阶配方是否采用 4 张维度升级卡；推荐候选为 4 张，低成本方案为 2 张。
11. 其它三种高级材料是否采用 4 个下界合金锭、8 个回响碎片和 4 个末地水晶。
12. 配方基础时长是否采用 960 tick，使默认 4× 三阶祭坛实际耗时约 12 秒。

## 31. 完成定义

只有同时满足以下条件才算功能完成：

- 三个版本全部实现并构建通过。
- 右击／潜行右击正确创建单面虚拟节点。
- 左击打开单端点 GUI 且绝不破坏方块。
- SavedData 可跨重启恢复。
- 区块卸载不删配置、加载后恢复运行。
- 设备移除能清理端点。
- 跨维度无需升级卡且不强加载。
- 蓝存入／橙抽取语义全局一致。
- 常驻框与离散矩形框动画完全符合第 18 节。
- 物品栏杖端呼吸动画符合 18.9，且不改变贴图透明轮廓。
- 原有节点、配置器、分发器高亮和外部接口无回归。
- 所有新网络包均有服务端权限、距离、维度和 revision 验证。
- 相关配置、语言、模型、配方、手册和测试同步三版本。
- 三阶祭坛配方符合第 29 节，且不存在工作台替代配方。
