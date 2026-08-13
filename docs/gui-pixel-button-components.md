# 天穹配置器像素 GUI：按钮组件拆分规范

## 基准图与坐标

- 组件基准图：[`final-working-520x500.png`](../assets/generated/iterative-pixel-repair/final-working-520x500.png)
- 工作画布：`520 × 500`
- 最终预览：`1040 × 1000`
- 工作稿每个像素对应最终图的 `2 × 2` 色块。
- 最终严格图形网格由 `260 × 250` 放大为 `1040 × 1000`。
- 独立组件目录：[`components/`](../assets/generated/iterative-pixel-repair/components/)
- 组件清单：[`manifest.json`](../assets/generated/iterative-pixel-repair/components/manifest.json)

组件坐标均使用 `520 × 500` 工作画布，格式为 `[x, y, width, height]`。

## 通用按钮视觉状态

项目三个版本共用同一套 `ConfigPanel.drawButtonChrome(active, selected)` 语义。

| 状态 | 条件 | 填充 | 边框 | 文字 | 附加标记 | 交互 |
|---|---|---|---|---|---|---|
| 不可用 | `active=false` | `BUTTON_DISABLED #101820` | `BORDER_DIM #24454F` | `MUTED #8FB7C1` | 无 | 不响应点击 |
| 可用、未选中 | `active=true, selected=false` | `BUTTON #0D1D25` | `BORDER #3E8B99` | `TEXT #E8FBFF` | 无 | 响应点击 |
| 可用、已选中 | `active=true, selected=true` | `BUTTON_SELECTED #12343C` | `BORDER_ACTIVE #68D7E5` | `TEXT #E8FBFF` | 底部 `ACCENT #FFE59A` 细线 | 响应点击 |

注意：

- `active` 表示“可否点击”，不是“功能是否开启”。
- `selected/enabled` 表示开关当前是否开启。
- 一个资源按钮可以同时满足 `active=true`、`selected=false`：按钮可点击，但该资源联动当前关闭。
- 红石循环按钮没有 `selected` 外观；当前业务档位由按钮文字表示。

## 1. 线路导航按钮

导航按钮使用同一种方形按钮 chrome，图标不同。

| 组件 | 裁片 | 状态示例 | 坐标 | 行为 |
|---|---|---|---|---|
| 第一条线路 | [`nav-first.png`](../assets/generated/iterative-pixel-repair/components/nav-first.png) | 可用 | `[326,42,33,30]` | 跳到第一条线路 |
| 上一条线路 | [`nav-previous.png`](../assets/generated/iterative-pixel-repair/components/nav-previous.png) | 可用 | `[362,42,31,30]` | 线路索引减一 |
| 下一条/新建 | [`nav-next-active.png`](../assets/generated/iterative-pixel-repair/components/nav-next-active.png) | 当前强调 | `[398,42,32,30]` | 下一条；位于末尾时创建线路 |
| 最后一条线路 | [`nav-last.png`](../assets/generated/iterative-pixel-repair/components/nav-last.png) | 可用 | `[435,42,31,30]` | 跳到最后一条线路 |
| 删除线路 | [`nav-close.png`](../assets/generated/iterative-pixel-repair/components/nav-close.png) | 可用、危险操作 | `[470,42,31,30]` | 删除当前线路 |
| 上一页连接面 | [`page-previous.png`](../assets/generated/iterative-pixel-repair/components/page-previous.png) | 可用 | `[424,123,32,27]` | 详情页减一 |
| 下一页连接面 | [`page-next.png`](../assets/generated/iterative-pixel-repair/components/page-next.png) | 可用 | `[461,123,32,27]` | 详情页加一 |

运行态规则：

- 第一条/上一条在线路索引为 `0` 时设为 `active=false`。
- 最后一条在线路索引为末尾时设为 `active=false`。
- 删除按钮在线路数不足或当前线路不可删除时设为 `active=false`。
- “下一条/新建”保持可用，末尾状态承担创建动作。

## 2. 资源类型开关

四个按钮均为独立开关，不是互斥单选。

