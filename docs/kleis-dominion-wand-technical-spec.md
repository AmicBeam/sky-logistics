# 克莱斯支配之杖（Kleis Dominion Wand）技术规格

> 状态：设计冻结前草案；仅记录需求与实现约束，不代表功能已实现。
> 更新日期：2026-09-01
> 覆盖版本：Minecraft 1.20.1 Forge、1.21.1 NeoForge、26.1.2 NeoForge

## 1. 目标

克莱斯支配之杖是一件独立的物流工具。玩家主手持有它，并在副手放置天穹配置器时，可以让物流网络记住设备的一个具体方块侧面。这个“记忆端点”不创建物流节点方块、不占用空间，但在线路配置、过滤、优先级和传输能力方面应与单连接面的天穹物流节点保持一致。

核心体验：

- 普通右击未绑定设备侧面：创建蓝色的存入端点。
- 潜行右击未绑定设备侧面：创建橙色的抽取端点。
- 再次右击任意已绑定侧面：直接删除端点。
- 左击已绑定侧面：打开单端点节点 GUI。
- 左击方块时绝不进入方块破坏流程。
- 左击实体时取消攻击伤害，并尝试把该实体传送到同维度 `Y=256`。
- 主手持杖且副手配置器提供当前线路时，只显示该线路的端点覆盖层。
- 主手持配置器、副手持杖时进入“覆盖面编辑模式”：显示附近有权查看的虚拟端点，可潜行右击复制端点配置，并用普通右击把配置器中的复制配置粘贴到端点。
- 配置器复制数据必须在真实物流节点与虚拟端点之间双向兼容，支持节点→端点、端点→节点和端点→端点。
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

### 5.1 杖在主手：端点创建模式

创建、删除端点、左击打开端点 GUI、阻止杖破坏方块和左击实体传送，只认主手中的克莱斯支配之杖。

### 5.2 副手配置器

创建、删除端点和打开 GUI 时，副手必须是 `ConfiguratorItem`。

推荐行为：

- 主手有杖、副手有配置器：完整功能可用。
- 主手有杖、副手无配置器：仍禁止用杖破坏方块；不创建／删除端点，不显示覆盖层；显示“副手需要天穹配置器”。
- 主手有配置器、副手有杖：进入第 5.3 节的覆盖面编辑模式，不触发杖的创建、删除、左击防破坏或实体传送。
- 仅副手有杖且主手不是配置器：按普通副手物品处理。

### 5.3 配置器在主手、杖在副手：覆盖面编辑模式

此模式复用真实物流节点的配置器复制／粘贴心智：

- 潜行右击已有虚拟端点覆盖面：把该单端点配置复制到主手配置器，并进入 `PasteMode`。
- 普通右击已有虚拟端点覆盖面，且配置器处于 `PasteMode`：把配置器中的复制配置粘贴到该端点。
- 普通右击已有虚拟端点覆盖面，且配置器不处于 `PasteMode`：打开该端点 GUI，与主手持杖左击打开的结果一致。
- 此编辑模式固定显示附近所有玩家有权查看的线路覆盖面，不按配置器当前 `lineId` 过滤；当前线路只影响高亮强度。
- 此模式下任何右击都不得创建或删除虚拟端点；永久删除仍只由“主手杖＋副手配置器”右击已绑定面完成。
- 复制／粘贴命中的是 `dimension + targetPos + targetFace`，不是整个目标方块；同一设备的不同面必须独立处理。
- 主手配置器仍按现有逻辑对真实物流节点工作；副手杖不能抢占或改变真实节点的复制／粘贴事件。

四种互操作路径都必须成立：

1. 真实节点 → 真实节点：保持现有行为。
2. 真实节点 → 虚拟端点：先潜行右击节点复制，再普通右击端点覆盖面粘贴。
3. 虚拟端点 → 真实节点：先潜行右击端点覆盖面复制，再普通右击节点粘贴。
4. 虚拟端点 → 虚拟端点：先复制源覆盖面，再普通右击目标覆盖面粘贴。

### 5.4 可视化条件

覆盖层有两种请求模式：

- **当前线路查看模式**：主手持杖，副手持有已绑定线路的 `ConfiguratorItem`。
- **附近端点编辑模式**：主手持有 `ConfiguratorItem`，副手持杖。

当前线路查看模式：

- 当前线路取自副手配置器的 `lineId`。
- 只显示该 `lineId` 在玩家当前维度、同步范围内的虚拟端点。
- 副手没有配置器、配置器尚未产生线路信息或当前 `lineId` 无效时，不显示任何覆盖层，也不请求 overlay snapshot。
- 玩家在配置器中切换线路时，客户端立即清空旧线路缓存并请求新线路快照。

附近端点编辑模式：

