const sharp = require(process.argv[2]);

const gridPath = process.argv[3];
const detailPath = process.argv[4];
const outputPath = process.argv[5];

const W = 1040;
const H = 1000;

// Regions contain typography only. Geometry, buttons, icons and borders stay
// on the strict 4x grid. Coordinates are framebuffer pixels.
const regions = [
  [68,18,210,48], [42,92,90,48], [148,92,260,48], [555,95,80,45],
  [105,174,150,52], [445,174,150,52], [770,174,150,52],
  [360,245,260,52], [760,245,85,48],
  [50,300,100,48], [150,300,100,48], [250,300,80,48], [335,300,80,48],
  [420,300,80,48], [505,300,110,48], [620,300,110,48], [740,300,130,48], [895,300,120,48],
  [735,360,165,58], [730,445,180,58], [735,530,170,58], [735,615,170,58],
  [525,360,90,58], [525,445,90,58], [525,530,90,58], [525,615,90,58],
  [135,745,115,58], [360,745,130,58], [615,745,130,58], [850,745,130,58],
  [110,820,125,48], [450,820,125,48], [785,820,125,48],
  [145,875,150,60], [470,875,80,60], [800,875,80,60],
  [550,95,300,48]
];

function inRegion(x, y) {
  return regions.some(([rx, ry, rw, rh]) => x >= rx && x < rx + rw && y >= ry && y < ry + rh);
}

async function main() {
  const grid = await sharp(gridPath).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const detail = await sharp(detailPath).resize(W,H,{fit:'fill'}).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const output = Buffer.from(grid.data);
  let copied = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (!inRegion(x,y)) continue;
    const i = (y * W + x) * 4;
    const r = detail.data[i], g = detail.data[i+1], b = detail.data[i+2];
    const max = Math.max(r,g,b), min = Math.min(r,g,b);
    const neutralGlyph = max >= 120 && max - min <= 55;
    const goldGlyph = r >= 135 && g >= 75 && b <= 95 && r > b * 1.65;
    if (!neutralGlyph && !goldGlyph) continue;
    output[i]=r; output[i+1]=g; output[i+2]=b; output[i+3]=255; copied++;
  }
  await sharp(output,{raw:{width:W,height:H,channels:4}}).png().toFile(outputPath);

  const final = await sharp(outputPath).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  let total=0, uniform=0;
  for(let y=0;y<H;y+=4) for(let x=0;x<W;x+=4) {
    total++; let ok=true; const base=(y*W+x)*4;
    for(let dy=0;dy<4&&ok;dy++) for(let dx=0;dx<4&&ok;dx++) {
      const p=((y+dy)*W+x+dx)*4;
      for(let c=0;c<4;c++) if(final.data[p+c]!==final.data[base+c]) {ok=false;break;}
    }
    if(ok) uniform++;
  }
  console.log(JSON.stringify({output:outputPath,copiedDetailPixels:copied,total4x4Blocks:total,uniform4x4Blocks:uniform,consistency:uniform/total,pass90:uniform/total>=0.9}));
  if(uniform/total<0.9) process.exitCode=1;
}

main().catch((error)=>{console.error(error);process.exitCode=1;});
