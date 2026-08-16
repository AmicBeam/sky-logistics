---
navigation:
  title: AStages 速率联动
  icon: speed_upgrade
  parent: logistics.md
  position: 4
---

# AStages 速率联动

安装 AStages 后，服务器可按线路持有者拥有的 stage 限制并逐步解锁单次搬运量。联动默认关闭，需在 `transfers.astages.enabled` 中启用。它覆盖无线节点、外部网络接口和简易管道。

物品、流体、Mekanism 化学品、FE、Botania mana 与 Ars Nouveau Source 分别计算。系统从 `initialRates` 取得初始值，再从持有者拥有的所有已配置 stage 中按资源取最高值，最后与设备自身上限和目标接收能力取最低值。

该速率只限制一次成功操作最多提交多少资源，不改变节点每 tick 工作次数、速度升级、服务器或线路操作预算、项链工作间隔。简易管道自身配置若更低，仍会成为最终上限。

无线线路以创建或认领它的玩家为持有者；简易管网以放置者或继承到的管网持有者判断。无法确定持有者或查询失败时只使用 `initialRates`，不会借用附近在线玩家的 stage。
