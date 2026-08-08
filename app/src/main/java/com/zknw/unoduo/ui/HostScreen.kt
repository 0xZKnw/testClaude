package com.zknw.unoduo.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.QrCode
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.StatusRow
import com.zknw.unoduo.ui.theme.Palette
import com.zknw.unoduo.vm.LinkStatus

@Composable
fun HostScreen(
    roomCode: String,
    joinLink: String,
    status: String,
    link: LinkStatus,
    onBack: () -> Unit
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
                "Ton salon",
                "Fais scanner ce QR code par l'autre téléphone.",
                onBack
            )
            Spacer(Modifier.height(28.dp))

            Box(contentAlignment = Alignment.Center) {
                PulseHalo(active = link == LinkStatus.ADVERTISING)
                if (joinLink.isNotEmpty()) {
                    QrCode(content = joinLink, size = 260.dp)
                }
            }

            Spacer(Modifier.height(24.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "OU CODE À SAISIR",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        roomCode.forEach { char ->
                            Box(
                                Modifier
                                    .size(38.dp, 48.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .background(Palette.Ink),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    char.toString(),
                                    color = Palette.Gold,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            StatusRow(
                text = status,
                busy = link == LinkStatus.ADVERTISING || link == LinkStatus.IDLE
            )

            Spacer(Modifier.weight(1f))
            GhostButton("Annuler", Modifier.fillMaxWidth()) { onBack() }
            Spacer(Modifier.height(22.dp))
        }
    }
}

/** Soft breathing ring behind the QR while the room is discoverable. */
@Composable
private fun PulseHalo(active: Boolean) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "halo")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "halo-scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "halo-alpha"
    )
    Box(
        Modifier
            .size(300.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(Palette.Gold)
    )
}
