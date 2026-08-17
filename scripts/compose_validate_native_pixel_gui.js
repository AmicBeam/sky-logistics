const sharp = require(process.argv[2]);
const path = require('path');

const outDir = process.argv[3];
const logicalPath = path.join(outDir, 'native-pixel-background-260x250.png');
const backgroundPath = path.join(outDir, 'native-pixel-background-4x.png');
const textPath = path.join(outDir, 'native-pixel-text-4x.png');
const finalPath = path.join(outDir, 'native-pixel-final-4x.png');
const lockPath = path.join(outDir, 'design-lock.json');

async function raw(p) { return sharp(p).ensureAlpha().raw().toBuffer({ resolveWithObject: true }); }

async function main() {
  await sharp(backgroundPath).composite([{ input: textPath }]).png().toFile(finalPath);
  const logical = await raw(logicalPath), bg = await raw(backgroundPath), text = await raw(textPath), final = await raw(finalPath);
  const dimensions = logical.info.width === 260 && logical.info.height === 250 && bg.info.width === 1040 && bg.info.height === 1000 && text.info.width === 1040 && text.info.height === 1000 && final.info.width === 1040 && final.info.height === 1000;
  let strict4x = true;
  for (let y = 0; y < 250 && strict4x; y++) for (let x = 0; x < 260 && strict4x; x++) {
    const li = (y * 260 + x) * 4;
    for (let dy = 0; dy < 4 && strict4x; dy++) for (let dx = 0; dx < 4 && strict4x; dx++) {
      const bi = ((y * 4 + dy) * 1040 + x * 4 + dx) * 4;
      for (let c = 0; c < 4; c++) if (logical.data[li+c] !== bg.data[bi+c]) { strict4x = false; break; }
    }
  }
  let compositeExact = true;
  for (let i = 0; i < final.data.length && compositeExact; i += 4) {
    if (text.data[i+3] === 0) for (let c = 0; c < 4; c++) if (final.data[i+c] !== bg.data[i+c]) { compositeExact = false; break; }
  }
  const lock = require(path.resolve(lockPath));
  const expected = {
    outerFrame: [1,1,258,248], routeBar: [5,18,250,23], statsBar: [5,44,250,15],
    connectionPanel: [5,62,250,116], connectionTable: [8,77,244,98], modeBar: [5,182,250,24],
    redstonePanel: [8,210,75,31], reservePanel: [87,210,80,31], priorityPanel: [171,210,80,31],
  };
  const layoutLocked = Object.entries(expected).every(([key, value]) => JSON.stringify(lock.rectangles[key]) === JSON.stringify(value))
    && JSON.stringify(lock.table.columnEdges) === JSON.stringify([9,37,63,84,106,128,158,181,227,251])
    && JSON.stringify(lock.table.rowEdges) === JSON.stringify([87,108,129,150,171]);
  const report = { dimensions, strict4x, compositeExact, layoutLocked, logical:[260,250], framebuffer:[1040,1000], provenance:'native logical drawing operations only; reference image was not a renderer input' };
  console.log(JSON.stringify(report));
  if (!dimensions || !strict4x || !compositeExact || !layoutLocked) process.exitCode = 1;
}

main().catch((e) => { console.error(e); process.exitCode = 1; });
