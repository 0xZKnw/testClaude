package com.zknw.unoduo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.ui.theme.Palette

/**
 * The shared cartoon vocabulary: flat fill, ink keyline, and a solid ink slab under
 * everything instead of a soft shadow. One place, so the menus and the table cannot
 * drift apart in style.
 */

private val BUTTON_SHAPE = RoundedCornerShape(18.dp)
private val PANEL_SHAPE = RoundedCornerShape(22.dp)
private const val INK_DEPTH = 5

@Composable
fun TableBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Felt)
            .background(Palette.feltBrush())
    ) {
        Box(Modifier.safeArea()) { content() }
    }
}

@Composable
fun MenuBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Felt)
            .background(Palette.menuBrush())
    ) {
        Box(Modifier.safeArea()) { content() }
    }
}

/**
 * The colour runs to the very edge of the screen; only the content is held back, and
 * only by what would actually hide it — a camera notch, or the system bars on the rare
 * phone that refuses to hide them. The keyboard is deliberately left out: the join
 * screen sizes its viewfinder from the space it is given, and shrinking that space
 * would push the code field off the bottom rather than into view.
 */
private fun Modifier.safeArea(): Modifier = this
    .fillMaxSize()
    .windowInsetsPadding(WindowInsets.systemBars)
    .windowInsetsPadding(WindowInsets.displayCutout)

/** Flat slab with an ink keyline and a solid drop under it. */
@Composable
fun InkSurface(
    modifier: Modifier = Modifier,
    color: Color = Palette.Slate,
    shape: Shape = PANEL_SHAPE,
    depth: Dp = INK_DEPTH.dp,
    border: Dp = 3.dp,
    content: @Composable () -> Unit
) {
    Box(modifier) {
        // The slab sits behind and slightly lower; it never drives the layout size.
        Box(
            Modifier
                .matchParentSize()
                .offset(y = depth)
                .clip(shape)
                .background(Palette.Outline)
        )
        Box(
            Modifier
                .clip(shape)
                .background(color)
                .border(border, Palette.Outline, shape)
        ) { content() }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    color: Color = Palette.Slate,
    content: @Composable () -> Unit
) {
    InkSurface(modifier = modifier, color = color) {
        Box(Modifier.padding(padding)) { content() }
    }
}

/**
 * Cartoon button: it physically sinks onto its ink slab when pressed, which is the
 * whole reason the slab exists.
 */
@Composable
fun CartoonButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Palette.Gold,
    onContainer: Color = Palette.Outline,
    fontSize: Int = 16,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = if (pressed || !enabled) 1.dp else 5.dp
    val sink = if (pressed && enabled) 4.dp else 0.dp
    val fill = if (enabled) container else Palette.SlateHigh
    val label = if (enabled) onContainer else Palette.TextDim

    Box(modifier.height(56.dp + 5.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset(y = depth)
                .clip(BUTTON_SHAPE)
                .background(Palette.Outline)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset(y = sink)
                .clip(BUTTON_SHAPE)
                .background(fill)
                .border(3.dp, Palette.Outline, BUTTON_SHAPE)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = label,
                fontWeight = FontWeight.Black,
                fontSize = fontSize.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Palette.Gold,
    onContainer: Color = Palette.Outline,
    onClick: () -> Unit
) = CartoonButton(text, modifier, enabled, container, onContainer, onClick = onClick)

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) = CartoonButton(
    text = text,
    modifier = modifier,
    enabled = enabled,
    container = Palette.SlateHigh,
    onContainer = Palette.Text,
    onClick = onClick
)

/** Small pill used for scores, counters and one-line notices. */
@Composable
fun InkChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Slate,
    textColor: Color = Palette.Text,
    fontSize: Int = 13
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(color)
            .border(2.5.dp, Palette.Outline, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = textColor, fontSize = fontSize.sp, fontWeight = FontWeight.Black)
    }
}

/** Circular ink-outlined button, for back arrows and close crosses. */
@Composable
fun InkIconButton(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = Palette.SlateHigh,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(3.dp, Palette.Outline, CircleShape)
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            color = Palette.Text,
            fontSize = (size.value * 0.44f).sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                InkIconButton("‹", onClick = onBack)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                title,
                color = Palette.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Palette.TextDim, fontSize = 14.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Palette.Gold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp
    )
}

@Composable
fun StatusRow(text: String, busy: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Palette.Gold,
                strokeWidth = 3.dp
            )
        }
        Text(text, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** The game is full of tap targets where a ripple would look out of place. */
@Composable
fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}
