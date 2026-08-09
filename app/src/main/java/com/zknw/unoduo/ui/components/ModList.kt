package com.zknw.unoduo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.GameMod
import com.zknw.unoduo.game.ordered
import com.zknw.unoduo.ui.theme.Palette

/**
 * The mod list, shared by the create screen and the solo screen so the two can never
 * offer different rules. Every mod is independent — ticking one never unticks another.
 */
@Composable
fun ModPicker(
    chosen: Set<GameMod>,
    modifier: Modifier = Modifier,
    onToggle: (GameMod) -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameMod.entries.forEach { mod ->
            ModRow(mod = mod, checked = mod in chosen) { onToggle(mod) }
        }
    }
}

@Composable
private fun ModRow(mod: GameMod, checked: Boolean, onClick: () -> Unit) {
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple { onClick() },
        color = if (checked) Palette.SlateHigh else Palette.Slate,
        padding = PaddingValues(16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            TickBox(checked)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    mod.label,
                    color = if (checked) Palette.Gold else Palette.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                Text(mod.blurb, color = Palette.TextDim, fontSize = 13.sp)
            }
        }
    }
}

/** The same ink-and-slab language as the buttons: it sinks when it is on. */
@Composable
private fun TickBox(checked: Boolean) {
    val shape = RoundedCornerShape(9.dp)
    val depth by animateDpAsState(if (checked) 0.dp else 3.dp, label = "tick-depth")

    Box(Modifier.size(28.dp, 31.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .offset(y = 3.dp)
                .clip(shape)
                .background(Palette.Outline)
        )
        Box(
            Modifier
                .size(28.dp)
                .offset(y = depth)
                .clip(shape)
                .background(if (checked) Palette.Gold else Palette.Ink)
                .border(3.dp, Palette.Outline, shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("✓", color = Palette.Outline, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/**
 * What a room is playing with, in one line. Shown to the host under its QR code and to
 * every guest in the lobby, so nobody discovers the +8 by eating one.
 */
@Composable
fun ModSummary(mods: Set<GameMod>, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth()) {
        Column {
            SectionLabel(if (mods.isEmpty()) "PARTIE NORMALE" else "PARTIE PERSONNALISÉE")
            Spacer(Modifier.height(10.dp))
            if (mods.isEmpty()) {
                Text(
                    "Règles maison habituelles, jeu de 108 cartes.",
                    color = Palette.TextDim,
                    fontSize = 13.sp
                )
            } else {
                mods.ordered().forEach { mod ->
                    InkChip(
                        text = mod.label,
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Palette.Gold,
                        textColor = Palette.Outline,
                        fontSize = 11
                    )
                }
            }
        }
    }
}
