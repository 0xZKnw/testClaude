package com.zknw.unoduo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders a QR code straight onto a Canvas — no Bitmap allocation, and the modules
 * get slightly rounded corners so it matches the rest of the UI.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    foreground: Color = Color(0xFF0A0D12),
    background: Color = Color(0xFFFAFAFA)
) {
    val matrix = remember(content) { encode(content) }

    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(10.dp)
    ) {
        if (matrix == null) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val modules = matrix.width
            val cell = this.size.minDimension / modules
            // Rounding softens the corner a scanner uses to find a module edge, so it
            // stays subtle. And each module is snapped to the same grid its neighbours
            // use: computed independently, the rounding leaves hairline seams that read
            // as noise.
            val radius = CornerRadius(cell * 0.12f, cell * 0.12f)
            for (y in 0 until modules) {
                for (x in 0 until modules) {
                    if (!matrix.get(x, y)) continue
                    val left = (x * cell).toInt().toFloat()
                    val top = (y * cell).toInt().toFloat()
                    val right = ((x + 1) * cell).toInt().toFloat()
                    val bottom = ((y + 1) * cell).toInt().toFloat()
                    drawRoundRect(
                        color = foreground,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = radius
                    )
                }
            }
        }
    }
}

private fun encode(content: String): BitMatrix? = try {
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            // The standard asks for four blank modules around the code, and a decoder
            // really does need them to find its edges. Zero here left barely one and a
            // half — which is exactly the kind of thing that makes a scanner hesitate
            // for seconds instead of locking on at once.
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
    )
} catch (_: Exception) {
    null
}
