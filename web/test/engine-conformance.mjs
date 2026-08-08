// Locks the JavaScript engine to the Kotlin one.
//
// The two were compared move by move — 240 complete games, every shuffle, every event
// string — and the resulting trace was hashed. Re-running this checks the JavaScript
// side still produces that exact trace, so any drift in the rules or in the bot breaks
// the build instead of quietly changing how the web version plays.
//
// To regenerate the reference after a deliberate rule change, both sides must be
// re-run and re-compared; see web/README.md.
//
//   node web/test/engine-conformance.mjs

import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const expected = readFileSync(join(here, 'trace-reference.sha256'), 'utf8').trim().split(/\s+/)[0];

const trace = execFileSync(process.execPath, [join(here, 'trace.mjs')], {
  maxBuffer: 64 * 1024 * 1024,
  encoding: 'utf8',
});
const actual = createHash('sha256').update(trace, 'utf8').digest('hex');
const lines = trace.trimEnd().split('\n').length;

console.log(`\n  ${lines} etats rejoues (240 parties, 2 a 5 joueurs, moteur + bot)`);
if (actual !== expected) {
  console.log(`\n  DIVERGENCE : le moteur JavaScript ne joue plus les memes parties.`);
  console.log(`  attendu : ${expected}`);
  console.log(`  obtenu  : ${actual}\n`);
  process.exit(1);
}
console.log(`  Trace identique a celle du moteur Kotlin (${actual.slice(0, 16)}…)\n`);