| 组件 | 裁片 | 当前示例 | 坐标 | 点击行为 |
|---|---|---|---|---|
| 物品 | [`resource-item-active.png`](../assets/generated/iterative-pixel-repair/components/resource-item-active.png) | 已选中 | `[18,370,113,40]` | 切换物品联动 |
| 流体 | [`resource-fluid.png`](../assets/generated/iterative-pixel-repair/components/resource-fluid.png) | 未选中 | `[140,370,112,40]` | 切换流体联动 |
| 能量 | [`resource-energy.png`](../assets/generated/iterative-pixel-repair/components/resource-energy.png) | 未选中 | `[262,370,112,40]` | 切换能量联动 |
| 自动 | [`resource-auto.png`](../assets/generated/iterative-pixel-repair/components/resource-auto.png) | 未选中 | `[383,370,115,40]` | 切换资源自动检测 |

状态映射：

- 功能开启：`active=true, selected=true`。
- 功能关闭但允许操作：`active=true, selected=false`。
- 当前配置不可编辑：`active=false`，此时无论业务值为何，都以不可用外观绘制。

## 3. 红石循环按钮

裁片：[`redstone-cycle-ignore.png`](../assets/generated/iterative-pixel-repair/components/redstone-cycle-ignore.png)  
坐标：`[28,440,124,34]`

红石控件是一个完整按钮：左侧火把图标、中央当前档位文字。右侧没有下拉箭头、下拉条或第二个点击区域。

运行代码中的 `RedstoneControl` 实际包含四个枚举值，并通过 `next()` 依次循环：

| 顺序 | 枚举 | 中文 | 实际行为 |
|---|---|---|---|
| 1 | `IGNORE` | 忽略 | 不检查红石，始终允许工作 |
| 2 | `HIGH` | 有信号 | 仅收到红石信号时允许工作 |
| 3 | `LOW` | 无信号 | 仅未收到红石信号时允许工作 |
| 4 | `DISABLED` | 禁用 | 无条件停止该面 |

如果把“红石条件档位”限定为信号判断，确实是前三种：忽略、有信号、无信号；但当前项目按钮的实际循环还包含第四种“禁用”。组件文档必须保留这个运行态，不能只画三档而让 UI 与存档/服务端状态不一致。

视觉状态：

- `active=true`：正常边框与正常文字，点击进入下一档。
- `active=false`：暗边框与弱化文字，点击无效。
- 业务档位不改变按钮边框，仅替换火把状态表现和中央文字。
- 不显示下拉箭头，因为交互是“点击循环”，不是“展开菜单”。

若产品决定改成真正三档，需要另行修改 `RedstoneControl.next()`、语言/序列化兼容与全部版本对应逻辑；本次资产拆分未修改游戏功能。

## 4. 数值调整按钮

| 组件 | 裁片 | 坐标 | 行为 |
|---|---|---|---|
| 留槽减少 | [`slot-decrease.png`](../assets/generated/iterative-pixel-repair/components/slot-decrease.png) | `[184,440,34,34]` | 槽位限制减一；Shift 快速减十 |
| 留槽增加 | [`slot-increase.png`](../assets/generated/iterative-pixel-repair/components/slot-increase.png) | `[279,440,34,34]` | 槽位限制加一；Shift 快速加十 |
| 优先级降低 | [`priority-decrease.png`](../assets/generated/iterative-pixel-repair/components/priority-decrease.png) | `[350,440,34,34]` | 优先级减一；Shift 快速减十 |
| 优先级提高 | [`priority-increase.png`](../assets/generated/iterative-pixel-repair/components/priority-increase.png) | `[458,440,27,34]` | 优先级加一；Shift 快速加十 |

中间黑色数值槽是只读显示框，不是按钮，不能与左右调整键合并为一个点击组件。

## 5. 表格状态组件

表格中的勾选与空框不是通用按钮 chrome，而是紧凑状态单元：

- 绿色勾：资源启用。
- 青色勾：流体等对应资源启用。
- 空金属框：资源关闭。
- 红石火把/空框：该连接面的红石状态摘要。

这些单元格属于详情列表展示，不对应底部配置器按钮的 `active/selected` 交互状态，不能复用底部按钮外观。

## 代码依据

- 红石枚举与循环：`common/src/main/java/com/skylogistics/util/RedstoneControl.java`
- 按钮颜色与 `active/selected` 绘制：`versions/*/src/main/java/com/skylogistics/client/ConfigPanel.java`
- 配置器按钮尺寸、位置和事件：`versions/*/src/main/java/com/skylogistics/client/ConfiguratorScreen.java`
- 红石条件实际行为：`versions/*/src/main/java/com/skylogistics/block/entity/SkyNodeBlockEntity.java`

上述红石枚举和按钮行为在 `1.20.1`、`1.21.1`、`26.1.2` 三个版本中一致。
