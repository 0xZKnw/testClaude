package com.zknw.unoduo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zknw.unoduo.ui.CreateScreen
import com.zknw.unoduo.ui.GameScreen
import com.zknw.unoduo.ui.HomeScreen
import com.zknw.unoduo.ui.HostScreen
import com.zknw.unoduo.ui.JoinScreen
import com.zknw.unoduo.ui.LobbyScreen
import com.zknw.unoduo.ui.ProfileScreen
import com.zknw.unoduo.ui.RulesScreen
import com.zknw.unoduo.ui.SettingsScreen
import com.zknw.unoduo.ui.SoloScreen
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.StatusRow
import com.zknw.unoduo.ui.theme.Palette
import com.zknw.unoduo.ui.theme.UnoDuoTheme
import com.zknw.unoduo.vm.AppViewModel
import com.zknw.unoduo.vm.LinkStatus
import com.zknw.unoduo.vm.Screen
import com.zknw.unoduo.update.Updater
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullScreen()
        setContent {
            UnoDuoTheme {
                Box(Modifier.fillMaxSize()) { App() }
            }
        }
    }

    // Android puts the bars back after a dialog, a permission prompt or a trip to the
    // recents screen, so hiding them once at startup is not enough.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullScreen()
    }

    /**
     * Nothing but the game on screen. The bars are hidden rather than merely made
     * transparent — left visible they cut a black band across the table, and the game is
     * played in portrait with a hand that wants every pixel of height.
     *
     * They come back on a swipe from the edge and go away again on their own, so
     * navigation is never actually lost.
     */
    private fun goFullScreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // What to do once the permission / bluetooth dialogs are out of the way.
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var localError by remember { mutableStateOf<String?>(null) }

    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val action = pendingAction
        pendingAction = null
        if (isBluetoothOn(context)) action?.invoke()
        else localError = "Le Bluetooth doit être activé pour jouer."
    }

    val requestBle = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val action = pendingAction
        if (result.values.all { it }) {
            if (isBluetoothOn(context)) {
                pendingAction = null
                action?.invoke()
            } else {
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            pendingAction = null
            localError = "Sans l'autorisation Bluetooth, impossible de connecter les deux téléphones."
        }
    }

    // The photo picker needs no permission, and a persisted grant keeps the avatar
    // readable after a restart.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.saveProfile(state.profile.name, state.profile.avatarColor, uri.toString())
        }
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (!granted) localError = "Sans la caméra, entre le code du salon à la main."
    }

    fun withBle(asHost: Boolean, action: () -> Unit) {
        val missing = blePermissions(asHost).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (!asHost && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationOn(context)) {
            localError =
                "Sur cette version d'Android, la localisation doit être activée pour détecter un salon Bluetooth."
            return
        }
        pendingAction = action
        when {
            missing.isNotEmpty() -> requestBle.launch(missing.toTypedArray())
            !isBluetoothOn(context) ->
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))

            else -> {
                pendingAction = null
                action()
            }
        }
    }

    BackHandler(enabled = state.screen != Screen.HOME) {
        when (state.screen) {
            Screen.RULES -> vm.closeRules()
            Screen.SETTINGS -> vm.closeSettings()
            Screen.PROFILE -> vm.closeProfile()
            Screen.CREATE -> vm.closeCreate()
            Screen.SOLO -> vm.closeSolo()
            Screen.GAME -> vm.leaveGame()
            else -> vm.goHome()
        }
    }

    when (state.screen) {
        Screen.HOME -> HomeScreen(
            profile = state.profile,
            // The radio is only asked for once the room is actually being opened.
            onHost = vm::openCreate,
            onJoin = { withBle(asHost = false) { vm.openJoin() } },
            // Solo needs no radio at all, so it skips the permission dance.
            onSolo = vm::openSolo,
            onRules = vm::openRules,
            onProfile = vm::openProfile,
            onSettings = vm::openSettings
        )

        Screen.PROFILE -> ProfileScreen(
            profile = state.profile,
            onSave = vm::saveProfile,
            onPickPhoto = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onResetStats = vm::resetStats,
            onBack = vm::closeProfile
        )

        Screen.CREATE -> CreateScreen(
            onHost = { mods -> withBle(asHost = true) { vm.startHosting(mods) } },
            onBack = vm::closeCreate
        )

        Screen.SOLO -> SoloScreen(onPick = vm::startSolo, onBack = vm::closeSolo)

        Screen.RULES -> RulesScreen(onBack = vm::closeRules)

        Screen.SETTINGS -> SettingsScreen(
            update = state.update,
            token = state.updateToken,
            canInstall = Updater.canInstall(context),
            onTokenChange = vm::setUpdateToken,
            onCheck = vm::checkForUpdate,
            onDownload = vm::downloadUpdate,
            onInstall = { path ->
                // Re-check: the user may have just granted the permission and come back.
                if (Updater.canInstall(context)) {
                    context.startActivity(Updater.installIntent(context, File(path)))
                } else {
                    context.startActivity(Updater.unknownSourcesIntent(context))
                }
            },
            onAllowInstalls = { context.startActivity(Updater.unknownSourcesIntent(context)) },
            onBack = vm::closeSettings
        )

        Screen.HOST -> HostScreen(
            roomCode = state.roomCode,
            joinLink = state.joinLink,
            status = state.statusText,
            link = state.link,
            players = state.players,
            photos = state.photos,
            mods = state.mods,
            canStart = state.canStart,
            onStart = vm::startGame,
            onBack = vm::goHome
        )

        Screen.LOBBY -> LobbyScreen(
            players = state.players,
            photos = state.photos,
            mySeat = state.mySeat,
            roomCode = state.roomCode,
            mods = state.mods,
            onLeave = vm::goHome
        )

        Screen.JOIN -> JoinScreen(
            hasCameraPermission = cameraGranted,
            onRequestCamera = { requestCamera.launch(Manifest.permission.CAMERA) },
            status = state.statusText,
            link = state.link,
            connecting = state.link == LinkStatus.SEARCHING ||
                state.link == LinkStatus.CONNECTING ||
                state.link == LinkStatus.CONNECTED,
            onScanned = vm::joinWith,
            onBack = vm::goHome
        )

        Screen.GAME -> {
            val view = state.view
            if (view == null) {
                MenuBackground {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        StatusRow("Distribution des cartes…", busy = true)
                    }
                }
            } else {
                GameScreen(
                    view = view,
                    photos = state.photos,
                    inputLocked = state.inputLocked,
                    onPlay = vm::playCard,
                    onDraw = vm::drawCard,
                    onPass = vm::passTurn,
                    onRematch = vm::requestRematch,
                    onQuit = vm::leaveGame
                )
            }
        }
    }

    val message = state.error ?: localError
    if (message != null) {
        val lostLink = state.link == LinkStatus.LOST
        AlertDialog(
            onDismissRequest = {
                if (state.error != null) vm.dismissError() else localError = null
                if (lostLink) vm.goHome()
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.error != null) vm.dismissError() else localError = null
                    if (lostLink) vm.goHome()
                }) {
                    Text(if (lostLink) "Retour au menu" else "OK", color = Palette.Gold)
                }
            },
            title = { Text("Oups", color = Palette.Text) },
            text = { Text(message, color = Palette.TextDim) },
            containerColor = Palette.Slate
        )
    }
}

private fun blePermissions(asHost: Boolean): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (asHost) {
            listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        }
    } else {
        if (asHost) {
            emptyList()
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

private fun isBluetoothOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return manager?.adapter?.isEnabled == true
}

private fun isLocationOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return manager != null && LocationManagerCompat.isLocationEnabled(manager)
}
