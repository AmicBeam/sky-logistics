#!/usr/bin/env node

const fs = require("fs");

function parseArgs(argv) {
  const result = {};
  for (let i = 2; i < argv.length; i += 2) {
    const key = argv[i];
    const value = argv[i + 1];
    if (!key?.startsWith("--") || value === undefined) {
      throw new Error(`Invalid argument near ${key || "<end>"}`);
    }
    result[key.slice(2)] = value;
  }
  return result;
}

function requireArg(args, name) {
  if (!args[name]) throw new Error(`Missing --${name}`);
  return args[name];
}

async function main() {
  const args = parseArgs(process.argv);
  const sharp = require(requireArg(args, "sharp-module"));
  const backgroundPath = requireArg(args, "background");
  const textPath = requireArg(args, "text");
  const background4xPath = requireArg(args, "background-4x");
  const finalPath = requireArg(args, "final");

  const backgroundMeta = await sharp(backgroundPath).metadata();
  const textMeta = await sharp(textPath).metadata();
  if (backgroundMeta.width !== 384 || backgroundMeta.height !== 244) {
    throw new Error(`Background must be 384x244, got ${backgroundMeta.width}x${backgroundMeta.height}`);
  }
  if (textMeta.width !== 1536 || textMeta.height !== 976 || !textMeta.hasAlpha) {
    throw new Error(`Text must be transparent 1536x976, got ${textMeta.width}x${textMeta.height}, alpha=${textMeta.hasAlpha}`);
  }

  await sharp(backgroundPath)
    .resize(1536, 976, { kernel: "nearest" })
    .png()
    .toFile(background4xPath);
  await sharp(background4xPath)
    .composite([{ input: textPath, left: 0, top: 0 }])
    .png()
    .toFile(finalPath);

  const background = await sharp(backgroundPath).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const background4x = await sharp(background4xPath).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const text = await sharp(textPath).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const final = await sharp(finalPath).ensureAlpha().raw().toBuffer({ resolveWithObject: true });

  let backgroundNearest4x = true;
  nearestCheck:
  for (let y = 0; y < 244; y++) {
    for (let x = 0; x < 384; x++) {
      const source = (y * 384 + x) * 4;
      for (let dy = 0; dy < 4; dy++) {
        for (let dx = 0; dx < 4; dx++) {
          const target = ((y * 4 + dy) * 1536 + x * 4 + dx) * 4;
          for (let channel = 0; channel < 4; channel++) {
            if (background.data[source + channel] !== background4x.data[target + channel]) {
              backgroundNearest4x = false;
              break nearestCheck;
            }
          }
        }
      }
    }
  }

  let nonTextUntouched = true;
  let textPixelCount = 0;
  for (let index = 0; index < final.data.length; index += 4) {
    if (text.data[index + 3] === 0) {
      for (let channel = 0; channel < 4; channel++) {
        if (final.data[index + channel] !== background4x.data[index + channel]) {
          nonTextUntouched = false;
          break;
        }
      }
    } else {
      textPixelCount++;
    }
  }

  const result = {
    background: `${background.info.width}x${background.info.height}`,
    framebuffer: `${background4x.info.width}x${background4x.info.height}`,
    backgroundNearest4x,
    nonTextUntouched,
    textPixelCount,
  };
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  if (!backgroundNearest4x || !nonTextUntouched) process.exitCode = 1;
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exit(1);
});
