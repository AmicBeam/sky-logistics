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
  - skylogistics:chora_nectar_block
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

## 三阶

三阶保留二阶的外圈角柱与天穹玻璃柱顶，但把内侧 5x5 框架的四个角替换为柯拉甘露块。三阶祭坛按配置的基础倍率工作，默认 4 倍。

<GameScene zoom={0.82} interactive={true} fullWidth={true}>
  <ImportStructure src="/structures/offering_circle_tier3.snbt" />
  <IsometricCamera yaw={35} pitch={30} />
</GameScene>

天穹玻璃由一阶供奉获得：祭坛放已充能尤洛伽水晶，两个供桌分别放 8 个任意玻璃和 8 个荧石粉。仪式约 8 秒完成，产出 8 个天穹玻璃。
