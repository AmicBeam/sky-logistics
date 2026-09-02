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

两种仓库仍可普通挖掘，但可抵御爆炸、熔岩、凋灵和末影龙破坏。潜行使用天穹扳手或兼容扳手右击任一仓库，可立即回收。

仓库终端支持按显示名搜索，并按数量、名称或模组排序。物品库中空手左击取一组、右击取 1 个，按住 Shift 直接放入背包；光标拿着物品时左击存入整组、右击存入 1 个。流体库需使用可装流体的容器完成灌入或取出。

<ItemLink id="filter_list" /> 用 18 个幽灵槽保存精确物品、流体及受支持的化学品样本，可设置白名单或黑名单、数据/组件与耐久匹配。把列表放入节点面过滤槽，或手持列表右击简易管道端点，会复制规则而不消耗物品；之后编辑原列表不会自动更新已复制规则。

需要按整类物品或流体标签匹配时，使用 <ItemLink id="tag_filter_list" />。它也有按资源 ID 命名空间匹配的模组模式，FE 使用虚拟模组 ID `forge`。外部网络物品抽取仍要求含具体物品条目的白名单，标签或模组过滤不能替代它。

<RecipeFor id="filter_list" fallbackText="未找到过滤列表配方。" />
