// Replays, in JavaScript, exactly the games web/test/TraceDump.kt replays in Kotlin,
// and prints the same trace. The two outputs are compared line by line.
//
//   node web/test/trace.mjs > /tmp/trace-js.txt

import { UnoEngine, Phase, Color, view as V } from '../src/engine.js';
import { decide, Difficulty, DIFFICULTY_ORDER } from '../src/bot.js';
import { KotlinRandom } from '../src/random.js';

const KIND_ORDER = {
  n: 0, s: 1, r: 2, d2: 3, w: 4, d4: 5, d8: 6, x2: 7, sp: 8, d12: 9, d50: 10,
};
const COLOR_ORDER = { R: 0, Y: 1, G: 2, B: 3, W: 4 };

// Kotlin prints enum names; map the wire codes back so the traces read the same.
const PHASE_NAME = { p: 'PLAYING', d: 'DECIDE_AFTER_DRAW', o: 'GAME_OVER' };
const COLOR_ENUM = { R: 'RED', Y: 'YELLOW', G: 'GREEN', B: 'BLUE', W: 'WILD' };
const PENALTY_NAME = { 0: 'NONE', 2: 'DRAW_TWO', 4: 'DRAW_FOUR' };

function snapshot(tag, e) {
  const hands = e.hands.map((h) => h.length).join(',');
  const seat0 = e.handOf(0)
    .slice()
    .sort((a, b) => (COLOR_ORDER[a.c] - COLOR_ORDER[b.c]) ||
      (KIND_ORDER[a.k] - KIND_ORDER[b.k]) || (a.n - b.n))
    .map((c) => c.i)
    .join('.');
  // What each seat has been shown by its own Espions, sorted so the two engines cannot
  // differ on set iteration order alone.
  const faceUp = e.seen
    .map((ids) => [...ids].sort((a, b) => a - b).join('.'))
    .join(',');
  return [
    tag, e.turn, PHASE_NAME[e.phase], COLOR_ENUM[e.activeColor], e.pendingDraw,
    PENALTY_NAME[e.pendingType], e.deckCount(), hands, e.top().i, e.direction, e.extraPlays,
    faceUp, e.winner === null ? 'null' : e.winner, seat0, e.event,
  ].join('|');
}

const lines = [];

// The plain game first, so a diff against an older trace starts with the lines that are
// supposed to be untouched.
// The jackpot passes are last, so everything above them is byte-for-byte what it was
// before the +50 existed — which is the claim worth being able to check.
const MOD_SETS = [
  ['', [], false],
  ['8', ['d8'], false],
  ['D', ['x2'], false],
  ['S', ['sp'], false],
  ['T', ['d12'], false],
  ['X', ['d8', 'x2', 'sp', 'd12'], false],
  ['j', [], true],
  ['J', ['d8', 'x2', 'sp', 'd12'], true],
];

for (const [tag, mods, jackpot] of MOD_SETS) {
  for (let players = 2; players <= 5; players++) {
    for (let seed = 1; seed <= 30; seed++) {
      // ---- policy A: always the lowest legal card
      let e = new UnoEngine(seed, 0, players, mods);
      e.startRound(seed % players, jackpot);
      lines.push(snapshot(`A${tag}${players}/${seed} deal`, e));
      let guard = 0;
      while (e.phase !== Phase.GAME_OVER && guard++ < 4000) {
        e.autoAdvance();
        lines.push(snapshot(`A${tag}${players}/${seed} auto`, e));
        if (e.phase === Phase.GAME_OVER) break;
        const seat = e.turn;
        const legal = e.legalCardIds(seat).slice().sort((a, b) => a - b);
        let action;
        if (legal.length) {
          e.playCard(seat, legal[0], Color.RED);
          action = `play${legal[0]}`;
        } else if (V.canPass(e.viewFor(seat))) {
          e.pass(seat);
          action = 'pass';
        } else {
          e.draw(seat);
          action = 'draw';
        }
        lines.push(snapshot(`A${tag}${players}/${seed} ${action}`, e));
      }

      // ---- policy B: the bot, every level
      const rng = new KotlinRandom(seed * 31, 0);
      e = new UnoEngine(seed, 0, players, mods);
      e.startRound(seed % players, jackpot);
      const levels = [...Array(players).keys()]
        .map((i) => DIFFICULTY_ORDER[i % DIFFICULTY_ORDER.length]);
      guard = 0;
      while (e.phase !== Phase.GAME_OVER && guard++ < 4000) {
        e.autoAdvance();
        if (e.phase === Phase.GAME_OVER) break;
        const seat = e.turn;
        const move = decide(e.viewFor(seat), levels[seat], rng);
        let action;
        if (move.kind === 'play') {
          e.playCard(seat, move.cardId, move.color);
          action = `play${move.cardId}/${move.color === null ? 'null' : COLOR_ENUM[move.color]}`;
        } else if (move.kind === 'draw') {
          e.draw(seat);
          action = 'draw';
        } else {
          e.pass(seat);
          action = 'pass';
        }
        lines.push(snapshot(`B${tag}${players}/${seed} ${action}`, e));
      }
    }
  }
}

process.stdout.write(lines.join('\n') + '\n');
