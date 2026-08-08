// Checks the JavaScript generator against sequences captured from Kotlin's own.
//
// The reference file is produced by web/test/RngDump.kt, run with a standalone Kotlin
// compiler. If a single number differs, the two engines would deal different hands and
// every later comparison would be meaningless.
//
//   node web/test/random-conformance.mjs

import { KotlinRandom, shuffled } from '../src/random.js';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const reference = readFileSync(join(here, 'random-reference.txt'), 'utf8').trim().split('\n');

/** Kotlin splits a Long seed into two Ints; JavaScript has no Long, so do it by hand. */
function seedParts(seed) {
  const big = BigInt(seed);
  return [Number(BigInt.asIntN(32, big)), Number(BigInt.asIntN(32, big >> 32n))];
}

let checked = 0;
const problems = [];

function same(label, mine, theirs) {
  checked++;
  if (mine.join(',') !== theirs) {
    problems.push(`${label}\n    kotlin : ${theirs}\n    js     : ${mine.join(',')}`);
  }
}

const draw = (random, count, fn) => Array.from({ length: count }, () => fn(random));

for (const line of reference) {
  const [seed, ints, bounded, pow2, order] = line.split('|');
  const [low, high] = seedParts(seed);

  same(`graine ${seed} — nextInt()`,
    draw(new KotlinRandom(low, high), 8, (r) => r.nextInt()), ints);

  same(`graine ${seed} — nextInt(108), avec rejet`,
    draw(new KotlinRandom(low, high), 8, (r) => r.nextIntBelow(108)), bounded);

  same(`graine ${seed} — nextInt(64), puissance de deux`,
    draw(new KotlinRandom(low, high), 8, (r) => r.nextIntBelow(64)), pow2);

  same(`graine ${seed} — shuffled()`,
    shuffled([...Array(12).keys()], new KotlinRandom(low, high)), order);
}

console.log(`\n  ${checked} sequences comparees sur ${reference.length} graines`);
if (problems.length) {
  console.log('\n  DIVERGENCES :\n');
  problems.forEach((p) => console.log('  ' + p + '\n'));
  process.exit(1);
}
console.log('  Le generateur JavaScript est identique a celui de Kotlin.\n');
