import sharp from "/Users/bytedance/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp/dist/index.mjs";
import { mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const inputs = [
  resolve("assets/generated/chora-kleis-pixel/chora_kleis_32_before_brown_lighten.png"),
  resolve("assets/generated/chora-kleis-pixel/chora_kleis_32_refined.png"),
  resolve("assets/generated/chora-kleis-pixel/chora_kleis_32_refined.png"),
];
const labels = ["previous brown", "lighter handle brown", "dark background"];
const backgrounds = ["#c6c6c6", "#c6c6c6", "#202936"];
const output = resolve("assets/generated/chora-kleis-pixel/chora_kleis_32_adjustment_acceptance.png");
const panelSize = 320;
const gap = 24;
const labelHeight = 38;
const width = panelSize * inputs.length + gap * (inputs.length + 1);
const height = panelSize + labelHeight + gap * 2;
const layers = [];

for (let index = 0; index < inputs.length; index += 1) {
  const left = gap + index * (panelSize + gap);
  const icon = await sharp(inputs[index])
    .resize(panelSize, panelSize, { kernel: sharp.kernel.nearest })
    .png()
    .toBuffer();
  const tile = await sharp({
    create: { width: panelSize, height: panelSize, channels: 4, background: backgrounds[index] },
  }).composite([{ input: icon }]).png().toBuffer();
  layers.push({ input: tile, left, top: gap });
  layers.push({
    input: Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${panelSize}" height="${labelHeight}">
      <text x="${panelSize / 2}" y="27" text-anchor="middle" fill="#edf6ff"
        font-family="sans-serif" font-size="20">${labels[index]}</text>
    </svg>`),
    left,
    top: gap + panelSize,
  });
}

await mkdir(dirname(output), { recursive: true });
await sharp({ create: { width, height, channels: 4, background: "#151d28" } })
  .composite(layers)
  .png()
  .toFile(output);
console.log(output);
