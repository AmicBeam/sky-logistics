const sharp = require(process.argv[2]);
const path = require('path');

const outDir = process.argv[3];
const W = 260;
const H = 250;
const SCALE = 4;
const rgba = Buffer.alloc(W * H * 4);

const C = {
  void: '#02090c', ink: '#061116', panel: '#0b1b21', panel2: '#10262d',
  edge0: '#10262e', edge1: '#244651', edge2: '#4b8992', steel0: '#111516',
  steel1: '#242829', steel2: '#474b4b', steel3: '#777c7a', white: '#d9dfdc',
  gold0: '#6d3b08', gold1: '#b5680d', gold2: '#edaa2c', gold3: '#ffd35c',
  cyan0: '#075267', cyan1: '#0e92af', cyan2: '#55d8e7', green0: '#245d0d',
  green1: '#55b928', green2: '#91e94f', red0: '#751313', red1: '#e72b24',
  red2: '#ff7b42', purple0: '#44204f', purple1: '#8b4a9a', purple2: '#d48add',
  blue: '#3479c9', earth: '#6ba840', transparent: '#00000000',
};

function hex(s) {
  if (s.length === 9) return [1, 3, 5, 7].map((i) => parseInt(s.slice(i, i + 2), 16));
  return [1, 3, 5].map((i) => parseInt(s.slice(i, i + 2), 16)).concat(255);
}
function pixel(x, y, color) {
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const v = hex(color); const i = (y * W + x) * 4;
  rgba[i] = v[0]; rgba[i + 1] = v[1]; rgba[i + 2] = v[2]; rgba[i + 3] = v[3];
}
function rect(x, y, w, h, color) {
  for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) pixel(xx, yy, color);
}
function vfill(x, y, w, h, colors) {
  for (let yy = 0; yy < h; yy++) rect(x, y + yy, w, 1, colors[Math.min(colors.length - 1, Math.floor(yy * colors.length / h))]);
}
function line(x0, y0, x1, y1, color) {
  let dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
  let dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, err = dx + dy;
  while (true) { pixel(x0, y0, color); if (x0 === x1 && y0 === y1) break; const e = 2 * err; if (e >= dy) { err += dy; x0 += sx; } if (e <= dx) { err += dx; y0 += sy; } }
}
function poly(points, color) {
  const minY = Math.min(...points.map((p) => p[1]));
  const maxY = Math.max(...points.map((p) => p[1]));
  for (let y = minY; y <= maxY; y++) {
    const xs = [];
    for (let i = 0; i < points.length; i++) {
      const a = points[i], b = points[(i + 1) % points.length];
      if (a[1] === b[1] || y < Math.min(a[1], b[1]) || y >= Math.max(a[1], b[1])) continue;
      xs.push(a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]));
    }
    xs.sort((a,b) => a-b);
    for (let i = 0; i + 1 < xs.length; i += 2) rect(Math.ceil(xs[i]), y, Math.floor(xs[i+1]) - Math.ceil(xs[i]) + 1, 1, color);
  }
}
function frame(x, y, w, h, active = false) {
  const hi = active ? C.gold3 : C.edge2, mid = active ? C.gold2 : C.edge1;
  rect(x + 2, y, w - 4, 1, hi); rect(x + 1, y + 1, w - 2, 1, mid);
  rect(x, y + 2, 1, h - 4, hi); rect(x + 1, y + 2, 1, h - 4, mid);
  rect(x + 2, y + h - 1, w - 4, 1, C.edge0); rect(x + 1, y + h - 2, w - 2, 1, C.edge1);
  rect(x + w - 1, y + 2, 1, h - 4, C.edge0); rect(x + w - 2, y + 2, 1, h - 4, C.edge1);
  pixel(x + 1, y + 1, hi); pixel(x + w - 2, y + 1, mid);
  pixel(x + 1, y + h - 2, mid); pixel(x + w - 2, y + h - 2, C.edge0);
}
function carvedFrame(x, y, w, h) {
  // Narrow double cyan outline with clipped Minecraft-style corners.
  rect(x + 2, y, w - 4, 1, C.edge2); rect(x + 1, y + 1, w - 2, 1, C.edge1);
  rect(x, y + 2, 1, h - 4, C.edge2); rect(x + 1, y + 2, 1, h - 4, C.edge0);
  rect(x + 2, y + h - 1, w - 4, 1, C.edge2); rect(x + 1, y + h - 2, w - 2, 1, C.edge0);
  rect(x + w - 1, y + 2, 1, h - 4, C.edge2); rect(x + w - 2, y + 2, 1, h - 4, C.edge0);
  pixel(x + 1, y + 1, C.edge2); pixel(x + w - 2, y + 1, C.edge2);
  pixel(x + 1, y + h - 2, C.edge2); pixel(x + w - 2, y + h - 2, C.edge2);
  rect(x + 3, y + 3, w - 6, 1, C.void); rect(x + 3, y + h - 4, w - 6, 1, C.edge0);
}
function inset(x, y, w, h, active = false) {
  rect(x, y, w, h, C.steel0); rect(x + 1, y + 1, w - 2, h - 2, C.steel1);
  rect(x + 2, y + 2, w - 4, h - 4, C.void);
  rect(x + 1, y + h - 1, w - 2, 1, active ? C.gold2 : C.steel3);
  rect(x + w - 1, y + 1, 1, h - 1, active ? C.gold1 : C.steel3);
}
function squareButton(x, y, w, h, active = false) {
  // Reference-style compact control: black outer keyline, steel bevel,
  // recessed dark face. Active pagination uses cyan, never gold.
  rect(x, y, w, h, C.void);
  rect(x + 1, y + 1, w - 2, h - 2, active ? C.cyan0 : C.steel2);
  rect(x + 1, y + 1, w - 2, 1, active ? C.cyan2 : C.steel3);
  rect(x + 1, y + 1, 1, h - 2, active ? C.cyan1 : C.steel3);
  rect(x + 1, y + h - 2, w - 2, 1, active ? '#032b35' : C.steel0);
  rect(x + w - 2, y + 1, 1, h - 2, C.steel0);
  vfill(x + 3, y + 3, w - 6, h - 6, active ? ['#12333a', '#082127', C.void] : ['#343839', '#242829', '#171a1b']);
  rect(x + 3, y + 3, w - 6, 1, active ? C.cyan0 : '#5a5e5d');
}
function modeButton(x, y, w, h, active = false) {
  // Wide cyan-black selector with the reference's clipped double border.
  rect(x, y, w, h, C.void);
  const hi = active ? C.gold3 : C.steel3;
  const mid = active ? C.gold2 : C.edge1;
  rect(x + 2, y, w - 4, 1, hi); rect(x + 1, y + 1, w - 2, 1, mid);
  rect(x, y + 2, 1, h - 4, hi); rect(x + 1, y + 2, 1, h - 4, mid);
  rect(x + 2, y + h - 1, w - 4, 1, active ? C.gold0 : C.steel0);
  rect(x + w - 1, y + 2, 1, h - 4, active ? C.gold0 : C.steel0);
  vfill(x + 3, y + 3, w - 6, h - 6, [C.panel2, '#0b2027', C.ink]);
  rect(x + 3, y + 3, w - 6, 1, active ? C.gold1 : C.edge0);
  pixel(x+1,y+1,hi); pixel(x+w-2,y+1,mid); pixel(x+1,y+h-2,mid);
}
function smallButton(x, y, w, h) {
  rect(x, y, w, h, C.void);
  rect(x + 1, y + 1, w - 2, h - 2, C.steel1);
  rect(x + 1, y + 1, w - 2, 1, C.steel3);
  rect(x + 1, y + 1, 1, h - 2, C.steel2);
  rect(x + 1, y + h - 2, w - 2, 1, C.steel0);
  rect(x + w - 2, y + 1, 1, h - 2, C.steel0);
  vfill(x + 3, y + 3, w - 6, h - 6, ['#303435', '#242829', '#191d1e']);
}
function tableCell(x, y, w, h) {
  rect(x, y, w, h, C.void);
  rect(x + 1, y + 1, w - 2, h - 2, C.steel1);
  rect(x + 1, y + 1, w - 2, 1, C.steel3);
  rect(x + 1, y + 1, 1, h - 2, C.steel2);
  rect(x + 1, y + h - 2, w - 2, 1, C.steel0);
  rect(x + w - 2, y + 1, 1, h - 2, C.steel0);
  vfill(x + 3, y + 3, w - 6, h - 6, ['#282c2c', '#222627', '#1a1e1f']);
}
function check(x, y, on, color = C.green1) {
  if (!on) {
    rect(x, y, 8, 8, C.steel0); rect(x + 1, y + 1, 6, 6, C.steel3);
    rect(x + 2, y + 2, 5, 5, C.steel1); rect(x + 3, y + 3, 3, 3, C.void); return;
  }
  // Standalone beveled checkmark, matching the reference's sprite treatment.
  pixel(x, y + 4, C.green0); pixel(x + 1, y + 5, C.green0); pixel(x + 2, y + 6, C.green0);
  pixel(x + 3, y + 5, C.green0); pixel(x + 4, y + 4, C.green0); pixel(x + 5, y + 3, C.green0); pixel(x + 6, y + 2, C.green0); pixel(x + 7, y + 1, C.green0);
  pixel(x + 1, y + 3, color); pixel(x + 2, y + 4, color); pixel(x + 3, y + 4, color);
  pixel(x + 4, y + 3, color); pixel(x + 5, y + 2, color); pixel(x + 6, y + 1, color); pixel(x + 7, y, C.green2);
}
function arrow(cx, cy, dir, color) {
  const shadow = dir === 'up' || dir === 'left' ? C.void : C.steel0;
  for (let y = -5; y <= 5; y++) for (let x = -5; x <= 5; x++) {
    let inside = false;
    if (dir === 'up') inside = (y >= -5 && y <= 0 && Math.abs(x) <= y + 5) || (y > 0 && Math.abs(x) <= 2);
    if (dir === 'down') inside = (y <= 5 && y >= 0 && Math.abs(x) <= 5 - y) || (y < 0 && Math.abs(x) <= 2);
    if (dir === 'left') inside = (x >= -5 && x <= 0 && Math.abs(y) <= x + 5) || (x > 0 && Math.abs(y) <= 2);
    if (dir === 'right') inside = (x <= 5 && x >= 0 && Math.abs(y) <= 5 - x) || (x < 0 && Math.abs(y) <= 2);
    if (inside) pixel(cx + x, cy + y, color);
  }
  // One-pixel light ridge and dark drop edge, authored on the logical grid.
  if (dir === 'up') { line(cx,cy-5,cx-4,cy,C.green2); line(cx+3,cy+1,cx+3,cy+5,shadow); }
  if (dir === 'down') { line(cx-4,cy,cx,cy+5,C.cyan2); line(cx+3,cy-4,cx+3,cy,shadow); }
  if (dir === 'left') { line(cx-5,cy,cx,cy-4,C.gold3); line(cx+1,cy+3,cx+5,cy+3,shadow); }
  if (dir === 'right') { line(cx,cy-4,cx+5,cy,C.cyan2); line(cx-4,cy+3,cx,cy+3,shadow); }
}
function cube(x, y) {
  // 12x12 logistics block sprite with distinct top/left/right planes.
  poly([[x+1,y+3],[x+5,y],[x+11,y+3],[x+6,y+6]], C.steel2);
  poly([[x+1,y+3],[x+6,y+6],[x+6,y+11],[x+1,y+8]], C.steel1);
  poly([[x+6,y+6],[x+11,y+3],[x+11,y+8],[x+6,y+11]], C.steel0);
  line(x + 1, y + 3, x + 5, y, C.steel3); line(x + 5, y, x + 11, y + 3, C.steel2);
  line(x + 2, y + 3, x + 5, y + 1, C.white); line(x + 5, y + 1, x + 10, y + 3, C.steel3);
  line(x + 1, y + 3, x + 6, y + 6, C.steel3); line(x + 6, y + 6, x + 11, y + 3, C.steel2);
  line(x + 6, y + 6, x + 6, y + 11, C.steel3); line(x + 11, y + 3, x + 11, y + 8, C.void);
  rect(x + 3, y + 5, 2, 3, C.void); pixel(x + 4, y + 6, C.gold3); pixel(x + 4, y + 7, C.gold1);
  pixel(x+2,y+7,C.steel3); pixel(x+8,y+8,C.steel1); pixel(x+9,y+9,C.void);
}
function torch(x, y) {
  rect(x + 2, y + 3, 2, 7, C.gold1); pixel(x + 3, y + 7, C.gold3);
  rect(x + 1, y + 1, 4, 3, C.red1); pixel(x + 2, y, C.red2); pixel(x + 3, y + 1, C.gold3);
}
function globe(x, y, purple = false) {
  const a = purple ? C.purple1 : C.blue, b = purple ? C.purple2 : C.earth;
  rect(x + 2, y, 5, 1, a); rect(x, y + 2, 9, 5, a); rect(x + 2, y + 8, 5, 1, C.void);
  pixel(x + 1, y + 1, a); pixel(x + 7, y + 1, a); pixel(x + 1, y + 7, C.void); pixel(x + 7, y + 7, C.void);
  pixel(x + 3, y + 2, b); pixel(x + 4, y + 3, b); pixel(x + 2, y + 4, b); pixel(x + 5, y + 5, b); pixel(x + 4, y + 6, b);
  pixel(x+2,y+2,purple?C.purple2:C.cyan2); pixel(x+1,y+3,C.white); pixel(x+6,y+7,purple?C.purple0:C.cyan0);
}
function chest(x, y) {
  rect(x+1,y+2,10,8,C.gold0); rect(x+2,y+1,8,2,C.gold2); rect(x+1,y+4,10,1,C.gold3);
  rect(x+2,y+5,8,4,C.gold1); rect(x+2,y+8,8,1,C.gold0); rect(x+5,y+4,2,4,C.steel0);
  pixel(x+5,y+5,C.steel3); pixel(x+6,y+5,C.white); pixel(x+2,y+2,C.gold3); pixel(x+9,y+3,C.gold0);
}
function droplet(x,y) {
  poly([[x+5,y],[x+1,y+6],[x+1,y+9],[x+3,y+11],[x+7,y+11],[x+9,y+9],[x+9,y+6]], C.steel2);
  line(x+5,y,x+2,y+7,C.white); pixel(x+3,y+8,C.steel3); pixel(x+7,y+9,C.steel0);
}
function autoRing(x,y) {
  rect(x+3,y,5,1,C.white); rect(x+1,y+2,1,6,C.steel3); rect(x+9,y+2,1,6,C.steel0); rect(x+3,y+9,5,1,C.steel1);
  pixel(x+2,y+1,C.steel3); pixel(x+8,y+1,C.steel2); pixel(x+2,y+8,C.steel2); pixel(x+8,y+8,C.steel0);
  rect(x+3,y+2,5,1,C.steel1); rect(x+3,y+7,5,1,C.steel2); pixel(x+3,y+3,C.steel2); pixel(x+7,y+6,C.steel0);
}
function plus(x, y, color = C.white) { rect(x + 2, y, 2, 6, color); rect(x, y + 2, 6, 2, color); }
function minus(x, y, color = C.white) { rect(x, y + 2, 6, 2, color); }

