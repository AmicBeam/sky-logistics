import sharp from "sharp";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const projectRoot = resolve(import.meta.dirname, "../../..");
const sourceRoot = resolve(projectRoot, "assets/generated/iterative-pixel-repair/components");
const outputRoot = resolve(import.meta.dirname, "../public/art/icons");

const icons = [
  { name: "item", file: "resource-item-active.png", left: 8, top: 3, width: 35, height: 34, color: null },
  { name: "fluid", file: "resource-fluid.png", left: 8, top: 3, width: 29, height: 34, color: [50, 174, 220] },
  { name: "energy", file: "resource-energy.png", left: 8, top: 3, width: 31, height: 34, color: [255, 202, 55] },
  { name: "auto", file: "resource-auto.png", left: 8, top: 3, width: 32, height: 34, color: [91, 196, 72] },
];

function colorDistance(pixel, background) {
  return Math.hypot(pixel[0] - background[0], pixel[1] - background[1], pixel[2] - background[2]);
}

async function extractIcon(icon) {
  const { data, info } = await sharp(resolve(sourceRoot, icon.file))
    .extract({ left: icon.left, top: icon.top, width: icon.width, height: icon.height })
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });

  const background = [7, 27, 36];
  const mask = new Uint8Array(info.width * info.height);

  for (let y = 0; y < info.height; y += 1) {
    for (let x = 0; x < info.width; x += 1) {
      const offset = (y * info.width + x) * 4;
      const pixel = [data[offset], data[offset + 1], data[offset + 2]];
      const luminance = pixel[0] * 0.2126 + pixel[1] * 0.7152 + pixel[2] * 0.0722;
      if (colorDistance(pixel, background) > 25 || luminance > 48) mask[y * info.width + x] = 1;
    }
  }

  // Only retain foreground components that contain a bright or strongly colored icon pixel.
  const visited = new Uint8Array(mask.length);
  const keep = new Uint8Array(mask.length);
  const neighbors = [[1, 0], [-1, 0], [0, 1], [0, -1]];
  for (let start = 0; start < mask.length; start += 1) {
    if (!mask[start] || visited[start]) continue;
    const queue = [start];
    const component = [];
    visited[start] = 1;
    let hasIconSeed = false;
    for (let cursor = 0; cursor < queue.length; cursor += 1) {
      const index = queue[cursor];
      component.push(index);
      const offset = index * 4;
      const max = Math.max(data[offset], data[offset + 1], data[offset + 2]);
      const min = Math.min(data[offset], data[offset + 1], data[offset + 2]);
      if (max > 75 || max - min > 35) hasIconSeed = true;
      const x = index % info.width;
      const y = Math.floor(index / info.width);
      for (const [dx, dy] of neighbors) {
        const nx = x + dx;
        const ny = y + dy;
        if (nx < 0 || nx >= info.width || ny < 0 || ny >= info.height) continue;
        const next = ny * info.width + nx;
        if (mask[next] && !visited[next]) {
          visited[next] = 1;
          queue.push(next);
        }
      }
    }
    if (hasIconSeed && component.length >= 5) component.forEach((index) => { keep[index] = 1; });
  }

  const output = Buffer.alloc(info.width * info.height * 4);
  for (let index = 0; index < keep.length; index += 1) {
    const offset = index * 4;
    if (!keep[index]) continue;
    const source = [data[offset], data[offset + 1], data[offset + 2]];
    if (icon.color) {
      const light = Math.max(source[0], source[1], source[2]) / 255;
      const shade = 0.28 + light * 0.9;
      output[offset] = Math.min(255, Math.round(icon.color[0] * shade));
      output[offset + 1] = Math.min(255, Math.round(icon.color[1] * shade));
      output[offset + 2] = Math.min(255, Math.round(icon.color[2] * shade));
    } else {
      output[offset] = source[0];
      output[offset + 1] = source[1];
      output[offset + 2] = source[2];
    }
    output[offset + 3] = 255;
  }

  await sharp(output, { raw: { width: info.width, height: info.height, channels: 4 } })
    .png({ palette: true })
    .toFile(resolve(outputRoot, `resource-${icon.name}.png`));
}

await mkdir(outputRoot, { recursive: true });
await Promise.all(icons.map(extractIcon));
