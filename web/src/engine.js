// The rules, ported from the Android app's UnoEngine.
//
// This is the same engine, not a similar one. The port is deliberately literal — same
// order of operations, same deal, same draw from the end of the pile, same event
// strings — because the two are checked against each other move by move by
// web/test/engine-conformance.mjs. Rewriting it "more idiomatically" is how the two
// versions would quietly drift apart and start dealing different games.
//
// Colours and kinds use the short codes the Android app puts on the wire, so a snapshot
// serialises to exactly the same JSON on both sides.

import { KotlinRandom, shuffled, shuffleInPlace } from './random.js';

export const Color = { RED: 'R', YELLOW: 'Y', GREEN: 'G', BLUE: 'B', WILD: 'W' };
export const PLAYABLE_COLORS = [Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE];
const COLOR_ORDER = { R: 0, Y: 1, G: 2, B: 3, W: 4 };

export const Kind = {
  NUMBER: 'n', SKIP: 's', REVERSE: 'r', DRAW_TWO: 'd2', WILD: 'w', DRAW_FOUR: 'd4',
  // Only ever in the deck when the matching mod is on. Appended, like Kotlin's enum, so
  // a standard game sorts exactly as it always did.
  DRAW_EIGHT: 'd8', DOUBLE_PLAY: 'x2', SPY: 'sp', DRAW_TWELVE: 'd12',
  /**
   * The jackpot. Not a mod and not in any deck: roughly one round in a hundred, one of
   * these is slipped into the draw pile and somebody eventually turns it over.
   */
  DRAW_FIFTY: 'd50',
};
const KIND_ORDER = {
  n: 0, s: 1, r: 2, d2: 3, w: 4, d4: 5, d8: 6, x2: 7, sp: 8, d12: 9, d50: 10,
};

/**
 * The jackpot's card id. Past every real card in the fattest possible deck, so it can
 * never collide with one whatever mods are on.
 */
export const JACKPOT_ID = 9000;

/** The optional rules a host can switch on. Order matters: it numbers the extra cards. */
export const Mod = {
  DRAW_EIGHT: 'd8', DOUBLE_PLAY: 'x2', SPY: 'sp', DRAW_TWELVE: 'd12',
};
export const MOD_ORDER = [Mod.DRAW_EIGHT, Mod.DOUBLE_PLAY, Mod.SPY, Mod.DRAW_TWELVE];
export const MOD_INFO = {
  d8: {
    label: 'Les +8',
    blurb: 'Deux +8 rejoignent le paquet. Ils fonctionnent exactement comme des +4, en '
      + 'plus lourd : ils se cumulent avec les +4 et les +8, et un +2 de la couleur '
      + 'annoncée les contre.',
  },
  x2: {
    label: 'Coup double',
    blurb: 'Trois cartes en plus. Tu annonces une couleur, puis tu poses deux cartes de '
      + 'suite. Une carte d\'attaque met fin au coup double : la pile part chez le voisin.',
  },
  sp: {
    label: 'Espion',
    blurb: 'Trois cartes en plus. Un Joker ordinaire — tu annonces une couleur — sauf '
      + "qu'il te montre une carte au hasard du joueur suivant. Toi seul la vois, et tu "
      + "la vois jusqu'à ce qu'il la pose.",
  },
  d12: {
    label: 'Le +12',
    blurb: 'Une seule carte, jamais distribuée : elle est cachée au hasard dans la pioche '
      + "et ne s'attrape qu'en piochant. Elle frappe comme un +4, en trois fois pire.",
  },
};
export const orderedMods = (mods) => MOD_ORDER.filter((m) => mods.includes(m));

/** How many cards a Coup double buys. */
const BONUS_PLAYS = 2;
export const DRAW_EIGHTS = 2;
export const DOUBLE_PLAYS = 3;
export const SPIES = 3;
/** Exactly one, and startRound keeps it out of every starting hand. */
export const DRAW_TWELVES = 1;

export const Penalty = { NONE: '0', DRAW_TWO: '2', DRAW_FOUR: '4' };
export const Phase = { PLAYING: 'p', DECIDE_AFTER_DRAW: 'd', GAME_OVER: 'o' };

