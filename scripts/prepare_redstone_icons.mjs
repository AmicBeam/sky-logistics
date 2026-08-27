import sharp from "../prototypes/configurator-gui/node_modules/sharp/dist/index.mjs";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const projectRoot = resolve(import.meta.dirname, "..");
const sourcePath = resolve(projectRoot, "assets/gui-icon-sources/wiki/Invicon_Stone_Button.png");
const iconRoot = resolve(projectRoot, "common/src/main/resources/assets/skylogistics/textures/gui/configurator");
const prototypeIconRoot = resolve(projectRoot, "prototypes/configurator-gui/public/art/icons");
const pulsePath = resolve(iconRoot, "redstone_pulse.png");
const reviewRoot = resolve(projectRoot, "art_review_exports/gui");
const previewPath = resolve(reviewRoot, "redstone-icons-acceptance.png");
const reportPath = resolve(reviewRoot, "redstone-icons-acceptance.json");
const iconNames = ["ignore", "high", "low", "pulse"];

function alphaBounds(data, width, height, channels) {
  let left = width;
  let top = height;
  let right = -1;
  let bottom = -1;
  let edgeAlphaPixels = 0;
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const alpha = data[(y * width + x) * channels + channels - 1];
      if (alpha === 0) continue;
      left = Math.min(left, x);
      top = Math.min(top, y);
      right = Math.max(right, x);
      bottom = Math.max(bottom, y);
      if (x === 0 || y === 0 || x === width - 1 || y === height - 1) edgeAlphaPixels += 1;
    }
  }
  if (right < 0) throw new Error("Image contains no visible pixels");
  return {
    left,
    top,
    right,
    bottom,
    width: right - left + 1,
    height: bottom - top + 1,
    margins: { left, top, right: width - right - 1, bottom: height - bottom - 1 },
    edgeAlphaPixels,
  };
}

async function inspect(path) {
  const image = sharp(path).ensureAlpha();
  const metadata = await image.metadata();
  const { data, info } = await image.raw().toBuffer({ resolveWithObject: true });
  return {
    format: metadata.format,
    width: info.width,
    height: info.height,
    channels: info.channels,
    hasAlpha: metadata.hasAlpha,
    isProgressive: metadata.isProgressive,
    isPalette: metadata.isPalette,
    alpha: alphaBounds(data, info.width, info.height, info.channels),
  };
}

await mkdir(iconRoot, { recursive: true });
await mkdir(reviewRoot, { recursive: true });

const source = await inspect(sourcePath);
if (source.format !== "png" || source.width !== 32 || source.height !== 32 || !source.hasAlpha) {
  throw new Error(`Unexpected Wiki source: ${JSON.stringify(source)}`);
}

// The original torch sprites touch the bottom edge. Recompose them one pixel
// higher without scaling or changing any visible pixel.
for (const name of ["high", "low"]) {
  const source = resolve(prototypeIconRoot, `redstone-${name}.png`);
  const shifted = await sharp(source)
    .extract({ left: 0, top: 1, width: 16, height: 15 })
    .extend({ bottom: 1, background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png({ palette: false, progressive: false })
    .toBuffer();
  await sharp(shifted).toFile(resolve(iconRoot, `redstone_${name}.png`));
}

await sharp(sourcePath)
  .resize(16, 16, { kernel: sharp.kernel.nearest })
  .ensureAlpha()
  .png({ palette: false, progressive: false })
  .toFile(pulsePath);

const icons = {};
for (const name of iconNames) {
  const path = resolve(iconRoot, `redstone_${name}.png`);
  const inspection = await inspect(path);
  if (inspection.format !== "png" || inspection.width !== 16 || inspection.height !== 16 || !inspection.hasAlpha) {
    throw new Error(`Invalid ${name} icon format: ${JSON.stringify(inspection)}`);
  }
  if (inspection.alpha.edgeAlphaPixels !== 0) {
    throw new Error(`${name} icon touches its outer edge: ${JSON.stringify(inspection.alpha)}`);
  }
  icons[name] = inspection;
}

const scale = 8;
const tileSize = 144;
const iconSize = 16 * scale;
const previewWidth = tileSize * iconNames.length;
const previewHeight = 176;
const composites = [];
for (let index = 0; index < iconNames.length; index += 1) {
  const name = iconNames[index];
  const input = await sharp(resolve(iconRoot, `redstone_${name}.png`))
    .resize(iconSize, iconSize, { kernel: sharp.kernel.nearest })
    .png()
    .toBuffer();
  composites.push({ input, left: index * tileSize + 8, top: 8 });
}
const labels = `
  <svg width="${previewWidth}" height="${previewHeight}" xmlns="http://www.w3.org/2000/svg">
    <style>text { font: 18px sans-serif; fill: #202020; text-anchor: middle; }</style>
    ${iconNames.map((name, index) => `<text x="${index * tileSize + tileSize / 2}" y="160">${name.toUpperCase()}</text>`).join("\n")}
  </svg>`;
composites.push({ input: Buffer.from(labels), left: 0, top: 0 });

await sharp({
  create: { width: previewWidth, height: previewHeight, channels: 4, background: "#c6c6c6" },
})
  .composite(composites)
  .png({ palette: false, progressive: false })
  .toFile(previewPath);

const report = {
  source: {
    file: "assets/gui-icon-sources/wiki/Invicon_Stone_Button.png",
    url: "https://minecraft.wiki/w/File:Invicon_Stone_Button.png",
    ...source,
  },
  conversion: "32x32 to 16x16, nearest-neighbor, no redraw",
  icons,
  preview: {
    file: "art_review_exports/gui/redstone-icons-acceptance.png",
    width: previewWidth,
    height: previewHeight,
    background: "#c6c6c6",
    iconScale: scale,
    interpolation: "nearest-neighbor",
  },
};
await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);
console.log(JSON.stringify(report, null, 2));