- 显示当前维度、同步范围内所有玩家有权查看的虚拟端点，不按主手配置器当前 `lineId` 过滤，确保复制真实节点后仍能命中其它线路上的目标端点。
- 与主手配置器当前 `lineId` 相同的端点保持正常 alpha；其它线路端点使用较低 alpha，并在准星命中时显示线路名，避免误粘贴。
- 配置器没有有效当前线路时仍可查看并复制已有端点；粘贴要求配置器包含服务端认可的完整 `ToolConfig` 和有效 `lineId`。
- 换手、丢失任一物品或退出编辑模式时，立即清空对应缓存。

## 6. 世界交互状态机

### 6.1 普通右击

条件：主手克莱斯支配之杖、副手配置器、命中方块侧面、服务器验证通过。

行为：

1. 计算 `EndpointKey`。
2. 若端点已存在，直接删除该端点，不考虑现有模式，并结束本次操作。
3. 若端点不存在，创建存入端点。
4. 新建时从副手配置器读取当前线路及 placement 配置。
5. 返回 `CONSUME`，阻止目标方块和副手配置器继续处理本次右击。

### 6.2 潜行右击

若端点已存在，与普通右击一样直接删除；若端点不存在，则创建抽取端点。

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
- 左击实体进入第 6.4 节的传送逻辑，不造成普通攻击伤害。

### 6.4 左击实体传送

主手持有克莱斯支配之杖并左击实体时，不要求副手配置器或线路信息。

服务端尝试在同一维度内把被点击实体传送到：

```text
targetX = entity.getX()
targetY = 256.0
targetZ = entity.getZ()
```

规则：

- 取消原版攻击，不造成近战伤害、击退或武器耐久消耗。
- 保持实体原有 X、Z、yaw、pitch；只修改 Y。
- 不改变维度，不强加载新区块。
- 玩家、生物及其它可被正常左击选中的实体都可以尝试；玩家目标仍须遵守服务器 PvP、权限和保护规则。
- 服务端必须验证实体 id、攻击距离、视线和主手物品，不能信任客户端目标。
- 必须尊重对应版本的实体传送事件及其它模组取消结果。
- 若目标已移除、死亡、传送事件被取消、位置不合法或对应实体 API 拒绝传送，则不产生效果并返回失败反馈。
- 乘客／载具关系不递归处理；只尝试传送被点击实体。API 不允许时按失败处理。
- 每次物理点击最多尝试一次，不增加额外连发计时器或自定义冷却。

`Y=256` 是固定目标值，不根据维度建筑高度自动钳制；无法接受该坐标的维度按“尝试失败”处理。

### 6.5 移除端点

任意右击手势命中已绑定的 `EndpointKey` 时直接删除端点：

1. 不区分普通右击或潜行右击。
2. 不显示二次确认。
3. 服务端校验所有者／权限后删除 SavedData 记录。
4. 从运行时网络索引注销。
5. 向当前线路的附近客户端广播删除增量。
6. 若该端点 GUI 正在打开，关闭菜单。

删除后的下一次右击才会按照普通／潜行状态重新创建端点；同一次点击不执行“删除后重建”。

### 6.6 覆盖面复制／粘贴状态机

条件：主手是 `ConfiguratorItem`、副手是克莱斯支配之杖、命中面已有虚拟端点、服务器验证通过。

潜行右击复制：

1. 客户端根据 overlay 命中得到候选 `EndpointKey`，立即消费本次交互，避免目标设备处理潜行右击。
2. 服务端重新射线并验证命中的维度、坐标、方块面、端点存在性和查看权限。
3. 服务端把端点记录投影为第 8.2 节定义的单面 `ToolConfig`，写入主手配置器。
4. 把主手配置器的 `PasteMode` 设为 `true`，同步物品栈并显示与真实节点一致的“已复制，可粘贴”反馈。

普通右击粘贴：

1. 若主手配置器不在 `PasteMode`，请求打开端点 GUI，不修改端点。
2. 若处于 `PasteMode`，服务端从主手配置器重新读取 `ToolConfig`，不得信任包内提交的配置副本。
3. 服务端按第 8.3 节将单面投影应用到目标端点，更新 SavedData、revision、运行时索引和附近 overlay 快照。
4. 与真实节点行为一致，成功粘贴后保持 `PasteMode`，允许连续粘贴；玩家通过配置器既有操作退出粘贴模式。

事件路由优先级：

- 命中真实 `SkyNodeBlockEntity` 时，始终交给主手配置器的现有 `useOnNode` 逻辑，副手杖不拦截。
- 命中普通设备且命中面存在虚拟端点时，覆盖面编辑逻辑消费事件。
- 命中普通设备但该面没有虚拟端点时，返回 `PASS`，不得创建端点，也不得阻止设备或配置器的正常交互。
- 客户端 overlay 只负责选择候选面；服务端必须重新确认该面当前仍有端点，不能仅凭客户端缓存执行复制或粘贴。

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

