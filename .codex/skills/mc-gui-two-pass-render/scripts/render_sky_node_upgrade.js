const fs = require("fs");
const path = require("path");
const cp = require("child_process");
const sharp = require(process.argv[2]);

const projectRoot = process.argv[3];
const fontZip = process.argv[4];
const conceptPath = process.argv[5];
const versions = ["1.20.1", "1.21.1", "26.1.2"];
const prefix = "sky-node-ae2-mek-upgrade";
const textureRoot = path.join(projectRoot, "common/src/main/resources/assets/skylogistics/textures");
const texture = (...parts) => path.join(textureRoot, ...parts);

const backgroundSvg = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="384" height="244" viewBox="0 0 384 244">
  <defs>
    <linearGradient id="frame" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#111d23"/><stop offset="1" stop-color="#071115"/>
    </linearGradient>
    <linearGradient id="panel" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#13232c"/><stop offset="1" stop-color="#0a151a"/>
    </linearGradient>
    <linearGradient id="button" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#1c3442"/><stop offset="1" stop-color="#10202a"/>
    </linearGradient>
    <linearGradient id="selected" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#173b58"/><stop offset="1" stop-color="#102946"/>
    </linearGradient>
  </defs>
  <g shape-rendering="crispEdges">
    <rect width="384" height="244" fill="#020608"/>
    <rect x="1" y="1" width="382" height="242" fill="url(#frame)" stroke="#3e8b99"/>
    <rect x="3" y="3" width="378" height="28" fill="#081116" stroke="#263b45"/>
    <rect x="7" y="6" width="20" height="20" fill="#10293a" stroke="#68d7e5"/>
    <rect x="112" y="7" width="110" height="18" fill="#03080a" stroke="#50636b"/>
    <rect x="257" y="7" width="35" height="18" fill="#03080a" stroke="#50636b"/>
    <g fill="url(#button)" stroke="#4c6876">
      <rect x="295" y="7" width="15" height="18"/><rect x="312" y="7" width="15" height="18"/>
      <rect x="329" y="7" width="15" height="18"/><rect x="346" y="7" width="15" height="18"/>
    </g>
    <rect x="363" y="7" width="17" height="18" fill="#453719" stroke="#c99d38"/>

    <rect x="5" y="34" width="109" height="126" fill="#071014" stroke="#3a5967"/>
    <g fill="url(#button)" stroke="#34505d">
      <rect x="9" y="41" width="49" height="36"/><rect x="61" y="41" width="49" height="36"/>
      <rect x="9" y="80" width="49" height="36"/><rect x="61" y="80" width="49" height="36"/>
      <rect x="9" y="119" width="49" height="36"/><rect x="61" y="119" width="49" height="36"/>
    </g>
    <rect x="9" y="80" width="49" height="36" fill="url(#selected)" stroke="#68d7e5"/>
    <g>
      <rect x="13" y="72" width="41" height="2" fill="#8fb7c1"/>
      <rect x="65" y="72" width="41" height="2" fill="#ed9d42"/>
      <rect x="13" y="111" width="41" height="2" fill="#66aee9"/>
      <rect x="65" y="111" width="41" height="2" fill="#8fb7c1"/>
      <rect x="13" y="150" width="41" height="2" fill="#66aee9"/>
      <rect x="65" y="150" width="41" height="2" fill="#ed9d42"/>
    </g>

    <rect x="117" y="34" width="262" height="126" fill="#071014" stroke="#3a5967"/>
    <rect x="312" y="39" width="30" height="16" fill="url(#selected)" stroke="#68d7e5"/>
    <rect x="345" y="39" width="30" height="16" fill="url(#button)" stroke="#4c6876"/>

    <rect x="122" y="59" width="121" height="54" fill="url(#panel)" stroke="#29434f"/>
    <g fill="url(#button)" stroke="#455e6a">
      <rect x="126" y="72" width="35" height="36"/><rect x="164" y="72" width="35" height="36"/>
      <rect x="202" y="72" width="35" height="36"/>
    </g>
    <rect x="126" y="72" width="35" height="36" fill="url(#selected)" stroke="#68d7e5"/>

    <rect x="246" y="59" width="128" height="54" fill="url(#panel)" stroke="#29434f"/>
    <g fill="url(#button)" stroke="#455e6a">
      <rect x="250" y="72" width="37" height="36"/><rect x="291" y="72" width="37" height="36"/>
      <rect x="332" y="72" width="37" height="36"/>
    </g>
    <rect x="291" y="72" width="37" height="36" fill="#3a2b17" stroke="#ed9d42"/>
    <circle cx="268" cy="86" r="5" fill="#1a2429" stroke="#8fb7c1"/>
    <path d="M299 86h8v-5l8 5-8 5v-4h-8z" fill="#ed9d42"/>
    <path d="M361 86h-8v5l-8-5 8-5v4h8z" fill="#66aee9"/>

    <rect x="122" y="116" width="252" height="39" fill="url(#panel)" stroke="#29434f"/>
    <rect x="186" y="124" width="20" height="20" fill="#071014" stroke="#50636b"/>
    <rect x="210" y="124" width="20" height="20" fill="#071014" stroke="#50636b"/>
    <rect x="242" y="124" width="128" height="20" fill="#09161c" stroke="#29434f"/>

    <rect x="5" y="164" width="374" height="75" fill="#071014" stroke="#3a5967"/>
    <g fill="#0a1114" stroke="#536067">
      <rect x="110" y="168" width="18" height="18"/><rect x="128" y="168" width="18" height="18"/>
      <rect x="146" y="168" width="18" height="18"/><rect x="164" y="168" width="18" height="18"/>
      <rect x="182" y="168" width="18" height="18"/><rect x="200" y="168" width="18" height="18"/>
      <rect x="218" y="168" width="18" height="18"/><rect x="236" y="168" width="18" height="18"/>
      <rect x="254" y="168" width="18" height="18"/>
      <rect x="110" y="186" width="18" height="18"/><rect x="128" y="186" width="18" height="18"/>
      <rect x="146" y="186" width="18" height="18"/><rect x="164" y="186" width="18" height="18"/>
      <rect x="182" y="186" width="18" height="18"/><rect x="200" y="186" width="18" height="18"/>
      <rect x="218" y="186" width="18" height="18"/><rect x="236" y="186" width="18" height="18"/>
      <rect x="254" y="186" width="18" height="18"/>
      <rect x="110" y="204" width="18" height="18"/><rect x="128" y="204" width="18" height="18"/>
      <rect x="146" y="204" width="18" height="18"/><rect x="164" y="204" width="18" height="18"/>
      <rect x="182" y="204" width="18" height="18"/><rect x="200" y="204" width="18" height="18"/>
      <rect x="218" y="204" width="18" height="18"/><rect x="236" y="204" width="18" height="18"/>
      <rect x="254" y="204" width="18" height="18"/>
      <rect x="110" y="222" width="18" height="18"/><rect x="128" y="222" width="18" height="18"/>
      <rect x="146" y="222" width="18" height="18"/><rect x="164" y="222" width="18" height="18"/>
      <rect x="182" y="222" width="18" height="18"/><rect x="200" y="222" width="18" height="18"/>
      <rect x="218" y="222" width="18" height="18"/><rect x="236" y="222" width="18" height="18"/>
      <rect x="254" y="222" width="18" height="18"/>
    </g>
  </g>
