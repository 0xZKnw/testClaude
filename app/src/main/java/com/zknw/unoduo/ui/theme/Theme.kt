package com.zknw.unoduo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.CardColor

/** The table palette. Deliberately dark and desaturated so the cards carry the colour. */
object Palette {
    val Ink = Color(0xFF0A0D12)
    val Night = Color(0xFF11161F)
    val Slate = Color(0xFF1A2230)
    val SlateHigh = Color(0xFF243043)
    val Line = Color(0xFF2E3A4E)
    val Text = Color(0xFFEDF1F7)
    val TextDim = Color(0xFF95A2B6)
    val Gold = Color(0xFFF2C14E)

    val FeltCore = Color(0xFF16513F)
    val FeltEdge = Color(0xFF0A241D)

    val Red = Color(0xFFE0342C)
    val RedDeep = Color(0xFFB2211B)
    val Yellow = Color(0xFFF6B819)
    val YellowDeep = Color(0xFFCE930A)
    val Green = Color(0xFF3BA34C)
    val GreenDeep = Color(0xFF2A7838)
    val Blue = Color(0xFF2A7CD8)
    val BlueDeep = Color(0xFF1B5AA6)
    val Wild = Color(0xFF181C24)
    val WildDeep = Color(0xFF0B0E13)

    fun face(color: CardColor): Color = when (color) {
        CardColor.RED -> Red
        CardColor.YELLOW -> Yellow
        CardColor.GREEN -> Green
        CardColor.BLUE -> Blue
        CardColor.WILD -> Wild
    }

    fun faceDeep(color: CardColor): Color = when (color) {
        CardColor.RED -> RedDeep
        CardColor.YELLOW -> YellowDeep
        CardColor.GREEN -> GreenDeep
        CardColor.BLUE -> BlueDeep
        CardColor.WILD -> WildDeep
    }

    /** Ink used for the glyph printed inside the white oval. */
    fun glyph(color: CardColor): Color = when (color) {
        CardColor.YELLOW -> YellowDeep
        CardColor.WILD -> Color(0xFF20252F)
        else -> faceDeep(color)
    }

    fun tableBrush(): Brush = Brush.radialGradient(
        colors = listOf(FeltCore, FeltEdge, Ink),
        radius = 1400f
    )
}

private val scheme = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Palette.Ink,
    secondary = Palette.Blue,
    background = Palette.Night,
    onBackground = Palette.Text,
    surface = Palette.Slate,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SlateHigh,
    onSurfaceVariant = Palette.TextDim,
    outline = Palette.Line,
    error = Palette.Red
)

private val typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 46.sp,
        letterSpacing = (-1).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.6.sp
    )
)

/** The table is dark on purpose, so the theme ignores the system setting. */
@Composable
fun UnoDuoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
