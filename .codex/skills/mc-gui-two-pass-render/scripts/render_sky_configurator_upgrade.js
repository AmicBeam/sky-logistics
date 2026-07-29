const fs = require("fs");
const path = require("path");
const cp = require("child_process");
const sharp = require(process.argv[2]);

const projectRoot = process.argv[3];
const fontZip = process.argv[4];
const conceptPath = process.argv[5];
const versions = ["1.20.1", "1.21.1", "26.1.2"];
const prefix = "configurator-ae2-mek-upgrade";

const asset = (...parts) => path.join(
  projectRoot,
  "common/src/main/resources/assets/skylogistics/textures",
  ...parts,
);

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
    <rect x="105" y="7" width="125" height="18" fill="#03080a" stroke="#50636b"/>
    <rect x="268" y="7" width="36" height="18" fill="#03080a" stroke="#50636b"/>
    <g fill="url(#button)" stroke="#4c6876">
      <rect x="307" y="7" width="13" height="18"/><rect x="322" y="7" width="13" height="18"/>
      <rect x="337" y="7" width="13" height="18"/><rect x="352" y="7" width="13" height="18"/>
    </g>
    <rect x="367" y="7" width="13" height="18" fill="#453719" stroke="#c99d38"/>

    <rect x="5" y="34" width="374" height="27" fill="url(#panel)" stroke="#314b58"/>
    <line x1="129" y1="38" x2="129" y2="57" stroke="#35505d"/>
    <line x1="254" y1="38" x2="254" y2="57" stroke="#35505d"/>
    <circle cx="17" cy="47" r="5" fill="#102934" stroke="#68d7e5"/>
    <rect x="15" y="45" width="4" height="4" fill="#68d7e5"/>
    <path d="M140 47h6v-4l6 4-6 4v-3h-6z" fill="#ed9d42"/>
    <path d="M269 47h6v4l6-4-6-4v3h-6z" fill="#66aee9"/>

    <rect x="5" y="64" width="229" height="175" fill="#071014" stroke="#3a5967"/>
    <rect x="9" y="80" width="221" height="17" fill="#10212a" stroke="#263f4b"/>
    <rect x="9" y="98" width="221" height="27" fill="url(#selected)" stroke="#68d7e5"/>
    <rect x="9" y="126" width="221" height="27" fill="#0a151a" stroke="#1c3039"/>
    <rect x="9" y="154" width="221" height="27" fill="#0d1b21" stroke="#1c3039"/>
    <rect x="9" y="182" width="221" height="27" fill="#0a151a" stroke="#1c3039"/>
    <rect x="9" y="210" width="221" height="25" fill="#0d1b21" stroke="#1c3039"/>
    <rect x="226" y="99" width="3" height="135" fill="#101c21"/>
    <rect x="226" y="100" width="3" height="51" fill="#4cb7c5"/>

    <rect x="237" y="64" width="142" height="175" fill="#071014" stroke="#3a5967"/>
    <rect x="241" y="80" width="134" height="55" fill="url(#panel)" stroke="#29434f"/>
    <g fill="url(#button)" stroke="#455e6a">
      <rect x="245" y="91" width="29" height="39"/><rect x="277" y="91" width="29" height="39"/>
      <rect x="309" y="91" width="29" height="39"/><rect x="341" y="91" width="30" height="39"/>
    </g>
    <rect x="245" y="91" width="29" height="39" fill="url(#selected)" stroke="#68d7e5"/>

    <rect x="241" y="138" width="134" height="34" fill="url(#panel)" stroke="#29434f"/>
    <rect x="245" y="151" width="126" height="16" fill="url(#button)" stroke="#4c6876"/>
    <circle cx="253" cy="159" r="3" fill="#a85454"/><path d="M364 157h4l-2 3z" fill="#a9bdc4"/>

    <rect x="241" y="175" width="134" height="27" fill="url(#panel)" stroke="#29434f"/>
    <rect x="245" y="184" width="20" height="14" fill="url(#button)" stroke="#4c6876"/>
    <rect x="268" y="184" width="79" height="14" fill="#03080a" stroke="#50636b"/>
    <rect x="350" y="184" width="21" height="14" fill="url(#button)" stroke="#4c6876"/>

    <rect x="241" y="205" width="134" height="30" fill="url(#panel)" stroke="#29434f"/>
    <rect x="245" y="216" width="20" height="14" fill="url(#button)" stroke="#4c6876"/>
    <rect x="268" y="216" width="79" height="14" fill="#03080a" stroke="#50636b"/>
    <rect x="350" y="216" width="21" height="14" fill="url(#button)" stroke="#4c6876"/>
  </g>
