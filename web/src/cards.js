// The cards, drawn as SVG.
//
// The Android app paints them on a Compose canvas; here one SVG per card gives the same
// result and scales to any width without going soft. Same palette, same proportions,
// same ink keyline round every symbol — the outline is what makes them read as cartoon
// rather than as flat vector shapes.

import { Color, Kind } from './engine.js';

import { defaultOf } from './cosmetics.js';

/** What a table with no wardrobe behind it draws. */
const DEFAULT_BACK = defaultOf('BACK');

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

/**
 * The +8: the +4's fan, dealt twice. Two staggered rows rather than eight cards in one
 * row — the point of the card is "twice the +4", and eight slivers side by side would
 * just read as noise.
 */
function wildRows(cx, cy, boxW, rows) {
  const four = [PALETTE.R, PALETTE.B, PALETTE.Y, PALETTE.G];
  const step = boxW * 0.38;
  const shift = boxW * 0.06;
  const first = -(rows - 1) / 2;
  let out = '';
  for (let i = 0; i < rows; i++) {
    out += miniCards(four, cx + shift * (first + i), cy + step * (first + i), boxW * 0.86);
  }
  return out;
}

const wildEight = (cx, cy, boxW) => wildRows(cx, cy, boxW, 2);
/** The +12: the same fan a third time. One card in the deck, and it looks like it. */
const wildTwelve = (cx, cy, boxW) => wildRows(cx, cy, boxW, 3);

/**
 * The Coup double: two blank cards with the multiplier over them. The cards alone would
 * read as a +2, so this is the one glyph that carries lettering — fitting, since it is
 * the one card whose effect is neither "somebody draws" nor "somebody is skipped".
 */
function doublePlay(cx, cy, boxW) {
  // The badge sits in the corner of the fan rather than over its middle: centred, it
  // covered the very cards it is there to count.
  const r = boxW * 0.23;
  const bx = cx + boxW * 0.30;
  const by = cy + boxW * 0.32;
  return `${miniCards([PALETTE.stock, PALETTE.stock], cx, cy, boxW)}
    <circle cx="${bx}" cy="${by}" r="${r}" fill="${PALETTE.outline}"/>
    <circle cx="${bx}" cy="${by}" r="${r * 0.78}" fill="${PALETTE.gold}"/>
    <text x="${bx}" y="${by}" text-anchor="middle" dominant-baseline="central"
      font-size="${r * 1.1}" font-weight="900" font-family="system-ui, sans-serif"
      fill="${PALETTE.outline}">&#215;2</text>`;
}

/**
 * The Espion: an eye whose iris is the Wild pinwheel. It has to say two things at once —
 * "this changes the colour" and "this looks at your hand" — and one emblem carrying both
 * reads faster than a wheel with a badge stuck on it.
 */
function eyeGlyph(cx, cy, boxW) {
  const w = boxW;
  const h = w * 0.62;
  const key = w * 0.055;
  const almond = `M${cx - w / 2} ${cy} Q${cx} ${cy - h} ${cx + w / 2} ${cy}`
    + ` Q${cx} ${cy + h} ${cx - w / 2} ${cy} Z`;
  const iris = w * 0.21;
  return `<path d="${almond}" fill="${PALETTE.stock}" stroke="${PALETTE.outline}"
      stroke-width="${key * 2}" stroke-linejoin="round" paint-order="stroke"/>
    ${wheel(cx, cy, iris)}
    <circle cx="${cx}" cy="${cy}" r="${iris * 0.34}" fill="${PALETTE.outline}"/>`;
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
    case Kind.DRAW_EIGHT: return wildEight(cx, cy, 70);
    case Kind.DOUBLE_PLAY: return doublePlay(cx - 5, cy - 8, 58);
    case Kind.SPY: return eyeGlyph(cx, cy, 66);
    case Kind.DRAW_TWELVE: return wildTwelve(cx, cy, 58);
    case Kind.DRAW_FIFTY: return jackpotGlyph(cx, cy, CARD_W);
    default: return '';
  }
}

/** One jagged strike running from the rim in towards the middle. */
function strike(cx, cy, reach, spread, degrees) {
  const rad = ((degrees - 90) * Math.PI) / 180;
  const nx = Math.cos(rad);
  const ny = Math.sin(rad);
  const px = -ny;
  const py = nx;
  return [[1, 0], [0.68, 0.5], [0.46, -0.35], [0.22, 0.4], [0, 0]]
    .map(([along, side], i) => {
      const x = cx + nx * reach * along + px * spread * side;
      const y = cy + ny * reach * along + py * spread * side;
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
    }).join(' ');
}

/**
 * The +50: the fan buried under a storm.
 *
 * Every other card in the deck is a flat cartoon. This one deliberately breaks that — it
 * is meant to be seen once and remembered — so it gets lightning, a lava core and a "50"
 * the size of the card. The animation rides on CSS classes rather than SMIL, which
 * nothing has to be told to keep running.
 */
