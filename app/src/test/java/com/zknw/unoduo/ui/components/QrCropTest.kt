package com.zknw.unoduo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crop decides which part of the camera frame is even looked at. A mistake here
 * reads the wrong band of the image, and the symptom is not an exception — it is a
 * scanner that simply never finds anything, which is indistinguishable from a bad one.
 */
class QrCropTest {

    @Test
    fun `the window is centred on the frame`() {
        val w = QrCrop.centred(1280, 720)
        // An odd remainder leaves one pixel on one side; anything more is off-centre.
        assertTrue("marges horizontales : ${w.left} et ${1280 - w.side - w.left}",
            kotlin.math.abs((1280 - w.side - w.left) - w.left) <= 1)
        assertTrue("marges verticales : ${w.top} et ${720 - w.side - w.top}",
            kotlin.math.abs((720 - w.side - w.top) - w.top) <= 1)
    }

    @Test
    fun `the window never leaves the frame`() {
        val sizes = listOf(
            640 to 480, 1280 to 720, 1920 to 1080, 720 to 1280, 480 to 640, 1 to 1, 4 to 3000,
        )
        for ((width, height) in sizes) {
            val w = QrCrop.centred(width, height)
            assertTrue("$width x $height : gauche", w.left >= 0)
            assertTrue("$width x $height : haut", w.top >= 0)
            assertTrue("$width x $height : déborde à droite", w.left + w.side <= width)
            assertTrue("$width x $height : déborde en bas", w.top + w.side <= height)
            assertTrue("$width x $height : côté nul", w.side >= 1)
        }
    }

    @Test
    fun `the window covers more than the viewfinder the player aims with`() {
        // The viewfinder is 68 % of the square preview, and the preview is the middle
        // square of the frame. Decoding less than that would fail on a code the player
        // can plainly see inside the box.
        val w = QrCrop.centred(1280, 720)
        val previewSquare = 720
        assertTrue(
            "la fenêtre (${w.side}) doit dépasser le viseur (${previewSquare * 0.68})",
            w.side > previewSquare * 0.68,
        )
        assertTrue("mais rester plus petite que l'image", w.side < previewSquare)
    }

    @Test
    fun `a portrait frame is handled the same way as a landscape one`() {
        val landscape = QrCrop.centred(1280, 720)
        val portrait = QrCrop.centred(720, 1280)
        assertEquals(landscape.side, portrait.side)
        assertEquals(landscape.left, portrait.top)
        assertEquals(landscape.top, portrait.left)
    }
}
