// Prints the level curve and the unlock catalogue exactly as web/test/CatalogueDump.kt
// prints them from the Kotlin build. Any difference between the two outputs is a
// difference between what the phone rewards and what the browser rewards.
//
//   node web/test/catalogue.mjs

import * as L from '../src/levels.js';
import { ALL, KINDS } from '../src/cosmetics.js';

const out = [];
out.push(`MAX=${L.MAX_LEVEL} WIN=${L.XP_WIN} LOSS=${L.XP_LOSS} RUN=${L.fullRun()}`);
for (let level = 1; level <= L.MAX_LEVEL; level++) {
  out.push(`lvl ${level} cost=${L.costOf(level)} total=${L.totalTo(level)}`);
}
for (let xp = 0; xp <= L.fullRun() + 200; xp += 137) {
  out.push(
    `xp ${xp} lvl=${L.levelAt(xp)} into=${L.into(xp)} span=${L.span(xp)} `
    + `pct=${L.percent(xp)} next=${L.toNext(xp)}`,
  );
}

/** '#rrggbb' -> 'RRGGBB'; empty stays a dash, matching the Kotlin dump. */
const hex = (value) => (value ? value.replace('#', '').toUpperCase() : '-');

for (const item of ALL) {
  out.push(
    `item ${item.id} ${KINDS[item.kind].code} ${item.level} ${item.style} ${item.motion} `
    + `${hex(item.a)} ${hex(item.b)} ${hex(item.c)} ${item.text} ${item.name}`,
  );
}

process.stdout.write(`${out.join('\n')}\n`);
