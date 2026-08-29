# 天穹配置器红石图标参考

后续将 HTML 原型实装进游戏时，红石配置图标参考 **Mekanism 1.21.x** 的状态化设计，不再沿用当前原型里“同一红石火把调亮暗”的临时表现。

## 官方参考

- 仓库：<https://github.com/mekanism/Mekanism>
- 核对提交：`11162452affe7b17b25cde251308c9d047c42e87`
- 控件实现：[`GuiRedstoneControlTab.java`](https://github.com/mekanism/Mekanism/blob/11162452affe7b17b25cde251308c9d047c42e87/src/main/java/mekanism/client/gui/element/tab/GuiRedstoneControlTab.java)
- 独立状态素材：
  - [`redstone_control_disabled.png`](https://github.com/mekanism/Mekanism/blob/11162452affe7b17b25cde251308c9d047c42e87/src/main/resources/assets/mekanism/gui/redstone_control_disabled.png)
  - [`redstone_control_high.png`](https://github.com/mekanism/Mekanism/blob/11162452affe7b17b25cde251308c9d047c42e87/src/main/resources/assets/mekanism/gui/redstone_control_high.png)
  - [`redstone_control_low.png`](https://github.com/mekanism/Mekanism/blob/11162452affe7b17b25cde251308c9d047c42e87/src/main/resources/assets/mekanism/gui/redstone_control_low.png)

## 实装约束

- 为 `忽略 / 有信号 / 无信号 / 脉冲` 分别使用独立素材，而不是对同一图标使用滤镜；脉冲使用观察者正面图标。
- 忽略、有信号和无信号沿用项目现有状态素材；脉冲直接使用 Minecraft Wiki 的原生 16×16 `Observer (front texture) JE3 BE3.png` 2D 纹理，转存为静态 RGBA PNG，不缩放、不重绘，也不在 GUI 初始化阶段调用物品渲染器。
- 四个红石图标均为 16×16 RGBA PNG；前三种状态四边保留透明像素，观察者正面作为完整方形纹理有意接触四边。验收预览使用 Minecraft GUI 灰 `#c6c6c6` 和 8× 最近邻放大。
- 单按钮左键按项目既有顺序循环；如后续增加右键操作，可参考 Mekanism 的反向循环，但不改变现有协议语义。
- 列表红石状态与底部红石控制按钮必须共用同一套状态资源。