export const HOST_SEAT = 0;
export const MIN_PLAYERS = 2;
export const MAX_PLAYERS = 5;

export const isWild = (card) => card.k === Kind.WILD || card.k === Kind.DRAW_FOUR
  || card.k === Kind.DRAW_EIGHT || card.k === Kind.DOUBLE_PLAY || card.k === Kind.SPY
  || card.k === Kind.DRAW_TWELVE || card.k === Kind.DRAW_FIFTY;
export const isPenalty = (card) => card.k === Kind.DRAW_TWO || card.k === Kind.DRAW_FOUR
  || card.k === Kind.DRAW_EIGHT || card.k === Kind.DRAW_TWELVE
  || card.k === Kind.DRAW_FIFTY;

/** The one card that is worth stopping the game for. */
export const isJackpot = (card) => card.k === Kind.DRAW_FIFTY;
export const isRealColor = (color) => color !== Color.WILD;

const COLOR_NAME = { R: 'rouge', Y: 'jaune', G: 'vert', B: 'bleu', W: '-' };

export function cardLabel(card) {
  const kindName = {
    n: String(card.n), s: 'Passe', r: 'Sens interdit', d2: '+2', w: 'Joker', d4: '+4',
    d8: '+8', x2: 'Coup double', sp: 'Espion', d12: '+12', d50: '+50',
  }[card.k];
  const colorName = card.c === Color.WILD ? '' : COLOR_NAME[card.c];
  return colorName ? `${kindName} ${colorName}` : kindName;
}

/** The classic 108-card deck, built in the same order so ids line up with Kotlin's. */
export function standardDeck() {
  return buildDeck([]);
}

/**
 * The deck for a room. The 108 classic cards are always built first and in the same
 * order, so their ids never move whatever is switched on — and the extra cards are added
 * in MOD_ORDER, never in the order the caller happened to list them.
 */
export function buildDeck(mods) {
  const cards = [];
  let id = 0;
  for (const color of PLAYABLE_COLORS) {
    cards.push({ i: id++, c: color, k: Kind.NUMBER, n: 0 });
    for (let n = 1; n <= 9; n++) {
      for (let twice = 0; twice < 2; twice++) cards.push({ i: id++, c: color, k: Kind.NUMBER, n });
    }
    for (let twice = 0; twice < 2; twice++) cards.push({ i: id++, c: color, k: Kind.SKIP, n: -1 });
    for (let twice = 0; twice < 2; twice++) cards.push({ i: id++, c: color, k: Kind.REVERSE, n: -1 });
    for (let twice = 0; twice < 2; twice++) cards.push({ i: id++, c: color, k: Kind.DRAW_TWO, n: -1 });
  }
  for (let i = 0; i < 4; i++) cards.push({ i: id++, c: Color.WILD, k: Kind.WILD, n: -1 });
  for (let i = 0; i < 4; i++) cards.push({ i: id++, c: Color.WILD, k: Kind.DRAW_FOUR, n: -1 });

  if (mods.includes(Mod.DRAW_EIGHT)) {
    for (let i = 0; i < DRAW_EIGHTS; i++) {
      cards.push({ i: id++, c: Color.WILD, k: Kind.DRAW_EIGHT, n: -1 });
    }
  }
  if (mods.includes(Mod.DOUBLE_PLAY)) {
    for (let i = 0; i < DOUBLE_PLAYS; i++) {
      cards.push({ i: id++, c: Color.WILD, k: Kind.DOUBLE_PLAY, n: -1 });
    }
  }
  if (mods.includes(Mod.SPY)) {
    for (let i = 0; i < SPIES; i++) {
      cards.push({ i: id++, c: Color.WILD, k: Kind.SPY, n: -1 });
    }
  }
  if (mods.includes(Mod.DRAW_TWELVE)) {
    for (let i = 0; i < DRAW_TWELVES; i++) {
      cards.push({ i: id++, c: Color.WILD, k: Kind.DRAW_TWELVE, n: -1 });
    }
  }
  return cards;
}

