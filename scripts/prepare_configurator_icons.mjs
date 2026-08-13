import sharp from "../prototypes/configurator-gui/node_modules/sharp/dist/index.mjs";
import { resolve } from "node:path";

const projectRoot = resolve(import.meta.dirname, "..");
const iconRoot = resolve(projectRoot, "common/src/main/resources/assets/skylogistics/textures/gui/configurator");

const resources = ["item", "fluid", "energy", "auto"];

for (const name of resources) {
  for (const suffix of ["", "_off"]) {
    const source = resolve(iconRoot, `resource_${name}${suffix}.png`);
    const { data, info } = await sharp(source).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    let minX = info.width;
    let minY = info.height;
    let maxX = -1;
    let maxY = -1;
    for (let y = 0; y < info.height; y += 1) {
      for (let x = 0; x < info.width; x += 1) {
        if (data[(y * info.width + x) * 4 + 3] === 0) continue;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      }
    }
    const width = maxX - minX + 1;
    const height = maxY - minY + 1;
    // Keep one transparent pixel around the icon so GUI blits never sample a
    // foreground pixel from the atlas edge.
    const scale = Math.min(15 / width, 15 / height);
    const resizedWidth = Math.max(1, Math.floor(width * scale));
    const resizedHeight = Math.max(1, Math.floor(height * scale));
    const left = Math.floor((18 - resizedWidth) / 2);
    const top = Math.floor((17 - resizedHeight) / 2);
    await sharp(source)
      .extract({ left: minX, top: minY, width, height })
      .resize(resizedWidth, resizedHeight, { kernel: "nearest" })
      .extend({
        left,
        right: 18 - resizedWidth - left,
        top,
        bottom: 17 - resizedHeight - top,
        background: { r: 0, g: 0, b: 0, alpha: 0 },
      })
      .png({ palette: true })
      .toFile(resolve(iconRoot, `resource_${name}${suffix}_small.png`));
  }
}
