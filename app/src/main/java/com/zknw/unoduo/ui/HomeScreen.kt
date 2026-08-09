package com.zknw.unoduo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.Card
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.CardKind
import com.zknw.unoduo.profile.Profile
import com.zknw.unoduo.ui.components.AvatarLook
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.InkChip
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.OutlinedGlyphText
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PlayerAvatar
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.UnoCardFace
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette

@Composable
fun HomeScreen(
    profile: Profile,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onSolo: () -> Unit,
    onRules: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    val scroll = rememberScrollState()

    MenuBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Everything here is a fixed block — the profile bar, the fan, the title,
            // four rows of buttons. Only the air between them can give. When the screen
            // is tall enough for the lot, that air stretches to fill it exactly and the
            // page does not scroll at all; the scroll is kept for the screens that
            // genuinely cannot show everything at once. Before this the page overflowed
            // by a few millimetres and dragged open on nothing.
            //
            // A player who has turned the system font size up needs more room before we
            // dare take the scroll away.
            val fontScale = LocalDensity.current.fontScale
            val needed = MIN_UNSCROLLED + FONT_HEADROOM * (fontScale - 1f).coerceAtLeast(0f)
            val fits = maxHeight >= needed

            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (fits) Modifier else Modifier.verticalScroll(scroll))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                ProfileBar(profile, onProfile)

                Air(fits, weight = 1f, fixed = 20.dp)
                LogoFan()
                Spacer(Modifier.height(12.dp))

                OutlinedGlyphText(
                    text = "UNO",
                    fontSize = 46.sp,
                    fill = Palette.Gold,
                    outlineWidth = 3.dp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "De 2 à 5 joueurs, un QR code, zéro internet.",
                    color = Palette.TextDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Air(fits, weight = 1f, fixed = 24.dp)

                PrimaryButton("Créer une partie", Modifier.fillMaxWidth()) { onHost() }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Rejoindre une partie",
                    modifier = Modifier.fillMaxWidth(),
                    container = Palette.Blue,
                    onContainer = Palette.Stock
                ) { onJoin() }
                Spacer(Modifier.height(12.dp))
                GhostButton("Jouer en solo", Modifier.fillMaxWidth()) { onSolo() }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton("Règles", Modifier.weight(1f)) { onRules() }
                    GhostButton("Réglages", Modifier.weight(1f)) { onSettings() }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Connexion Bluetooth LE — les téléphones doivent être proches.",
                    color = Palette.TextDim,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Air(fits, weight = 0.4f, fixed = 0.dp)
            }
        }
    }
}

/**
 * Below this the fixed blocks alone fill the screen, so the page is allowed to scroll.
 * Above it, the gaps take up the slack and there is nothing left to scroll.
 */
private val MIN_UNSCROLLED = 720.dp

/** Extra room demanded per unit of system font scale, since the titles grow with it. */
private val FONT_HEADROOM = 140.dp

/** A gap that stretches when the page fits on screen, and is a plain spacer when it does not. */
@Composable
private fun ColumnScope.Air(fits: Boolean, weight: Float, fixed: Dp) {
    if (fits) Spacer(Modifier.weight(weight)) else if (fixed > 0.dp) Spacer(Modifier.height(fixed))
}

/** Tapping anywhere on this bar opens the profile — avatar, pseudo and stats. */
@Composable
private fun ProfileBar(profile: Profile, onProfile: () -> Unit) {
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple { onProfile() },
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(
                name = profile.name,
                look = AvatarLook(profile.avatarColor, profile.photoUri),
                size = 52.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.ifBlank { "Choisis ton pseudo" },
                    color = Palette.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (profile.stats.roundsPlayed == 0) {
                        "Aucune manche jouée"
                    } else {
                        "${profile.stats.roundsWon} victoires · ${profile.stats.winRate} %"
                    },
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            InkChip("Profil", color = Palette.SlateHigh, fontSize = 12)
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
    Box(Modifier.size(width = 240.dp, height = 152.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy((-28).dp)) {
            cards.forEach { (card, angle) ->
                UnoCardFace(
                    card = card,
                    width = 82.dp,
                    modifier = Modifier
                        .offset(y = if (angle == 0f) (-12).dp else 0.dp)
                        .graphicsLayer { rotationZ = angle }
                )
            }
        }
    }
}