### 7.5 实体攻击拦截与传送请求

客户端 `InteractionKeyMappingTriggered` 命中 `EntityHitResult` 且主手为克莱斯支配之杖时：

1. 取消普通攻击输入，避免先造成伤害再由服务器回滚。
2. 发送只包含目标 entity id 的传送请求。
3. 不要求副手配置器。

服务端也必须通过物品的左击实体 hook 或 `AttackEntityEvent` 取消原版伤害路径。传送请求只表达意图，服务端重新验证当前命中、距离、权限和实体状态后，才执行第 6.4 节的传送尝试。

客户端没有权力直接设置实体位置。单人游戏也必须经过逻辑服务端路径。

## 8. 配置复制、粘贴与互操作

### 8.1 新建端点时复制配置

复用 `ConfiguratorItem.ToolConfig` 和 `ConfiguratorItem.FaceConfig` 的语义，不复制粘贴模式状态。

从副手配置器复制：

- 当前 `lineId`
- 当前线路显示名与 assigned name
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

### 8.2 虚拟端点复制到配置器

虚拟端点只有一个连接面，不能伪造一份六面节点配置。复制端点时生成：

```text
ToolConfig
  lineId / lineName = EndpointRecord 的线路
  placement = 当前端点的单面 FaceConfig
  faces = 默认六面配置
  hasCopiedFaces = false
  upgrades = 当前端点允许复制的逻辑升级
```

`placement` 必须包含端点当前的：

- 抽取／存入模式
- 资源启用状态与自动检测状态
- 红石控制固定写为 `IGNORE`；覆盖面本身不支持红石，不能把源端点或真实节点的红石配置复制出去
- 优先级
- 槽位／维持量限制
- 过滤器

复制完成后沿用 `ConfiguratorItem.writeConfig`、线路名称绑定和 `PasteMode` 的现有同步路径。这样把虚拟端点粘贴到真实节点时，真实节点会走既有 `hasCopiedFaces == false` 的 placement 粘贴语义，只修改节点当前目标面，而不会覆盖节点其它五面。

### 8.3 配置器粘贴到虚拟端点

无论配置器中的复制源是真实节点还是虚拟端点，粘贴到虚拟端点时都只应用 `ToolConfig.placement()`：

- 真实节点复制产生的 `ToolConfig` 已把节点当前目标方向的 `FaceConfig` 放入 `placement`；不得把 `faces` 六面配置展开到一个虚拟端点。
- 虚拟端点复制产生的 `ToolConfig` 本身就是单面 projection。
- 粘贴会复制线路、单面模式、资源设置、优先级、限制、过滤器和兼容升级；忽略源配置中的红石字段，目标端点始终使用 `IGNORE`。
- 粘贴可以改变目标端点的 `lineId`；必须先从旧线路索引注销，再注册到新线路，并向附近查看者刷新完整快照。
- 目标面当前不再暴露任何启用资源能力时拒绝粘贴，不写入半成品配置。

升级复制必须复用真实节点的库存约束：只安装玩家物品栏中实际存在、且虚拟端点支持的缺失升级，不得凭配置复制物品。维度升级对虚拟端点无意义，因为其跨维能力内置；粘贴时忽略该升级并给出只读提示，不写入 SavedData，也不消耗玩家物品。

### 8.4 复制数据的兼容边界

- `PasteMode` 只属于配置器物品栈，不写入端点 SavedData，也不作为端点配置的一部分复制。
- 配置器线路浏览光标、其它五面配置和 GUI 临时状态不得写入虚拟端点。
- 未知或来自更新版本的配置字段按现有 `ToolConfig` 兼容规则处理；不能因为客户端提交未知 NBT 而覆盖服务端数据。
- 复制源在复制完成后被删除或改动，不影响配置器中已经保存的快照；粘贴时仍以配置器快照和服务端权限校验为准。

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
  priority
  itemSlotLimit
  faceFilters
  upgrades
  createdGameTime
  modifiedGameTime
