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
};
const KIND_ORDER = { n: 0, s: 1, r: 2, d2: 3, w: 4, d4: 5 };

export const Penalty = { NONE: '0', DRAW_TWO: '2', DRAW_FOUR: '4' };
export const Phase = { PLAYING: 'p', DECIDE_AFTER_DRAW: 'd', GAME_OVER: 'o' };

export const HOST_SEAT = 0;
export const MIN_PLAYERS = 2;
export const MAX_PLAYERS = 5;

export const isWild = (card) => card.k === Kind.WILD || card.k === Kind.DRAW_FOUR;
export const isRealColor = (color) => color !== Color.WILD;

const COLOR_NAME = { R: 'rouge', Y: 'jaune', G: 'vert', B: 'bleu', W: '-' };

export function cardLabel(card) {
  const kindName = {
    n: String(card.n), s: 'Passe', r: 'Sens interdit', d2: '+2', w: 'Joker', d4: '+4',
  }[card.k];
  const colorName = card.c === Color.WILD ? '' : COLOR_NAME[card.c];
  return colorName ? `${kindName} ${colorName}` : kindName;
}

/** The classic 108-card deck, built in the same order so ids line up with Kotlin's. */
export function standardDeck() {
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

function emptyStats() {
  return {
    cp: 0, cd: 0, d2: 0, d4: 0, w: 0, sk: 0, co: 0, pt: 0, bt: 0, bd: 0,
  };
}

export class UnoEngine {
  constructor(seedLow, seedHigh, playerCount) {
    if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
      throw new Error(`Une partie se joue de ${MIN_PLAYERS} à ${MAX_PLAYERS} joueurs`);
    }
    this.rng = new KotlinRandom(seedLow, seedHigh);
    this.playerCount = playerCount;
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
  startRound(starter) {
    this.hands.forEach((h) => { h.length = 0; });
    this.drawPile = shuffled(standardDeck(), this.rng);
    this.discardPile = [];

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

    this.discardPile.push(start);
    this.activeColor = start.c;
    this.turn = Math.min(Math.max(starter, 0), this.playerCount - 1);
    this.direction = 1;
    this.pendingDraw = 0;
    this.pendingType = Penalty.NONE;
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
        // Any +2 stacks onto a +2, and a +4 may be dropped on it too.
        allowed = hand.filter((c) => c.k === Kind.DRAW_TWO || c.k === Kind.DRAW_FOUR);
      } else if (this.pendingType === Penalty.DRAW_FOUR) {
        // A +4 is answered by another +4, or by a +2 of the chosen colour.
        allowed = hand.filter((c) =>
          c.k === Kind.DRAW_FOUR || (c.k === Kind.DRAW_TWO && c.c === this.activeColor));
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
    this.discardPile.push(card);
    this.drawnCardId = -1;
    this.lastPlayedBy = seat;
    this.clearPenaltyMark();
    this.phase = Phase.PLAYING;

    const tally = this.stats[seat];
    tally.cp++;
    if (card.k === Kind.DRAW_TWO) { tally.d2++; if (wasCountering) tally.co++; }
    else if (card.k === Kind.DRAW_FOUR) { tally.d4++; if (wasCountering) tally.co++; }
    else if (card.k === Kind.WILD) tally.w++;
    else if (card.k === Kind.SKIP || card.k === Kind.REVERSE) tally.sk++;

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
      default:
        break;
    }

    if (hand.length === 0) {
      this.winner = seat;
      this.phase = Phase.GAME_OVER;
      this.scores[seat]++;
      this.pushEvent(`${name} gagne la manche !`);
    }
    return true;
  }

  /** Either eats the pending stack, or draws one card in a normal turn. */
  draw(seat) {
    if (seat !== this.turn || this.phase !== Phase.PLAYING) return false;

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
    if (seat !== this.turn || this.phase !== Phase.DECIDE_AFTER_DRAW) return false;
    this.drawnCardId = -1;
    this.clearPenaltyMark();
    this.phase = Phase.PLAYING;
    this.turn = this.seatAfter(seat);
    this.pushEvent(`${this.seatName(seat)} passe`);
    return true;
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
      });
    }
    return out;
  }

  pushEvent(text) {
    this.event = text;
    this.eventId++;
  }
}

// Derived read-only helpers, mirroring GameView's computed properties in Kotlin.
export const view = {
  playerCount: (v) => v.ri.length + 1,
  yourTurn: (v) => v.ts === v.y && v.ph !== Phase.GAME_OVER,
  youWon: (v) => v.w === v.y,
  mustAnswerPenalty: (v) => v.pd > 0 && view.yourTurn(v),
  canPass: (v) => v.ph === Phase.DECIDE_AFTER_DRAW && view.yourTurn(v),
  canDraw: (v) => view.yourTurn(v) && v.ph === Phase.PLAYING && v.pd === 0,
  mustDraw: (v) => view.canDraw(v) && v.l.length === 0,
  topCameFromOpponent: (v) => v.lp !== null && v.lp !== v.y,
  penaltyIsMine: (v) => v.pk > 0 && v.pv === v.y,
  rivalOf: (v, seat) => v.ri.find((r) => r.s === seat) ?? null,
  nameOf: (v, seat) => (seat === v.y ? v.yn : (view.rivalOf(v, seat)?.n ?? '?')),
  turnName: (v) => view.nameOf(v, v.ts),
  rematchReady: (v) => (v.ry ? 1 : 0) + v.ri.filter((r) => r.r).length,
};
