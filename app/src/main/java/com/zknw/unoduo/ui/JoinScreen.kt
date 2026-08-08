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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.net.RoomCode
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.QrScanner
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.StatusRow
import com.zknw.unoduo.ui.theme.Palette
import com.zknw.unoduo.vm.LinkStatus

@Composable
fun JoinScreen(
    hasCameraPermission: Boolean,
    onRequestCamera: () -> Unit,
    status: String,
    link: LinkStatus,
    connecting: Boolean,
    onScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    var manualCode by remember { mutableStateOf("") }

    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(26.dp))
            ScreenHeader("Rejoindre", "Scanne le QR code affiché par ton adversaire.", onBack)
            Spacer(Modifier.height(20.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Palette.Ink)
                    .border(1.dp, Palette.Line, RoundedCornerShape(24.dp))
            ) {
                when {
                    connecting -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        StatusRow(status.ifBlank { "Connexion…" }, busy = true)
                    }

                    hasCameraPermission -> {
                        QrScanner(Modifier.fillMaxSize(), onResult = onScanned)
                        ViewFinder()
                    }

                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "L'accès à la caméra est nécessaire pour lire le QR code.",
                            color = Palette.TextDim,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("Autoriser la caméra") { onRequestCamera() }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "OU ENTRE LE CODE À 6 CARACTÈRES",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualCode,
                            onValueChange = { manualCode = RoomCode.normalize(it) },
                            singleLine = true,
                            enabled = !connecting,
                            placeholder = { Text("ABC234", color = Palette.TextDim) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
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
                        Spacer(Modifier.width(12.dp))
                        PrimaryButton(
                            text = "OK",
                            enabled = RoomCode.isValid(manualCode) && !connecting
                        ) { onScanned(manualCode) }
                    }
                }
            }

            if (status.isNotEmpty() && !connecting) {
                Spacer(Modifier.height(16.dp))
                StatusRow(status, busy = link == LinkStatus.SEARCHING)
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

/** Corner brackets over the camera preview. */
@Composable
private fun ViewFinder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.68f)
                .aspectRatio(1f)
                .border(3.dp, Palette.Gold.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
        )
    }
}
