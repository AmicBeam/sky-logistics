#!/usr/bin/env node

const cp = require("child_process");
const fs = require("fs");

function parseArgs(argv) {
  const result = {};
  for (let i = 2; i < argv.length; i += 2) {
    const key = argv[i];
    const value = argv[i + 1];
    if (!key?.startsWith("--") || value === undefined) throw new Error(`Invalid argument near ${key || "<end>"}`);
    result[key.slice(2)] = value;
  }
  return result;
}

function requireArg(args, name) {
  if (!args[name]) throw new Error(`Missing --${name}`);
  return args[name];
}

function loadGlyphs(zipPath) {
  const listing = cp.execFileSync("unzip", ["-Z1", zipPath], { encoding: "utf8" });
  const hexName = listing.split("\n").find((name) => name.endsWith(".hex"));
  if (!hexName) throw new Error("No .hex file found in Unihex zip");
  const contents = cp.execFileSync("unzip", ["-p", zipPath, hexName], {
    encoding: "utf8",
    maxBuffer: 32 * 1024 * 1024,
  });
  const glyphs = new Map();
  for (const line of contents.split("\n")) {
    const colon = line.indexOf(":");
    if (colon > 0) glyphs.set(Number.parseInt(line.slice(0, colon), 16), line.slice(colon + 1).trim());
  }
  return glyphs;
}

function parseGlyph(glyphs, char) {
  const hex = glyphs.get(char.codePointAt(0));
  if (!hex) return null;
  const width = hex.length / 4;
  const digits = width / 4;
  const rows = Array.from({ length: 16 }, (_, row) =>
    Number.parseInt(hex.slice(row * digits, (row + 1) * digits), 16),
  );
  return { width, rows };
}

function layout(glyphs, item) {
  const parts = [];
  let cursor = 0;
  for (const char of [...item.text]) {
    if (char === " ") {
      cursor += 4 * item.scale;
      continue;
    }
    const glyph = parseGlyph(glyphs, char);
    if (!glyph) {
      cursor += 8 * item.scale;
      continue;
    }
    let left = glyph.width;
    let right = -1;
    for (const bits of glyph.rows) {
      for (let col = 0; col < glyph.width; col++) {
        if (bits & (1 << (glyph.width - 1 - col))) {
          left = Math.min(left, col);
          right = Math.max(right, col);
        }
      }
    }
    if (right < left) {
      cursor += 4 * item.scale;
      continue;
    }
    if (char.codePointAt(0) >= 0x3000) {
      left = 0;
      right = glyph.width - 1;
    }
    parts.push({ glyph, left, right, x: cursor });
    cursor += (right - left + 2) * item.scale;
  }
  return { parts, width: Math.max(0, cursor - item.scale) };
}

async function main() {
  const args = parseArgs(process.argv);
  const sharp = require(requireArg(args, "sharp-module"));
  const glyphs = loadGlyphs(requireArg(args, "font-zip"));
  const items = JSON.parse(fs.readFileSync(requireArg(args, "spec"), "utf8"));
  const groups = [];

  for (const rawItem of items) {
    const item = {
      anchor: "start",
      color: "#d8dcde",
      scale: 2,
      ...rawItem,
    };
    if (!Number.isInteger(item.x) || !Number.isInteger(item.y) || ![1, 2].includes(item.scale)) {
      throw new Error(`Invalid logical coordinates or scale for ${JSON.stringify(rawItem)}`);
    }
    if (!["start", "middle", "end"].includes(item.anchor) || typeof item.text !== "string") {
      throw new Error(`Invalid anchor or text for ${JSON.stringify(rawItem)}`);
    }
    const textLayout = layout(glyphs, item);
    let x = item.x * 4;
    if (item.anchor === "middle") x -= Math.round(textLayout.width / 2);
    if (item.anchor === "end") x -= textLayout.width;
    const y = item.y * 4 - 14 * item.scale;
    const rects = [];
    for (const part of textLayout.parts) {
      for (let row = 0; row < 16; row++) {
        for (let col = part.left; col <= part.right; col++) {
          if (part.glyph.rows[row] & (1 << (part.glyph.width - 1 - col))) {
            rects.push(`<rect x="${x + part.x + (col - part.left) * item.scale}" y="${y + row * item.scale}" width="${item.scale}" height="${item.scale}"/>`);
          }
        }
      }
    }
    groups.push(`<g fill="${item.color}" shape-rendering="crispEdges">${rects.join("")}</g>`);
  }

  const svg = Buffer.from(
    `<svg xmlns="http://www.w3.org/2000/svg" width="1536" height="976" viewBox="0 0 1536 976">${groups.join("")}</svg>`,
  );
  await sharp(svg).png().toFile(requireArg(args, "out"));
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exit(1);
});
