import sharp from "../prototypes/configurator-gui/node_modules/sharp/dist/index.mjs";
import { resolve } from "node:path";
import { readFile } from "node:fs/promises";

const projectRoot = resolve(import.meta.dirname, "..");
const iconRoot = resolve(projectRoot, "common/src/main/resources/assets/skylogistics/textures/gui/configurator");
const sourceRoot = resolve(projectRoot, "assets/gui-icon-sources");
const prototypeRoot = resolve(projectRoot, "prototypes/configurator-gui/public/art/icons");

const resources = ["item", "fluid", "energy", "auto"];

for (const name of resources) {
  const svg = await readFile(resolve(sourceRoot, `resource-${name}.svg`));
  const active = await sharp(svg, { density: 96 }).resize(18, 17).png().toBuffer();
  const inactive = await sharp(active).grayscale().modulate({ brightness: 0.68 }).png().toBuffer();
  await sharp(active).png({ palette: true }).toFile(resolve(iconRoot, `resource_${name}_small.png`));
  await sharp(inactive).png({ palette: true }).toFile(resolve(iconRoot, `resource_${name}_off_small.png`));
  await sharp(svg, { density: 192 }).resize(36, 34).png({ palette: true })
    .toFile(resolve(prototypeRoot, `resource-${name}.png`));
}
