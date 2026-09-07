import sharp from "/Users/bytedance/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp/dist/index.mjs";
import { resolve } from "node:path";
import { unlink } from "node:fs/promises";

const input = resolve("assets/item-icon-sources/kleis-dominion-wand-animation/base.png");
const output = resolve("common/src/main/resources/assets/skylogistics/textures/item/kleis_dominion_wand.png");
const cyan = [[60,145,197], [82,171,207], [104,196,219], [121,220,233], [156,236,240], [185,255,244]];
const amber = [[232,154,54], [238,170,68], [244,187,84], [255,213,111], [255,230,138], [255,240,162]];
const { data, info } = await sharp(input).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
if (info.width !== 32 || info.height !== 32) throw new Error("Expected 32x32 base sprite");
const frames = [];
for (let frame = 0; frame < 6; frame += 1) {
  const pixels = Buffer.from(data);
  for (let offset = 0; offset < pixels.length; offset += 4) {
    const isCyan = pixels[offset] === 121 && pixels[offset + 1] === 220 && pixels[offset + 2] === 233;
    const isAmber = pixels[offset] === 255 && pixels[offset + 1] === 213 && pixels[offset + 2] === 111;
    const replacement = isCyan ? cyan[frame] : isAmber ? amber[frame] : null;
    if (replacement) [pixels[offset], pixels[offset + 1], pixels[offset + 2]] = replacement;
  }
  frames.push(await sharp(pixels, { raw: info }).png().toBuffer());
}
await sharp({ create: { width: 32, height: 192, channels: 4, background: {r:0,g:0,b:0,alpha:0} } })
  .composite(frames.map((frame, index) => ({ input: frame, left: 0, top: index * 32 })))
  .png().toFile(output + ".animated.tmp.png");
await sharp(output + ".animated.tmp.png").toFile(output);
await unlink(output + ".animated.tmp.png");
console.log(output);
