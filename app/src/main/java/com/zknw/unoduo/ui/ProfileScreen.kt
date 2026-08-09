package com.zknw.unoduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.profile.Profile
import com.zknw.unoduo.ui.components.AvatarLook
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.InkChip
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PlayerAvatar
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.SectionLabel
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette

@Composable
fun ProfileScreen(
    profile: Profile,
    onSave: (String, Int, String?) -> Unit,
    onPickPhoto: () -> Unit,
    onResetStats: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var color by remember(profile.avatarColor) { mutableIntStateOf(profile.avatarColor) }
    var confirmReset by remember { mutableStateOf(false) }

    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(26.dp))
            ScreenHeader("Profil", "Ton identité et tes statistiques.", onBack)
            Spacer(Modifier.height(20.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayerAvatar(
                        name = name,
                        look = AvatarLook(color, profile.photoUri),
                        size = 104.dp,
                        ring = 4.dp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("Choisir une photo", Modifier.weight(1f)) { onPickPhoto() }
                        if (profile.photoUri != null) {
                            GhostButton("Retirer", Modifier.width(110.dp)) {
                                onSave(name, color, null)
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    SectionLabel("COULEUR", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    AvatarColorRow(selected = color, onSelect = { color = it })

                    Spacer(Modifier.height(18.dp))
                    SectionLabel("PSEUDO", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 14) name = it },
                        singleLine = true,
                        placeholder = { Text("Joueur", color = Palette.TextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text,
                            unfocusedTextColor = Palette.Text,
                            focusedBorderColor = Palette.Gold,
                            unfocusedBorderColor = Palette.Outline,
                            cursorColor = Palette.Gold,
                            focusedContainerColor = Palette.Ink,
                            unfocusedContainerColor = Palette.Ink
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Enregistrer", Modifier.fillMaxWidth()) {
                        onSave(name, color, profile.photoUri)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            StatsPanels(profile)

            Spacer(Modifier.height(16.dp))
            if (confirmReset) {
                Panel(Modifier.fillMaxWidth(), color = Palette.RedDeep) {
                    Column {
                        Text(
                            "Remettre toutes les statistiques à zéro ? C'est définitif.",
                            color = Palette.Stock,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(
                                text = "Effacer",
                                modifier = Modifier.weight(1f),
                                container = Palette.Red,
                                onContainer = Palette.Stock
                            ) {
                                onResetStats()
                                confirmReset = false
                            }
                            GhostButton("Annuler", Modifier.weight(1f)) { confirmReset = false }
                        }
                    }
                }
            } else {
                GhostButton("Réinitialiser les stats", Modifier.fillMaxWidth()) {
                    confirmReset = true
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AvatarColorRow(selected: Int, onSelect: (Int) -> Unit) {
    // Two rows of four: eight fixed swatches never need to fight for width.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0..3, 4..7).forEach { range ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                range.forEach { index ->
                    val chosen = index == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(Palette.avatarColor(index))
                            .border(
                                if (chosen) 5.dp else 3.dp,
                                if (chosen) Palette.Gold else Palette.Outline,
                                CircleShape
                            )
                            .clickableNoRipple { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsPanels(profile: Profile) {
    val stats = profile.stats
    val totals = stats.totals

    Panel(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("BILAN")
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BigStat("${stats.roundsWon}", "gagnées", Palette.Green, Modifier.weight(1f))
                BigStat("${stats.roundsLost}", "perdues", Palette.Red, Modifier.weight(1f))
                BigStat("${stats.winRate} %", "victoires", Palette.Gold, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkChip("${stats.roundsPlayed} manches", color = Palette.SlateHigh)
                InkChip("Série : ${stats.currentStreak}", color = Palette.SlateHigh)
                InkChip("Record : ${stats.bestStreak}", color = Palette.SlateHigh)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Panel(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("SÉRIES ET RECORDS")
            Spacer(Modifier.height(12.dp))
            StatLine("Série de victoires en cours", stats.currentStreak, Palette.Green)
            StatLine("Meilleure série", stats.bestStreak, Palette.Green)
            StatLine("Pire série de défaites", stats.worstStreak, Palette.Red)
            // Zero means "never won one", not "won without playing a card".
            StatText(
                "Victoire la plus expéditive",
                if (stats.fastestWin == 0) "—" else "${stats.fastestWin} cartes",
                Palette.Gold
            )
            StatLine("Pire main à l'arrivée", stats.worstHand, Palette.Red)
        }
    }

    Spacer(Modifier.height(12.dp))

    Panel(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("RYTHME")
            Spacer(Modifier.height(12.dp))
            StatText("Cartes posées par manche", stats.cardsPerRound)
            StatText("Cartes piochées par manche", stats.drawnPerRound)
            StatText("Part de pioche", "${stats.drawRate} %")
            StatText("Part d'attaque", "${stats.aggression} %")
            StatText("Cartes restantes quand tu perds", stats.averageLoss)
        }
    }

    Spacer(Modifier.height(12.dp))

    Panel(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("CARTES")
            Spacer(Modifier.height(12.dp))
            StatLine("Cartes posées", totals.cardsPlayed)
            StatLine("Cartes piochées", totals.cardsDrawn)
            StatLine("Chiffres posés", totals.numbersPlayed)
            StatLine("Passe et sens interdit", totals.skipsPlayed)
            StatLine("Jokers posés", totals.wildsPlayed)
            StatLine("Coups doubles", totals.doublePlaysPlayed)
            StatLine("Espions", totals.spiesPlayed)
        }
    }

    Spacer(Modifier.height(12.dp))

    Panel(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("GUERRE DES CUMULS")
            Spacer(Modifier.height(12.dp))
            StatLine("+2 posés", totals.drawTwosPlayed)
            StatLine("+4 posés", totals.drawFoursPlayed)
            StatLine("+8 posés", totals.drawEightsPlayed)
            StatLine("+12 posés", totals.drawTwelvesPlayed)
            StatLine("Contres réussis", totals.countersPlayed)
            StatLine("Cartes encaissées", totals.penaltyCardsTaken)
            StatText("Encaissées par manche", stats.penaltiesTakenPerRound)
            StatLine("Plus gros cumul infligé", totals.biggestStackDealt, Palette.Green)
            StatLine("Plus gros cumul encaissé", totals.biggestStackTaken, Palette.Red)
        }
    }

    Spacer(Modifier.height(12.dp))

    Panel(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("DERNIÈRE CARTE")
            Spacer(Modifier.height(12.dp))
            StatLine("Fois où tu as touché l'UNO", totals.unoReached, Palette.Gold)
            StatText("Transformées en victoire", "${stats.closingRate} %", Palette.Gold)
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Ink)
            .border(3.dp, Palette.Outline, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = tint, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text(label, color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int, tint: Color = Palette.Text) =
    StatText(label, value.toString(), tint)

@Composable
private fun StatText(label: String, value: String, tint: Color = Palette.Text) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Palette.TextDim,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(value, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}