/** Hands are shown grouped by colour, then by symbol, then by number. */
function handOrder(a, b) {
  return (COLOR_ORDER[a.c] - COLOR_ORDER[b.c]) ||
    (KIND_ORDER[a.k] - KIND_ORDER[b.k]) ||
    (a.n - b.n);
}

/** Kotlin's sortedWith is stable; Array.sort is too in every current engine. */
const sortedHand = (hand) => hand.slice().sort(handOrder);

/**
 * One seat's round, counted by the host. Same fields and same short keys as the Android
 * app's RoundStats, so the two versions describe a round in exactly the same terms.
 */
function emptyStats() {
  return {
    cp: 0, cd: 0, nb: 0, d2: 0, d4: 0, e8: 0, e12: 0, e50: 0, wc: 0, dp: 0, sp: 0,
    sk: 0, ct: 0, pt: 0, bt: 0, bd: 0, un: 0, cl: 0,
  };
}

export class UnoEngine {
  constructor(seedLow, seedHigh, playerCount, mods = []) {
    if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
      throw new Error(`Une partie se joue de ${MIN_PLAYERS} à ${MAX_PLAYERS} joueurs`);
    }
    this.rng = new KotlinRandom(seedLow, seedHigh);
    this.playerCount = playerCount;
    this.mods = orderedMods(mods);
    this.seats = [...Array(playerCount).keys()];
    this.hands = this.seats.map(() => []);
    this.drawPile = [];
    this.discardPile = [];

    this.activeColor = Color.RED;
    this.turn = HOST_SEAT;
    this.pendingDraw = 0;
    this.pendingType = Penalty.NONE;
    this.phase = Phase.PLAYING;
    this.winner = null;
    this.direction = 1;
    this.extraPlays = 0;
    // Who has seen what, thanks to an Espion: watching seat -> ids it has been shown.
    // Private on purpose — the card is turned over for the player who spent the Espion
    // and for nobody else, not even the victim. An id drops out of every watcher's set
    // the moment that card is played.
    this.seen = this.seats.map(() => new Set());
    this.drawnCardId = -1;
    this.event = '';
    this.eventId = 0;
    this.roundId = 0;
    this.lastPlayedBy = null;
    this.penaltyTaken = 0;
    this.penaltyVictim = null;

