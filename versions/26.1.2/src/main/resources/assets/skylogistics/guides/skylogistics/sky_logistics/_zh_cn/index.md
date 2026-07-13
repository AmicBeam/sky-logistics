---
navigation:
  title: 总览
  icon: sky_node
  position: 0
item_ids:
  - skylogistics:sky_node
---

# 天穹物流手册

天穹物流不是一格一格铺管道。玩家放置天穹物流节点，服务器会直接配对同一线路上的抽取面和存入面。线路没有隐藏缓存；目标放不下时，源头里的资源不会先被取走。

<ItemGrid>
  <ItemIcon id="configurator" />
  <ItemIcon id="sky_node" />
  <ItemIcon id="item_vault" />
  <ItemIcon id="fluid_vault" />
  <ItemIcon id="offering_altar" />
</ItemGrid>

一条线路可以同时搬运物品、流体和能量。节点按面配置：抽取面从相邻机器或容器拿出资源，存入面把资源送入相邻机器或容器。节点界面可以分别编辑六个方向。

推荐流程：先用配置器建立一条线路；在源机器旁放节点并设为抽取；在目标容器旁放同线路节点并设为存入。以后扩展重复机器时，用配置器复制和粘贴设置。

## 主题

- [祭坛与供奉](offerings.md)：充能水晶、搭建法阵、制作柯拉甘露。
- [物流网络](logistics.md)：连接节点、仓库、过滤器和外部网络。
- [工具与升级](tools.md)：配置线路、复制设置、提升吞吐或跨维度运输。

<RecipeFor id="sky_node" fallbackText="未找到天穹节点配方，请检查配方数据或 JEI。" />
