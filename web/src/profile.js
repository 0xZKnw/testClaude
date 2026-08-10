// Who you are, kept on this device.
//
// The Android app stores this in SharedPreferences; here it is localStorage. Same
// fields, same lifetime totals, so a player recognises their own profile on either.
//
// The photo is stored already shrunk. It has to cross a data channel to the other
// players, and a full-resolution camera shot would be megabytes for a circle drawn at
// forty pixels.

import { levelAt, xpFor, fullRun } from './levels.js';
// Aliased: shrinkPhoto below takes a promise resolve of its own.
import { resolve as resolveCosmetic, find, stickersAt, rewardsAt } from './cosmetics.js';

const KEY = 'uno.profile';

export const AVATAR_COLORS = [
  '#f23b2e', '#ff8a1e', '#ffc21a', '#41c258',
  '#19b79b', '#2e9cf2', '#7a5cf0', '#f25da8',
];

/** Thumbnail side in pixels: enough for the biggest avatar the UI draws. */
const PHOTO_SIDE = 128;
const PHOTO_QUALITY = 0.72;

const emptyStats = () => ({
  roundsPlayed: 0, roundsWon: 0, roundsLost: 0,
  currentStreak: 0, bestStreak: 0, worstStreak: 0, lossStreak: 0,
  fastestWin: 0, worstHand: 0,
  cardsPlayed: 0, cardsDrawn: 0, numbersPlayed: 0,
  drawTwosPlayed: 0, drawFoursPlayed: 0, drawEightsPlayed: 0, drawTwelvesPlayed: 0,
  wildsPlayed: 0, doublePlaysPlayed: 0, spiesPlayed: 0, skipsPlayed: 0,
  countersPlayed: 0, penaltyCardsTaken: 0,
  biggestStackTaken: 0, biggestStackDealt: 0,
  unoReached: 0, cardsLeftAtEnd: 0,
});

/** Every attacking card laid, of whatever size. */
export const penaltiesPlayed = (s) =>
  s.drawTwosPlayed + s.drawFoursPlayed + s.drawEightsPlayed + s.drawTwelvesPlayed;

/** Whole percent, 0 when nothing has been played yet. */
export const winRate = (s) =>
  (s.roundsPlayed === 0 ? 0 : Math.trunc((s.roundsWon * 100) / s.roundsPlayed));

/** Integer arithmetic, one decimal, French comma — the same string Android prints. */
function oneDecimal(value, over) {
  const tenths = Math.trunc((value * 10 + Math.trunc(over / 2)) / over);
  return `${Math.trunc(tenths / 10)},${tenths % 10}`;
}

export const perRound = (s, value) =>
  (s.roundsPlayed === 0 ? '—' : oneDecimal(value, s.roundsPlayed));

/**
 * How much of what passes through your hands you had to draw rather than lay. A high
 * number is a hand that never fits the table, not bad luck alone.
 */
export function drawRate(s) {
  const handled = s.cardsPlayed + s.cardsDrawn;
  return handled === 0 ? 0 : Math.trunc((s.cardsDrawn * 100) / handled);
}

/** Of everything you laid, how much of it was an attack. */
export const aggression = (s) =>
  (s.cardsPlayed === 0 ? 0 : Math.trunc((penaltiesPlayed(s) * 100) / s.cardsPlayed));

/** Average cards left in hand when you lost. */
export const averageLoss = (s) =>
  (s.roundsLost === 0 ? '—' : oneDecimal(s.cardsLeftAtEnd, s.roundsLost));

/** How often reaching a single card actually turned into a win. */
export const closingRate = (s) =>
  (s.unoReached === 0 ? 0 : Math.min(100, Math.trunc((s.roundsWon * 100) / s.unoReached)));

export function loadProfile() {
  const fallback = {
    name: '', avatarColor: 0, photo: null, stats: emptyStats(), xp: 0, wearing: {},
  };
  try {
    const stored = JSON.parse(localStorage.getItem(KEY) || '{}');
    return {
      name: typeof stored.name === 'string' ? stored.name : '',
      avatarColor: Number.isInteger(stored.avatarColor) ? stored.avatarColor : 0,
      photo: typeof stored.photo === 'string' ? stored.photo : null,
      stats: { ...emptyStats(), ...(stored.stats || {}) },
      xp: Number.isInteger(stored.xp) ? stored.xp : 0,
      wearing: (stored.wearing && typeof stored.wearing === 'object') ? stored.wearing : {},
    };
  } catch {
    return fallback;
  }
}

/** The level this profile's experience buys. */
export const levelOf = (profile) => levelAt(profile.xp || 0);