```

### 10.2 不持久化的运行时字段

以下字段只保存在服务器运行时缓存：

- 每资源传输游标
- 最近传输时间
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

克莱斯支配之杖创建的虚拟覆盖面不支持红石控制，始终视为 `RedstoneControl.IGNORE`：

- 调度器不得读取 `targetPos` 或邻近位置的红石信号。
- `isFaceRedstoneAllowed()` 恒为 `true`。
- 不保存或消费脉冲边沿状态。
- 从配置器或真实节点复制／粘贴时忽略红石字段。
- 读取旧存档中遗留的红石字段时必须归一化为 `IGNORE`；后续保存不得恢复旧状态。
- GUI 保留红石控件所在布局，但按钮必须禁用并显示“覆盖面不支持红石”的 tooltip；服务端同时拒绝对应动作，不能只依赖客户端禁用。

## 15. GUI

### 15.1 总体

左击已绑定面打开专用 `KleisDominionWandMenu`／`KleisDominionWandScreen`，视觉与节点 GUI 一致，但只管理一个端点。

不能把 `targetPos` 伪装成 `SkyNodeMenu` 的 BlockEntity 位置。当前 `SkyNodeMenu` 和 `SkyNodeScreen` 会直接从世界取 `SkyNodeBlockEntity`，虚拟端点需要独立后端。

### 15.2 可复用视觉组件

- `ConfigPanel`
- 节点 GUI 的资源按钮、模式按钮、优先级步进器，以及只读禁用状态的红石按钮
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
- 禁用的红石控制（固定显示“忽略”）
- 优先级
- 槽位／维持量限制
- 当前面的过滤器
- 支持的非原版资源提示
- “跨维度：内置”

GUI 不提供二次确认式删除按钮；端点统一通过再次右击目标面直接删除。

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

- `KleisDominionWandToggleEndpointPacket`
  - 右击未绑定面时创建；右击已绑定面时删除
  - 字段：dimension、pos、face、requestedMode
- `KleisDominionWandOpenMenuPacket`
  - 左击请求打开菜单
  - 字段：dimension、pos、face
- `KleisDominionWandMenuActionPacket`
  - 修改单端点配置
  - 字段：containerId、endpointKey、revision、action、value
- `KleisDominionWandTeleportEntityPacket`
  - 左击实体时请求把目标传送到 Y=256
  - 字段：entityId
- `KleisDominionWandOverlayRequestPacket`
  - 字段只包含请求模式：`CURRENT_LINE` 或 `EDIT_NEARBY`
  - `CURRENT_LINE`：客户端主手持杖且副手配置器有线路时请求当前维度、当前线路附近端点
  - `EDIT_NEARBY`：客户端主手配置器、副手持杖时请求当前维度附近所有有权查看的端点
  - 服务端必须从实际双手物品与配置器重新推导模式和 lineId，不能信任客户端声明
- `KleisEndpointEditPacket`
  - `COPY`：配置器主手、杖副手时潜行右击覆盖面复制
  - `PASTE`：配置器主手、杖副手时普通右击覆盖面粘贴
  - 字段：pos、face、expectedRevision、action；维度由服务端玩家当前位置确定
  - 不携带 `ToolConfig`；服务端从主手配置器物品栈读取

### S2C

- `KleisDominionWandEndpointSnapshotPacket`
  - 菜单配置快照
- `KleisDominionWandOverlaySnapshotPacket`
  - 当前维度附近可见端点列表
- `KleisDominionWandActionResultPacket`
  - 失败原因或成功反馈
- 配置器物品栈更新继续复用 `ConfiguratorStackPacket`／容器槽同步，不新增一份客户端自报配置协议

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
- lineId
- mode
- revision
- 可选 resource mask

### 17.2 请求策略

客户端进入世界或切换维度时先预取当前维度附近所有有权查看的线路覆盖面；进入任一有效双持模式时再刷新相应快照：

- 主手杖＋副手有效配置器：请求 `CURRENT_LINE`。
- 主手配置器＋副手杖：请求 `EDIT_NEARBY`。

之后：

- 玩家进入新区块时刷新。
- 暂时换手或收起工具时保留客户端最近一次快照；重新进入相同双持编辑模式时先立即显示缓存，再异步刷新，避免遮罩延迟出现。
- 任一双持组合被破坏、换手或相关物品移除时立即清空缓存并停止请求。
- `CURRENT_LINE` 下配置器切换当前线路时立即清空旧缓存并请求新 `lineId`。
- `EDIT_NEARBY` 下配置器切换线路不必重新请求全量端点，只更新当前线路高亮；复制或粘贴导致端点换线时由服务端刷新附近快照。
- 不做定时轮询；端点变更由服务端主动同步，玩家移动只在跨越区块边界时重新请求附近快照。
- 登录、退出、切维度时清空缓存。
- 服务端变更通过附近完整快照主动更新查看者；覆盖面数量和变更频率较低，不额外维护 delta 协议。

### 17.3 服务端筛选

两种模式都只发送：

- 玩家当前维度
- 已加载区块
- 距玩家小于配置半径
- 玩家有权查看的端点

此外：

- `CURRENT_LINE` 只发送与服务端从副手配置器读取到的当前 `lineId` 相同的端点。
- `EDIT_NEARBY` 不按线路过滤，但仍受最大条数、距离、加载状态和逐端点查看权限限制。

## 18. 方块面覆盖层

### 18.1 颜色

- 存入：蓝色。
- 抽取：橙色。
- 同一方块坐标同时存在抽取面和存入面时，方块中心透视标记使用浅紫色；各侧面仍按自身模式保持蓝色或橙色。

建议从现有像素资产取色：

- 蓝主色：`#79DCE9`
- 蓝深色：`#3C91C5`
- 橙主色：`#FFD56F`
- 橙深色：`#E89A36`

