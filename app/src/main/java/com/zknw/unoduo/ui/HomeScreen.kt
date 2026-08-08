package com.zknw.unoduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.Card
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.CardKind
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.UnoCardFace
import com.zknw.unoduo.ui.theme.Palette

@Composable
fun HomeScreen(
    playerName: String,
    onNameChange: (String) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit
) {
    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            LogoFan()
            Spacer(Modifier.height(26.dp))

            Text(
                "UNO DUO",
                color = Palette.Text,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Deux joueurs, un QR code, zéro internet.",
                color = Palette.TextDim,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(34.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "TON PSEUDO",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = onNameChange,
                        singleLine = true,
                        placeholder = { Text("Joueur", color = Palette.TextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text,
                            unfocusedTextColor = Palette.Text,
                            focusedBorderColor = Palette.Gold,
                            unfocusedBorderColor = Palette.Line,
                            cursorColor = Palette.Gold,
                            focusedContainerColor = Palette.Ink.copy(alpha = 0.5f),
                            unfocusedContainerColor = Palette.Ink.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            PrimaryButton("Créer une partie", Modifier.fillMaxWidth()) { onHost() }
            Spacer(Modifier.height(12.dp))
            GhostButton("Rejoindre une partie", Modifier.fillMaxWidth()) { onJoin() }
            Spacer(Modifier.height(12.dp))
            GhostButton("Règles du jeu", Modifier.fillMaxWidth()) { onRules() }
            Spacer(Modifier.height(12.dp))
            GhostButton("Réglages", Modifier.fillMaxWidth()) { onSettings() }

            Spacer(Modifier.weight(1f))
            Text(
                "Connexion Bluetooth LE — les deux téléphones doivent être proches.",
                color = Palette.TextDim.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 22.dp)
            )
        }
    }
}

/** Three fanned cards used as the menu's hero image. */
@Composable
private fun LogoFan() {
    val cards = listOf(
        Card(0, CardColor.BLUE, CardKind.NUMBER, 7) to -18f,
        Card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR) to 0f,
        Card(2, CardColor.GREEN, CardKind.DRAW_TWO) to 18f
    )
    Box(Modifier.size(width = 230.dp, height = 150.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(210.dp, 110.dp)
                .clip(RoundedCornerShape(80.dp))
                .background(
                    Brush.radialGradient(
                        listOf(Palette.Gold.copy(alpha = 0.16f), Palette.Ink.copy(alpha = 0f))
                    )
                )
        )
        Row(horizontalArrangement = Arrangement.spacedBy((-26).dp)) {
            cards.forEach { (card, angle) ->
                UnoCardFace(
                    card = card,
                    width = 78.dp,
                    modifier = Modifier
                        .offset(y = if (angle == 0f) (-10).dp else 0.dp)
                        .graphicsLayer { rotationZ = angle }
                )
            }
        }
    }
}