</svg>`);

const text = [
  { x: 31, y: 20, text: "天穹配置器", color: "#ffe59a", scale: 2 },
  { x: 106, y: 7, text: "线路名称", color: "#8fb7c1", scale: 1 },
  { x: 111, y: 22, text: "主网络", color: "#e8fbff", scale: 2 },
  { x: 234, y: 7, text: "线路序号", color: "#8fb7c1", scale: 1 },
  { x: 286, y: 22, text: "3 / 8", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 314, y: 22, text: "|<", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 329, y: 22, text: "<", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 344, y: 22, text: ">+", color: "#68d7e5", scale: 1, anchor: "middle" },
  { x: 359, y: 22, text: ">|", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 374, y: 22, text: "×", color: "#ffe59a", scale: 2, anchor: "middle" },

  { x: 27, y: 44, text: "节点数", color: "#8fb7c1", scale: 1 },
  { x: 27, y: 57, text: "5", color: "#68d7e5", scale: 2 },
  { x: 157, y: 44, text: "抽取数", color: "#8fb7c1", scale: 1 },
  { x: 157, y: 57, text: "2", color: "#ed9d42", scale: 2 },
  { x: 286, y: 44, text: "存入数", color: "#8fb7c1", scale: 1 },
  { x: 286, y: 57, text: "3", color: "#66aee9", scale: 2 },

  { x: 10, y: 76, text: "线路连接面", color: "#68d7e5", scale: 2 },
  { x: 229, y: 76, text: "1–5 / 8", color: "#8fb7c1", scale: 1, anchor: "end" },
  { x: 35, y: 93, text: "目标", color: "#8fb7c1", scale: 1, anchor: "middle" },
  { x: 114, y: 93, text: "模式", color: "#8fb7c1", scale: 1, anchor: "middle" },
  { x: 148, y: 93, text: "资源", color: "#8fb7c1", scale: 1, anchor: "middle" },
  { x: 184, y: 93, text: "优先", color: "#8fb7c1", scale: 1, anchor: "middle" },
  { x: 214, y: 93, text: "红石", color: "#8fb7c1", scale: 1, anchor: "middle" },

  { x: 30, y: 108, text: "天穹节点", color: "#e8fbff", scale: 2 },
  { x: 30, y: 121, text: "0,64,0 · 主世界", color: "#8fb7c1", scale: 1 },
  { x: 108, y: 115, text: "存入", color: "#66aee9", scale: 1 },
  { x: 144, y: 115, text: "物品", color: "#e8fbff", scale: 1 },
  { x: 184, y: 115, text: "1", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 207, y: 115, text: "忽略", color: "#e8fbff", scale: 1 },

  { x: 30, y: 136, text: "天穹石", color: "#e8fbff", scale: 2 },
  { x: 30, y: 149, text: "12,64,-8 · 主世界", color: "#8fb7c1", scale: 1 },
  { x: 108, y: 143, text: "抽取", color: "#ed9d42", scale: 1 },
  { x: 144, y: 143, text: "流体", color: "#e8fbff", scale: 1 },
  { x: 184, y: 143, text: "2", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 207, y: 143, text: "忽略", color: "#e8fbff", scale: 1 },

  { x: 30, y: 164, text: "物品管道", color: "#e8fbff", scale: 2 },
  { x: 30, y: 177, text: "-6,63,14 · 主世界", color: "#8fb7c1", scale: 1 },
  { x: 108, y: 171, text: "存入", color: "#66aee9", scale: 1 },
  { x: 144, y: 171, text: "能量", color: "#e8fbff", scale: 1 },
  { x: 184, y: 171, text: "3", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 205, y: 171, text: "高信号", color: "#e8fbff", scale: 1 },

  { x: 30, y: 192, text: "流体管道", color: "#e8fbff", scale: 2 },
  { x: 30, y: 205, text: "20,70,-3 · 主世界", color: "#8fb7c1", scale: 1 },
  { x: 108, y: 199, text: "抽取", color: "#ed9d42", scale: 1 },
  { x: 141, y: 199, text: "自动", color: "#e8fbff", scale: 1 },
  { x: 184, y: 199, text: "4", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 205, y: 199, text: "低信号", color: "#e8fbff", scale: 1 },

  { x: 30, y: 220, text: "能量管道", color: "#e8fbff", scale: 2 },
  { x: 30, y: 233, text: "-16,64,22 · 主世界", color: "#8fb7c1", scale: 1 },
  { x: 108, y: 227, text: "存入", color: "#66aee9", scale: 1 },
  { x: 144, y: 227, text: "物品", color: "#e8fbff", scale: 1 },
  { x: 184, y: 227, text: "5", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 207, y: 227, text: "忽略", color: "#e8fbff", scale: 1 },

  { x: 242, y: 76, text: "所选连接面配置", color: "#68d7e5", scale: 2 },
  { x: 245, y: 88, text: "资源", color: "#8fb7c1", scale: 1 },
  { x: 259, y: 128, text: "物品", color: "#68d7e5", scale: 1, anchor: "middle" },
  { x: 291, y: 128, text: "流体", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 323, y: 128, text: "能量", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 356, y: 128, text: "自动", color: "#e8fbff", scale: 1, anchor: "middle" },
  { x: 245, y: 147, text: "红石", color: "#8fb7c1", scale: 1 },
  { x: 260, y: 164, text: "忽略", color: "#e8fbff", scale: 2 },
  { x: 245, y: 181, text: "槽数", color: "#8fb7c1", scale: 1 },
  { x: 255, y: 197, text: "−", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 308, y: 197, text: "不限制", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 360, y: 197, text: "+", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 245, y: 212, text: "优先级", color: "#8fb7c1", scale: 1 },
  { x: 255, y: 229, text: "−", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 308, y: 229, text: "0", color: "#e8fbff", scale: 2, anchor: "middle" },
  { x: 360, y: 229, text: "+", color: "#e8fbff", scale: 2, anchor: "middle" },
];

async function sprite(file, x, y) {
  return { input: await sharp(file).png().toBuffer(), left: x, top: y };
}

(async () => {
  const sprites = [
    await sprite(asset("item", "configurator.png"), 9, 8),
    await sprite(asset("block", "sky_node.png"), 11, 104),
    await sprite(asset("block", "celestial_stone.png"), 11, 132),
    await sprite(asset("block", "simple_item_pipe.png"), 11, 160),
    await sprite(asset("block", "simple_fluid_pipe.png"), 11, 188),
    await sprite(asset("block", "simple_energy_pipe.png"), 11, 216),
    await sprite(asset("block", "simple_item_pipe.png"), 251, 95),
    await sprite(asset("block", "simple_fluid_pipe.png"), 283, 95),
    await sprite(asset("block", "simple_energy_pipe.png"), 315, 95),
    await sprite(asset("block", "sky_node.png"), 348, 95),
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