</svg>`);

const text = [
  { x: 31, y: 20, text: "天穹物流节点", color: "#ffe59a", scale: 2 },
  { x: 71, y: 7, text: "线路名", color: "#8fb7c1", scale: 1 },
  { x: 118, y: 22, text: "主网络", color: "#e8fbff", scale: 2 },
  { x: 225, y: 7, text: "线路序号", color: "#8fb7c1", scale: 1 },
  { x: 274, y: 22, text: "3/8", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 302, y: 22, text: "|<", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 319, y: 22, text: "<", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 336, y: 22, text: ">+", color: "#68d7e5", scale: 1, anchor: "middle" },
  { x: 353, y: 22, text: ">|", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 371, y: 22, text: "×", color: "#ffe59a", scale: 2, anchor: "middle" },

  { x: 9, y: 39, text: "连接面", color: "#68d7e5", scale: 1 },
  { x: 13, y: 50, text: "上", color: "#e8fbff", scale: 2 },
  { x: 31, y: 53, text: "天穹节点", color: "#e8fbff", scale: 1 },
  { x: 31, y: 68, text: "无", color: "#8fb7c1", scale: 1 },
  { x: 65, y: 50, text: "下", color: "#e8fbff", scale: 2 },
  { x: 83, y: 53, text: "天穹石", color: "#e8fbff", scale: 1 },
  { x: 83, y: 68, text: "抽取", color: "#ed9d42", scale: 1 },
  { x: 13, y: 89, text: "北", color: "#68d7e5", scale: 2 },
  { x: 31, y: 92, text: "物品管道", color: "#e8fbff", scale: 1 },
  { x: 31, y: 107, text: "存入", color: "#66aee9", scale: 1 },
  { x: 65, y: 89, text: "南", color: "#e8fbff", scale: 2 },
  { x: 83, y: 92, text: "流体管道", color: "#e8fbff", scale: 1 },
  { x: 83, y: 107, text: "无", color: "#8fb7c1", scale: 1 },
  { x: 13, y: 128, text: "西", color: "#e8fbff", scale: 2 },
  { x: 31, y: 131, text: "流体管道", color: "#e8fbff", scale: 1 },
  { x: 31, y: 146, text: "存入", color: "#66aee9", scale: 1 },
  { x: 65, y: 128, text: "东", color: "#e8fbff", scale: 2 },
  { x: 83, y: 131, text: "能量管道", color: "#e8fbff", scale: 1 },
  { x: 83, y: 146, text: "抽取", color: "#ed9d42", scale: 1 },

  { x: 122, y: 48, text: "北面：物品管道", color: "#68d7e5", scale: 2 },
  { x: 327, y: 52, text: "基础", color: "#68d7e5", scale: 1, anchor: "middle" },
  { x: 360, y: 52, text: "更多", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 126, y: 68, text: "资源", color: "#8fb7c1", scale: 1 },
  { x: 144, y: 106, text: "物品", color: "#68d7e5", scale: 1, anchor: "middle" },
  { x: 182, y: 106, text: "流体", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 220, y: 106, text: "能量", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 250, y: 68, text: "模式", color: "#8fb7c1", scale: 1 },
  { x: 268, y: 106, text: "无", color: "#8fb7c1", scale: 1, anchor: "middle" },
  { x: 309, y: 106, text: "抽取", color: "#ed9d42", scale: 1, anchor: "middle" },
  { x: 350, y: 106, text: "存入", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 126, y: 128, text: "升级插槽", color: "#8fb7c1", scale: 1 },
  { x: 246, y: 128, text: "当前配置只作用于北面", color: "#8fb7c1", scale: 1 },
  { x: 9, y: 176, text: "玩家背包", color: "#68d7e5", scale: 2 },
  { x: 9, y: 191, text: "27 格物品栏", color: "#8fb7c1", scale: 1 },
  { x: 9, y: 232, text: "快捷栏", color: "#8fb7c1", scale: 1 },
];

async function sprite(file, x, y) {
  return { input: await sharp(file).png().toBuffer(), left: x, top: y };
}

(async () => {
  const sprites = [
    await sprite(texture("block", "sky_node.png"), 9, 8),
    await sprite(texture("block", "sky_node.png"), 13, 53),
    await sprite(texture("block", "celestial_stone.png"), 65, 53),
    await sprite(texture("block", "simple_item_pipe.png"), 13, 92),
    await sprite(texture("block", "simple_fluid_pipe.png"), 65, 92),
    await sprite(texture("block", "simple_fluid_pipe.png"), 13, 131),
    await sprite(texture("block", "simple_energy_pipe.png"), 65, 131),
    await sprite(texture("block", "simple_item_pipe.png"), 136, 77),
    await sprite(texture("block", "simple_fluid_pipe.png"), 174, 77),
    await sprite(texture("block", "simple_energy_pipe.png"), 212, 77),
    await sprite(texture("item", "speed_upgrade.png"), 188, 126),
    await sprite(texture("item", "dimension_upgrade.png"), 212, 126),
  ];
  const background = await sharp(backgroundSvg).composite(sprites).png().toBuffer();
  const background4x = await sharp(background)
    .resize(1536, 976, { kernel: "nearest" })
    .png()
    .toBuffer();
  const temporarySpec = path.join("/tmp", `${prefix}-text.json`);
  fs.writeFileSync(temporarySpec, JSON.stringify(text));

  for (const version of versions) {
    const outDir = path.join(projectRoot, "versions", version, "docs", "gui-mockups");
    fs.mkdirSync(outDir, { recursive: true });
    const backgroundPath = path.join(outDir, `${prefix}-background.png`);
    const background4xPath = path.join(outDir, `${prefix}-background-4x.png`);
    const textPath = path.join(outDir, `${prefix}-text-4x.png`);
    const finalPath = path.join(outDir, `${prefix}-final-4x.png`);
    fs.writeFileSync(backgroundPath, background);
    fs.writeFileSync(background4xPath, background4x);
    cp.execFileSync(process.execPath, [
      path.join(projectRoot, ".codex/skills/mc-gui-two-pass-render/scripts/render_unifont_text.js"),
      "--sharp-module", process.argv[2],
      "--font-zip", fontZip,
      "--spec", temporarySpec,
      "--out", textPath,
    ]);
    const final = await sharp(background4x)
      .composite([{ input: textPath, left: 0, top: 0 }])
      .png()
      .toBuffer();
    fs.writeFileSync(finalPath, final);
    if (conceptPath) {
      fs.copyFileSync(conceptPath, path.join(outDir, `${prefix}-imagegen-reference.png`));
    }
  }
})();
