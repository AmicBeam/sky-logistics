const sharp = require(process.argv[2]);
const path = require('path');

const outDir = process.argv[3];
const W = 1040;
const H = 1000;

const labels = [
  [80, 58, '天穹配置器', 27, '#f3b43a', 'start', 700],
  [55, 130, '线路', 23, '#a9b8bf'], [165, 130, 'AmicBeam-0', 25, '#edf1ef'], [580, 130, '1/3', 22, '#edf1ef'],
  [130, 211, '节点  6', 22, '#b7c3c7'], [464, 211, '抽取  2', 22, '#b7c3c7'], [789, 211, '存入  4', 22, '#b7c3c7'],
  [380, 287, '线路连接面', 24, '#9fb3bb'], [785, 287, '1/2', 22, '#edf1ef'],
  [68, 339, '设备', 18, '#aebbc0'], [180, 339, '模式', 18, '#aebbc0'], [291, 339, '物', 18, '#aebbc0'],
  [374, 339, '流', 18, '#aebbc0'], [462, 339, '能', 18, '#aebbc0'], [554, 339, '优先', 18, '#aebbc0'],
  [656, 339, '红石', 18, '#aebbc0'], [794, 339, '坐标', 18, '#aebbc0'], [936, 339, '维度', 18, '#aebbc0'],
  [573, 407, '0', 21, '#edf1ef', 'middle'], [573, 491, '-1', 21, '#edf1ef', 'middle'],
  [573, 575, '1', 21, '#edf1ef', 'middle'], [573, 659, '0', 21, '#edf1ef', 'middle'],
  [815, 407, '12  64  -8', 21, '#edf1ef', 'middle'], [815, 491, '-32  70  15', 21, '#edf1ef', 'middle'],
  [815, 575, '5  55  100', 21, '#edf1ef', 'middle'], [815, 659, '0  128  0', 21, '#edf1ef', 'middle'],
  [160, 793, '物品', 22, '#ffd35c'], [394, 793, '流体', 22, '#aebbc0'], [650, 793, '能量', 22, '#aebbc0'], [887, 793, '自动', 22, '#aebbc0'],
  [147, 865, '红石', 20, '#aebbc0'], [477, 865, '留槽', 20, '#aebbc0'], [813, 865, '优先', 20, '#aebbc0'],
  [176, 925, '忽略', 21, '#edf1ef'], [505, 925, '0', 22, '#edf1ef', 'middle'], [841, 925, '0', 22, '#edf1ef', 'middle'],
];

function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

async function main() {
  const nodes = labels.map(([x,y,text,size,color,anchor='start',weight=500]) =>
    `<text x="${x}" y="${y}" fill="${color}" font-size="${size}" font-weight="${weight}" text-anchor="${anchor}" font-family="Hiragino Sans GB, STHeiti, Menlo, sans-serif">${esc(text)}</text>`
  ).join('\n');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}"><rect width="100%" height="100%" fill="none"/>${nodes}</svg>`;
  const textPath = path.join(outDir, 'native-pixel-text-4x.png');
  await sharp(Buffer.from(svg)).png().toFile(textPath);
  console.log(JSON.stringify({ textPath, framebuffer:[W,H], labels:labels.length }));
}

main().catch((e) => { console.error(e); process.exitCode = 1; });