/** Resolved through the catalogue, so a stale or unearned choice cannot show. */
export const wornOf = (profile, kind) =>
  resolveCosmetic((profile.wearing || {})[kind] || '', kind, levelOf(profile));

/** The stickers this profile has earned, in rail order. */
export const stickersOf = (profile) => stickersAt(levelOf(profile));

/** Puts a cosmetic on. Unearned or unknown ids are simply not stored. */
export function wear(id) {
  const p = loadProfile();
  const item = find(id);
  if (!item || item.level > levelOf(p)) return p;
  p.wearing = { ...(p.wearing || {}), [item.kind]: item.id };
  return saveProfile(p);
}

/**
 * Adds the experience a finished round is worth and hands back what it unlocked.
 *
 * Deliberately separate from recordRound: a game against the machine is not a result
 * worth recording, but it is still a round played, and a progression that ignored solo
 * would punish anybody without somebody to play against.
 */
export function addXp(won) {
  const stored = loadProfile();
  // Captured before the mutation: at the very top the addition is clamped, so working
  // backwards from the new total would lie about where the bar started.
  const start = stored.xp || 0;
  const gained = xpFor(won);
  const from = levelOf(stored);
  stored.xp = Math.min(start + gained, fullRun());
  const profile = saveProfile(stored);
  const to = levelOf(profile);
  const unlocked = [];
  for (let level = from + 1; level <= to; level++) unlocked.push(...rewardsAt(level));
  return { gained, before: start, from, to, unlocked, profile, levelledUp: to > from };
}

export function saveProfile(profile) {
  try {
    localStorage.setItem(KEY, JSON.stringify(profile));
  } catch {
    // A full or blocked storage must not take the game down with it.
  }
  return profile;
}

/**
 * Folds a finished round into the lifetime totals. Counters add up; the two "biggest"
 * figures are records, so they keep the larger of the two.
 */
export function recordRound(won, roundStats) {
  const p = loadProfile();
  const s = p.stats;
  const r = roundStats || {};
  s.roundsPlayed++;
  if (won) s.roundsWon++; else s.roundsLost++;
  s.currentStreak = won ? s.currentStreak + 1 : 0;
  s.bestStreak = Math.max(s.bestStreak, s.currentStreak);
  s.lossStreak = won ? 0 : s.lossStreak + 1;
  s.worstStreak = Math.max(s.worstStreak, s.lossStreak);
  // A record, so a first win sets it rather than being beaten by the zero default.
  if (won) s.fastestWin = s.fastestWin === 0 ? (r.cp || 0) : Math.min(s.fastestWin, r.cp || 0);
  s.worstHand = Math.max(s.worstHand, r.cl || 0);
  s.cardsPlayed += r.cp || 0;
  s.cardsDrawn += r.cd || 0;
  s.numbersPlayed += r.nb || 0;
  s.drawTwosPlayed += r.d2 || 0;
  s.drawFoursPlayed += r.d4 || 0;
  s.drawEightsPlayed += r.e8 || 0;
  s.drawTwelvesPlayed += r.e12 || 0;
  s.wildsPlayed += r.wc || 0;
  s.doublePlaysPlayed += r.dp || 0;
  s.spiesPlayed += r.sp || 0;
  s.skipsPlayed += r.sk || 0;
  s.countersPlayed += r.ct || 0;
  s.penaltyCardsTaken += r.pt || 0;
  s.biggestStackTaken = Math.max(s.biggestStackTaken, r.bt || 0);
  s.biggestStackDealt = Math.max(s.biggestStackDealt, r.bd || 0);
  s.unoReached += r.un || 0;
  // Summed, not kept: the lifetime total is what an average is built from.
  s.cardsLeftAtEnd += r.cl || 0;
  return saveProfile(p);
}

/**
 * Wipes the tally only. Experience and the wardrobe survive on purpose: clearing a
 * scoreboard is one thing, confiscating a hundred levels of unlocks is another.
 */
export function resetStats() {
  const p = loadProfile();
  p.stats = emptyStats();
  return saveProfile(p);
}

/** Squares the picked photo on its centre and shrinks it to a thumbnail. */
export function shrinkPhoto(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      const side = Math.min(img.width, img.height);
      if (!side) { reject(new Error('image vide')); return; }
      const canvas = document.createElement('canvas');
      canvas.width = PHOTO_SIDE;
      canvas.height = PHOTO_SIDE;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(
        img,
        (img.width - side) / 2, (img.height - side) / 2, side, side,
        0, 0, PHOTO_SIDE, PHOTO_SIDE,
      );
      resolve(canvas.toDataURL('image/jpeg', PHOTO_QUALITY));
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('illisible')); };
    img.src = url;
  });
}
