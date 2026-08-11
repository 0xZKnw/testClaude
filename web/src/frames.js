// The ring that goes round an avatar, drawn rather than gradient-faked.
//
// The first version built every frame out of repeating-conic-gradient, which is fine for
// a two-tone band and hopeless for anything with a shape: fire came out as a cog, the
// crown came out as the same cog in gold, and the stars were tick marks. Anything with a
// name — fire, lightning, water, an orbit, stars, a crown — is now an actual path.
//
// The geometry is the same arithmetic as the Android app's AvatarFrame.kt, in the same
// units, so a player wearing Enfer sees the same fire on either. The ring sits *outside*
// the avatar, so putting one on never shrinks the face.

/** Ink keyline colour, matching the palette the cards are drawn with. */
const INK = '#14161d';

const TAU = Math.PI * 2;
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

/** A point on a circle, angles measured from twelve o'clock and running clockwise. */
function onRing(cx, cy, r, deg) {
  const a = ((deg - 90) * Math.PI) / 180;
  return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
}

/**
 * A fixed scatter in 0..1 for index [i]. Not randomness: the same frame has to look the
 * same on every repaint, and the phone has to draw the same fire from the same formula.
 */
const jitter = (i) => {
  const v = Math.sin(i * 12.9898) * 43758.5453;
  return v - Math.floor(v);
};

const round2 = (n) => Math.round(n * 100) / 100;
const point = (p) => `${round2(p[0])} ${round2(p[1])}`;

/**
 * One lick of flame — or one point of a crown, which is the same shape with straighter
 * sides. Sampled along the outline rather than curved: at forty pixels across the
 * difference is invisible, and the same sampling runs on the phone.
 *
 * [half] is the half-width **in degrees**, and that is the first thing that separates
 * fire from a sun: a tongue whose width comes from the band rather than from the spacing
 * is a needle, and a ring of needles is a sun whatever colour it is painted.
 *
 * [taper] is the second. At 1 the sides run straight to the tip and the shape is a
 * triangle — a crown point. Above 1 the width falls away early and the tip draws out to
 * a spike, which is what fire does. [lean] curls that tip sideways, so a ring of them
 * looks blown about rather than radiating.
 */
function tongue(cx, cy, base, height, at, half, taper = 1, lean = 0) {
  const steps = 10;
  const foot = base - height * 0.12;
  const side = (t, sign) => at + sign * half * (1 - t) ** taper + lean * t * t;
  const out = [`M${point(onRing(cx, cy, foot, side(0, -1)))}`];
  for (let s = 1; s <= steps; s++) {
    const t = s / steps;
    out.push(`L${point(onRing(cx, cy, base + height * t, side(t, -1)))}`);
  }
  for (let s = steps - 1; s >= 0; s--) {
    const t = s / steps;
    out.push(`L${point(onRing(cx, cy, base + height * t, side(t, 1)))}`);
  }
  out.push(`L${point(onRing(cx, cy, foot, side(0, 1)))}`);
  return `${out.join('')}Z`;
}

/** One tooth of a lightning ring: out, across, back in. */
function zag(cx, cy, r, amp, index, total) {
  const step = 360 / total;
  const at = index * step;
  return `M${point(onRing(cx, cy, r + amp * 0.5, at))}`
    + `L${point(onRing(cx, cy, r - amp * 0.5, at + step * 0.5))}`
    + `L${point(onRing(cx, cy, r + amp * 0.5, at + step))}`;
}

/** A circle whose radius rides a sine wave. */
function ripple(cx, cy, r, amp, cycles, phase) {
  const steps = 72;
  let d = '';
  for (let s = 0; s <= steps; s++) {
    const at = (s * 360) / steps;
    const wave = Math.sin(((at / 360) * cycles + phase) * TAU);
    d += `${s === 0 ? 'M' : 'L'}${point(onRing(cx, cy, r + amp * wave, at))}`;
  }
  return `${d}Z`;
}

/** A four-pointed sparkle, the shape the cards already use for a highlight. */
function star(cx, cy, size) {
  const thin = size * 0.28;
  return `M${round2(cx)} ${round2(cy - size)}`
    + `L${round2(cx + thin)} ${round2(cy - thin)}L${round2(cx + size)} ${round2(cy)}`
    + `L${round2(cx + thin)} ${round2(cy + thin)}L${round2(cx)} ${round2(cy + size)}`
    + `L${round2(cx - thin)} ${round2(cy + thin)}L${round2(cx - size)} ${round2(cy)}`
    + `L${round2(cx - thin)} ${round2(cy - thin)}Z`;
}

/**
 * An arc, as a stroked path. Used everywhere a band has to be built out of pieces:
 * SVG has no conic gradient, and a sweep cut into segments is both exact and cheap
 * enough at the sizes an avatar is ever drawn.
 */
