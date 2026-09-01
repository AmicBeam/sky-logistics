import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const outputDirectory = resolve("assets/item-icon-sources/chora-kleis-pixel");

const C = {
  outline: "#102733",
  bronzeDark: "#5c391d",
  bronze: "#9b622c",
  gold: "#e0ad50",
  goldHi: "#fff0a2",
  navy: "#172849",
  blueDark: "#224f7a",
  blue: "#3c91c5",
  cyan: "#79dce9",
  cyanHi: "#b9fff4",
  orangeDark: "#a85b23",
  orange: "#e89a36",
  orangeHi: "#ffd56f",
};

function clamp(value, minimum, maximum) {
  return Math.max(minimum, Math.min(maximum, value));
}

function distanceToSegment(px, py, ax, ay, bx, by) {
  const dx = bx - ax;
  const dy = by - ay;
  const lengthSquared = dx * dx + dy * dy;
  const t = lengthSquared === 0 ? 0 : clamp(((px - ax) * dx + (py - ay) * dy) / lengthSquared, 0, 1);
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

function createCanvas(size) {
  return Array.from({ length: size }, () => Array(size).fill(null));
}

function setPixel(canvas, x, y, color) {
  const size = canvas.length;
  const ix = Math.floor(x);
  const iy = Math.floor(y);
  if (ix > 0 && iy > 0 && ix < size - 1 && iy < size - 1) canvas[iy][ix] = color;
}

function paintDistance(canvas, predicate, colorFor) {
  const size = canvas.length;
  for (let y = 1; y < size - 1; y += 1) {
    for (let x = 1; x < size - 1; x += 1) {
      const px = x + 0.5;
      const py = y + 0.5;
      const value = predicate(px, py, x, y);
      if (value !== false && value !== null && value !== undefined) {
        canvas[y][x] = typeof colorFor === "function" ? colorFor(value, px, py, x, y) : colorFor;
      }
    }
  }
}

function paintCircle(canvas, cx, cy, radius, color) {
  paintDistance(canvas, (x, y) => Math.hypot(x - cx, y - cy) <= radius, color);
}

function paintDiamond(canvas, cx, cy, radius, color) {
  paintDistance(canvas, (x, y) => Math.abs(x - cx) + Math.abs(y - cy) <= radius, color);
}

function paintSegment(canvas, ax, ay, bx, by, radius, color) {
  paintDistance(canvas, (x, y) => distanceToSegment(x, y, ax, ay, bx, by) <= radius, color);
}

function generate(size, noOutline = false) {
  if (size === 16) return generate16(noOutline);
  const canvas = createCanvas(size);
  const neutralEdge = noOutline ? C.bronzeDark : C.outline;
  const scale = size / 64;
  const u = { x: Math.SQRT1_2, y: -Math.SQRT1_2 };
  const p = { x: Math.SQRT1_2, y: Math.SQRT1_2 };
  const center = { x: 44 * scale, y: 20 * scale };
  const outer = size === 32 ? 8.0 : 14 * scale;
  const inner = size === 32 ? 4.45 : 8.4 * scale;
  const gapHalfWidth = Math.max(0.8, 1.45 * scale);
  const gripStart = { x: 7.2 * scale, y: 56.8 * scale };
  const gripEnd = { x: 19.5 * scale, y: 44.5 * scale };
  const connector = {
    x: center.x - u.x * (outer - 0.5 * scale),
    y: center.y - u.y * (outer - 0.5 * scale),
  };

  // Long diagonal silhouette and lower-left leather grip.
  paintSegment(canvas, gripStart.x, gripStart.y, connector.x, connector.y,
    Math.max(1.15, 2.55 * scale), neutralEdge);
  paintSegment(canvas, gripEnd.x, gripEnd.y, connector.x, connector.y,
    Math.max(0.7, 1.65 * scale), C.bronzeDark);
  paintSegment(canvas, gripEnd.x, gripEnd.y, connector.x, connector.y,
    Math.max(0.35, 0.75 * scale), C.gold);
  paintSegment(canvas, gripStart.x, gripStart.y, gripEnd.x, gripEnd.y,
    Math.max(1.1, 2.15 * scale), C.navy);

  // Leather wrap bands are size-specific clusters rather than a scaled texture.
  const wrapCount = size >= 64 ? 7 : size >= 32 ? 3 : 2;
  for (let index = 1; index < wrapCount; index += 1) {
    const t = index / wrapCount;
    const cx = gripStart.x + (gripEnd.x - gripStart.x) * t;
    const cy = gripStart.y + (gripEnd.y - gripStart.y) * t;
    paintSegment(canvas, cx - p.x * 2.2 * scale, cy - p.y * 2.2 * scale,
      cx + p.x * 2.2 * scale, cy + p.y * 2.2 * scale,
      Math.max(0.35, 0.55 * scale), C.blueDark);
  }

  // Pommel and centered connector.
  paintDiamond(canvas, gripStart.x - u.x * 1.4 * scale, gripStart.y - u.y * 1.4 * scale,
    Math.max(1.3, 2.7 * scale), neutralEdge);
  paintDiamond(canvas, gripStart.x - u.x * 1.4 * scale, gripStart.y - u.y * 1.4 * scale,
    Math.max(0.8, 1.7 * scale), C.gold);
  paintDiamond(canvas, connector.x, connector.y, Math.max(1.4, 3.5 * scale), neutralEdge);
  paintDiamond(canvas, connector.x, connector.y, Math.max(0.9, 2.5 * scale),
    size === 32 ? C.gold : C.bronze);
  if (size >= 64) {
    paintDiamond(canvas, connector.x - 0.6 * scale, connector.y - 0.6 * scale,
      1.1 * scale, C.goldHi);
  }

  // Two open circular arcs. The gap stripe lies exactly on the wand axis.
  for (let y = 1; y < size - 1; y += 1) {
    for (let x = 1; x < size - 1; x += 1) {
      const px = x + 0.5;
      const py = y + 0.5;
      const dx = px - center.x;
      const dy = py - center.y;
      const radius = Math.hypot(dx, dy);
      const cross = dx * p.x + dy * p.y;
      if (radius < inner || radius > outer || Math.abs(cross) < gapHalfWidth) continue;

      const blueSide = cross < 0;
      const edgeDistance = Math.min(outer - radius, radius - inner);
      const edgeWidth = size === 32 ? 0.42 : Math.max(0.65, 1.2 * scale);
      const outerFrameWidth = size === 32 ? 1.25 : 3.1 * scale;
      const outerGoldWidth = size === 32 ? 0.72 : 2.0 * scale;
      const energyWidth = size === 32 ? 1.35 : 2.4 * scale;
      let color;
      if (edgeDistance < edgeWidth) color = noOutline
        ? (blueSide ? C.blueDark : C.orangeDark)
        : C.outline;
      else if (size === 32 && radius < inner + energyWidth) {
        color = blueSide ? C.cyan : C.orangeHi;
      } else if (size === 32) {
        color = blueSide ? C.blue : C.orange;
      } else if (radius > outer - outerFrameWidth) color = radius > outer - outerGoldWidth ? C.gold : C.bronze;
      else if (radius < inner + energyWidth) {
        color = blueSide ? C.cyan : C.orangeHi;
      } else {
        color = blueSide ? C.blueDark : C.orangeDark;
      }
      canvas[y][x] = color;
    }
  }

  // The engraved meander survives only at 64. At 32 it reads as noise, so the
  // uninterrupted gold frame itself carries the Greek ornamental character.
  if (size >= 64) {
    for (let y = 1; y < size - 1; y += 1) {
      for (let x = 1; x < size - 1; x += 1) {
        const dx = x + 0.5 - center.x;
        const dy = y + 0.5 - center.y;
        const radius = Math.hypot(dx, dy);
        const cross = dx * p.x + dy * p.y;
        if (Math.abs(cross) < gapHalfWidth * 1.4) continue;
        const angle = Math.atan2(dy, dx) + Math.PI;
        const angularCell = Math.floor(angle * (size >= 64 ? 7 : 4));
        if (radius > inner + 3.1 * scale && radius < outer - 3.7 * scale
            && (angularCell + Math.floor(radius / Math.max(1, 1.7 * scale))) % 4 === 0) {
          canvas[y][x] = C.gold;
        }
      }
    }
  }

  // Paired side hubs and inward wards, mirror-balanced across the wand axis.
  const hubRadius = (inner + outer) * 0.5;
  for (const side of [-1, 1]) {
    const hub = {
      x: center.x + p.x * hubRadius * side,
      y: center.y + p.y * hubRadius * side,
    };
    const accent = side < 0 ? C.cyan : C.orangeHi;
    const accentDark = side < 0 ? C.blue : C.orange;
    if (size >= 64) {
      paintCircle(canvas, hub.x, hub.y, 3.1 * scale, neutralEdge);
      paintCircle(canvas, hub.x, hub.y, 2.05 * scale, C.gold);
      paintCircle(canvas, hub.x, hub.y, 1.05 * scale, accent);
      paintSegment(canvas, hub.x, hub.y,
        hub.x - p.x * side * 5.0 * scale,
        hub.y - p.y * side * 5.0 * scale,
        Math.max(0.45, 0.75 * scale), accentDark);
      paintDiamond(canvas,
        hub.x - p.x * side * 4.8 * scale,
        hub.y - p.y * side * 4.8 * scale,
        Math.max(0.65, 1.15 * scale), accent);
    } else {
      // At 32, retain the concept-art hub as a single symmetric 3x3 jewel,
      // a three-step inward spear, and a two-step outward key tooth.
      // Pixel centers must mirror across x+y=32. Draw the blue center first;
      // its exact reflected orange coordinate is (31-y, 31-x).
      const cx = side < 0 ? 18 : 25;
      const cy = side < 0 ? 6 : 13;
      const outward = side;
      const materialEdge = side < 0 ? C.blueDark : C.orangeDark;
      paintDiamond(canvas, cx + 0.5, cy + 0.5, 2.2, materialEdge);
      paintDiamond(canvas, cx + 0.5, cy + 0.5, 1.25, C.gold);
      setPixel(canvas, cx, cy, accent);

      // Outward tooth and inward spear share the hub's exact 180-degree mirror.
      setPixel(canvas, cx + outward * 2, cy + outward * 2, C.gold);
      setPixel(canvas, cx + outward * 3, cy + outward * 3, materialEdge);
      setPixel(canvas, cx - outward * 2, cy - outward * 2, materialEdge);
      setPixel(canvas, cx - outward * 3, cy - outward * 3, accent);
    }
  }

  // Intentional top-left highlights, reduced at small sizes.
  if (size >= 64) setPixel(canvas, center.x - outer * 0.55, center.y - outer * 0.72, C.goldHi);
  if (size >= 64) setPixel(canvas, gripEnd.x - 1, gripEnd.y - 1, C.goldHi);
  if (size >= 64) setPixel(canvas, center.x - outer * 0.78, center.y - outer * 0.2, C.cyanHi);

  return canvas;
}

function generate16(noOutline = false) {
  const canvas = createCanvas(16);
  const put = (color, coordinates) => {
    for (const [x, y] of coordinates) setPixel(canvas, x, y, color);
  };

  // Use the vanilla diamond-pickaxe handle grammar: a three-pixel staircase
  // with two dark edge cells and one alternating highlight cell. Preserve the
  // 32px wand's navy grip and bronze/gold shaft instead of borrowing its colors.
  const gripEdge = noOutline ? C.navy : C.outline;
  const shaftEdge = noOutline ? C.bronzeDark : C.outline;
  put(gripEdge, [
    [1, 13], [2, 13], [3, 13], [2, 12], [3, 12], [4, 12],
    [3, 11], [4, 11], [5, 11], [4, 10], [5, 10], [6, 10],
  ]);
  put(C.blueDark, [[2, 13], [3, 12], [4, 11], [5, 10]]);
  put(shaftEdge, [
    [5, 9], [6, 9], [7, 9], [6, 8], [7, 8], [8, 8],
    [7, 7], [8, 7], [9, 7],
  ]);
  put(C.bronze, [[6, 9], [8, 7]]);
  put(C.gold, [[7, 8]]);
  put(C.bronzeDark, [[1, 14]]);
  put(C.gold, [[2, 14]]);

  // At 16px the two arc gaps collapse into color transitions. Preserve the
  // complete circular silhouette and its readable inner hole; a literal open
  // north-east gap makes the head read as pliers rather than a ritual ring.
  const blueRing = [
    [9, 1], [10, 1], [11, 1], [8, 2], [9, 2], [7, 3], [8, 3],
    [7, 4], [8, 4], [7, 5], [8, 5], [8, 6], [9, 6],
  ];
  const orangeRing = [
    [12, 1], [12, 2], [13, 2], [13, 3], [14, 3], [13, 4], [14, 4],
    [13, 5], [14, 5], [12, 6], [13, 6], [9, 7], [10, 7], [11, 7], [12, 7],
  ];
  put(noOutline ? C.blueDark : C.outline, blueRing);
  put(C.blue, [[9, 1], [8, 2], [7, 3], [7, 4], [7, 5], [8, 6]]);
  put(C.cyan, [[10, 1], [9, 2], [8, 3], [8, 4], [8, 5], [9, 6]]);

  put(noOutline ? C.orangeDark : C.outline, orangeRing);
  put(C.orange, [[13, 2], [14, 3], [14, 4], [14, 5], [13, 6], [12, 7]]);
  put(C.orangeHi, [[12, 2], [13, 3], [13, 4], [13, 5], [12, 6], [11, 7]]);

  // Gold transition cells preserve the 32px structural band without breaking the loop.
  put(C.gold, [[11, 1], [10, 7]]);

  // Preserve every identifiable head detail as a minimal cluster: each arc
  // keeps one outward key tooth, one gold-set jewel, and one inward ward tip.
  put(C.blueDark, [[7, 1]]);
  put(C.gold, [[8, 2], [9, 3]]);
  put(C.cyanHi, [[10, 3]]);
  put(C.cyan, [[10, 4]]);

  put(C.orangeDark, [[14, 7]]);
  put(C.gold, [[13, 6], [12, 5]]);
  put(C.goldHi, [[11, 5]]);
  put(C.orangeHi, [[11, 4]]);
  return canvas;
}

function canvasToSvg(canvas) {
  const size = canvas.length;
  const runs = [];
  for (let y = 0; y < size; y += 1) {
    let start = 0;
    while (start < size) {
      const color = canvas[y][start];
      if (color === null) {
        start += 1;
        continue;
      }
      let end = start + 1;
      while (end < size && canvas[y][end] === color) end += 1;
      runs.push(`  <rect x="${start}" y="${y}" width="${end - start}" height="1" fill="${color}"/>`);
      start = end;
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges">\n${runs.join("\n")}\n</svg>\n`;
}

await mkdir(outputDirectory, { recursive: true });
for (const size of [64, 32, 16]) {
  const output = resolve(outputDirectory, `chora_kleis_${size}.svg`);
  await writeFile(output, canvasToSvg(generate(size)));
  console.log(output);
  const noOutlineOutput = resolve(outputDirectory, `chora_kleis_${size}_no_outline.svg`);
  await writeFile(noOutlineOutput, canvasToSvg(generate(size, true)));
  console.log(noOutlineOutput);
}