    this.scores = this.seats.map(() => 0);
    this.stats = this.seats.map(() => emptyStats());
    this.avatars = this.seats.slice();
    this.names = this.seats.map((s) => this.defaultName(s));
  }

  defaultName(seat) {
    return seat === HOST_SEAT ? 'Hôte' : `Joueur ${seat + 1}`;
  }

  setName(seat, name) {
    if (seat >= 0 && seat < this.playerCount) {
      this.names[seat] = (name || '').trim() ? name : this.defaultName(seat);
    }
  }

  setAvatar(seat, avatar) {
    if (seat >= 0 && seat < this.playerCount) this.avatars[seat] = avatar;
  }

  seatName(seat) {
    return this.names[seat] ?? this.defaultName(seat);
  }

  /** The seat `step` places along, following the current direction. */
  seatAfter(from, step = 1) {
    const moved = (from + this.direction * step) % this.playerCount;
    return moved < 0 ? moved + this.playerCount : moved;
  }

  top() {
    return this.discardPile[this.discardPile.length - 1];
  }

  deckCount() {
    return this.drawPile.length;
  }

  handOf(seat) {
    return this.hands[seat];
  }

  score(seat) {
    return this.scores[seat];
  }

  /**
   * Deals a fresh round. The starting card is re-drawn until it is a plain number card,
   * which removes every "first card is a +2 / wild / skip" special case.
   */
  /**
   * Deals a round.
   *
   * `jackpot` slips the single +50 into the draw pile. The decision is the caller's, not
   * the engine's, and it defaults to off — which is what keeps a normal round shuffling
   * exactly as it always has, down to the card ids. Nothing here is rolled: an engine
   * that decided this itself could not be replayed.
   */
  startRound(starter, jackpot = false) {
    this.hands.forEach((h) => { h.length = 0; });
    this.drawPile = shuffled(buildDeck(this.mods), this.rng);
    this.discardPile = [];

    // The +12 is dealt to nobody. It is lifted out before the hands go round and slipped
    // back into the pile afterwards, which is the whole mod: the only way to meet it is
    // to draw it.
    const hidden = this.drawPile.filter((c) => c.k === Kind.DRAW_TWELVE);
    if (hidden.length) this.drawPile = this.drawPile.filter((c) => c.k !== Kind.DRAW_TWELVE);

    for (let round = 0; round < 7; round++) {
      for (const seat of this.seats) this.hands[seat].push(this.drawPile.pop());
    }

    const setAside = [];
    let starterCard = null;
    while (this.drawPile.length) {
      const c = this.drawPile.pop();
      if (c.k === Kind.NUMBER) { starterCard = c; break; }
      setAside.push(c);
    }
    // The deck always contains 76 number cards, so this cannot stay null.
    const start = starterCard ?? { i: -1, c: Color.RED, k: Kind.NUMBER, n: 0 };
    this.drawPile.push(...setAside);
    shuffleInPlace(this.drawPile, this.rng);

    // Somewhere at random in what is left, so nobody can count the deck down to it. The
    // draw comes off the end of the array, so index 0 is the very bottom.
    for (const card of hidden) {
      this.drawPile.splice(this.rng.nextIntBelow(this.drawPile.length + 1), 0, card);
    }

    // The jackpot is minted rather than dealt: it belongs to no deck, so its id sits past
    // every real card and cannot collide with one.
    if (jackpot) {
      const card = { i: JACKPOT_ID, c: Color.WILD, k: Kind.DRAW_FIFTY, n: -1 };
      this.drawPile.splice(this.rng.nextIntBelow(this.drawPile.length + 1), 0, card);
    }

    this.discardPile.push(start);
    this.activeColor = start.c;
    this.turn = Math.min(Math.max(starter, 0), this.playerCount - 1);
    this.direction = 1;
    this.pendingDraw = 0;
    this.pendingType = Penalty.NONE;
    this.extraPlays = 0;
    this.seen.forEach((s) => s.clear());
    this.phase = Phase.PLAYING;
    this.winner = null;
    this.drawnCardId = -1;
    this.lastPlayedBy = null;
    this.clearPenaltyMark();
    this.stats = this.seats.map(() => emptyStats());
    this.roundId++;
    this.pushEvent('Nouvelle manche');
  }

  // -------------------------------------------------------------------- legality

  legalCardIds(seat) {
    if (this.phase === Phase.GAME_OVER || seat !== this.turn) return [];
    const hand = this.hands[seat];
    if (!hand) return [];

    if (this.pendingDraw > 0) {
      let allowed;
      if (this.pendingType === Penalty.DRAW_TWO) {
        // Any +2 stacks onto a +2, and a wild penalty may be dropped on it too.
        allowed = hand.filter(isPenalty);
      } else if (this.pendingType === Penalty.DRAW_FOUR) {
        // A wild penalty is answered by another one, or by a +2 of the chosen colour.
        // The +8 is a +4 that hits harder, so it lands in both places.
        allowed = hand.filter((c) => c.k === Kind.DRAW_FOUR || c.k === Kind.DRAW_EIGHT
          || c.k === Kind.DRAW_TWELVE
          || c.k === Kind.DRAW_FIFTY
          || (c.k === Kind.DRAW_TWO && c.c === this.activeColor));
      } else {
        allowed = [];
      }
      return allowed.map((c) => c.i);
    }

    if (this.phase === Phase.DECIDE_AFTER_DRAW) {
      const drawn = hand.find((c) => c.i === this.drawnCardId);
      if (!drawn) return [];
      return this.matches(drawn) ? [drawn.i] : [];
    }

    return hand.filter((c) => this.matches(c)).map((c) => c.i);
  }

  /** Standard match rule: colour, or same number, or same symbol, or a wild. */
  matches(card) {
    if (isWild(card)) return true;
    if (card.c === this.activeColor) return true;
    const top = this.top();
    // After a wild, only the chosen colour matters — the wild itself has no symbol.
    if (isWild(top)) return false;
    if (card.k === Kind.NUMBER) return top.k === Kind.NUMBER && card.n === top.n;
    return card.k === top.k;
  }

  // ----------------------------------------------------------------------- moves

  playCard(seat, cardId, chosenColor) {
    if (!this.legalCardIds(seat).includes(cardId)) return false;
    const hand = this.hands[seat];
    const index = hand.findIndex((c) => c.i === cardId);
    if (index < 0) return false;
    if (isWild(hand[index]) && (!chosenColor || !isRealColor(chosenColor))) return false;

    const wasCountering = this.pendingDraw > 0;
    const card = hand.splice(index, 1)[0];
    // A card that has been played is no longer anybody's to hide.
    this.seen.forEach((s) => s.delete(card.i));
    this.discardPile.push(card);
    this.drawnCardId = -1;
    this.lastPlayedBy = seat;
    this.clearPenaltyMark();
    this.phase = Phase.PLAYING;

    const tally = this.stats[seat];
    tally.cp++;
    if (card.k === Kind.NUMBER) tally.nb++;
    else if (card.k === Kind.DRAW_TWO) tally.d2++;
    else if (card.k === Kind.DRAW_FOUR) tally.d4++;
    else if (card.k === Kind.DRAW_EIGHT) tally.e8++;
    else if (card.k === Kind.DRAW_TWELVE) tally.e12++;
    else if (card.k === Kind.DRAW_FIFTY) tally.e50++;
    else if (card.k === Kind.WILD) tally.wc++;
    else if (card.k === Kind.DOUBLE_PLAY) tally.dp++;
    else if (card.k === Kind.SPY) tally.sp++;
    else if (card.k === Kind.SKIP || card.k === Kind.REVERSE) tally.sk++;
    // Landing any attacking card on a pile somebody else started is a counter.
    if (wasCountering && isPenalty(card)) tally.ct++;
    // Counted on the way down to one, not on the way out: going out is a win, and
    // sitting on a single card is the thing that makes a round tense.
    if (hand.length === 1) tally.un++;

    const name = this.seatName(seat);
    switch (card.k) {
      case Kind.NUMBER:
        this.activeColor = card.c;
        this.turn = this.seatAfter(seat);
        this.pushEvent(`${name} pose ${cardLabel(card)}`);
        break;
      case Kind.SKIP: {
        this.activeColor = card.c;
        const skipped = this.seatAfter(seat);
        this.turn = this.seatAfter(seat, 2);
        this.pushEvent(`${name} saute le tour de ${this.seatName(skipped)}`);
        break;
      }
      case Kind.REVERSE:
        this.activeColor = card.c;
        if (this.playerCount === 2) {
          // No direction to flip in a duel, so it lands as a Skip.
          this.turn = this.seatAfter(seat, 2);
          this.pushEvent(`${name} inverse le sens — ${this.seatName(this.seatAfter(seat))} passe`);
        } else {
          this.direction = -this.direction;
          this.turn = this.seatAfter(seat);
          this.pushEvent(`${name} inverse le sens — à ${this.seatName(this.turn)}`);
        }
        break;
      case Kind.DRAW_TWO:
        this.activeColor = card.c;
        this.pendingDraw += 2;
        this.pendingType = Penalty.DRAW_TWO;
        tally.bd = Math.max(tally.bd, this.pendingDraw);
        this.turn = this.seatAfter(seat);
        this.pushEvent(`${name} pose +2 — total +${this.pendingDraw}`);
        break;
      case Kind.WILD:
        this.activeColor = chosenColor;
        this.turn = this.seatAfter(seat);
        this.pushEvent(`${name} choisit ${COLOR_NAME[this.activeColor]}`);
        break;
      case Kind.DRAW_FOUR:
        this.activeColor = chosenColor;
        this.pendingDraw += 4;
        this.pendingType = Penalty.DRAW_FOUR;
        tally.bd = Math.max(tally.bd, this.pendingDraw);
        this.turn = this.seatAfter(seat);
        this.pushEvent(`${name} pose +4 ${COLOR_NAME[this.activeColor]} — total +${this.pendingDraw}`);
        break;
      case Kind.DRAW_FIFTY:
        this.activeColor = chosenColor;
        this.pendingDraw += 50;
        // Same family again: the +50 is absurd, not a new rule. A +2 of the announced
        // colour still sends it on, which is the funniest thing that can happen to
        // somebody holding it.
        this.pendingType = Penalty.DRAW_FOUR;
        tally.bd = Math.max(tally.bd, this.pendingDraw);
        this.turn = this.seatAfter(seat);
        this.pushEvent(`${name} pose +50 ${COLOR_NAME[this.activeColor]} — total +${this.pendingDraw}`);
        break;
      case Kind.DRAW_TWELVE:
        this.activeColor = chosenColor;
        this.pendingDraw += 12;
        // Same family as the +4 and the +8, so every rule that reads the penalty type
        // treats the three the same way.
        this.pendingType = Penalty.DRAW_FOUR;
        tally.bd = Math.max(tally.bd, this.pendingDraw);
        this.turn = this.seatAfter(seat);
        this.pushEvent(`${name} pose +12 ${COLOR_NAME[this.activeColor]} — total +${this.pendingDraw}`);
        break;
      case Kind.DRAW_EIGHT:
        this.activeColor = chosenColor;
        this.pendingDraw += 8;
        // Deliberately the same penalty type as a +4: every rule that keys off the type
        // treats the two identically, which is the whole point of the mod.
        this.pendingType = Penalty.DRAW_FOUR;
        tally.bd = Math.max(tally.bd, this.pendingDraw);
        this.turn = this.seatAfter(seat);
        this.pushEvent(`${name} pose +8 ${COLOR_NAME[this.activeColor]} — total +${this.pendingDraw}`);
        break;
      case Kind.DOUBLE_PLAY:
        this.activeColor = chosenColor;
        // The turn stays put: the two bonus cards are laid down right now.
        this.turn = seat;
        this.pushEvent(`${name} joue un coup double en ${COLOR_NAME[this.activeColor]}`);
        break;
      case Kind.SPY: {
        this.activeColor = chosenColor;
        const victim = this.seatAfter(seat);
        const spied = this.revealOne(seat, victim);
        this.turn = victim;
        // Deliberately vague: the event line is read by the whole table, and naming the
        // card here would hand everyone what the Espion just bought.
        this.pushEvent(spied === null
          ? `${name} choisit ${COLOR_NAME[this.activeColor]} — rien à espionner`
          : `${name} espionne une carte de ${this.seatName(victim)}`);
        break;
      }
      default:
        break;
    }

    this.advanceBonus(card, seat);

    if (hand.length === 0) {
      this.winner = seat;
      this.phase = Phase.GAME_OVER;
      this.extraPlays = 0;
      this.scores[seat]++;
      // What everybody was left holding, frozen here because the hands are about to
      // stop changing and this is the only moment it means anything.
      this.seats.forEach((other) => { this.stats[other].cl = this.hands[other].length; });
      this.pushEvent(`${name} gagne la manche !`);
    }
    return true;
  }

  /**
   * Keeps the Coup double running, or ends it. Only a quiet card — a number or a Joker —
   * spends a bonus play and leaves the table where it is; anything that moves the turn on
   * ends the bonus, because the stack has to reach the next player.
   */
  advanceBonus(card, seat) {
    if (card.k === Kind.DOUBLE_PLAY) {
      this.extraPlays = BONUS_PLAYS;
      this.appendEvent(` — ${BONUS_PLAYS} cartes à poser`);
      return;
    }
    if (this.extraPlays === 0) return;

    const quiet = card.k === Kind.NUMBER || card.k === Kind.WILD;
    if (!quiet) { this.extraPlays = 0; return; }
    this.extraPlays--;
    this.turn = this.extraPlays > 0 ? seat : this.seatAfter(seat);
    if (this.extraPlays > 0) this.appendEvent(` — encore ${this.extraPlays}`);
  }

  /** Either eats the pending stack, or draws one card in a normal turn. */
  draw(seat) {
    if (seat !== this.turn || this.phase !== Phase.PLAYING) return false;
    // A Coup double is played out of the hand you already have.
    if (this.extraPlays > 0) return false;

    if (this.pendingDraw > 0) {
      const amount = this.pendingDraw;
      const skipTurn = this.pendingType === Penalty.DRAW_FOUR;
      for (let i = 0; i < amount; i++) this.drawOne(seat);
      this.pendingDraw = 0;
      this.pendingType = Penalty.NONE;
      this.drawnCardId = -1;
      this.penaltyTaken = amount;
      this.penaltyVictim = seat;
      const hit = this.stats[seat];
      hit.pt += amount;
      hit.bt = Math.max(hit.bt, amount);
      if (skipTurn) {
        // House rule: eating a +4 also costs you your turn.
        this.turn = this.seatAfter(seat);
        this.pushEvent(`${this.seatName(seat)} pioche ${amount} et passe son tour`);
      } else {
        // House rule: eating a +2 does NOT cost you your turn.
        this.pushEvent(`${this.seatName(seat)} pioche ${amount} et joue`);
      }
      return true;
    }

    this.clearPenaltyMark();
    const card = this.drawOne(seat);
    if (!card) {
      this.turn = this.seatAfter(seat);
      this.pushEvent(`Pioche vide — ${this.seatName(seat)} passe`);
      return true;
    }
    if (this.matches(card)) {
      this.drawnCardId = card.i;
      this.phase = Phase.DECIDE_AFTER_DRAW;
      this.pushEvent(`${this.seatName(seat)} pioche une carte`);
    } else {
      this.drawnCardId = -1;
      this.turn = this.seatAfter(seat);
      this.pushEvent(`${this.seatName(seat)} pioche et passe`);
    }
    return true;
  }

  pass(seat) {
    if (seat !== this.turn) return false;
    if (this.phase === Phase.PLAYING && this.extraPlays > 0) {
      this.extraPlays = 0;
      this.clearPenaltyMark();
      this.turn = this.seatAfter(seat);
      this.pushEvent(`${this.seatName(seat)} s'arrête là`);
      return true;
    }
    if (this.phase !== Phase.DECIDE_AFTER_DRAW) return false;
    this.drawnCardId = -1;
    this.clearPenaltyMark();
    this.phase = Phase.PLAYING;
    this.turn = this.seatAfter(seat);
    this.pushEvent(`${this.seatName(seat)} passe`);
    return true;
  }

  /**
   * Shows `watcher` one of `holder`'s cards, picked at random from the ones that watcher
   * has not already been shown. Null when there is nothing left to show. The candidates
   * are taken in the hand's own order, not the display order: the two engines must draw
   * the same card from the same seed.
   */
  revealOne(watcher, holder) {
    const known = this.seen[watcher];
    const candidates = this.hands[holder].filter((c) => !known.has(c.i));
    if (!candidates.length) return null;
    const card = candidates[this.rng.nextIntBelow(candidates.length)];
    known.add(card.i);
    return card;
  }

  /** The cards of `holder` that `watcher` has been shown. */
  revealedTo(watcher, holder) {
    if (watcher === holder) return [];
    return this.hands[holder].filter((c) => this.seen[watcher].has(c.i));
  }

  clearPenaltyMark() {
    this.penaltyTaken = 0;
    this.penaltyVictim = null;
  }

  drawOne(seat) {
    if (!this.drawPile.length) this.refillFromDiscard();
    if (!this.drawPile.length) return null;
    const card = this.drawPile.pop();
    this.hands[seat].push(card);
    this.stats[seat].cd++;
    return card;
  }

  refillFromDiscard() {
    if (this.discardPile.length <= 1) return;
    const top = this.discardPile.pop();
    this.drawPile.push(...this.discardPile);
    this.discardPile = [top];
    shuffleInPlace(this.drawPile, this.rng);
  }

  /**
   * Resolves only what the player has no say in: a stack they cannot counter is taken
   * for them. A normal draw stays a deliberate act.
   */
  autoAdvance() {
    // A Coup double with nothing left to lay is not a decision, it is a dead end.
    if (this.phase === Phase.PLAYING && this.extraPlays > 0
      && this.legalCardIds(this.turn).length === 0) {
      this.pass(this.turn);
    }
    let guard = 0;
    while (
      this.phase === Phase.PLAYING &&
      this.pendingDraw > 0 &&
      this.legalCardIds(this.turn).length === 0 &&
      guard++ < 300
    ) {
      if (!this.draw(this.turn)) return;
    }
  }

  // ------------------------------------------------------------------------ view

  viewFor(seat, rematch = new Set()) {
    return {
      y: seat,
      h: sortedHand(this.hands[seat]),
      l: this.legalCardIds(seat),
      t: this.top(),
      ac: this.activeColor,
      ts: this.turn,
      pd: this.pendingDraw,
      pt: this.pendingType,
      ph: this.phase,
      dk: this.drawPile.length,
      ri: this.rivalsFor(seat, rematch),
      w: this.winner,
      dc: this.turn === seat ? this.drawnCardId : -1,
      yn: this.seatName(seat),
      ev: this.event,
      ei: this.eventId,
      ry: rematch.has(seat),
      ys: this.scores[seat],
      rd: this.roundId,
      lp: this.lastPlayedBy,
      pk: this.penaltyTaken,
      pv: this.penaltyVictim,
      st: { ...this.stats[seat] },
      ya: this.avatars[seat],
      dr: this.direction,
      // Sent to everyone, not just the player on turn: the table wants to know why one
      // player is laying three cards in a row.
      xp: this.extraPlays,
      md: this.mods,
    };
  }

  /** The others, in seating order starting just after `seat` — not turn order. */
  rivalsFor(seat, rematch) {
    const out = [];
    for (let step = 1; step < this.playerCount; step++) {
      const other = (seat + step) % this.playerCount;
      out.push({
        s: other,
        n: this.seatName(other),
        a: this.avatars[other],
        c: this.hands[other].length,
        p: this.scores[other],
        r: rematch.has(other),
        // Only what *this* seat has been shown. Every player gets a different answer
        // here, which is the point of the Espion.
        rv: this.revealedTo(seat, other),
      });
    }
    return out;
  }

  pushEvent(text) {
    this.event = text;
    this.eventId++;
  }

  /** Adds to the line just pushed without counting as a second event. */
  appendEvent(suffix) {
    this.event += suffix;
  }
}