最终 RGBA 应通过游戏内浅色、深色、发光和透明方块背景测试后冻结。

### 18.2 常驻半透明面

覆盖层可见期间，每个虚拟端点始终绘制低 alpha 的整面半透明色罩。

不得绘制常驻外框或辅助细线环；线框仅用于第 18.3 节的周期动画。

### 18.3 动画框

动画层绘制半透明矩形线框，内部保持透明。

尺寸是离散像素帧：

```text
2×2, 4×4, 6×6, 8×8, 10×10, 12×12, 14×14, 16×16
```

对任意尺寸 `S`：

```text
min = (16 - S) / 2
max = (16 + S) / 2
```

不绘制辅助环。

不得对矩形内部填色。

### 18.4 抽取动画

橙色，由内向外：

```text
2 → 4 → 6 → 8 → 10 → 12 → 14 → 16 → 消失／重置
```

`16×16` 与方块面边界重合，随后动画框消失并等待重置。

### 18.5 存入动画

蓝色，由外向内：

```text
16 → 14 → 12 → 10 → 8 → 6 → 4 → 2 → 消失／重置
```

空间尺寸必须逐级跳变，不做连续缩放插值。

### 18.6 时间

冻结的初始参数：

- 每个尺寸保持 `5 tick`
- 八个尺寸共 `40 tick`
- 重置间隔 `10 tick`
- 完整周期 `50 tick`（2.5 秒，位于要求的 2–3 秒范围内）
- 动画框常规 `alpha = 0.50`
- 不要求额外的末帧淡出

动画相位直接使用客户端当前 level game time 对 50 tick 取模，保证同一客户端内所有端点严格同步且不依赖帧率；不为每个端点维护独立起始时间。方向仅由 mode 反转，便于玩家直接比较流向。

### 18.7 几何与深度

- 覆盖层沿目标面法线向外偏移约 `0.002` 方块，避免 Z-fighting。
- 所有目标先用正常深度测试绘制可见的半透明色罩。
- 不透视端点面本身；每个已同步端点目标都在方块内部 `[0.25, 0.75]³` 绘制 alpha `0.50` 的中心立方体，并使用 `GREATER` 深度测试、只写颜色的遮挡通道显示被实体方块遮住的片段。此标记方式与 AE2LT 的 see-through inner cube 一致；同一目标方块绑定多个面时中心立方体每帧只绘制一次。
- 关闭面剔除或为六个方向生成正确顶点顺序。
- 半透明动画层不写深度。
- 动画框不可越过方块面的 `[0,1] × [0,1]` 范围。
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

物品呼吸动画首版固定每项保持 `3 tick`：

```text
10 个播放项 × 3 tick = 30 tick = 1.5 秒／周期
```

首版 `.png.mcmeta` 固定为：

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

原版 animated texture metadata 会在所有引用该纹理的模型中播放。本功能明确接受并要求同时出现在：

- 物品栏
- 手持第一／第三人称
- 掉落物
- 物品展示框等普通 item model 场景

实现应优先使用一份普通 animated texture metadata，让所有 item model 场景共享同一动画图集，不使用按显示上下文分支的自定义渲染器。该路径只增加一个小型 atlas 动画，在性能预算内。

#### 客户端配置

首版不要求提供按显示上下文关闭动画的能力。若保留 `animateKleisDominionWandItemIcon` 配置，关闭时使用 `L0` 或中间亮度静态帧；不得为此引入每帧自定义物品渲染器。

### 18.10 编辑模式命中反馈

主手配置器、副手杖时：

- 当前线路端点沿用第 18.1 节正常颜色与 alpha。
- 其它线路端点保持蓝／橙模式颜色，但常驻框与动画框 alpha 乘以 `0.45`，不得改用第三种模式颜色。
- 准星射线命中某个已同步覆盖面时，该面恢复正常 alpha，并在物品栏上方的 action bar 区域分两行显示线路名和已启用资源（物品／流体／能量）；抽取／存入继续只通过覆盖面颜色区分。
- 命中判定必须使用原版方块射线结果的 `pos + face` 与客户端 overlay 缓存精确匹配，不给覆盖层创建超出原方块面的额外碰撞箱。
- 客户端命中反馈不是权限或端点存在性的最终证据；服务端拒绝后必须显示失败原因并回滚任何预测高亮。

## 19. GUI 与覆盖层的一致性

- 蓝色必须在 GUI、世界覆盖层、tooltip 和线路详情中始终表示存入。
- 橙色必须始终表示抽取。
- 改模式后先等待服务端确认，再更新 GUI 与覆盖层；可做客户端预测，但失败必须回滚。

