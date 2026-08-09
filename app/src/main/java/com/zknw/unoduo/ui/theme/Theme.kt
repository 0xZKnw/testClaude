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

/**
 * Cartoon palette: flat saturated colours, a warm white card stock and one near-black
 * used as an ink outline everywhere. Nothing relies on transparency to convey state —
 * see-through elements read as rendering glitches rather than as design.
 */
object Palette {
    /** The single ink colour used for every cartoon outline. */
    val Outline = Color(0xFF14161D)

    /** Warm white paper, never pure #FFFFFF: it would glare against the felt. */
    val Stock = Color(0xFFFDFBF4)

    val Ink = Color(0xFF0B0E14)
    val Night = Color(0xFF161A25)
    val Slate = Color(0xFF212836)
    val SlateHigh = Color(0xFF2E3749)
    val Line = Color(0xFF3B475D)
    val Text = Color(0xFFF3F6FB)
    val TextDim = Color(0xFF9DAABF)
    val Gold = Color(0xFFFFC531)

    /** Solid dark wash used to mute a card without making it transparent. */
    val Scrim = Color(0xFF0C1018)

    // The table is deliberately colourless — the same near-black family as the ink
    // keyline every card is drawn with, so the world reads as ink on colour.
    //
    // Green felt fought the green cards; indigo then fought the blue ones and tinted
    // the warm white stock mauve. Any hue here competes with a deck that already owns
    // four of them. A dark neutral competes with none, lets all four sing at once, and
    // makes the gold of a playable card read instantly.
    //
    // The lit centre stays a step below SlateHigh so the chips and panels drawn on top
    // never sink into it.
    val FeltLight = Color(0xFF2A3444)
    val Felt = Color(0xFF171D27)
    val FeltDark = Color(0xFF080A10)

    val Red = Color(0xFFF23B2E)
    val RedDeep = Color(0xFFC01C12)
    val Yellow = Color(0xFFFFC21A)
    val YellowDeep = Color(0xFFDC9200)
    val Green = Color(0xFF41C258)
    val GreenDeep = Color(0xFF259A3C)
    val Blue = Color(0xFF2E9CF2)
    val BlueDeep = Color(0xFF1668C4)
    val Wild = Color(0xFF262C39)
    val WildDeep = Color(0xFF151A24)

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

    /** Ink used for the glyph sitting inside the white oval. */
    fun glyph(color: CardColor): Color = when (color) {
        CardColor.YELLOW -> YellowDeep
        CardColor.WILD -> Color(0xFF2B3240)
        else -> faceDeep(color)
    }

    /** Flat table with a heavy vignette — a table, not a gradient wallpaper. */
    fun feltBrush(): Brush = Brush.radialGradient(
        colors = listOf(FeltLight, Felt, FeltDark),
        radius = 1500f
    )

    /** Menu backdrop, same family as the table so screens feel like one place. */
    fun menuBrush(): Brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF19202C), Color(0xFF080A0F))
    )

    /** Avatar backgrounds. Same flat, saturated logic as the cards. */
    val avatarColors: List<Color> = listOf(
        Color(0xFFF23B2E),
        Color(0xFFFF8A1E),
        Color(0xFFFFC21A),
        Color(0xFF41C258),
        Color(0xFF19B79B),
        Color(0xFF2E9CF2),
        Color(0xFF7A5CF0),
        Color(0xFFF25DA8)
    )

    fun avatarColor(index: Int): Color = avatarColors[index.mod(avatarColors.size)]
}

private val scheme = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Palette.Outline,
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
        fontWeight = FontWeight.Black,
        fontSize = 23.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
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
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        letterSpacing = 0.6.sp
    )
)

/** The table is dark on purpose, so the theme ignores the system setting. */
@Composable
fun UnoDuoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
