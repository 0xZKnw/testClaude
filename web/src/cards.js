// The cards, drawn as SVG.
//
// The Android app paints them on a Compose canvas; here one SVG per card gives the same
// result and scales to any width without going soft. Same palette, same proportions,
// same ink keyline round every symbol — the outline is what makes them read as cartoon
// rather than as flat vector shapes.

import { Color, Kind } from './engine.js';

export const PALETTE = {
  outline: '#14161d',
  stock: '#fdfbf4',
  gold: '#ffc531',
  R: '#f23b2e', Rd: '#c01c12',
  Y: '#ffc21a', Yd: '#dc9200',
  G: '#41c258', Gd: '#259a3c',
  B: '#2e9cf2', Bd: '#1668c4',
  W: '#262c39', Wd: '#151a24',
};

/** Ink used for the glyph sitting inside the white oval. */
const GLYPH_INK = { R: '#c01c12', Y: '#dc9200', G: '#259a3c', B: '#1668c4', W: '#2b3240' };

export const CARD_W = 100;
export const CARD_H = 152;

const svgEscape = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');

/** Two overlapping cards for a +2, four for a +4 — upright, splayed like a hand. */
function miniCards(colors, cx, cy, boxW) {
  const pad = boxW * 0.06;
  const span = boxW - pad * 2;
  const w = span * (colors.length > 2 ? 0.4 : 0.58);
  const h = w * 1.45;
  const step = colors.length > 1 ? (span - w) / (colors.length - 1) : 0;
  const spread = colors.length > 2 ? 8 : 11;
  const middle = (colors.length - 1) / 2;
  const border = w * 0.15;
  return colors.map((color, i) => {
    const x = cx - boxW / 2 + pad + step * i;
    const y = cy - h / 2;
    const angle = (i - middle) * spread;
    return `<g transform="rotate(${angle} ${x + w / 2} ${cy})">
      <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${w * 0.22}" fill="${PALETTE.outline}"/>
      <rect x="${x + border}" y="${y + border}" width="${w - border * 2}" height="${h - border * 2}"
            rx="${w * 0.18}" fill="${color}"/>
    </g>`;
  }).join('');
}

/** The four-colour pinwheel printed on a Wild. */
function wheel(cx, cy, r) {
  const q = (a, b, color) => {
    const p = (deg) => [cx + r * Math.cos(deg * Math.PI / 180), cy + r * Math.sin(deg * Math.PI / 180)];
    const [x1, y1] = p(a);
    const [x2, y2] = p(b);
    return `<path d="M${cx} ${cy} L${x1} ${y1} A${r} ${r} 0 0 1 ${x2} ${y2} Z" fill="${color}"/>`;
  };
  const spoke = r * 0.13;
  return `<circle cx="${cx}" cy="${cy}" r="${r * 1.13}" fill="${PALETTE.outline}"/>
    ${q(180, 270, PALETTE.R)}${q(270, 360, PALETTE.Y)}${q(0, 90, PALETTE.G)}${q(90, 180, PALETTE.B)}
    <rect x="${cx - r}" y="${cy - spoke / 2}" width="${r * 2}" height="${spoke}" fill="${PALETTE.outline}"/>
    <rect x="${cx - spoke / 2}" y="${cy - r}" width="${spoke}" height="${r * 2}" fill="${PALETTE.outline}"/>`;
}

/** A circle with a bar across it, the bar landing exactly on the ring. */
function skipGlyph(cx, cy, size, color) {
  const stroke = size * 0.15;
  const r = (size - stroke * 1.9) / 2;
  const d = r * 0.7071;
  const ring = (width, tint) => `
    <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${tint}" stroke-width="${width}"/>
    <line x1="${cx - d}" y1="${cy + d}" x2="${cx + d}" y2="${cy - d}"
          stroke="${tint}" stroke-width="${width}"/>`;
  return ring(stroke * 1.9, PALETTE.outline) + ring(stroke, color);
}

/** Two arrows chasing each other; the keyline follows the path so it stays even. */
function reverseGlyph(cx, cy, size, color) {
  const key = size * 0.075;
  const w = size - key * 2;
  const h = size - key * 2;
  const shaft = h * 0.17;
  const head = h * 0.34;
  const gap = h * 0.16;
  const left = cx - w / 2;
  const right = cx + w / 2;
  const startY = cy - (shaft * 2 + gap) / 2;

  const arrow = (topY, rightwards) => {
    const tail = rightwards ? left : right;
    const tip = rightwards ? right : left;
    const neck = rightwards ? tip - head * 0.72 : tip + head * 0.72;
    const midY = topY + shaft / 2;
    const barb = head / 2;
    return `M${tail} ${topY} L${neck} ${topY} L${neck} ${midY - barb} L${tip} ${midY}
            L${neck} ${midY + barb} L${neck} ${topY + shaft} L${tail} ${topY + shaft} Z`;
  };
  const paint = (d) => `<path d="${d}" fill="${color}" stroke="${PALETTE.outline}"
      stroke-width="${key * 2}" stroke-linejoin="round" paint-order="stroke"/>`;
  return paint(arrow(startY, true)) + paint(arrow(startY + shaft + gap, false));
}

