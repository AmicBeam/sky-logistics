---
navigation:
  title: 尤洛伽水晶与充能
  icon: eulogia_crystal
  parent: offerings.md
  position: 1
item_ids:
  - skylogistics:eulogia_crystal
  - skylogistics:celestial_stone
  - skylogistics:celestial_glass
---

# 尤洛伽水晶与充能

<ItemLink id="eulogia_crystal" /> 刚合成出来时还没有充能。把它带到足够高的地方就会开始吸收星辉；服务器配置 `rituals.skyRitualMinY` 默认是 Y=200。

<RecipeFor id="eulogia_crystal" fallbackText="未找到尤洛伽水晶配方。" />

尤洛伽水晶以钻石为核心，不消耗末影珍珠。刚合成出的水晶尚未充能。

未充能水晶放在玩家背包里就能充能，也可以放在 <ItemLink id="offering_table" /> 上等待充能。普通箱子不会充能水晶。默认充能时间是 60 秒，由 `rituals.eulogiaCrystalChargeSeconds` 控制。

充能完成后，水晶图标会变化，提示也会显示已完成注入。充能水晶可以和 `#c:stones` 合成 <ItemLink id="celestial_stone" />；<ItemLink id="celestial_glass" /> 则需要通过供奉获得。

<RecipeFor id="celestial_stone" fallbackText="未找到天穹石配方。" />
<RecipeFor id="celestial_stone_slab" fallbackText="未找到天穹石台阶配方。" />
<RecipeFor id="celestial_stone_wall" fallbackText="未找到天穹石墙配方。" />
