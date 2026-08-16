---
navigation:
  title: 简易管道
  icon: simple_item_pipe
  parent: index.md
  position: 1
item_ids:
  - skylogistics:simple_item_pipe
  - skylogistics:simple_fluid_pipe
  - skylogistics:simple_energy_pipe
  - skylogistics:sky_wrench
---

# 简易管道

简易管道是无线物流节点之外的有线运输方案。物品、流体和能量管道只会与同类管道相连，每组相连的管道形成一条线路。管道没有界面，放下后始终工作，并会自动连接周围可用的机器或容器。

<ItemGrid>
  <ItemIcon id="simple_item_pipe" />
  <ItemIcon id="simple_fluid_pipe" />
  <ItemIcon id="simple_energy_pipe" />
  <ItemIcon id="sky_wrench" />
</ItemGrid>

## 放置与端点

普通放置时，点击一侧连接的容器默认为存入端；潜行放置时，该侧默认为抽取端。其他相邻的可用容器会自动以存入模式连接。

用天穹扳手普通右击管道的某一连接侧，可断开或恢复该侧连接。潜行右击管道可将其快速拆除，掉落物会优先放入背包。

用天穹配置器右击与机器相连的端点，可在抽取和存入之间切换，且不会打开配置器界面。抽取端会显示更宽的套环。

## 默认传输速率

- 物品：每个抽取端每刻最多 64 个物品；一次传输最多使用一个来源槽位和一个目标槽位。
- 流体：每个抽取端每刻最多 10,000 mB。
- 能量：每个抽取端每刻最多 100,000 FE。
- Botania mana：每个抽取端每刻最多 50 mana，对齐近距离普通 Mana Spreader 的取整平均吞吐。
- Ars Nouveau 魔源：每个抽取端每刻最多 50 Source，对齐普通 Source Relay 每 20 tick 搬运 1,000 Source 的平均吞吐。

同一条线路默认最多包含 256 个管道方块。放置或重新连接会超过上限时，新连接会保持断开并显示提示。服务器设置可能改变默认传输速率和线路上限。

## 合成

每种管道一次合成 8 个。

<RecipeFor id="simple_item_pipe" fallbackText="未找到简易物品管道配方。" />
<RecipeFor id="simple_fluid_pipe" fallbackText="未找到简易流体管道配方。" />
<RecipeFor id="simple_energy_pipe" fallbackText="未找到简易能量管道配方。" />
<RecipeFor id="sky_wrench" fallbackText="未找到天穹扳手配方。" />
