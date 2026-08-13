const sharp = require(process.argv[2]);
const path = require('path');
const fs = require('fs');

const input = process.argv[3];
const outputDir = process.argv[4];

// Coordinates use the requested 520x500 working canvas.
const components = [
  ['nav-first', 'navigation', 'enabled', 326, 42, 33, 30],
  ['nav-previous', 'navigation', 'enabled', 362, 42, 31, 30],
  ['nav-next-active', 'navigation', 'active-current', 398, 42, 32, 30],
  ['nav-last', 'navigation', 'enabled', 435, 42, 31, 30],
  ['nav-close', 'navigation-destructive', 'enabled', 470, 42, 31, 30],
  ['page-previous', 'navigation', 'enabled', 424, 123, 32, 27],
  ['page-next', 'navigation', 'enabled', 461, 123, 32, 27],
  ['resource-item-active', 'resource-toggle', 'active-selected', 18, 370, 113, 40],
  ['resource-fluid', 'resource-toggle', 'inactive-unselected', 140, 370, 112, 40],
  ['resource-energy', 'resource-toggle', 'inactive-unselected', 262, 370, 112, 40],
  ['resource-auto', 'resource-toggle', 'inactive-unselected', 383, 370, 115, 40],
  ['redstone-cycle-ignore', 'redstone-cycle', 'enabled-mode-ignore', 28, 440, 124, 34],
  ['slot-decrease', 'numeric-adjust', 'enabled', 184, 440, 34, 34],
  ['slot-increase', 'numeric-adjust', 'enabled', 279, 440, 34, 34],
  ['priority-decrease', 'numeric-adjust', 'enabled', 350, 440, 34, 34],
  ['priority-increase', 'numeric-adjust', 'enabled', 458, 440, 27, 34]
];

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const meta = await sharp(input).metadata();
  if (meta.width !== 520 || meta.height !== 500) throw new Error(`Expected 520x500, got ${meta.width}x${meta.height}`);
  const manifest = [];
  for (const [name,type,state,x,y,width,height] of components) {
    const file = `${name}.png`;
    await sharp(input).extract({left:x,top:y,width,height}).png().toFile(path.join(outputDir,file));
    manifest.push({name,type,state,bounds:[x,y,width,height],file});
  }
  fs.writeFileSync(path.join(outputDir,'manifest.json'), JSON.stringify({source:path.basename(input),canvas:[520,500],components:manifest},null,2)+'\n');
  console.log(JSON.stringify({outputDir,count:manifest.length,canvas:[520,500]}));
}

main().catch((error)=>{console.error(error);process.exitCode=1;});