function arc(cx, cy, r, from, to) {
  const a = onRing(cx, cy, r, from);
  const b = onRing(cx, cy, r, to);
  const wide = to - from > 180 ? 1 : 0;
  return `M${point(a)}A${round2(r)} ${round2(r)} 0 ${wide} 1 ${point(b)}`;
}

/** Mixes two '#rrggbb' colours, used to build a sweep out of flat segments. */
function mix(from, to, t) {
  const read = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
  const [r1, g1, b1] = read(from);
  const [r2, g2, b2] = read(to);
  const part = (x, y) => Math.round(x + (y - x) * t).toString(16).padStart(2, '0');
  return `#${part(r1, r2)}${part(g1, g2)}${part(b1, b2)}`;
}

/**
 * A band of colour swept through [stops], as segments. The list is closed by the caller
 * repeating its first colour, so the sweep meets itself instead of showing a seam where
 * 360° meets 0°.
 */
function sweep(cx, cy, r, width, stops, segments = 48) {
  const span = stops.length - 1;
  let out = '';
  for (let i = 0; i < segments; i++) {
    const t = (i / segments) * span;
    const slot = Math.min(span - 1, Math.floor(t));
    const colour = mix(stops[slot], stops[slot + 1], t - slot);
    // Half a degree of overlap, so antialiasing cannot leave hairlines between segments.
    out += `<path d="${arc(cx, cy, r, (i * 360) / segments - 0.5, ((i + 1) * 360) / segments + 0.5)}"
      fill="none" stroke="${colour}" stroke-width="${round2(width)}"/>`;
  }
  return out;
}

/**
 * The frame for one cosmetic, as an SVG string plus the square it needs.
 *
 * [size] is the avatar's own diameter; the ring is added around it.
 */
export function frameSvg(item, size) {
  const band = clamp(size * 0.115, 3.5, 10);
  const ink = clamp(size * 0.045, 1.5, 3.5);
  const outer = size + (band + ink) * 2;
  const m = outer / 2;
  // How far past the ring the tallest ornament reaches, so nothing is clipped.
  const pad = band * 2.4;
  const box = outer + pad * 2;
  const R = outer / 2;
  const paint = R - ink;
  const a = item.a || '#3b475d';
  const b = item.b || a;
  const c = item.c || b;
  const mid = m + pad;

  const body = STYLES[item.style]
    ? STYLES[item.style]({ x: mid, y: mid, R, paint, band, a, b, c })
    // A frame from a newer build than this page: draw the plain ring rather than nothing.
    : STYLES.SOLID({ x: mid, y: mid, R, paint, band, a, b, c });

  return {
    outer,
    box,
    svg: `<svg class="frame-art" viewBox="0 0 ${round2(box)} ${round2(box)}"
      width="${round2(box)}" height="${round2(box)}" xmlns="http://www.w3.org/2000/svg">
      <circle cx="${round2(mid)}" cy="${round2(mid)}" r="${round2(R)}" fill="${INK}"/>
      ${body}</svg>`,
  };
}

/** The flat band every style is painted on top of. */
const disc = (g, colour) =>
  `<circle cx="${round2(g.x)}" cy="${round2(g.y)}" r="${round2(g.paint)}" fill="${colour}"/>`;

/**
 * The bevel that stops a plain ring reading as a flat sticker: a light arc across the
 * top-left, a dark one across the bottom-right. Two strokes, and the difference between
 * a coloured circle and something with a surface.
 */
function bevel(g) {
  const r = g.paint - g.band * 0.34;
  const w = g.band * 0.30;
  return `<path d="${arc(g.x, g.y, r, 205, 340)}" fill="none" stroke="#ffffff"
      stroke-opacity=".26" stroke-width="${round2(w)}" stroke-linecap="round"/>
    <path d="${arc(g.x, g.y, r, 25, 160)}" fill="none" stroke="${INK}"
      stroke-opacity=".28" stroke-width="${round2(w)}" stroke-linecap="round"/>`;
}

/** A stroked ring with gaps, so the ink disc underneath shows through them. */
function dashed(g, colour, on, off, cap = 'butt') {
  return `<circle cx="${round2(g.x)}" cy="${round2(g.y)}" r="${round2(g.paint - g.band / 2)}"
    fill="none" stroke="${colour}" stroke-width="${round2(g.band)}" stroke-linecap="${cap}"
    stroke-dasharray="${round2(on)} ${round2(off)}"/>`;
}

/** Wraps [inner] in a group that turns for ever. */
const spinning = (g, seconds, inner, reverse = false) =>
  `<g style="transform-box:view-box;transform-origin:${round2(g.x)}px ${round2(g.y)}px;
    animation:framespin ${seconds}s linear infinite${reverse ? ' reverse' : ''}">${inner}</g>`;

