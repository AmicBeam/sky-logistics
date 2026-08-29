# Configurator GUI texture attribution

- `redstone_high.png`, `redstone_low.png`, and `redstone_ignore.png` use
  Minecraft's redstone torch, unlit redstone torch, and redstone dust artwork.
- `redstone_pulse.png` uses Minecraft Wiki's original 16x16
  [`Observer (front texture) JE3 BE3.png`](https://minecraft.wiki/w/File:Observer_(front_texture)_JE3_BE3.png).
  It is copied as a static 2D RGBA texture without resizing or redrawing.
- `resource_*_small.png` icons are rasterized directly at their final 18x17
  logical size from the project's deterministic SVG icon sources.

The local state mapping is:

- Sky Logistics `IGNORE` -> unlit redstone dust
- Sky Logistics `HIGH` -> lit redstone torch
- Sky Logistics `LOW` -> unlit redstone torch
- Sky Logistics `PULSE` -> static Minecraft Wiki observer front texture
