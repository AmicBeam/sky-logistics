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

## 节点

<ItemLink id="sky_node" /> 的每一面都可以独立设置为禁用、抽取、插入或仅连接。线路可以同时处理物品、流体和能量，节点还支持优先级和红石控制。

## 仓储

<ItemLink id="item_vault" /> 和 <ItemLink id="fluid_vault" /> 是紧凑的存储端点，适合作为高吞吐机器与无线线路之间的缓冲。

## 外部网络

安装对应模组时，天穹物流可以对接 AE2、精致存储和 Beyond Dimensions：

<ItemGrid>
  <ItemIcon id="sky_me_interface" />
  <ItemIcon id="sky_rs_interface" />
  <ItemIcon id="sky_dimension_interface" />
</ItemGrid>

<RecipeFor id="item_vault" fallbackText="未找到物品仓储配方。" />
<RecipeFor id="fluid_vault" fallbackText="未找到流体仓储配方。" />
