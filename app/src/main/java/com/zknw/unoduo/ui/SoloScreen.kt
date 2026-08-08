package com.zknw.unoduo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.Difficulty
import com.zknw.unoduo.ui.components.AvatarLook
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.InkChip
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PlayerAvatar
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette

@Composable
fun SoloScreen(onPick: (Difficulty) -> Unit, onBack: () -> Unit) {
    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(26.dp))
            ScreenHeader(
                "Jouer en solo",
                "Contre la machine, sans Bluetooth. Mêmes règles maison.",
                onBack
            )
            Spacer(Modifier.height(26.dp))

            Difficulty.entries.forEach { difficulty ->
                DifficultyCard(difficulty) { onPick(difficulty) }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Le bot ne voit que ce que tu vois : ta main lui est cachée, " +
                    "comme la sienne l'est pour toi.",
                color = Palette.TextDim,
                fontSize = 12.sp
            )
            Text(
                "Les parties solo ne comptent pas dans les statistiques du profil.",
                color = Palette.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.weight(1f))
            GhostButton("Retour", Modifier.fillMaxWidth()) { onBack() }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun DifficultyCard(difficulty: Difficulty, onClick: () -> Unit) {
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple { onClick() },
        padding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(
                name = difficulty.label,
                look = AvatarLook(avatarColorOf(difficulty)),
                size = 46.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    difficulty.label,
                    color = Palette.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    difficulty.blurb,
                    color = Palette.TextDim,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            InkChip("Jouer", color = Palette.Gold, textColor = Palette.Outline, fontSize = 12)
        }
    }
}

/** Green, amber, red: the level should read before the label does. */
private fun avatarColorOf(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.EASY -> 3
    Difficulty.MEDIUM -> 1
    Difficulty.HARD -> 0
}
