# Configurator GUI texture attribution

- `redstone_high.png`, `redstone_low.png`, and `redstone_ignore.png` use
  Minecraft's redstone torch, unlit redstone torch, and redstone dust artwork.
- `resource_*.png` and `resource_*_small.png` are Sky Logistics configurator
  artwork derived from this project's approved HTML prototype.

The local state mapping is:

- Sky Logistics `IGNORE` -> unlit redstone dust
- Sky Logistics `HIGH` -> lit redstone torch
- Sky Logistics `LOW` -> unlit redstone torch
- Legacy `DISABLED` values are read as `IGNORE` and are not exposed by the UI.