// Derived read-only helpers, mirroring GameView's computed properties in Kotlin.
export const view = {
  playerCount: (v) => v.ri.length + 1,
  yourTurn: (v) => v.ts === v.y && v.ph !== Phase.GAME_OVER,
  youWon: (v) => v.w === v.y,
  mustAnswerPenalty: (v) => v.pd > 0 && view.yourTurn(v),
  inBonus: (v) => (v.xp ?? 0) > 0 && v.ph === Phase.PLAYING,
  canPass: (v) => view.yourTurn(v)
    && (v.ph === Phase.DECIDE_AFTER_DRAW || view.inBonus(v)),
  canDraw: (v) => view.yourTurn(v) && v.ph === Phase.PLAYING && v.pd === 0
    && (v.xp ?? 0) === 0,
  mustDraw: (v) => view.canDraw(v) && v.l.length === 0,
  topCameFromOpponent: (v) => v.lp !== null && v.lp !== v.y,
  penaltyIsMine: (v) => v.pk > 0 && v.pv === v.y,
  rivalOf: (v, seat) => v.ri.find((r) => r.s === seat) ?? null,
  nameOf: (v, seat) => (seat === v.y ? v.yn : (view.rivalOf(v, seat)?.n ?? '?')),
  turnName: (v) => view.nameOf(v, v.ts),
  rematchReady: (v) => (v.ry ? 1 : 0) + v.ri.filter((r) => r.r).length,
};
