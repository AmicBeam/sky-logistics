---
navigation:
  title: 外部网络接口
  icon: sky_me_interface
  parent: logistics.md
  position: 3
item_ids:
  - skylogistics:sky_me_interface
  - skylogistics:sky_rs_interface
  - skylogistics:sky_dimension_interface
---

# 外部网络接口

外部网络接口把其它模组的大型存储网络接入天穹线路。三种接口共用节点式界面：选择线路、抽取或存入模式、资源类型、两个升级槽，以及红石、优先级和过滤规则。接口本身没有中间缓存；目标拒收时资源仍留在原网络。

## 天穹 ME 接口

<ItemLink id="sky_me_interface" /> 仅在安装 Applied Energistics 2 时可用。它搬运 ME 网络中的物品和流体；安装 AppFlux、Applied Mekanistics、Applied Botanics 或 Ars Énergistique 后，还可分别访问 FE、化学品、mana 或 Source。每条资源路径都有独立服务端开关。

<RecipeFor id="sky_me_interface" fallbackText="安装 AE2 后可查看天穹 ME 接口配方。" />

## 天穹 RS 接口

<ItemLink id="sky_rs_interface" /> 仅在安装 Refined Storage 时可用，把 RS 网络中的物品和流体暴露给天穹线路。物品与流体路径可以分别关闭。

<RecipeFor id="sky_rs_interface" fallbackText="安装精致存储后可查看天穹 RS 接口配方。" />

## 天穹维度网络接口

<ItemLink id="sky_dimension_interface" /> 仅在安装 Beyond Dimensions 时可用，可访问其网络中的物品、流体和 FE；安装 Mekanism 或 Ars Nouveau 后还可访问化学品或 Source。它接入的是第三方存储网络，不等同于让普通节点跨维度工作的维度升级卡。

<RecipeFor id="sky_dimension_interface" fallbackText="安装 Beyond Dimensions 后可查看接口配方。" />

## 过滤与限制

从外部网络抽取物品时，必须配置至少包含一个具体物品的白名单，避免无界扫描整个网络；标签或模组过滤不能代替这份可枚举白名单。关闭联动开关不会删除接口方块或破坏已保存线路。