## 20. 失效与清理

### 20.1 正常破坏

设备被玩家正常破坏时，行为与真实物流节点失去相邻目标一致：

1. 保留 SavedData 中的 EndpointKey 和全部配置。
2. capability 不可用时从活动传输索引暂时注销，进入 dormant。
3. 标记线路拓扑脏。
4. 空气位置没有可渲染方块面，因此客户端暂时不绘制覆盖层。
5. 同坐标重新出现可用设备后重新探测并恢复端点。

### 20.2 爆炸、命令替换和活塞

方块被爆炸、命令、活塞或其它模组替换时同样保留绑定。虚拟端点绑定的是“维度＋坐标＋面”，不是某个特定方块 ID 或 BlockEntity 实例。

- 不保存或比较 `boundBlockId`。
- 不因 blockstate 或 BlockEntity 实例变化删除端点。
- 方块变化只触发 capability 重新探测和运行时索引刷新。
- 新方块在同一 `targetFace` 暴露可用能力时，沿用原线路、模式、过滤、优先级和升级配置继续工作。
- 只有玩家再次右击该面时才永久删除 EndpointRecord。

这与真实物流节点保持一致：相邻设备消失不会删除节点本身，新的相邻设备出现后节点继续使用原配置。

### 20.3 capability 暂时失效

当目标位置为空、方块被替换或 capability 暂时返回空：

- 当 tick 跳过传输。
- 不立即删配置。
- 无任何支持能力时标记 dormant，但不删除记录。
- 区块重新加载或邻居／能力失效通知后重新探测。

不得因为区块卸载而删除端点。

## 21. 权限与安全

所有 C2S 请求必须验证：

- 玩家仍在服务器上。
- 玩家当前维度等于请求维度。
- 创建、删除、左击打开和实体传送请求中，主手仍是克莱斯支配之杖；需要端点配置时副手仍是配置器。
- 覆盖面复制／粘贴／右击打开请求中，主手仍是配置器、副手仍是克莱斯支配之杖。
- 复制时玩家有权查看源端点；粘贴时玩家有权修改目标端点并使用配置器快照中的目标线路。
- 粘贴配置只从服务端看到的主手配置器读取；包内不得接受客户端提供的 `ToolConfig`、过滤器或升级 NBT。
- 粘贴安装逻辑升级时逐项验证物品栏、升级类型和堆叠上限；内置跨维端点不消耗维度升级。
- 命中位置在正常方块交互距离内。
- 服务端重新射线或至少验证 pos／face 与玩家视线合理。
- 目标区块已加载；不得由请求触发加载。
- 玩家有权使用该线路。
- 玩家有权修改该端点。
- endpoint revision 与服务端一致。
- 实体传送请求的 entity id 仍存在于玩家当前维度。
- 实体目标仍在正常攻击距离和视线内，并通过 PvP／保护／传送事件检查。

建议所有者规则：创建者成为端点 owner；线路 owner 规则沿用 `SkyPlayerLines`。管理员权限和团队共享若已有统一实现，应复用而不是另建名单。

## 22. 配置项

建议服务器配置：

- `enableKleisDominionWand`：总开关，默认 `true`
- `kleisDominionWandMaxEndpointsPerPlayer`：默认 `256`
- `kleisDominionWandMaxEndpointsPerChunk`：默认 `64`
- `kleisDominionWandOverlayRange`：默认 `64`
- `kleisDominionWandOverlayMaxEntries`：默认 `512`
- `kleisDominionWandValidationIntervalTicks`：默认 `20`

建议客户端配置：

- `renderKleisDominionWandOverlays`：默认 `true`
- `kleisDominionWandOverlayAnimation`：默认 `true`
- `kleisDominionWandOverlayAlpha`：默认外框 `0.85`、动画框 `0.50`
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
- 未绑定面普通右击创建存入／潜行右击创建抽取
- 任意右击已绑定面只删除、不在同一次点击重建
- 从配置器复制 placement 配置
- 虚拟端点复制为 `hasCopiedFaces == false` 的单面 `ToolConfig`
- 真实节点复制配置粘贴到虚拟端点时只应用 `placement`
- 复制／粘贴包不携带客户端自报 `ToolConfig`
- 成功连续粘贴后 `PasteMode` 保持，显式退出后关闭
- 删除端点后 SavedData、运行时索引和覆盖缓存同步移除
- intrinsic cross-dimension 恒为 true
- capability side 使用 targetFace
- 覆盖面的红石状态恒为 `IGNORE`，不读取世界信号、不消费脉冲
- 动画尺寸序列
- 存入序列是抽取序列的反向
- 2×2／4×4 等边框像素掩码，确保内部不填充

