import sharp from "/Users/bytedance/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp/dist/index.mjs";
import { writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const input = resolve(process.argv[2] ?? "assets/generated/chora-kleis-pixel/chora_kleis_32_refined.png");
const output = resolve(process.argv[3] ?? "assets/generated/chora-kleis-pixel/chora_kleis_32_refined-symmetry.json");
const { data, info } = await sharp(input).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
if (info.width !== 32 || info.height !== 32) throw new Error("Expected a 32x32 sprite");

const rgba = (x, y) => {
  const offset = (y * info.width + x) * 4;
  return Array.from(data.subarray(offset, offset + 4));
};

let silhouetteMismatches = 0;
const mismatchCoordinates = [];
for (let y = 0; y < 32; y += 1) {
  for (let x = 0; x < 32; x += 1) {
    const mirrorX = 31 - y;
    const mirrorY = 31 - x;
    const opaque = rgba(x, y)[3] !== 0;
    const mirrorOpaque = rgba(mirrorX, mirrorY)[3] !== 0;
    if (opaque !== mirrorOpaque) {
      silhouetteMismatches += 1;
      if (mismatchCoordinates.length < 32) mismatchCoordinates.push({ x, y, mirrorX, mirrorY });
    }
  }
}

const report = {
  input,
  axis: "pixel-center reflection across x+y=32",
  mapping: "(x,y) -> (31-y,31-x)",
  silhouetteMismatches,
  mismatchCoordinates,
  passed: silhouetteMismatches === 0,
};
await writeFile(output, `${JSON.stringify(report, null, 2)}\n`);
console.log(JSON.stringify(report));
if (!report.passed) process.exitCode = 1;
