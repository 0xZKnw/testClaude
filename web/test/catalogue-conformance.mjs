// Locks the JavaScript unlock catalogue to the Kotlin one.
//
// Levels, costs, bar readings and every one of the cosmetics — id, family, level, style,
// colours, emoji, name — are printed by both builds and compared. A reward moved to a
// different level on one side, or a colour typed wrong, breaks the build instead of
// quietly giving phone and browser players different games.
//
// To regenerate after a deliberate change, run both dumps and diff them:
//   kotlinc app/src/main/java/com/zknw/unoduo/progress/*.kt web/test/CatalogueDump.kt -d out
//   java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out CatalogueDumpKt > k.txt
//   node web/test/catalogue.mjs > js.txt && diff k.txt js.txt
// then store the sha256 of k.txt in catalogue-reference.sha256.
//
//   node web/test/catalogue-conformance.mjs

import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const expected = readFileSync(join(here, 'catalogue-reference.sha256'), 'utf8').trim().split(/\s+/)[0];

const dump = execFileSync(process.execPath, [join(here, 'catalogue.mjs')], { encoding: 'utf8' });
const actual = createHash('sha256').update(dump, 'utf8').digest('hex');
const lines = dump.trimEnd().split('\n').length;

console.log(`\n  ${lines} lignes de catalogue rejouees (courbe de niveaux + cosmetiques)`);
if (actual !== expected) {
  console.log('\n  DIVERGENCE : le catalogue JavaScript ne correspond plus au catalogue Kotlin.');
  console.log(`  attendu : ${expected}`);
  console.log(`  obtenu  : ${actual}\n`);
  process.exit(1);
}
console.log(`  Catalogue identique a celui de l'app Android (${actual.slice(0, 16)}…)\n`);
