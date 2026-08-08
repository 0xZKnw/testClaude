package com.zknw.unoduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.BuildConfig
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.ScreenHeader
import com.zknw.unoduo.ui.components.StatusRow
import com.zknw.unoduo.ui.theme.Palette
import com.zknw.unoduo.update.Updater
import com.zknw.unoduo.vm.UpdateState

@Composable
fun SettingsScreen(
    update: UpdateState,
    token: String,
    canInstall: Boolean,
    onTokenChange: (String) -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onAllowInstalls: () -> Unit,
    onBack: () -> Unit
) {
    MenuBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(26.dp))
            ScreenHeader("Réglages", "Version installée et mises à jour.", onBack)
            Spacer(Modifier.height(20.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Label("VERSION INSTALLÉE")
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            BuildConfig.GIT_SHA,
                            color = Palette.Gold,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.fillMaxWidth(0.04f))
                        Text(
                            "UNO Duo ${BuildConfig.VERSION_NAME}",
                            color = Palette.TextDim,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Label("MISE À JOUR")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Récupère directement le dernier APK publié par la CI du dépôt, " +
                            "sans repasser par le navigateur.",
                        color = Palette.TextDim,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    UpdateBlock(
                        update = update,
                        canInstall = canInstall,
                        onCheck = onCheck,
                        onDownload = onDownload,
                        onInstall = onInstall,
                        onAllowInstalls = onAllowInstalls
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Label("JETON GITHUB — OPTIONNEL")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Le dépôt ${Updater.OWNER}/${Updater.REPO} est public : la mise à jour " +
                            "fonctionne sans rien remplir ici. Ce champ ne sert que si tu le " +
                            "repasses en privé un jour — colle alors un jeton d'accès personnel " +
                            "avec la portée « Contents: read ». Il reste sur ce téléphone.",
                        color = Palette.TextDim,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    TokenField(token, onTokenChange)
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = Palette.TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp
    )
}

@Composable
private fun UpdateBlock(
    update: UpdateState,
    canInstall: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onAllowInstalls: () -> Unit
) {
    when (update) {
        is UpdateState.Idle -> PrimaryButton(
            text = "Vérifier les mises à jour",
            modifier = Modifier.fillMaxWidth()
        ) { onCheck() }

        is UpdateState.Checking -> StatusRow("Interrogation de GitHub…", busy = true)

        is UpdateState.UpToDate -> Column {
            Text(
                "Tu es déjà à jour (${update.commit}).",
                color = Palette.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            GhostButton("Revérifier", Modifier.fillMaxWidth()) { onCheck() }
        }

        is UpdateState.Found -> Column {
            Text(
                "Nouvelle version disponible : ${update.release.commit}",
                color = Palette.Gold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            if (update.release.sizeBytes > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${update.release.sizeBytes / (1024 * 1024)} Mo à télécharger",
                    color = Palette.TextDim,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Télécharger", Modifier.fillMaxWidth()) { onDownload() }
        }

        is UpdateState.Downloading -> Column {
            Text(
                "Téléchargement… ${(update.progress * 100).toInt()} %",
                color = Palette.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { update.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = Palette.Gold,
                trackColor = Palette.Ink
            )
        }

        is UpdateState.Ready -> Column {
            Text(
                "Prêt à installer (${update.commit}).",
                color = Palette.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            if (canInstall) {
                PrimaryButton("Installer", Modifier.fillMaxWidth()) { onInstall(update.path) }
            } else {
                Text(
                    "Android demande d'abord l'autorisation d'installer des applications " +
                        "depuis UNO Duo.",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Autoriser l'installation", Modifier.fillMaxWidth()) {
                    onAllowInstalls()
                }
            }
        }

        is UpdateState.Failed -> Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Red.copy(alpha = 0.16f))
                    .padding(12.dp)
            ) {
                Text(update.reason, color = Palette.Text, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            GhostButton("Réessayer", Modifier.fillMaxWidth()) { onCheck() }
        }
    }
}

@Composable
private fun TokenField(token: String, onTokenChange: (String) -> Unit) {
    var draft by remember(token) { mutableStateOf(token) }
    Column {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            placeholder = { Text("github_pat_…", color = Palette.TextDim) },
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
        Spacer(Modifier.height(10.dp))
        Row {
            PrimaryButton("Enregistrer", Modifier.fillMaxWidth(0.55f)) { onTokenChange(draft) }
            Spacer(Modifier.fillMaxWidth(0.05f))
            GhostButton("Effacer") {
                draft = ""
                onTokenChange("")
            }
        }
    }
}
