package com.zknw.unoduo.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Profile pictures that can cross a Bluetooth link.
 *
 * A picked photo is a `content://` URI, which means nothing on anyone else's phone, so
 * the image itself has to travel. It is squared, shrunk to a thumbnail and compressed
 * hard: at [SIDE_PX] pixels a JPEG lands around three kilobytes, which is a couple of
 * dozen BLE frames sent once when a player joins — against a few hundred bytes for a
 * game snapshot sent on every move.
 */
object AvatarImage {

    /** Enough for the biggest avatar the UI draws, on a dense screen. */
    const val SIDE_PX = 128

    private const val QUALITY = 72

    /** Refuses anything that would clog the link, however it got there. */
    const val MAX_ENCODED_CHARS = 24_000

    /** Reads the picked photo and returns it Base64-encoded, or null if unreadable. */
    fun encode(context: Context, uri: String): String? = try {
        val parsed = Uri.parse(uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > SIDE_PX * 2 || bounds.outHeight / sample > SIDE_PX * 2) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(parsed)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        decoded?.let { bitmap ->
            val square = centerCrop(bitmap)
            val out = ByteArrayOutputStream()
            square.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            if (square !== bitmap) square.recycle()
            bitmap.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                .takeIf { it.length <= MAX_ENCODED_CHARS }
        }
    } catch (_: Exception) {
        null
    }

    fun decode(data: String): Bitmap? = try {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }

    /** Squares the photo on its centre, then scales it down to the thumbnail size. */
    private fun centerCrop(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        if (side <= 0) return source
        val cropped = Bitmap.createBitmap(
            source,
            (source.width - side) / 2,
            (source.height - side) / 2,
            side,
            side
        )
        if (side <= SIDE_PX) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, SIDE_PX, SIDE_PX, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }
}
