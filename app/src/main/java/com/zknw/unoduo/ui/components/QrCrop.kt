package com.zknw.unoduo.ui.components

/**
 * Where in the camera frame to look.
 *
 * Kept apart from the camera so the arithmetic can be tested: getting it wrong reads a
 * band of the wrong part of the image, which fails in a way that looks like "the scanner
 * is just bad" rather than like a bug.
 */
object QrCrop {

    /**
     * The viewfinder is a square covering [VIEWFINDER] of a square preview, and the
     * preview itself shows the middle square of a wider camera frame. Decoding a little
     * more than the viewfinder shows means a slightly off aim still reads.
     */
    private const val VIEWFINDER = 0.68f
    private const val MARGIN = 1.2f

    data class Window(val left: Int, val top: Int, val side: Int)

    fun centred(width: Int, height: Int): Window {
        val shortest = minOf(width, height)
        val side = (shortest * VIEWFINDER * MARGIN).toInt().coerceIn(1, shortest)
        return Window(
            left = ((width - side) / 2).coerceAtLeast(0),
            top = ((height - side) / 2).coerceAtLeast(0),
            side = side
        )
    }
}
