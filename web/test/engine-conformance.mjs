// Locks the JavaScript engine to the Kotlin one.
//
// The two were compared move by move — 1440 complete games covering the plain game, each mod on its own and all of them at
// once, every shuffle, every event string — and the resulting trace was hashed. Re-running
// this checks the JavaScript side still produces that exact trace, so any drift in the
// rules or in the bot breaks the build instead of quietly changing how the web version
// plays.
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
const reference = readFileSync(join(here, 'trace-reference.sha256'), 'utf8').trim().split('\n');
const expected = reference[0].trim().split(/\s+/)[0];

// The trace is a few hundred megabytes of text and grows every time a case is added to
// it, so the ceiling is set well above what it needs rather than just above: a run that
// dies on ENOBUFS looks exactly like a run that found nothing wrong.
const trace = execFileSync(process.execPath, [join(here, 'trace.mjs')], {
  maxBuffer: 512 * 1024 * 1024,
  encoding: 'utf8',
});
const actual = createHash('sha256').update(trace, 'utf8').digest('hex');
const rows = trace.trimEnd().split('\n');
const lines = rows.length;

// The second line of the reference is the hash of everything that existed before the
// +50. Checking it here rather than by hand is what keeps "a normal round is untouched"
// an actual guarantee instead of a claim in a commit message.
const [beforeHash, beforeCount] = reference[1].match(/^(\S+).*?(\d{4,})/).slice(1);
const prefix = createHash('sha256')
  .update(`${rows.slice(0, Number(beforeCount)).join('\n')}\n`, 'utf8')
  .digest('hex');
if (prefix !== beforeHash) {
  console.log('\n  DIVERGENCE : le +50 a bouge des parties qui existaient avant lui.');
  console.log(`  attendu : ${beforeHash}`);
  console.log(`  obtenu  : ${prefix}\n`);
  process.exit(1);
}
console.log(`  Les ${beforeCount} premieres lignes, d'avant le +50, sont inchangees.`);

console.log(`\n  ${lines} etats rejoues (1920 parties, 2 a 5 joueurs, tous les mods, le +50, moteur + bot)`);
if (actual !== expected) {
  console.log(`\n  DIVERGENCE : le moteur JavaScript ne joue plus les memes parties.`);
  console.log(`  attendu : ${expected}`);
  console.log(`  obtenu  : ${actual}\n`);
  process.exit(1);
}
console.log(`  Trace identique a celle du moteur Kotlin (${actual.slice(0, 16)}…)\n`);
