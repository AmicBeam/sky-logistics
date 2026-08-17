const sharp = require(process.argv[2]);

const originalPath = process.argv[3];
const pixelPath = process.argv[4];
const outputPath = process.argv[5];

// Text-only regions in the 1040x1000 source. They intentionally exclude
// icons and panel edges so those remain part of the strict 4x geometry.
const regions = [
  [78, 24, 180, 45], [48, 96, 70, 38], [165, 96, 205, 38], [578, 100, 65, 32],
  [127, 181, 100, 38], [461, 181, 100, 38], [785, 181, 100, 38],
  [375, 257, 205, 40], [782, 260, 60, 32],
  [58, 312, 75, 35], [166, 312, 70, 35], [282, 312, 45, 35], [365, 312, 45, 35],
  [450, 312, 45, 35], [544, 312, 70, 35], [645, 312, 70, 35], [775, 312, 70, 35], [925, 312, 70, 35],
  [746, 371, 145, 35], [746, 457, 145, 35], [746, 542, 145, 35], [746, 628, 145, 35],
  [558, 371, 55, 35], [558, 457, 55, 35], [558, 542, 55, 35], [558, 628, 55, 35],
  [145, 753, 75, 40], [385, 753, 75, 40], [647, 753, 75, 40], [886, 753, 75, 40],
  [134, 837, 85, 32], [467, 837, 85, 32], [805, 837, 85, 32],
  [170, 881, 105, 45], [480, 881, 55, 45], [811, 881, 55, 45],
  [386, 881, 45, 45], [574, 881, 45, 45], [716, 881, 45, 45], [908, 881, 45, 45],
];

function inTextRegion(x, y) {
  return regions.some(([rx, ry, rw, rh]) => x >= rx && x < rx + rw && y >= ry && y < ry + rh);
}

async function main() {
  const width = 1040;
  const height = 1000;
  const { data: original } = await sharp(originalPath)
    .extract({ left: 0, top: 0, width, height })
    .ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const { data: pixel } = await sharp(pixelPath)
    .ensureAlpha().raw().toBuffer({ resolveWithObject: true });

  let copied = 0;
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      if (!inTextRegion(x, y)) continue;
      const i = (y * width + x) * 4;
      pixel[i] = original[i];
      pixel[i + 1] = original[i + 1];
      pixel[i + 2] = original[i + 2];
      pixel[i + 3] = original[i + 3];
      copied += 1;
    }
  }

  await sharp(pixel, { raw: { width, height, channels: 4 } }).png().toFile(outputPath);
  console.log(JSON.stringify({ output: outputPath, width, height, originalTextPixels: copied }));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
