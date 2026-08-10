// Experience and levels — the same arithmetic as the Android app's Levels.kt, integer
// for integer. A float here would eventually disagree with the phone on a boundary, and
// the two versions are meant to describe the same profile.

export const MAX_LEVEL = 100;
export const XP_WIN = 25;
export const XP_LOSS = 10;

/** What the first level costs. */
const BASE = 20;

/** How much more each level costs than the one before it. */
const STEP = 5;

/** Experience needed to go from `level` to the next one. Zero at the top. */
export function costOf(level) {
  return (level < 1 || level >= MAX_LEVEL) ? 0 : BASE + STEP * (level - 1);
}

/** Total experience needed to reach `level` from scratch. Closed form, not a loop. */
export function totalTo(level) {
  const done = Math.min(Math.max(level, 1), MAX_LEVEL) - 1;
  return done * BASE + STEP * done * (done - 1) / 2;
}

export const xpFor = (won) => (won ? XP_WIN : XP_LOSS);

/** The level this much experience buys, clamped to the top. */
export function levelAt(xp) {
  if (xp <= 0) return 1;
  let level = 1;
  let spent = 0;
  while (level < MAX_LEVEL) {
    const cost = costOf(level);
    if (xp - spent < cost) break;
    spent += cost;
    level++;
  }
  return level;
}

/** How far into the current level, in points. */
export const into = (xp) => Math.max(xp, 0) - totalTo(levelAt(xp));

/** How wide the current level is, in points. Zero at the top. */
export const span = (xp) => costOf(levelAt(xp));

/** Progress through the current level, 0..100. A maxed profile reads 100. */
export function percent(xp) {
  const width = span(xp);
  return width <= 0 ? 100 : Math.trunc((into(xp) * 100) / width);
}

/** Experience still owed before the next level. Zero at the top. */
export function toNext(xp) {
  const width = span(xp);
  return width <= 0 ? 0 : width - into(xp);
}

/** Everything from scratch to the top. */
export const fullRun = () => totalTo(MAX_LEVEL);
