package com.zknw.unoduo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.ui.theme.Palette

@Composable
fun TableBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .background(Palette.tableBrush())
    ) { content() }
}

@Composable
fun MenuBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Palette.Night, Palette.Ink))
            )
    ) { content() }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Slate.copy(alpha = 0.92f))
            .border(1.dp, Palette.Line, RoundedCornerShape(22.dp))
            .padding(padding)
    ) { content() }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Palette.Gold,
    onContainer: Color = Palette.Ink,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = onContainer,
            disabledContainerColor = Palette.SlateHigh,
            disabledContentColor = Palette.TextDim
        )
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Palette.Text,
            disabledContentColor = Palette.TextDim
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                BackChip(onBack)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Palette.TextDim, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BackChip(onBack: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Palette.Slate)
            .border(1.dp, Palette.Line, CircleShape)
            .clickableNoRipple { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Text("‹", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
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
                strokeWidth = 2.dp
            )
        }
        Text(text, color = Palette.TextDim, fontSize = 14.sp)
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
