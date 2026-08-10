// What players send each other alongside the cards, ported from the Android app's
// Talk.kt. None of it touches the rules, so none of it lives in the engine.

import { STICKER_ITEMS } from './cosmetics.js';

/**
 * A line has to fit in a data-channel frame, not in a novel. Long enough for a taunt,
 * short enough that nobody can stall the table by pasting a page of text.
 */
export const MAX_CHARS = 120;

/**
 * Every sticker there is, in catalogue order. Sent as an index, never as text: a build
 * that does not know a sticker ignores it instead of drawing a tofu box.
 *
 * The index is into the *whole* catalogue, not into what the sender has unlocked —
 * otherwise two players at different levels would disagree about what index 7 means.
 * Which of these the rail actually offers is a separate question, answered by the
 * sender's level.
 */
export const STICKERS = STICKER_ITEMS.map((item) => item.text);

export const sticker = (index) => STICKERS[index] ?? null;

/** Trims and caps a typed line; returns null when there is nothing worth sending. */
export function cleanLine(raw) {
  const text = String(raw ?? '').trim().slice(0, MAX_CHARS);
  return text.length ? text : null;
}