function jackpotGlyph(cx, cy, w) {
  const bolts = [0, 1, 2, 3].map((i) => `<path d="${strike(cx, cy, w * 0.6, w * 0.18, i * 90 + 45)}"
    class="jk-bolt jk-bolt${i}" fill="none" stroke="#fff3c4" stroke-width="${w * 0.045}"
    stroke-linecap="round" stroke-linejoin="round"/>`).join('');
  return `<defs>
      <radialGradient id="jk-core">
        <stop offset="0" stop-color="#fff3c4" stop-opacity=".9"/>
        <stop offset="0.55" stop-color="#ff5a1e" stop-opacity=".55"/>
        <stop offset="1" stop-color="#ff5a1e" stop-opacity="0"/>
      </radialGradient>
    </defs>
    <circle class="jk-core" cx="${cx}" cy="${cy}" r="${w * 0.62}" fill="url(#jk-core)"/>
    ${bolts}
    <g transform="translate(0 ${-w * 0.30})">${wildTwelve(cx, cy, 42)}</g>
    <text x="${cx}" y="${cy + w * 0.36}" text-anchor="middle" dominant-baseline="central"
      font-size="${w * 0.34}" font-weight="900" font-family="system-ui, sans-serif"
      fill="#fff3c4" stroke="${PALETTE.outline}" stroke-width="${w * 0.035}"
      stroke-linejoin="round" paint-order="stroke">50</text>`;
}

