# Configurator GUI texture attribution

- `redstone_high.png`, `redstone_low.png`, and `redstone_ignore.png` use
  Minecraft's redstone torch, unlit redstone torch, and redstone dust artwork.
- `redstone_pulse.png` is a nearest-neighbor 16x16 conversion of Minecraft
  Wiki's 32x32 [`Invicon Stone Button.png`](https://minecraft.wiki/w/File:Invicon_Stone_Button.png).
  The inventory render is used without redrawing.
- `resource_*_small.png` icons are rasterized directly at their final 18x17
  logical size from the project's deterministic SVG icon sources.

The local state mapping is:

- Sky Logistics `IGNORE` -> unlit redstone dust
- Sky Logistics `HIGH` -> lit redstone torch
- Sky Logistics `LOW` -> unlit redstone torch
- Sky Logistics `PULSE` -> static Minecraft Wiki stone button inventory render
