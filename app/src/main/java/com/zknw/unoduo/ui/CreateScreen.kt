package com.zknw.unoduo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.GameMod
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.InkChip
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.ModPicker
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette

/**
 * The fork between a plain game and a custom one. The custom branch is not a separate
 * screen: ticking it opens the mod list in place, so the choice and its consequences
 * are visible at the same time.
 */
@Composable
fun CreateScreen(onHost: (Set<GameMod>) -> Unit, onBack: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(emptySet<GameMod>()) }

    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(22.dp))
            ScreenHeader(
                "Créer une partie",
                "Les règles maison sont toujours là. Le reste est à toi.",
                onBack
            )
            Spacer(Modifier.height(20.dp))

            ModeCard(
                title = "Partie normale",
                subtitle = "Le jeu de 108 cartes et les règles maison, rien de plus.",
                selected = !custom
            ) { custom = false }

            Spacer(Modifier.height(12.dp))

            ModeCard(
                title = "Partie personnalisée",
                subtitle = "Ajoute les mods que tu veux. Ils se cumulent tous.",
                selected = custom
            ) { custom = true }

            AnimatedVisibility(visible = custom) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(16.dp))
                    ModPicker(chosen) { mod ->
                        chosen = if (mod in chosen) chosen - mod else chosen + mod
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Un mod n'enlève jamais rien : il ajoute des cartes au paquet. " +
                            "Les autres joueurs les voient dans le salon avant le lancement.",
                        color = Palette.TextDim,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Ouvrir le salon",
                modifier = Modifier.fillMaxWidth()
            ) { onHost(if (custom) chosen else emptySet()) }

            Spacer(Modifier.height(10.dp))
            GhostButton("Retour", Modifier.fillMaxWidth()) { onBack() }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple { onClick() },
        color = if (selected) Palette.SlateHigh else Palette.Slate,
        padding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (selected) Palette.Gold else Palette.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = Palette.TextDim, fontSize = 13.sp)
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                InkChip("Choisi", color = Palette.Gold, textColor = Palette.Outline, fontSize = 11)
            }
        }
    }
}
