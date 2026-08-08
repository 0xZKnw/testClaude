package com.zknw.unoduo.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.zknw.unoduo.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How a player wants to be shown: a picked photo, or a colour plus their initial. */
data class AvatarLook(val colorIndex: Int, val photoUri: String? = null)

/**
 * Ink-outlined round avatar. A photo wins when there is one; otherwise the initial
 * sits on a flat colour, drawn in the same cartoon lettering as the cards.
 */
@Composable
fun PlayerAvatar(
    name: String,
    look: AvatarLook,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Dp = 3.dp,
    ringColor: androidx.compose.ui.graphics.Color = Palette.Outline
) {
    val context = LocalContext.current
    val bitmap = rememberAvatarBitmap(context, look.photoUri, size)

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Palette.avatarColor(look.colorIndex))
            .border(ring, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            OutlinedGlyphText(
                text = name.trim().take(1).uppercase().ifBlank { "?" },
                fontSize = (size.value * 0.44f).sp,
                fill = Palette.Stock,
                outlineWidth = size * 0.035f
            )
        }
    }
}

/**
 * Decodes the picked photo once, downsampled to roughly what the avatar needs —
 * loading a full-resolution photo for a 40dp circle is how you run out of memory.
 */
@Composable
private fun rememberAvatarBitmap(context: Context, uri: String?, size: Dp): ImageBitmap? {
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    val targetPx = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }

    LaunchedEffect(uri, targetPx) {
        if (uri.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) { decode(context, uri, targetPx) }
    }
    return bitmap
}

private fun decode(context: Context, uri: String, targetPx: Int): ImageBitmap? = try {
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    val wanted = targetPx.coerceAtLeast(96) * 2
    while (bounds.outWidth / sample > wanted || bounds.outHeight / sample > wanted) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(parsed)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
    }
} catch (_: Exception) {
    null
}
