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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
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
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zknw.unoduo.ui.GameScreen
import com.zknw.unoduo.ui.HomeScreen
import com.zknw.unoduo.ui.HostScreen
import com.zknw.unoduo.ui.JoinScreen
import com.zknw.unoduo.ui.RulesScreen
import com.zknw.unoduo.ui.components.MenuBackground
import com.zknw.unoduo.ui.components.StatusRow
import com.zknw.unoduo.ui.theme.Palette
import com.zknw.unoduo.ui.theme.UnoDuoTheme
import com.zknw.unoduo.vm.AppViewModel
import com.zknw.unoduo.vm.LinkStatus
import com.zknw.unoduo.vm.Screen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnoDuoTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    App()
                }
            }
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
            Screen.GAME -> vm.leaveGame()
            else -> vm.goHome()
        }
    }

    when (state.screen) {
        Screen.HOME -> HomeScreen(
            playerName = state.playerName,
            onNameChange = vm::setName,
            onHost = { withBle(asHost = true) { vm.startHosting() } },
            onJoin = { withBle(asHost = false) { vm.openJoin() } },
            onRules = vm::openRules
        )

        Screen.RULES -> RulesScreen(onBack = vm::closeRules)

        Screen.HOST -> HostScreen(
            roomCode = state.roomCode,
            joinLink = state.joinLink,
            status = state.statusText,
            link = state.link,
            onBack = vm::goHome
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
                    inputLocked = state.inputLocked,
                    onPlay = vm::playCard,
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
        if (asHost) emptyList() else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun isBluetoothOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return manager?.adapter?.isEnabled == true
}

private fun isLocationOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return manager != null && LocationManagerCompat.isLocationEnabled(manager)
}