### 26.2 GameTest／集成测试

- 单维度物品、流体、能量抽取和存入
- 跨维度已加载端点传输
- 未加载区块不传输且不强加载
- 区块重载后恢复注册
- 设备破坏后端点进入 dormant 且配置保留
- 同坐标重新放置兼容设备后沿用原配置恢复
- 方块 ID 或 BlockEntity 实例替换不会删除绑定
- 过滤、优先级、限量；覆盖面不受红石影响
- 多个面绑定同一设备
- 同一线路混合真实节点与虚拟节点
- 真实节点→虚拟端点、虚拟端点→真实节点、虚拟端点→虚拟端点复制／粘贴
- 粘贴改变 lineId 时旧／新线路索引与附近 overlay 快照同步正确
- 粘贴升级只消耗实际物品，维度升级不消耗且不写入虚拟端点
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
- 左击实体不造成伤害并尝试传送到原 X/Z、Y=256
- 实体传送失败、权限拒绝及事件取消反馈
- 左击空气
- 与其它模组扳手／工具同装
- 方块带自身左键行为
- 主手配置器＋副手杖潜行右击覆盖面复制
- 主手配置器＋副手杖普通右击覆盖面连续粘贴
- 主手配置器＋副手杖、非粘贴模式右击覆盖面打开 GUI
- 主手配置器＋副手杖右击未绑定普通设备面不被误拦截
- 主手配置器操作真实节点时副手杖不抢占事件

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
- 物品呼吸 30 tick 周期无首尾跳闪
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
- 每个面每帧只绘制固定且有上限的线段，不随网络规模外的状态增长。
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
- 已复制虚拟端点配置，可连续粘贴
- 已将配置粘贴到虚拟端点
- 配置器没有可粘贴的配置
- 复制源配置版本不受支持
- 目标面不支持配置中启用的资源
- 已忽略内置跨维端点不需要的维度升级
- 无权修改该端点
- 目标区块未加载
- 端点已失效
- 已移除虚拟节点
- 端点数量达到上限
- 已将目标送往 Y=256
- 无法传送该目标

## 29. 三阶祭坛配方

### 29.1 已确认规则

克莱斯支配之杖必须通过三阶天穹供奉祭坛制作，不提供工作台替代配方。

已确认配方骨架：

```text
配方类型：skylogistics:sky_offering
最低祭坛等级：3
祭坛中央：4 × 柯拉甘露
供桌核心材料：维度升级卡
其它供桌材料：原版常见高级材料
结果：1 × 克莱斯支配之杖
```

“祭坛中央是甘露”在数据上解释为 `4 × skylogistics:chora_nectar`，不是柯拉甘露块，也不要求额外充能状态。

三阶祭坛结构本身需要四个柯拉甘露块，并按 `tierThreeAltarWorkSpeedMultiplier` 加速供奉；该结构成本和速度倍率不计入配方材料。

### 29.2 配方格式约束

现有 `OfferingRecipe.MAX_OFFERINGS = 4`，因此供桌最多出现四种 counted ingredient。每种材料可以要求多个数量，但不能定义第五种材料。

三个版本应分别提供语义相同的配方文件：

- 1.20.1：`data/skylogistics/recipes/kleis_dominion_wand.json`
- 1.21.1：`data/skylogistics/recipe/kleis_dominion_wand.json`
- 26.1.2：`data/skylogistics/recipe/kleis_dominion_wand.json`

1.21.1／26.1.2 固定字段：

```json
{
  "type": "skylogistics:sky_offering",
  "altar_tier": 3,
  "main": {
    "ingredient": {
      "item": "skylogistics:chora_nectar"
    },
    "count": 4
  },
  "offerings": [
    {
      "ingredient": {
        "item": "skylogistics:eulogia_crystal"
      },
      "count": 64,
      "charged": true
    },
    {
      "ingredient": {
        "item": "skylogistics:dimension_upgrade"
      },
      "count": 1
    },
    {
      "ingredient": {
        "item": "minecraft:echo_shard"
      },
      "count": 8
    },
    {
      "ingredient": {
        "item": "minecraft:end_crystal"
      },
      "count": 16
    }
  ],
  "result": {
    "id": "skylogistics:kleis_dominion_wand",
    "count": 1
  },
  "duration": 9600
}
```

1.20.1 使用该版本既有的扁平 counted ingredient 格式：`main`／`offerings` 条目直接写 `item` 与 `count`，结果使用 `result.item`；材料、数量、祭坛等级和 9600 tick 时长必须与新版本一致。

64 个尤洛伽水晶必须全部已充能：配方条目写 `charged: true`。它们可以在一个供桌槽中以完整一组提交。

### 29.3 最终材料与时长

供桌材料已冻结：

