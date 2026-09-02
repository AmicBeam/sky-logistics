---
navigation:
  title: 工具与升级
  icon: configurator
  parent: index.md
  position: 4
item_ids:
  - skylogistics:configurator
  - skylogistics:filter_list
  - skylogistics:tag_filter_list
  - skylogistics:speed_upgrade
  - skylogistics:dimension_upgrade
  - skylogistics:sky_necklace
  - skylogistics:kleis_dominion_wand
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
  <ItemIcon id="kleis_dominion_wand" />
</ItemGrid>

详细页面：

- [天穹配置器](configurator.md)
- [配置器与升级](logistics_configurator_upgrades.md)
- [容器与过滤](logistics_vaults_filters.md)
- [天穹项链](sky_necklace.md)

## 过滤器

<ItemLink id="filter_list" /> 用于指定物品或流体；需要按标签匹配一整类资源时，使用 <ItemLink id="tag_filter_list" />。

## 升级

<ItemLink id="speed_upgrade" /> 可以让节点更快地处理资源。<ItemLink id="dimension_upgrade" /> 可以连接其它已加载维度中的同线路端点。

## 天穹项链

<ItemLink id="sky_necklace" /> 支持抽取、存入和维持三种模式；完整的白名单要求、升级和维持数行为参见[天穹项链](sky_necklace.md)。

## 克莱斯支配之杖

主手持 <ItemLink id="kleis_dominion_wand" />、副手持已配置的天穹配置器，可以创建或移除无线虚拟侧面。交换双手后进入编辑模式：潜行右击高亮覆盖面复制单面配置，再右击其它覆盖面或真实物流节点粘贴；从真实节点复制的配置也可以粘贴到虚拟覆盖面。

<RecipeFor id="configurator" fallbackText="未找到配置器配方。" />
<RecipeFor id="sky_necklace" fallbackText="未找到天穹项链配方。" />
<RecipeFor id="kleis_dominion_wand" fallbackText="未找到克莱斯支配之杖供奉配方。" />