function centreMark(card) {
  const cx = CARD_W / 2;
  const cy = CARD_H / 2;
  const ink = GLYPH_INK[card.c];
  switch (card.k) {
    case Kind.NUMBER:
      return `<text x="${cx}" y="${cy}" text-anchor="middle" dominant-baseline="central"
        font-size="64" font-weight="900" font-family="system-ui, sans-serif"
        fill="${ink}" stroke="${PALETTE.outline}" stroke-width="4.5"
        stroke-linejoin="round" paint-order="stroke">${card.n}</text>`;
    case Kind.SKIP: return skipGlyph(cx, cy, 50, ink);
    case Kind.REVERSE: return reverseGlyph(cx, cy, 50, ink);
    case Kind.DRAW_TWO: return miniCards([PALETTE[card.c], PALETTE[card.c]], cx, cy, 52);
    case Kind.WILD: return wheel(cx, cy, 27);
    case Kind.DRAW_FOUR:
      return miniCards([PALETTE.R, PALETTE.B, PALETTE.Y, PALETTE.G], cx, cy, 62);
    default: return '';
  }
}

function cornerMark(card, x, y, flip) {
  const rotate = flip ? `rotate(180 ${x} ${y})` : '';
  const text = (t) => `<text x="${x}" y="${y}" text-anchor="middle" dominant-baseline="central"
    font-size="20" font-weight="900" font-family="system-ui, sans-serif"
    fill="${PALETTE.stock}" stroke="${PALETTE.outline}" stroke-width="2.2"
    stroke-linejoin="round" paint-order="stroke" transform="${rotate}">${t}</text>`;
  switch (card.k) {
    case Kind.NUMBER: return text(card.n);
    case Kind.DRAW_TWO: return text('+2');
    case Kind.DRAW_FOUR: return text('+4');
    case Kind.SKIP: return `<g transform="${rotate}">${skipGlyph(x, y, 20, PALETTE.stock)}</g>`;
    case Kind.REVERSE: return `<g transform="${rotate}">${reverseGlyph(x, y, 20, PALETTE.stock)}</g>`;
    case Kind.WILD: return `<g transform="${rotate}">${wheel(x, y, 9)}</g>`;
    default: return '';
  }
}

/** The face of a card. `dimmed` washes it with a solid dark tint, never transparency. */
export function cardFace(card, { dimmed = false } = {}) {
  const face = PALETTE[card.c];
  const deep = PALETTE[card.c + 'd'];
  const gradient = `g${card.c}`;
  const oval = card.c === Color.WILD ? '' : `
    <g transform="rotate(-22 ${CARD_W / 2} ${CARD_H / 2})">
      <ellipse cx="${CARD_W / 2}" cy="${CARD_H / 2}" rx="47" ry="44" fill="${PALETTE.outline}"/>
      <ellipse cx="${CARD_W / 2}" cy="${CARD_H / 2}" rx="43.5" ry="40.5" fill="${PALETTE.stock}"/>
    </g>`;
  return `<svg class="card-svg" viewBox="0 0 ${CARD_W} ${CARD_H}" xmlns="http://www.w3.org/2000/svg">
    <defs><linearGradient id="${gradient}" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${face}"/><stop offset="1" stop-color="${deep}"/>
    </linearGradient></defs>
    <rect width="${CARD_W}" height="${CARD_H}" rx="13" fill="${PALETTE.outline}"/>
    <rect x="3.5" y="3.5" width="${CARD_W - 7}" height="${CARD_H - 7}" rx="11" fill="${PALETTE.stock}"/>
    <rect x="9.7" y="9.7" width="${CARD_W - 19.4}" height="${CARD_H - 19.4}" rx="8.5"
          fill="url(#${gradient})"/>
    ${oval}
    ${centreMark(card)}
    ${cornerMark(card, 19, 20, false)}
    ${cornerMark(card, CARD_W - 19, CARD_H - 20, true)}
    ${dimmed ? `<rect width="${CARD_W}" height="${CARD_H}" rx="13" fill="#0c1018" opacity="0.62"/>` : ''}
  </svg>`;
}

/** The back of a card: the opponent's hand and the draw pile. */
export function cardBack() {
  return `<svg class="card-svg" viewBox="0 0 ${CARD_W} ${CARD_H}" xmlns="http://www.w3.org/2000/svg">
    <defs><linearGradient id="gback" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#2b3242"/><stop offset="1" stop-color="#161a24"/>
    </linearGradient></defs>
    <rect width="${CARD_W}" height="${CARD_H}" rx="13" fill="${PALETTE.outline}"/>
    <rect x="3.5" y="3.5" width="${CARD_W - 7}" height="${CARD_H - 7}" rx="11" fill="${PALETTE.stock}"/>
    <rect x="9.7" y="9.7" width="${CARD_W - 19.4}" height="${CARD_H - 19.4}" rx="8.5" fill="url(#gback)"/>
    <g transform="rotate(-28 ${CARD_W / 2} ${CARD_H / 2})">
      <ellipse cx="${CARD_W / 2}" cy="${CARD_H / 2}" rx="47" ry="30" fill="${PALETTE.outline}"/>
      <ellipse cx="${CARD_W / 2}" cy="${CARD_H / 2}" rx="43" ry="26" fill="${PALETTE.R}"/>
      <text x="${CARD_W / 2}" y="${CARD_H / 2}" text-anchor="middle" dominant-baseline="central"
        font-size="25" font-weight="900" font-family="system-ui, sans-serif"
        fill="${PALETTE.stock}" stroke="${PALETTE.outline}" stroke-width="2.2"
        stroke-linejoin="round" paint-order="stroke">UNO</text>
    </g>
  </svg>`;
}

/** A flat colour swatch, used by the wild colour picker. */
export function colorChip(color) {
  return `<svg viewBox="0 0 100 100" class="chip-svg" xmlns="http://www.w3.org/2000/svg">
    <rect width="100" height="100" rx="28" fill="${PALETTE.outline}"/>
    <rect x="6" y="6" width="88" height="88" rx="24" fill="${PALETTE[color]}"/>
  </svg>`;
}

export { svgEscape };