| 材料 | 数量 | 意象／成本 |
| --- | ---: | --- |
| 尤洛伽水晶 | 64 | 以完整一组已充能天穹结晶提供仪式能量；全部要求充能 |
| 维度升级卡 | 1 | 固化内置跨维度能力 |
| 回响碎片 | 8 | 为无形节点提供位置回响 |
| 末影水晶 | 16 | 提供跨空间锚定与杖端呼吸能量 |

基础 `duration = 9600 tick`，即不考虑祭坛加速时为 8 分钟。

三阶祭坛速度倍率按现有逻辑生效：

```text
实际 tick = 9600 / tierThreeAltarWorkSpeedMultiplier
```

默认三阶倍率为 `4×`，因此默认实际耗时为 `2400 tick = 120 秒 = 2 分钟`。服务器把倍率调整为其它值时，实际耗时相应变化，但配方材料不变。

世界观含义：中央四份柯拉甘露作为承载虚拟节点的“容受介质”；完整一组尤洛伽水晶提供足够的天穹结晶能量；回响碎片让不存在实体方块的节点仍能在位置上留下回响；末影水晶提供空间锚定与蓝橙呼吸能量；唯一一张维度升级卡把跨维度能力固化在杖中。

### 29.4 配方验收

- 一阶／二阶祭坛不能启动配方。
- 中央少于 4 个柯拉甘露或物品不匹配时不能启动。
- 供桌材料必须精确满足 64 个尤洛伽水晶、1 张维度升级卡、8 个回响碎片和 16 个末影水晶。
- 64 个尤洛伽水晶必须全部已充能；未充能水晶不能启动配方。
- 多余供物会导致匹配失败，沿用现有严格匹配语义。
- 完成后只产出一根克莱斯支配之杖。
- 三版本 JEI／手册展示的材料、数量和时长一致。
- 基础时间显示为 9600 tick／8 分钟；服务器三阶速度倍率只改变实际耗时，不改变材料消耗。

## 30. 已冻结决策

以下产品决策已经冻结，不再作为实施阻塞项：

1. 左击实体不攻击，改为尝试在同维度传送到 `Y=256`，X/Z 不变。
2. 右击已绑定面直接删除端点，不二次确认。
3. 世界覆盖动画采用 50 tick／2.5 秒周期，只保留半透明面和 alpha 0.50 的动画框，不绘制常驻外框或辅助细线环。
4. 主手杖模式下，没有副手配置器或配置器没有当前线路时不显示覆盖层。
5. 主手杖模式的覆盖层只显示副手配置器当前 `lineId` 的端点；主手配置器＋副手杖的编辑模式显示附近所有有权查看的端点并弱化非当前线路。
6. 方块被破坏、替换或 BlockEntity 实例变化时保留端点配置，能力恢复后继续工作。
7. 物品呼吸动画在物品栏、手持、掉落物和展示框中统一播放，使用普通 animated texture。
8. 物品呼吸动画采用 30 tick／1.5 秒周期。
9. 主手配置器＋副手杖进入覆盖面编辑模式；潜行右击复制、普通右击粘贴，复制数据与真实物流节点双向兼容。
10. 虚拟覆盖面不支持红石，始终忽略世界红石信号；GUI 红石按钮禁用，复制／粘贴不传播红石状态。
11. 支配之杖配方中的 64 个尤洛伽水晶全部要求已充能。

## 31. 完成定义

只有同时满足以下条件才算功能完成：

- 三个版本全部实现并构建通过。
- 右击／潜行右击正确创建单面虚拟节点。
- 再次右击已绑定面直接删除端点，且不要求二次确认。
- 左击打开单端点 GUI 且绝不破坏方块。
- 左击实体不造成攻击伤害，并按第 6.4 节尝试传送至 Y=256。
- SavedData 可跨重启恢复。
- 区块卸载不删配置、加载后恢复运行。
- 设备移除或替换不会删除端点；同坐标能力恢复后继续使用原配置。
- 跨维度无需升级卡且不强加载。
- 蓝存入／橙抽取语义全局一致。
- 覆盖面始终使用 `RedstoneControl.IGNORE`，GUI 红石按钮禁用且服务端不能改变该状态。
- 常驻框与离散矩形框动画完全符合第 18 节。
- 主手杖的查看覆盖层仅在副手配置器有当前线路时显示，并只包含该线路端点。
- 主手配置器＋副手杖时显示有权限的附近端点，支持节点→端点、端点→节点和端点→端点复制／粘贴。
- 物品栏杖端呼吸动画符合 18.9，且不改变贴图透明轮廓。
- 原有节点、配置器、分发器高亮和外部接口无回归。
- 所有新网络包均有服务端权限、距离、维度和 revision 验证。
- 相关配置、语言、模型、配方、手册和测试同步三版本。
- 三阶祭坛配方符合第 29 节，且不存在工作台替代配方。
