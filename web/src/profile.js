// Who you are, kept on this device.
//
// The Android app stores this in SharedPreferences; here it is localStorage. Same
// fields, same lifetime totals, so a player recognises their own profile on either.
//
// The photo is stored already shrunk. It has to cross a data channel to the other
// players, and a full-resolution camera shot would be megabytes for a circle drawn at
// forty pixels.

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
  cardsPlayed: 0, cardsDrawn: 0, drawTwosPlayed: 0, drawFoursPlayed: 0,
  wildsPlayed: 0, skipsPlayed: 0, countersPlayed: 0,
  penaltyCardsTaken: 0, biggestStackTaken: 0, biggestStackDealt: 0,
});

export function loadProfile() {
  const fallback = { name: '', avatarColor: 0, photo: null, stats: emptyStats() };
  try {
    const stored = JSON.parse(localStorage.getItem(KEY) || '{}');
    return {
      name: typeof stored.name === 'string' ? stored.name : '',
      avatarColor: Number.isInteger(stored.avatarColor) ? stored.avatarColor : 0,
      photo: typeof stored.photo === 'string' ? stored.photo : null,
      stats: { ...emptyStats(), ...(stored.stats || {}) },
    };
  } catch {
    return fallback;
  }
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
  s.roundsPlayed++;
  if (won) s.roundsWon++; else s.roundsLost++;
  s.cardsPlayed += roundStats.cp || 0;
  s.cardsDrawn += roundStats.cd || 0;
  s.drawTwosPlayed += roundStats.d2 || 0;
  s.drawFoursPlayed += roundStats.d4 || 0;
  s.wildsPlayed += roundStats.w || 0;
  s.skipsPlayed += roundStats.sk || 0;
  s.countersPlayed += roundStats.co || 0;
  s.penaltyCardsTaken += roundStats.pt || 0;
  s.biggestStackTaken = Math.max(s.biggestStackTaken, roundStats.bt || 0);
  s.biggestStackDealt = Math.max(s.biggestStackDealt, roundStats.bd || 0);
  return saveProfile(p);
}

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
