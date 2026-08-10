// The solo opponent, ported from the Android app's Bot.
//
// Same caveat as the engine: this is a literal port, kept in step by
// web/test/engine-conformance.mjs, which makes both versions play the same games with
// the same random source and compares every single move. The order of the random draws
// matters as much as the decisions themselves — dithering is rolled before the card is
// chosen, and the colour after — so it is preserved exactly.

import { Color, Kind, PLAYABLE_COLORS, isWild, view } from './engine.js';

export const Difficulty = {
  EASY: 'EASY',
  MEDIUM: 'MEDIUM',
  HARD: 'HARD',
};

export const DIFFICULTY_ORDER = [Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD];

export const DIFFICULTY_INFO = {
  EASY: {
    label: 'Facile',
    botName: 'Bot facile',
    blurb: 'Joue au hasard et oublie souvent de poser.',
  },
  MEDIUM: {
    label: 'Moyen',
    botName: 'Bot moyen',
    blurb: 'Joue correctement, mais se trompe encore.',
  },
  HARD: {
    label: 'Difficile',
    botName: 'Bot difficile',
    blurb: 'Ne gâche rien et te bloque dès qu\'il peut.',
  },
};

const MEDIUM_SLIP_PERCENT = 30;

/** How often the bot needlessly draws instead of playing. */
function ditherPercent(difficulty) {
  if (difficulty === Difficulty.EASY) return 25;
  if (difficulty === Difficulty.MEDIUM) return 8;
  return 0;
}

/** Kotlin's maxBy keeps the first element reaching the maximum. */
function firstMaxBy(items, score) {
  let best = items[0];
  let bestScore = score(best);
  for (let i = 1; i < items.length; i++) {
    const s = score(items[i]);
    if (s > bestScore) { best = items[i]; bestScore = s; }
  }
  return best;
}

/**
 * How much the bot wants to get rid of a card.
 *
 * The ranking of the attacking cards comes from the house rules, and is not the one you
 * would expect: eating a +2 does not cost you your turn, so a +2 thrown at someone
 * holding a single card does not stop them going out. A Skip does, and so does a +4.
 */
function score(card, v, sharp) {
  const counts = v.ri.map((r) => r.c);
  const closest = counts.length ? Math.min(...counts) : 7;
  const threatened = closest <= (sharp ? 2 : 1);

  // In a duel a Skip hands the turn straight back: a free card shed every time.
  const duel = view.playerCount(v) === 2;
  let value;
  switch (card.k) {
    case Kind.NUMBER: value = 10; break;
    case Kind.SKIP:
    case Kind.REVERSE:
      value = (sharp && duel) ? 80 : (threatened ? 70 : 20);
      break;
    case Kind.DRAW_TWO: value = threatened ? 40 : 30; break;
    case Kind.WILD: value = 5; break;
    case Kind.DRAW_FOUR: value = threatened ? 65 : 6; break;
    // Same card, twice the bite: never worth less than a +4.
    case Kind.DRAW_EIGHT: value = threatened ? 90 : 8; break;
    // There is one in the whole deck: worth holding, worth more still on somebody who is
    // about to go out.
    case Kind.DRAW_TWELVE: value = threatened ? 120 : 10; break;
    // Fifty cards. There is no situation in which sitting on this beats playing it, and
    // the bot should not pretend otherwise.
    case Kind.DRAW_FIFTY: value = 400; break;
    // Two cards gone for the price of one, and the colour is yours to pick.
    case Kind.DOUBLE_PLAY: value = v.h.length > 2 ? 60 : 4; break;
    // A Joker that also buys information: a little more than a bare one, no more.
    case Kind.SPY: value = 9; break;
    default: value = 0;
  }

  if (sharp) {
    if (card.c !== Color.WILD) {
      value += 2 * v.h.filter((c) => c.c === card.c && c.i !== card.i).length;
    }
    if (v.h.length === 2 && !isWild(card)) value += 15;
    if (v.h.length === 1) value += 1000;
  }
  return value;
}

function chooseColor(v, played, difficulty, rng) {
  if (difficulty === Difficulty.EASY) {
    return PLAYABLE_COLORS[rng.nextIntBelow(PLAYABLE_COLORS.length)];
  }
  const rest = v.h.filter((c) => c.i !== played.i);
  const best = firstMaxBy(PLAYABLE_COLORS, (color) =>
    rest.filter((c) => c.c === color).length * 4 +
    rest.filter((c) => c.c === color && c.k !== Kind.NUMBER).length);
  if (!rest.some((c) => c.c === best)) {
    return PLAYABLE_COLORS[rng.nextIntBelow(PLAYABLE_COLORS.length)];
  }
  return best;
}

/**
 * Decides from the same snapshot a human player receives, so the bot never sees anyone's
 * hand and cannot cheat, whatever the level.
 */
export function decide(v, difficulty, rng) {
  if (!view.yourTurn(v)) return { kind: 'draw' };
  const legal = v.h.filter((c) => v.l.includes(c.i));

  if (!legal.length) {
    return view.canPass(v) ? { kind: 'pass' } : { kind: 'draw' };
  }

  // Drawing a card you did not need is the beginner mistake that actually costs games.
  if (view.canDraw(v) && rng.nextIntBelow(100) < ditherPercent(difficulty)) {
    return { kind: 'draw' };
  }

  // Mid Coup double there is nothing to draw, so the same hesitation comes out as
  // stopping early instead — otherwise the easy bot never wastes a bonus.
  if (view.inBonus(v) && rng.nextIntBelow(100) < ditherPercent(difficulty)) {
    return { kind: 'pass' };
  }

  let card;
  if (difficulty === Difficulty.EASY) {
    card = legal[rng.nextIntBelow(legal.length)];
  } else if (difficulty === Difficulty.MEDIUM) {
    card = rng.nextIntBelow(100) < MEDIUM_SLIP_PERCENT
      ? legal[rng.nextIntBelow(legal.length)]
      : firstMaxBy(legal, (c) => score(c, v, false));
  } else {
    card = firstMaxBy(legal, (c) => score(c, v, true));
  }

  const color = isWild(card) ? chooseColor(v, card, difficulty, rng) : null;
  return { kind: 'play', cardId: card.i, color };
}
