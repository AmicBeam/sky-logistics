---
navigation:
  title: 容器与过滤
  icon: item_vault
  parent: logistics.md
  position: 2
item_ids:
  - skylogistics:item_vault
  - skylogistics:fluid_vault
  - skylogistics:filter_list
  - skylogistics:tag_filter_list
  - skylogistics:chora_nectar
---

# 容器与过滤

<ItemLink id="item_vault" /> 按种类存放物品，<ItemLink id="fluid_vault" /> 按种类存放流体。刚制作出来时可记录的种类较少，手持 <ItemLink id="chora_nectar" /> 右击仓库可以扩展上限；潜行右击会尽量一次用掉手中的一组甘露。

<RecipeFor id="item_vault" fallbackText="未找到物品仓储配方。" />
<RecipeFor id="fluid_vault" fallbackText="未找到流体仓储配方。" />

玩家可以打开仓库界面查看内容，物流节点、漏斗和其它自动化设备也可以访问它们。作为线路目标时，它们很适合集中存放大量同类物品或流体。

<ItemLink id="filter_list" /> 用来限制节点搬运什么。右击打开过滤界面，可以设置样品物品、白名单或黑名单，也可以决定是否匹配物品数据和耐久。把设置好的过滤列表放入节点对应面的过滤槽时不会消耗它；节点只会复制其中的规则。复制和粘贴节点设置时也会复制这些过滤规则。

需要按整类标签匹配时，使用 <ItemLink id="tag_filter_list" />。

<RecipeFor id="filter_list" fallbackText="未找到过滤列表配方。" />
