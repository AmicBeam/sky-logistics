---
navigation:
  title: 使用天穹供奉祭坛
  icon: offering_altar
  parent: offerings.md
  position: 2
item_ids:
  - skylogistics:offering_altar
  - skylogistics:offering_table
---

# 使用天穹供奉祭坛

<ItemLink id="offering_altar" /> 和 <ItemLink id="offering_table" /> 都只有 1 个供奉槽，最多放一组物品，没有界面。手持物品右击会放入，空手右击会取出；漏斗和管道类自动化也可以访问这个物品槽。

<RecipeFor id="offering_altar" fallbackText="未找到供奉祭坛配方。" />
<RecipeFor id="offering_table" fallbackText="未找到供奉台配方。" />

祭坛需要消耗已充能尤洛伽水晶，供桌只需要天穹石。

祭坛必须放在配置的天空高度或更高，并搭好有效供奉法阵。把主材料放在祭坛上，把供品放在四侧供桌上；供桌顺序不限，只要材料齐全就能开始。

材料齐全后，祭坛会出现粒子并开始计时。等待配方时间结束后，输入会一次性消失，产物会优先留在祭坛槽里；如果放不下，剩余产物会弹出到祭坛上方。

没有匹配配方时，祭坛不会一直扫描。它会在自身物品、邻近供桌变化以及世界加载后重新检查。如果装了 Jade，准星对准祭坛或供桌时可以看到非空供奉槽；空槽不会额外显示。