function cornerMark(card, x, y, flip) {
  const rotate = flip ? `rotate(180 ${x} ${y})` : '';
  const label = (t, fill) => `<text x="${x}" y="${y}" text-anchor="middle" dominant-baseline="central"
    font-size="20" font-weight="900" font-family="system-ui, sans-serif"
    fill="${fill}" stroke="${PALETTE.outline}" stroke-width="2.2"
    stroke-linejoin="round" paint-order="stroke" transform="${rotate}">${t}</text>`;
  const text = (t) => label(t, PALETTE.stock);
  const goldText = (t) => label(t, PALETTE.gold);
  switch (card.k) {
    case Kind.NUMBER: return text(card.n);
    case Kind.DRAW_TWO: return text('+2');
    case Kind.DRAW_FOUR: return text('+4');
    case Kind.DRAW_EIGHT: return text('+8');
    case Kind.DOUBLE_PLAY: return goldText('&#215;2');
    case Kind.SPY: return `<g transform="${rotate}">${eyeGlyph(x, y, 24)}</g>`;
    // A third digit needs a smaller face, or it touches the keyline.
    case Kind.DRAW_TWELVE: return `<text x="${x}" y="${y}" text-anchor="middle"
      dominant-baseline="central" font-size="16.5" font-weight="900"
      font-family="system-ui, sans-serif" fill="${PALETTE.stock}" stroke="${PALETTE.outline}"
      stroke-width="2.2" stroke-linejoin="round" paint-order="stroke"
      transform="${rotate}">+12</text>`;
    case Kind.DRAW_FIFTY: return `<text x="${x}" y="${y}" text-anchor="middle"
      dominant-baseline="central" font-size="16.5" font-weight="900"
      font-family="system-ui, sans-serif" fill="#fff3c4" stroke="${PALETTE.outline}"
      stroke-width="2.2" stroke-linejoin="round" paint-order="stroke"
      transform="${rotate}">+50</text>`;
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
/**
 * The repeating motif printed on a back or woven into a cloth.
 *
 * Faint and repeated, never a picture: a back is on screen a dozen times at once and a
 * cloth sits under the whole game, so a motif that shouted would be unbearable inside
 * one round. Tiles are inline SVG — a strict page has no business fetching an image to
 * draw a card.
 */
const MOTIF_SHAPES = {
  BOLTS: 'M15 3 L7 13 h5 l-3 10 8-12 h-5 z',
  FLAMES: 'M13 3 C9 9 15 10 13 14 C11 12 12 10 10 9 C7 13 6 19 13 22 C20 19 18 10 13 3 z',
  CONFETTI: 'M6 10 l7-4 5 6 -7 4 z',
  CROWNS: 'M5 19 v-11 l4 5 4-7 4 7 4-5 v11 z',
  RAYS: 'M13 0 v26',
  STRIPES: 'M-4 22 L22 -4 M9 35 L35 9',
  DOTS: 'M13 13 m-3 0 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0',
  WAVES: 'M0 16 q6.5 -7 13 0 t13 0',
  HEX: 'M13 1 L25 13 L13 25 L1 13 z',
};

/** The ones that are outlines rather than solids. */
const MOTIF_STROKED = new Set(['RAYS', 'STRIPES', 'WAVES', 'HEX']);

/**
 * The motif as an SVG tile, for painting inside another drawing.
 *
 * It has to live in the same SVG as the card: an HTML layer underneath would be hidden
 * by the card's own artwork, and one on top would cover the UNO.
 */
export function motifPattern(pattern, colour, id) {
  const shape = MOTIF_SHAPES[pattern];
  if (!shape) return { defs: '', fill: '' };
  const paint = MOTIF_STROKED.has(pattern)
    ? `fill="none" stroke="${colour}" stroke-width="1.6"`
    : `fill="${colour}"`;
  return {
    defs: `<pattern id="${id}" width="26" height="26" patternUnits="userSpaceOnUse">`
      + `<path d="${shape}" ${paint} opacity="0.22"/></pattern>`,
    fill: `url(#${id})`,
  };
}

/**
 * The same motif as a standalone layer, for the table cloth — which is a plain div with
 * nothing painted over it, so an HTML layer is both simpler and cheaper there.
 */
export function motifHtml(pattern, colour) {
  if (!pattern || pattern === 'PLAIN') return '';
  const { defs, fill } = motifPattern(pattern, colour, 'p');
  if (!defs) return '';
  const svg = "<svg xmlns='http://www.w3.org/2000/svg' width='100%' height='100%'>"
    + `<defs>${defs.replace(/"/g, "'")}</defs>`
    + `<rect width='100%' height='100%' fill='url(#p)'/></svg>`;
  // Single quotes throughout: this ends up inside a double-quoted style attribute.
  return `<div class="motif" style="background-image:url('data:image/svg+xml,`
    + `${encodeURIComponent(svg)}')"></div>`;
}

/**
 * The back of a card. `skin` is the unlocked back the player picked: only the inner
 * panel and the oval change, because the paper, the ink keyline and the tilted UNO are
 * what make every one of them read as the same deck.
 *
 * The gradient id carries the skin, so the wardrobe can show eighteen different backs
 * on one page without them all borrowing the first one's colours.
 */
export function cardBack(skin = DEFAULT_BACK) {
  const gid = `gback-${skin.id.replace(/[^a-z0-9]/gi, '')}`;
  // The movement rides on top as a plain div, not inside the drawing: a CSS transform on
  // one extra layer costs nothing, while animating the SVG would repaint every card on
  // every frame — and a dozen backs are on screen at once.
  // BLAZE and STORM take their heat from the oval colour, handed to the stylesheet as a
  // variable so the keyframes stay one rule rather than one per skin.
  const hot = skin.motion === 'BLAZE' || skin.motion === 'STORM'
    ? ` style="--hot:${skin.c}${skin.motion === 'BLAZE' ? '7a' : '6b'}"`
    : '';
  const motion = skin.motion && skin.motion !== 'NONE'
    ? `<div class="m-${skin.motion}"${skin.motion === 'DRIFT'
      ? ` style="background:linear-gradient(${skin.c}33, transparent)"` : hot}></div>`
    : '';
  const motif = motifPattern(skin.pattern, skin.c, `pat-${gid}`);
  return `${motion}<svg class="card-svg" viewBox="0 0 ${CARD_W} ${CARD_H}" xmlns="http://www.w3.org/2000/svg">
    <defs><linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${skin.a}"/><stop offset="1" stop-color="${skin.b}"/>
    </linearGradient>${motif.defs}</defs>
    <rect width="${CARD_W}" height="${CARD_H}" rx="13" fill="${PALETTE.outline}"/>
    <rect x="3.5" y="3.5" width="${CARD_W - 7}" height="${CARD_H - 7}" rx="11" fill="${PALETTE.stock}"/>
    <rect x="9.7" y="9.7" width="${CARD_W - 19.4}" height="${CARD_H - 19.4}" rx="8.5" fill="url(#${gid})"/>
    ${motif.fill ? `<rect x="9.7" y="9.7" width="${CARD_W - 19.4}" height="${CARD_H - 19.4}"
      rx="8.5" fill="${motif.fill}"/>` : ''}
    <g transform="rotate(-28 ${CARD_W / 2} ${CARD_H / 2})">
      <!-- Nested rather than combined: a CSS transform would replace the rotate
           attribute outright, and the tilt is what makes the back read as a back. -->
      <g class="${skin.motion === 'PULSE' ? 'm-PULSE' : (skin.motion === 'BLAZE' ? 'm-OVALBLAZE' : '')}"
         style="transform-origin:${CARD_W / 2}px ${CARD_H / 2}px">
      <ellipse cx="${CARD_W / 2}" cy="${CARD_H / 2}" rx="47" ry="30" fill="${PALETTE.outline}"/>
      <ellipse cx="${CARD_W / 2}" cy="${CARD_H / 2}" rx="43" ry="26" fill="${skin.c}"/>
      <text x="${CARD_W / 2}" y="${CARD_H / 2}" text-anchor="middle" dominant-baseline="central"
        font-size="25" font-weight="900" font-family="system-ui, sans-serif"
        fill="${PALETTE.stock}" stroke="${PALETTE.outline}" stroke-width="2.2"
        stroke-linejoin="round" paint-order="stroke">UNO</text>
      </g>
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
