const sharp = require(process.argv[2]);

const input = process.argv[3];
const output = process.argv[4];

async function main() {
  const image = sharp(input);
  const { width, height } = await image.metadata();
  if (width !== 1040 || height !== 1000) throw new Error(`Expected 1040x1000, got ${width}x${height}`);
  const { data } = await image.ensureAlpha().raw().toBuffer({ resolveWithObject: true });

  // The third-round model image uses a 1040x1000 crop. The arrow occupies
  // the right end of the one-piece redstone button. Replace glyph pixels
  // with nearby button-face colors while retaining the button's outer bevel.
  const x0 = 247, y0 = 873, x1 = 302, y1 = 941;
  for (let y = y0; y < y1; y += 1) {
    for (let x = x0; x < x1; x += 1) {
      const i = (y * width + x) * 4;
      // Restore the complete interior patch, including the dark drop shadow
      // of the arrow. Keep the outer button bevel untouched.
      if (x > x0 + 8 && x < x1 - 8 && y > y0 + 8 && y < y1 - 8) {
        const sampleX = 240;
        const s = (y * width + sampleX) * 4;
        data[i] = data[s]; data[i + 1] = data[s + 1]; data[i + 2] = data[s + 2]; data[i + 3] = 255;
      }
    }
  }
  await sharp(data, { raw: { width, height, channels: 4 } }).png().toFile(output);
  console.log(JSON.stringify({ output, removed: 'redstone dropdown arrow', size: [width, height] }));
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
