---
navigation:
  title: 天穹供奉法阵
  icon: offering_table
  parent: offerings.md
  position: 3
item_ids:
  - skylogistics:offering_altar
  - skylogistics:offering_table
  - skylogistics:celestial_stone
  - skylogistics:celestial_glass
---

# 天穹供奉法阵

先把祭坛放在中心，再在东南西北四侧各放一个供桌。祭坛下方一层需要一圈 5x5 的天穹石框架；图中空出来的位置可以留空，也可以放其它方块。柯拉甘露需要二阶法阵。

## 一阶

一阶祭坛只需要下层外圈完整。天穹石、天穹石台阶、楼梯和墙都可以算作框架。

<GameScene zoom={1.05} interactive={true} fullWidth={true}>
  <ImportStructure src="/structures/offering_circle_tier1.snbt" />
  <IsometricCamera yaw={35} pitch={30} />
</GameScene>

## 二阶

二阶会在更外圈四角增加天穹石柱，并把天穹玻璃放在柱顶。

<GameScene zoom={0.82} interactive={true} fullWidth={true}>
  <ImportStructure src="/structures/offering_circle_tier2.snbt" />
  <IsometricCamera yaw={35} pitch={30} />
</GameScene>

天穹玻璃由一阶供奉获得：祭坛放已充能尤洛伽水晶，两个供桌分别放 8 个 `#c:glass_blocks` 和 8 个荧石粉。仪式时间是 160 ticks，产出 8 个天穹玻璃。
