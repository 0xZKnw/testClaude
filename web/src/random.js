// Kotlin's random number generator, reimplemented.
//
// The rules engine exists twice now: in Kotlin for the Android app, in JavaScript for
// this page. Identical rules are not enough to trust that — the only way to *prove* the
// two agree is to make them play the very same games and compare move by move, and that
// needs the very same shuffles.
//
// So this is `kotlin.random.Random` exactly: the XorWow generator, its seeding, its
// warm-up, its bounded-integer rejection loop and its Fisher-Yates. Any deviation shows
// up immediately in web/test/conformance — the traces stop matching.
//
// Everything is forced back to 32-bit with `| 0` and `>>> 0`, because JavaScript numbers
// are doubles and Kotlin's Int overflow has to wrap rather than grow.

export class KotlinRandom {
  /** Mirrors `Random(seed: Long)`, which splits the seed into two Ints. */
  constructor(seedLow, seedHigh = 0) {
    const seed1 = seedLow | 0;
    const seed2 = seedHigh | 0;
    this.x = seed1;
    this.y = seed2;
    this.z = 0;
    this.w = 0;
    this.v = ~seed1;
    this.addend = ((seed1 << 10) ^ (seed2 >>> 4)) | 0;
    if ((this.x | this.y | this.z | this.w | this.v) === 0) {
      throw new Error('graine invalide : au moins un mot doit être non nul');
    }
    // Kotlin discards the first 64 draws so a weak seed cannot show through.
    for (let i = 0; i < 64; i++) this.nextInt();
  }

  nextInt() {
    let t = this.x;
    t = (t ^ (t >>> 2)) | 0;
    this.x = this.y;
    this.y = this.z;
    this.z = this.w;
    const v0 = this.v;
    this.w = v0;
    t = (t ^ (t << 1) ^ v0 ^ (v0 << 4)) | 0;
    this.v = t;
    this.addend = (this.addend + 362437) | 0;
    return (t + this.addend) | 0;
  }

  /** `Int.takeUpperBits`: the high bits are the well-mixed ones. */
  nextBits(bitCount) {
    return (this.nextInt() >>> (32 - bitCount)) & (-bitCount >> 31);
  }

  /**
   * `nextInt(until)`. A power of two takes the high bits directly; anything else
   * rejects the tail of the range so every value stays equally likely.
   */
  nextIntBelow(until) {
    if (until <= 0) throw new Error('borne invalide');
    const n = until;
    if ((n & -n) === n) {
      // fastLog2 of a power of two.
      let bitCount = 31 - Math.clz32(n);
      return this.nextBits(bitCount);
    }
    let v;
    let bits;
    do {
      bits = this.nextInt() >>> 1;
      v = bits % n;
    } while (((bits - v + (n - 1)) | 0) < 0);
    return v;
  }

  nextDouble() {
    // Kotlin builds a double from 26 then 27 bits.
    return (this.nextBits(26) * (1 << 27) + this.nextBits(27)) / Math.pow(2, 53);
  }
}

/** `List.shuffled(random)`: Fisher-Yates from the end, exactly as Kotlin walks it. */
export function shuffled(items, random) {
  const out = items.slice();
  for (let i = out.length - 1; i >= 1; i--) {
    const j = random.nextIntBelow(i + 1);
    const copy = out[i];
    out[i] = out[j];
    out[j] = copy;
  }
  return out;
}

/** In-place, for the deck being reshuffled mid-round. */
export function shuffleInPlace(items, random) {
  for (let i = items.length - 1; i >= 1; i--) {
    const j = random.nextIntBelow(i + 1);
    const copy = items[i];
    items[i] = items[j];
    items[j] = copy;
  }
  return items;
}
