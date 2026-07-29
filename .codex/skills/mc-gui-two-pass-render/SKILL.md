---
name: mc-gui-two-pass-render
description: Create and validate Minecraft GUI mockups whose non-text geometry is a strict nearest-neighbor 4x rendering of a 384x244 logical canvas while text is rendered separately at 1536x976 framebuffer resolution. Use for Sky Logistics UI redesigns, Minecraft GUI Scale 4 previews, pixel-grid verification, or requests requiring crisp Chinese text without sacrificing pixel-perfect panels, borders, buttons, slots, and icons.
---

# Minecraft GUI two-pass rendering

Produce four artifacts:

1. `*-background.png`: 384x244 geometry without text.
2. `*-background-4x.png`: strict nearest-neighbor 1536x976 geometry.
3. `*-text-4x.png`: transparent 1536x976 text layer.
4. `*-final-4x.png`: composited preview.

## Workflow

1. Use image generation only as a layout reference. Never treat its raster output as the geometry layer.
2. Rebuild panels, borders, buttons, slots, scrollbars, and icons on a 384x244 integer grid using SVG, canvas, or code.
3. Keep all text out of the logical background.
4. Render the background directly at 384x244. Do not render large and downsample.
5. Render text separately at 1536x976 using Minecraft's font renderer or original glyph resources. Preserve finer glyph detail; do not force text into 4x4 blocks.
6. Composite the two layers without resizing either layer.
7. Run `scripts/render_and_validate.js`. Reject the result unless both checks pass:
   - every background pixel maps to one identical 4x4 block;
   - wherever the text layer is transparent, the final image equals the 4x background exactly.
8. Synchronize corresponding artifacts across all supported project versions.

## Rendering rules

- Keep the logical canvas exactly 384x244 and the framebuffer exactly 1536x976.
- Align geometry coordinates and one-pixel strokes to logical integer coordinates.
- Use nearest-neighbor only for the geometry enlargement.
- Allow colors and gradients inside the logical image; each resulting logical pixel must still expand to one uniform 4x4 block.
- Keep the text layer transparent outside glyph pixels.
- Place text using logical anchors multiplied by four, but rasterize glyphs at framebuffer resolution.
- Use generated text only for ideation. Use deterministic Minecraft glyph rendering for deliverables.
- Preserve the requested feature inventory. Do not infer new buttons, settings, tabs, filters, or actions from a generated reference.

## Validation command

Load the workspace Node.js and Sharp paths, then run:

```bash
node scripts/render_and_validate.js \
  --sharp-module /absolute/path/to/node_modules/sharp \
  --background /absolute/path/to/background.png \
  --text /absolute/path/to/text-4x.png \
  --background-4x /absolute/path/to/background-4x.png \
  --final /absolute/path/to/final-4x.png
```

Treat a nonzero exit code as a failed artifact. Report the two Boolean checks and dimensions.

## Unihex text-layer command

Prepare a JSON array containing logical anchors:

```json
[
  {"x": 22, "y": 14, "text": "天穹配置器", "color": "#ffe071", "scale": 2},
  {"x": 228, "y": 83, "text": "1–5 / 8", "color": "#9aa4a8", "scale": 1, "anchor": "end"}
]
```

Then render the transparent framebuffer text layer:

```bash
node scripts/render_unifont_text.js \
  --sharp-module /absolute/path/to/node_modules/sharp \
  --font-zip /absolute/path/to/minecraft/font/unifont.zip \
  --spec /absolute/path/to/text.json \
  --out /absolute/path/to/text-4x.png
```

Use `scale: 2` for the normal Minecraft GUI glyph height and `scale: 1` for compact metadata. Keep `x` and `y` in 384x244 logical coordinates.
