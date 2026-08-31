# AStages 联动

Sky Logistics 的 **1.20.1 Forge 与 1.21.1 NeoForge** 版本可以按线路持有者拥有的 AStages stage，限制并逐步解锁物流系统的**单次搬运量**。该功能是可选联动，默认关闭。26.1.2 不支持 AStages，本页配置不适用于该版本。

## 作用范围

联动同时作用于：

- 无线物流节点及接入同一线路的外部网络接口；
- 天穹简易物品、流体和能量管道；
- 物品、流体、Mekanism 化学品、FE、Botania mana、Ars Nouveau Source 六种基础资源；1.21.1 还可独立限制坚守者灵魂。

这里的“速率”只表示一次成功搬运最多提交多少资源，不改变：

- 节点每 tick 的工作次数；
- 槽位并行升级提供的操作频率；
- `serverOpsPerTick`、`lineOpsPerTick` 等操作预算；
- 项链工作间隔或一次工作尝试的目标数。

最终单次搬运量仍会同时受到管道自身配置、节点上限、目标接收能力等限制，取其中最低值。若希望 stage 把简易管道解锁到高于其默认值，还需要把对应的 `simple...PipeTransferRate` 配置为不低于解锁值。

## 判定规则

启用后，每种资源分别按以下顺序计算：

1. 从 `initialRates` 取得初始上限。
2. 查找线路持有者拥有且出现在 `stageRates` 中的所有 stage。
3. 对该资源取初始值和所有已拥有 stage 配置值中的最高值。
4. 再与设备自身上限及本次可搬运数量取最低值。

不同资源互不影响。一个 stage 可以只填写需要解锁的字段；省略字段会继续使用初始值或其它已拥有 stage 解锁出的更高值。同名 stage 出现多次时，也会按每种资源的最高值合并。

## 多人游戏中的线路持有者

- 无线线路使用其持有者判断 stage，不使用当前操作节点的玩家、最近玩家或资源来源玩家。
- 新线路会记录创建或认领它的玩家；已有持有者不会因其他玩家使用线路而改变。
- 简易管道放置时记录玩家；接入已有管网时继承管网持有者。管网合并后会统一持有者。
- 无法确定持有者时，只应用 `initialRates`，不会借用在线玩家的 stage。

因此，共用线路是否解锁取决于线路持有者，而不是正在打开 GUI 或站在管道旁的玩家。

## 配置示例

配置位于服务端配置的 `transfers.astages` 分类：

```toml
[transfers.astages]
enabled = true
stageRates = [
  { stage = "logistics_tier_1", items = 128, fluids = 20000 },
  { stage = "logistics_tier_2", items = 256, chemicals = 40000, souls = 40000, energy = 250000 },
  { stage = "magic_logistics", mana = 300000, source = 300000 }
]

[transfers.astages.initialRates]
items = 64
fluids = 10000
chemicals = 10000
souls = 10000
energy = 100000
mana = 100000
source = 100000
```

`stageRates` 中每个字典必须包含非空的 `stage`，其余资源字段均为可选正整数；`souls` 仅由 1.21.1 的坚守者灵魂运输使用：

| 字段 | 资源 | 初始默认值 |
| --- | --- | ---: |
| `items` | 物品数量 | 64 |
| `fluids` | 流体，mB | 10,000 |
| `chemicals` | Mekanism 化学品 | 10,000 |
| `souls` | Industrial Foregoing: Souls 坚守者灵魂（1.21.1） | 10,000 |
| `energy` | FE | 100,000 |
| `mana` | Botania mana | 100,000 |
| `source` | Ars Nouveau Source | 100,000 |

所有数值都必须至少为 `1`。stage 配置低于初始值时不会降低初始上限，因为解锁过程始终取最高值。

若 `stageRates` 为空，表示没有实装任何 stage 解锁条目；此时即使 `enabled = true` 且配置了 `initialRates`，进度限速也不生效。

## 安装与故障安全

- 需要 AStages 2.x；1.20.1 Forge 和 1.21.1 NeoForge 的模组元数据将它声明为可选依赖。
- `enabled = false` 时不查询 AStages，物流行为保持原样。
- 若开启功能但 AStages 不存在、API 查询失败或线路没有持有者，系统安全地使用 `initialRates`。
- stage 查询按“玩家 + 游戏 tick”缓存；同一持有者在一个 tick 内发生多次搬运时不会重复查询 AStages。

其它服务端传输配置参见 [[配置说明]]，管道自身上限参见 [[简易管道]]。
