package com.zknw.unoduo.ui.components

import androidx.compose.ui.graphics.Color
import com.zknw.unoduo.progress.Motion
import kotlin.math.abs
import kotlin.math.cos

/**
 * The colour of fire and the colour of lightning, fixed rather than taken from whatever
 * is burning.
 *
 * Every cloth in the catalogue is dark on purpose — it sits under the cards — so lighting
 * one with its own tone lights nothing at all. These two are what make the effect read as
 * an element rather than as a brightness slider.
 */
val EMBER = Color(0xFFFF8A1E)
val LIGHTNING = Color(0xFFCFE4FF)

/**
 * The shape of the two top-tier movements, kept as plain maths in one place.
 *
 * A cloth, a card back, a pseudo, a title and a thrown sticker all wear the same [Motion]
 * and all have to burn or flash in the same rhythm — otherwise a level-100 player looks
 * like five unrelated effects sharing a screen. The clocks stay with each surface, since
 * a cloth breathes slower than a word; only the curve is shared.
 *
 * Both take a sawtooth beat in 0..1 and hand back an intensity in 0..1.
 */

private const val TWO_PI = (2 * Math.PI).toFloat()

/**
 * A fire swelling and dying back, with a faster flicker riding on top so it never looks
 * like a plain sine. Never reaches zero: embers stay lit between breaths.
 */
fun blazeHeat(beat: Float): Float {
    val swell = 0.5f - 0.5f * cos(beat * TWO_PI)
    val flicker = 0.5f - 0.5f * cos(beat * TWO_PI * 3.7f)
    return (0.18f + 0.62f * swell + 0.20f * flicker).coerceIn(0f, 1f)
}

/**
 * Lightning: dark almost all the time, then two strikes in quick succession.
 *
 * The long dark stretch is the whole point — a strobe that never rests reads as a broken
 * screen, and what makes a flash land is how long you waited for it.
 */
fun stormFlash(beat: Float): Float {
    val first = spike(beat, at = 0.02f, width = 0.045f)
    val second = spike(beat, at = 0.11f, width = 0.030f)
    return maxOf(first, second)
}

private fun spike(beat: Float, at: Float, width: Float): Float =
    (1f - abs(beat - at) / width).coerceIn(0f, 1f)

/** Movements whose clock must run one way only, because their curve is not symmetric. */
fun Motion.runsOneWay(): Boolean =
    this == Motion.SHEEN || this == Motion.BLAZE || this == Motion.STORM
