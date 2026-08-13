const sharp = require(process.argv[2]);

const input = process.argv[3];
const output = process.argv[4];
const logicalSize = 314;
const scale = 4;

async function main() {
  // Materialize the logical raster before enlargement so Sharp cannot fuse
  // the two resize operations into one resampling pass.
  const logical = await sharp(input)
    .resize(logicalSize, logicalSize, { fit: 'fill', kernel: 'nearest' })
    .png()
    .toBuffer();

  await sharp(logical)
    .resize(logicalSize * scale, logicalSize * scale, {
      fit: 'fill',
      kernel: 'nearest',
    })
    .png()
    .toFile(output);

  const { data, info } = await sharp(output)
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });

  let total = 0;
  let uniform = 0;
  for (let y = 0; y < info.height; y += scale) {
    for (let x = 0; x < info.width; x += scale) {
      total += 1;
      let matches = true;
      const base = (y * info.width + x) * 4;
      for (let dy = 0; dy < scale && matches; dy += 1) {
        for (let dx = 0; dx < scale && matches; dx += 1) {
          const current = ((y + dy) * info.width + x + dx) * 4;
          for (let channel = 0; channel < 4; channel += 1) {
            if (data[current + channel] !== data[base + channel]) {
              matches = false;
              break;
            }
          }
        }
      }
      if (matches) uniform += 1;
    }
  }

  console.log(JSON.stringify({
    output,
    width: info.width,
    height: info.height,
    uniform4x4: uniform,
    total,
    pass: uniform === total,
  }));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
