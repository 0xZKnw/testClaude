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
            .padding(14.dp)
    ) {
        if (matrix == null) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val modules = matrix.width
            val cell = this.size.minDimension / modules
            val radius = CornerRadius(cell * 0.28f, cell * 0.28f)
            for (y in 0 until modules) {
                for (x in 0 until modules) {
                    if (!matrix.get(x, y)) continue
                    drawRoundRect(
                        color = foreground,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
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
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
    )
} catch (_: Exception) {
    null
}
