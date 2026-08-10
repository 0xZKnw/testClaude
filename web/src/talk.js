// What players throw at each other alongside the cards, ported from the Android app's
// Talk.kt. None of it touches the rules, so none of it lives in the engine.
//
// Stickers only, deliberately. Typing at a card table means looking away from it.

import { STICKER_ITEMS } from './cosmetics.js';

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
