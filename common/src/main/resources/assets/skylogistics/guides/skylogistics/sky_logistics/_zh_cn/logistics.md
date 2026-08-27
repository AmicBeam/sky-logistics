---
navigation:
  title: 物流网络
  icon: sky_node
  parent: index.md
  position: 3
item_ids:
  - skylogistics:sky_node
  - skylogistics:item_vault
  - skylogistics:fluid_vault
  - skylogistics:sky_me_interface
  - skylogistics:sky_rs_interface
  - skylogistics:sky_dimension_interface
---

# 物流网络

物流节点提供无线运输。把抽取面和存入面设为同一线路，资源就能在机器之间直接搬运。目标无法接收时，资源会留在来源中。若需要逐格铺设的有线网络，请参阅[简易管道](simple_pipes.md)。

继续阅读：

- [节点与管道端点](logistics_nodes.md)
- [容器与过滤](logistics_vaults_filters.md)
- [外部网络接口](external_network_interfaces.md)
- [配置器与升级](logistics_configurator_upgrades.md)


## 外部网络

安装对应模组时，天穹物流可以对接 AE2、精致存储和 Beyond Dimensions。详细资源路径、过滤要求与服务端开关参见[外部网络接口](external_network_interfaces.md)：

<ItemGrid>
  <ItemIcon id="sky_me_interface" />
  <ItemIcon id="sky_rs_interface" />
  <ItemIcon id="sky_dimension_interface" />
</ItemGrid>

<RecipeFor id="item_vault" fallbackText="未找到物品仓储配方。" />
<RecipeFor id="fluid_vault" fallbackText="未找到流体仓储配方。" />