async function main() {
  rect(0, 0, W, H, C.void);
  // Outer carved frame and title band.
  carvedFrame(1, 1, 258, 248); carvedFrame(5, 18, 250, 23); carvedFrame(5, 44, 250, 15);
  rect(7, 20, 246, 19, C.ink); rect(7, 46, 246, 11, C.panel);
  // Original title emblem, authored at logical resolution.
  // Title emblem is its own gold device sprite, not a UI button.
  rect(8,7,9,9,C.void); rect(9,7,7,1,C.gold2); rect(8,8,1,7,C.gold3); rect(9,15,7,1,C.gold0); rect(16,8,1,7,C.gold0);
  rect(10,9,5,5,C.gold1); rect(11,10,3,3,C.gold3); pixel(12,11,C.panel); pixel(10,9,C.gold3); pixel(14,13,C.gold0);
  inset(38, 23, 96, 14); squareButton(166, 23, 14, 14); squareButton(183, 23, 14, 14); squareButton(200, 23, 14, 14, true); squareButton(217, 23, 14, 14); squareButton(234, 23, 14, 14);
  // Pagination glyphs remain much finer than the table direction sprites.
  line(170,27,170,33,C.white); line(174,27,171,30,C.white); line(171,30,174,33,C.white);
  line(192,27,188,30,C.white); line(188,30,192,33,C.white);
  line(204,27,204,33,C.white); line(208,27,205,30,C.white); line(205,30,208,33,C.white);
  line(221,27,225,30,C.white); line(225,30,221,33,C.white); line(226,27,226,33,C.white);
  line(239, 27, 244, 32, C.red1); line(244, 27, 239, 32, C.red1);
  line(86, 45, 86, 58, C.edge1); line(170, 45, 170, 58, C.edge1);
  // Main connection table shell: cyan double frame outside, beveled steel table inside.
  carvedFrame(5, 62, 250, 116); rect(7, 64, 246, 112, C.ink);
  carvedFrame(8, 77, 244, 98);
  const cols = [9,37,63,84,106,128,158,181,227,251];
  for (let row = 0; row < 4; row++) {
    for (let col = 0; col < cols.length - 1; col++) {
      tableCell(cols[col], 87 + row * 21, cols[col + 1] - cols[col], 21);
    }
  }
  for (let row = 0; row < 4; row++) {
    const cy = 97 + row * 21;
    cube(17, cy - 5);
    arrow(50, cy, ['up','down','left','right'][row], [C.green1,C.cyan1,C.gold2,C.cyan1][row]);
    check(70, cy - 4, row !== 2); check(91, cy - 4, row === 1 || row === 3, C.cyan1); check(113, cy - 4, row !== 1);
    inset(136, cy - 5, 17, 11); check(165, cy - 4, false); if (row === 0 || row === 2) torch(167, cy - 5);
    globe(236, cy - 4, row >= 2);
  }
  // Bottom selector row.
  carvedFrame(5, 182, 250, 24); modeButton(10, 186, 57, 17, true); modeButton(72, 186, 57, 17); modeButton(134, 186, 57, 17); modeButton(196, 186, 54, 17);
  chest(20, 189);
  // droplet, lightning, auto ring.
  droplet(81,188);
  line(151, 189, 146, 196, C.white); line(146, 196, 151, 196, C.white); line(151, 196, 147, 201, C.white);
  autoRing(209,189);
  // Lower controls.
  carvedFrame(8, 210, 75, 31); carvedFrame(87, 210, 80, 31); carvedFrame(171, 210, 80, 31);
  inset(15, 221, 17, 15); torch(21, 223); squareButton(36, 221, 42, 15);
  smallButton(94, 221, 16, 15); inset(115, 221, 23, 15); smallButton(143, 221, 16, 15); minus(99, 226); plus(148, 225);
  smallButton(178, 221, 16, 15); inset(199, 221, 23, 15); smallButton(227, 221, 16, 15); minus(183, 226); plus(232, 225);
  // Hand-placed material glints: intentional single logical pixels.
  for (const [x,y,c] of [[3,4,C.cyan2],[256,4,C.edge2],[3,245,C.edge2],[256,245,C.cyan2],[10,64,C.cyan1],[250,174,C.edge2],[68,187,C.gold3]]) pixel(x,y,c);

  const logical = path.join(outDir, 'native-pixel-background-260x250.png');
  const enlarged = path.join(outDir, 'native-pixel-background-4x.png');
  await sharp(rgba, { raw: { width: W, height: H, channels: 4 } }).png().toFile(logical);
  await sharp(rgba, { raw: { width: W, height: H, channels: 4 } }).resize(W*SCALE,H*SCALE,{kernel:'nearest'}).png().toFile(enlarged);
  console.log(JSON.stringify({ logical, enlarged, logicalSize:[W,H], framebuffer:[W*SCALE,H*SCALE], palette:Object.keys(C).length }));
}

main().catch((e) => { console.error(e); process.exitCode = 1; });
