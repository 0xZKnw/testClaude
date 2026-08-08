package com.zknw.unoduo.ui.components

import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Turns camera frames into text. Holds its own scratch buffer, so a scanning session
 * allocates once instead of once per frame — thirty times a second of garbage is enough
 * to make the preview stutter on a modest phone.
 *
 * Not thread-safe: it is driven from the analyzer's single executor.
 */
class QrDecoder {

    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    private var scratch = ByteArray(0)
    private var frame = 0

    fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        if (rowStride <= 0 || image.height <= 0) return null

        val window = QrCrop.centred(image.width, image.height)

        // Only the rows the window covers are copied, and ZXing is told the real stride,
        // so nothing has to be repacked row by row.
        val needed = window.side * rowStride
        if (scratch.size < needed) scratch = ByteArray(needed)
        val start = window.top * rowStride
        if (buffer.capacity() < start + needed) return null
        buffer.position(start)
        buffer.get(scratch, 0, needed)

        val source = PlanarYUVLuminanceSource(
            scratch, rowStride, window.side,
            window.left, 0, window.side, window.side,
            false
        )

        frame++
        read(source)?.let { return it }

        // A light-on-dark code is rare enough that trying it on every frame would halve
        // the frame rate to cover a case that mostly does not happen. Once in a while is
        // enough for it to still be found.
        if (frame % INVERT_EVERY == 0) {
            read(source.invert())?.let { return it }
        }
        return null
    }

    private fun read(source: PlanarYUVLuminanceSource) = try {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }

    private companion object {
        const val INVERT_EVERY = 8
    }
}
