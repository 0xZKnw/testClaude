package com.zknw.unoduo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.GameMod
import com.zknw.unoduo.game.HOST_SEAT
import com.zknw.unoduo.game.MAX_PLAYERS
import com.zknw.unoduo.game.MIN_PLAYERS
import com.zknw.unoduo.game.Seat
import com.zknw.unoduo.net.LobbyPlayer
import com.zknw.unoduo.ui.components.AvatarLook
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.InkChip
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.ModSummary
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PlayerAvatar
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.SectionLabel
import com.zknw.unoduo.ui.components.StatusRow
import com.zknw.unoduo.ui.theme.Palette

/**
 * Who is in the room. Shared by the host, who sees it under its own QR code, and by
 * the guests, who have nothing else to look at until the deal.
 */
@Composable
fun PlayerRoster(
    players: List<LobbyPlayer>,
    photos: Map<Seat, String>,
    mySeat: Seat,
    modifier: Modifier = Modifier
) {
    Panel(modifier.fillMaxWidth()) {
        Column {
            SectionLabel("JOUEURS ${players.size}/$MAX_PLAYERS")
            Spacer(Modifier.height(12.dp))
            players.forEach { player ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerAvatar(
                        name = player.name,
                        look = AvatarLook(player.avatar, photoData = photos[player.seat]),
                        size = 38.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        player.name,
                        color = Palette.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (player.seat == HOST_SEAT) {
                        InkChip("Hôte", color = Palette.Gold, fontSize = 11)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (player.seat == mySeat) {
                        InkChip("Toi", color = Palette.Blue, fontSize = 11)
                    }
                }
            }
            // Empty chairs, so the room reads as "waiting" rather than "finished".
            repeat((MIN_PLAYERS - players.size).coerceAtLeast(0)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("…", color = Palette.TextDim, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "En attente d'un joueur",
                        color = Palette.TextDim,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/** What a guest sees between joining and the first card being dealt. */
@Composable
fun LobbyScreen(
    players: List<LobbyPlayer>,
    photos: Map<Seat, String>,
    mySeat: Seat,
    roomCode: String,
    mods: Set<GameMod>,
    onLeave: () -> Unit
) {
    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(26.dp))
            ScreenHeader(
                "Salon $roomCode",
                "L'hôte lance la partie quand tout le monde est là.",
                onLeave
            )
            Spacer(Modifier.height(24.dp))
            PlayerRoster(players, photos, mySeat)
            Spacer(Modifier.height(16.dp))
            ModSummary(mods)
            Spacer(Modifier.height(22.dp))
            StatusRow(text = "En attente de l'hôte…", busy = true)
            Spacer(Modifier.weight(1f))
            GhostButton("Quitter le salon", Modifier.fillMaxWidth()) { onLeave() }
            Spacer(Modifier.height(22.dp))
        }
    }
}

/** Kept next to the roster because the two always change together. */
@Composable
fun LobbyHint(players: Int, modifier: Modifier = Modifier) {
    Text(
        when {
            players < MIN_PLAYERS -> "Il faut au moins $MIN_PLAYERS joueurs."
            players >= MAX_PLAYERS -> "Salon complet."
            else -> "Tu peux lancer, ou attendre d'autres joueurs."
        },
        color = Palette.TextDim,
        fontSize = 12.sp,
        modifier = modifier
    )
}

