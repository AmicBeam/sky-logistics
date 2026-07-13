---
navigation:
  title: 物流网络
  icon: sky_node
  parent: index.md
  position: 2
item_ids:
  - skylogistics:sky_node
  - skylogistics:item_vault
  - skylogistics:fluid_vault
  - skylogistics:sky_me_interface
  - skylogistics:sky_rs_interface
  - skylogistics:sky_dimension_interface
---

# 物流网络

节点不会生成管线。服务器会把同一线路上的抽取面和插入面配对，只搬运目标实际能接收的资源；目标拒收时不会先从来源里扣掉资源。

继续阅读：

- [节点与管道端点](logistics_nodes.md)
- [容器与过滤](logistics_vaults_filters.md)
- [配置器与升级](logistics_configurator_upgrades.md)


## 外部网络

安装对应模组时，天穹物流可以对接 AE2、精致存储和 Beyond Dimensions：

<ItemGrid>
  <ItemIcon id="sky_me_interface" />
  <ItemIcon id="sky_rs_interface" />
  <ItemIcon id="sky_dimension_interface" />
</ItemGrid>

<RecipeFor id="item_vault" fallbackText="未找到物品仓储配方。" />
<RecipeFor id="fluid_vault" fallbackText="未找到流体仓储配方。" />
