---
navigation:
  title: 工具与升级
  icon: configurator
  parent: index.md
  position: 3
item_ids:
  - skylogistics:configurator
  - skylogistics:filter_list
  - skylogistics:tag_filter_list
  - skylogistics:speed_upgrade
  - skylogistics:dimension_upgrade
  - skylogistics:sky_necklace
---

# 工具与升级

配置器是复制、粘贴和编辑线路设置的核心工具。过滤器和升级则用于细调节点在线路运行时的行为。

<ItemGrid>
  <ItemIcon id="configurator" />
  <ItemIcon id="filter_list" />
  <ItemIcon id="tag_filter_list" />
  <ItemIcon id="speed_upgrade" />
  <ItemIcon id="dimension_upgrade" />
  <ItemIcon id="sky_necklace" />
</ItemGrid>

## 过滤器

<ItemLink id="filter_list" /> 用于指定物品或流体；需要按标签匹配一整类资源时，使用 <ItemLink id="tag_filter_list" />。

## 升级

<ItemLink id="speed_upgrade" /> 提升吞吐。服务器配置允许时，<ItemLink id="dimension_upgrade" /> 可以启用远距离或跨维度运输。

## 天穹项链

安装 Curios 时，<ItemLink id="sky_necklace" /> 可以通过饰品栏与玩家物品栏交互；没有 Curios 时则使用可用的主物品栏后备逻辑。

<RecipeFor id="configurator" fallbackText="未找到配置器配方。" />
<RecipeFor id="sky_necklace" fallbackText="未找到天穹项链配方。" />