const STYLES = {
  SOLID: (g) => disc(g, g.a) + bevel(g),

  DUO: (g) => sweep(g.x, g.y, g.paint - g.band / 2, g.band, [g.a, g.b, g.a]) + bevel(g),

  // Two colours, one inside the other; the outer band is the wider of the two.
  DUAL: (g) => disc(g, g.a)
    + `<circle cx="${round2(g.x)}" cy="${round2(g.y)}" r="${round2(g.paint - g.band * 0.55)}"
        fill="${g.b}"/>` + bevel(g),

  DASH: (g) => dashed(g, g.a, g.band * 1.5, g.band * 1.1),

  // Round caps and a wide gap: beads threaded on a ring, not a dashed line.
  BEADS: (g) => dashed(g, g.a, g.band * 0.05, g.band * 1.25, 'round'),

  // Chunky teeth on a second colour, cut deep enough to read at forty pixels.
  NOTCH: (g) => disc(g, g.b) + dashed(g, g.a, g.band * 2.4, g.band * 1.6),

  // The halo breathes; the ring underneath does not, so the shape stays readable at the
  // dim end of the pulse.
  GLOW: (g) => `<circle cx="${round2(g.x)}" cy="${round2(g.y)}" r="${round2(g.R)}"
      fill="none" stroke="${g.a}" stroke-width="${round2(g.band * 1.6)}"
      style="animation:framehalo 1.7s ease-in-out infinite alternate"/>`
    + disc(g, g.a) + bevel(g),

  // A narrow bright stripe sweeping round a flat band: a sheen, not a rainbow.
  SHINE: (g) => disc(g, g.a) + spinning(
    g, 2.4,
    sweep(g.x, g.y, g.paint - g.band / 2, g.band,
      [g.a, g.a, g.b, g.b, g.a, g.a], 60),
  ) + bevel(g),

  SPIN: (g) => spinning(
    g, 4.2, sweep(g.x, g.y, g.paint - g.band / 2, g.band, [g.a, g.b, g.c, g.a]),
  ) + bevel(g),

  // ------------------------------------------------------------------- things

  // Tongues of fire licking outward, each on its own offset so the ring flickers rather
  // than pumping in unison. The hot core keeps the base brighter than the tips.
  FLAME: (g) => {
    // Eighteen tongues, no two alike. Evenly spaced spikes of one width and one height
    // are a sun — which is exactly what the first two attempts drew — so width, height
    // and lean all come off a fixed jitter, and the tips taper to needles.
    const n = 18;
    let out = disc(g, g.a);
    for (let i = 0; i < n; i++) {
      const j = jitter(i);
      const k = jitter(i + 31);
      const tall = 1.0 + 2.2 * j;
      const wide = (180 / n) * (0.55 + 0.75 * k);
      const at = (i * 360) / n + (j - 0.5) * 9;
      // The lean is what keeps a ring of them from radiating: fire is blown sideways.
      const lean = (k - 0.5) * 26;
      out += `<path d="${tongue(g.x, g.y, g.paint, g.band * tall, at, wide, 2.4, lean)}"
        fill="${i % 3 === 1 ? g.b : g.a}"
        style="transform-box:view-box;transform-origin:${round2(g.x)}px ${round2(g.y)}px;
          animation:framelick ${round2(0.6 + j * 0.5)}s ease-in-out
            ${round2(-i * 0.11)}s infinite alternate"/>`;
    }
    // A hot core, so the base of the fire is brighter than its tips.
    return out + `<circle cx="${round2(g.x)}" cy="${round2(g.y)}"
      r="${round2(g.paint - g.band * 0.3)}" fill="${g.b}" opacity=".62"/>`;
  },

  // A zigzag ring, with two teeth lit at a time running round it.
  BOLT: (g) => {
    const n = 14;
    const r = g.paint - g.band * 0.5;
    const w = round2(g.band * 0.42);
    let out = `<circle cx="${round2(g.x)}" cy="${round2(g.y)}" r="${round2(g.paint)}"
      fill="${g.a}" opacity=".28"/>`;
    for (let i = 0; i < n; i++) {
      const d = zag(g.x, g.y, r, g.band, i, n);
      out += `<path d="${d}" fill="none" stroke="${g.a}" stroke-width="${w}"
        stroke-linecap="round" stroke-linejoin="round"/>`;
      // The same tooth again in the hot colour, blinking on as the spark passes it.
      out += `<path d="${d}" fill="none" stroke="${g.b}" stroke-width="${w}"
        stroke-linecap="round" stroke-linejoin="round" opacity="0"
        style="animation:framelit 1.4s steps(1) ${round2((-i * 1.4) / n)}s infinite"/>`;
    }
    return out;
  },

  // Water: two ripples rolling round at different speeds and in opposite directions.
  WAVE: (g) => {
    const r = g.paint - g.band * 0.5;
    return `<circle cx="${round2(g.x)}" cy="${round2(g.y)}" r="${round2(g.paint)}"
        fill="${g.a}" opacity=".35"/>`
      + spinning(g, 3.6, `<path d="${ripple(g.x, g.y, r, g.band * 0.5, 6, 0)}" fill="none"
          stroke="${g.a}" stroke-width="${round2(g.band * 0.55)}" stroke-linejoin="round"/>`)
      + spinning(g, 5.4, `<path d="${ripple(g.x, g.y, r, g.band * 0.34, 6, 0.5)}" fill="none"
          stroke="${g.b}" stroke-width="${round2(g.band * 0.36)}" stroke-linejoin="round"/>`, true);
  },

  // A bead running round the ring, with a tail fading behind it.
  ORBIT: (g) => {
    const r = g.paint - g.band * 0.5;
    let tail = '';
    for (let i = 0; i < 8; i++) {
      const [px, py] = onRing(g.x, g.y, r, -i * 4.3);
      tail += `<circle cx="${round2(px)}" cy="${round2(py)}"
        r="${round2(Math.max(0.08, 0.5 - i * 0.045) * g.band)}" fill="${g.a}"
        opacity="${round2((1 - i / 8) * 0.9)}"/>`;
    }
    return disc(g, g.b) + bevel(g) + spinning(g, 2.6, tail);
  },

  // Small stars set into the ring, twinkling out of step with each other.
  STARS: (g) => {
    const n = 8;
    let out = disc(g, g.b) + bevel(g);
    for (let i = 0; i < n; i++) {
      const [px, py] = onRing(g.x, g.y, g.paint - g.band * 0.5, (i * 360) / n);
      out += `<path d="${star(px, py, g.band * 0.62)}" fill="${g.a}"
        style="transform-box:view-box;transform-origin:${round2(px)}px ${round2(py)}px;
          animation:frametwinkle 2s ease-in-out ${round2(-i * 0.37)}s infinite alternate"/>`;
    }
    return out;
  },

  // The last frame in the game: gold points all round, a jewel on each, and a shine that
  // sweeps over the lot.
  CROWN: (g) => {
    // A crown, not a ring of gold spikes. Three attempts at points-all-round each came
    // out as a sun wearing gold; one small crown sitting on top of a gold band is read
    // correctly at a glance, which is the only thing a level-100 frame has to do.
    const w = g.band * 2.0;
    const h = g.band * 2.4;
    const top = g.y - g.paint - h * 0.34;
    const ink = round2(g.band * 0.13);
    const tip = (x, y, r) => `<circle cx="${round2(g.x + x)}" cy="${round2(top + y)}"
      r="${round2(r)}" fill="${g.b}" stroke="${INK}" stroke-width="${ink}"/>`;

    const crown = `M${round2(g.x - w)} ${round2(top + h * 0.34)}
      L${round2(g.x - w)} ${round2(top - h * 0.62)}
      L${round2(g.x - w * 0.46)} ${round2(top - h * 0.14)}
      L${round2(g.x)} ${round2(top - h)}
      L${round2(g.x + w * 0.46)} ${round2(top - h * 0.14)}
      L${round2(g.x + w)} ${round2(top - h * 0.62)}
      L${round2(g.x + w)} ${round2(top + h * 0.34)}Z`;

    return sweep(g.x, g.y, g.paint - g.band / 2, g.band, [g.a, g.b, g.a, g.c, g.a])
      + bevel(g)
      + spinning(g, 3,
        `<path d="${arc(g.x, g.y, g.paint - g.band / 2, -16, 16)}" fill="none" stroke="${g.b}"
          stroke-width="${round2(g.band)}" stroke-linecap="round" opacity=".85"/>`)
      + `<path d="${crown}" fill="${g.a}" stroke="${INK}" stroke-width="${ink}"
          stroke-linejoin="round"/>`
      // The band across the bottom of it, and a stone set in the middle.
      + `<rect x="${round2(g.x - w)}" y="${round2(top - h * 0.02)}"
          width="${round2(w * 2)}" height="${round2(h * 0.36)}" rx="${round2(h * 0.1)}"
          fill="${g.c}" stroke="${INK}" stroke-width="${ink}"/>`
      + `<circle cx="${round2(g.x)}" cy="${round2(top + h * 0.16)}" r="${round2(g.band * 0.26)}"
          fill="${g.b}" stroke="${INK}" stroke-width="${ink}"/>`
      + tip(0, -h, g.band * 0.3)
      + tip(-w, -h * 0.62, g.band * 0.24)
      + tip(w, -h * 0.62, g.band * 0.24);
  },
};
