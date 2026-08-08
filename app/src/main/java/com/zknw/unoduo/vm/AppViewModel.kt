package com.zknw.unoduo.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.GameView
import com.zknw.unoduo.game.Seat
import com.zknw.unoduo.game.UnoEngine
import com.zknw.unoduo.net.BleGuest
import com.zknw.unoduo.net.BleHost
import com.zknw.unoduo.net.JoinLink
import com.zknw.unoduo.net.NetMsg
import com.zknw.unoduo.net.RoomCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.random.Random

enum class Screen { HOME, RULES, HOST, JOIN, GAME }

enum class LinkStatus { IDLE, ADVERTISING, SEARCHING, CONNECTING, CONNECTED, LOST }

data class UiState(
    val screen: Screen = Screen.HOME,
    val playerName: String = "",
    val isHost: Boolean = false,
    val roomCode: String = "",
    val joinLink: String = "",
    val link: LinkStatus = LinkStatus.IDLE,
    val statusText: String = "",
    val error: String? = null,
    val view: GameView? = null,
    val inputLocked: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("unoduo", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(playerName = prefs.getString("name", "") ?: "")
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var host: BleHost? = null
    private var guest: BleGuest? = null

    private var engine: UnoEngine? = null
    private var guestName: String = "Invité"
    private var nextStarter: Seat = Seat.GUEST
    private var rematchHost = false
    private var rematchGuest = false
    private var unlockJob: kotlinx.coroutines.Job? = null

    // ------------------------------------------------------------- navigation

    fun setName(name: String) {
        val trimmed = name.take(14)
        prefs.edit().putString("name", trimmed).apply()
        _state.update { it.copy(playerName = trimmed) }
    }

    fun goHome() {
        teardown()
        _state.update {
            UiState(playerName = it.playerName)
        }
    }

    fun openRules() = _state.update { it.copy(screen = Screen.RULES) }

    fun closeRules() = _state.update { it.copy(screen = Screen.HOME) }

    fun dismissError() = _state.update { it.copy(error = null) }

    // ------------------------------------------------------------------ host

    fun startHosting() {
        teardown()
        val code = RoomCode.random()
        val name = displayName()
        _state.update {
            it.copy(
                screen = Screen.HOST,
                isHost = true,
                roomCode = code,
                joinLink = JoinLink.build(code, name),
                link = LinkStatus.IDLE,
                statusText = "Démarrage du salon…",
                view = null,
                error = null
            )
        }
        val bleHost = BleHost(getApplication(), code, hostListener)
        host = bleHost
        bleHost.start()
    }

    private val hostListener = object : BleHost.Listener {
        override fun onAdvertising() = post {
            _state.update {
                it.copy(link = LinkStatus.ADVERTISING, statusText = "En attente d'un joueur…")
            }
        }

        override fun onGuestConnected() = post {
            _state.update { it.copy(statusText = "Joueur détecté…") }
        }

        override fun onGuestDisconnected() = post {
            if (_state.value.screen == Screen.GAME) {
                _state.update {
                    it.copy(link = LinkStatus.LOST, error = "Ton adversaire s'est déconnecté.")
                }
            } else {
                _state.update {
                    it.copy(link = LinkStatus.ADVERTISING, statusText = "En attente d'un joueur…")
                }
            }
        }

        override fun onMessage(msg: NetMsg) = post { handleFromGuest(msg) }

        override fun onError(message: String) = post {
            _state.update { it.copy(error = message, link = LinkStatus.IDLE) }
        }
    }

    private fun handleFromGuest(msg: NetMsg) {
        when (msg) {
            is NetMsg.Hello -> {
                if (msg.code != _state.value.roomCode) {
                    host?.send(NetMsg.Welcome(ok = false, reason = "Mauvais code de salon"))
                    return
                }
                guestName = msg.name.ifBlank { "Invité" }
                host?.send(NetMsg.Welcome(ok = true, hostName = displayName()))
                _state.update { it.copy(screen = Screen.GAME, link = LinkStatus.CONNECTED) }
                val running = engine
                if (running == null) {
                    startNewRound(firstRound = true)
                } else {
                    // The guest reconnected mid-game: resume instead of redealing.
                    running.setNames(displayName(), guestName)
                    broadcast()
                }
            }

            is NetMsg.Play -> {
                val e = engine ?: return
                if (e.playCard(Seat.GUEST, msg.cardId, msg.color)) broadcast()
                else host?.send(NetMsg.State(e.viewFor(Seat.GUEST, rematchGuest, rematchHost)))
            }

            NetMsg.Draw -> {
                val e = engine ?: return
                if (e.draw(Seat.GUEST)) broadcast()
                else host?.send(NetMsg.State(e.viewFor(Seat.GUEST, rematchGuest, rematchHost)))
            }

            NetMsg.Pass -> {
                val e = engine ?: return
                if (e.pass(Seat.GUEST)) broadcast()
                else host?.send(NetMsg.State(e.viewFor(Seat.GUEST, rematchGuest, rematchHost)))
            }

            NetMsg.Rematch -> {
                rematchGuest = true
                if (rematchHost) startNewRound(firstRound = false) else broadcast()
            }

            NetMsg.Bye -> {
                _state.update {
                    it.copy(link = LinkStatus.LOST, error = "Ton adversaire a quitté la partie.")
                }
            }

            is NetMsg.Welcome, is NetMsg.State -> Unit // host never receives these
        }
    }

    private fun startNewRound(firstRound: Boolean) {
        var e = engine
        if (e == null) {
            e = UnoEngine(Random(SecureRandom().nextLong()))
            engine = e
        }
        e.setNames(displayName(), guestName)
        val starter = if (firstRound) Seat.HOST else nextStarter
        nextStarter = starter.other
        rematchHost = false
        rematchGuest = false
        e.startRound(starter)
        broadcast()
    }

    private fun broadcast() {
        val e = engine ?: return
        // The player on turn must always have a real choice: anything forced (drawing
        // because nothing is playable, eating a stack you cannot counter) happens here.
        e.autoAdvance()
        _state.update {
            it.copy(
                screen = Screen.GAME,
                view = e.viewFor(Seat.HOST, rematchHost, rematchGuest),
                inputLocked = false
            )
        }
        host?.send(NetMsg.State(e.viewFor(Seat.GUEST, rematchGuest, rematchHost)))
    }

    // ----------------------------------------------------------------- guest

    fun openJoin() {
        teardown()
        _state.update {
            it.copy(
                screen = Screen.JOIN,
                isHost = false,
                link = LinkStatus.IDLE,
                statusText = "",
                roomCode = "",
                view = null,
                error = null
            )
        }
    }

    /** Called with the raw QR content, or with a hand-typed room code. */
    fun joinWith(raw: String) {
        val parsed = JoinLink.parse(raw) ?: run {
            _state.update { it.copy(error = "QR code ou code de salon invalide.") }
            return
        }
        if (guest != null) return
        _state.update {
            it.copy(roomCode = parsed.code, statusText = "Recherche du salon…", error = null)
        }
        val bleGuest = BleGuest(getApplication(), parsed.code, guestListener)
        guest = bleGuest
        bleGuest.start()
    }

    private val guestListener = object : BleGuest.Listener {
        override fun onSearching() = post {
            _state.update { it.copy(link = LinkStatus.SEARCHING, statusText = "Recherche du salon…") }
        }

        override fun onConnecting() = post {
            _state.update { it.copy(link = LinkStatus.CONNECTING, statusText = "Connexion…") }
        }

        override fun onReady() = post {
            _state.update { it.copy(statusText = "Entrée dans le salon…") }
            guest?.send(NetMsg.Hello(_state.value.roomCode, displayName()))
        }

        override fun onMessage(msg: NetMsg) = post { handleFromHost(msg) }

        override fun onDisconnected() = post {
            _state.update { it.copy(link = LinkStatus.LOST, error = "Connexion perdue avec l'hôte.") }
        }

        override fun onError(message: String) = post {
            guest?.stop()
            guest = null
            _state.update { it.copy(error = message, link = LinkStatus.IDLE, statusText = "") }
        }
    }

    private fun handleFromHost(msg: NetMsg) {
        when (msg) {
            is NetMsg.Welcome -> {
                if (!msg.ok) {
                    _state.update {
                        it.copy(error = msg.reason.ifBlank { "Salon refusé" }, link = LinkStatus.IDLE)
                    }
                } else {
                    _state.update { it.copy(link = LinkStatus.CONNECTED, statusText = "Connecté") }
                }
            }

            is NetMsg.State -> {
                unlockJob?.cancel()
                _state.update {
                    it.copy(
                        screen = Screen.GAME,
                        link = LinkStatus.CONNECTED,
                        view = msg.view,
                        inputLocked = false
                    )
                }
            }

            NetMsg.Bye -> _state.update {
                it.copy(link = LinkStatus.LOST, error = "L'hôte a quitté la partie.")
            }

            is NetMsg.Hello, is NetMsg.Play, NetMsg.Draw, NetMsg.Pass, NetMsg.Rematch -> Unit
        }
    }

    // ------------------------------------------------------------ game input

    fun playCard(cardId: Int, color: CardColor?) {
        val view = _state.value.view ?: return
        if (!view.yourTurn || _state.value.inputLocked) return
        if (cardId !in view.legal) return
        if (_state.value.isHost) {
            val e = engine ?: return
            if (e.playCard(Seat.HOST, cardId, color)) broadcast()
        } else {
            lockInput()
            guest?.send(NetMsg.Play(cardId, color))
        }
    }

    fun passTurn() {
        val view = _state.value.view ?: return
        if (!view.canPass || _state.value.inputLocked) return
        if (_state.value.isHost) {
            val e = engine ?: return
            if (e.pass(Seat.HOST)) broadcast()
        } else {
            lockInput()
            guest?.send(NetMsg.Pass)
        }
    }

    fun requestRematch() {
        if (_state.value.isHost) {
            rematchHost = true
            if (rematchGuest) startNewRound(firstRound = false) else broadcast()
        } else {
            guest?.send(NetMsg.Rematch)
        }
    }

    /** Blocks further taps until the host answers, with a safety release. */
    private fun lockInput() {
        _state.update { it.copy(inputLocked = true) }
        unlockJob?.cancel()
        unlockJob = viewModelScope.launch {
            delay(2500)
            _state.update { it.copy(inputLocked = false) }
        }
    }

    // ----------------------------------------------------------------- misc

    fun leaveGame() {
        if (_state.value.isHost) host?.send(NetMsg.Bye) else guest?.send(NetMsg.Bye)
        viewModelScope.launch {
            delay(120)
            goHome()
        }
    }

    private fun displayName(): String = _state.value.playerName.ifBlank { "Joueur" }

    private fun teardown() {
        unlockJob?.cancel()
        host?.stop()
        host = null
        guest?.stop()
        guest = null
        engine = null
        rematchHost = false
        rematchGuest = false
        nextStarter = Seat.GUEST
        guestName = "Invité"
    }

    private fun post(block: () -> Unit) {
        viewModelScope.launch { withContext(Dispatchers.Main) { block() } }
    }

    override fun onCleared() {
        teardown()
        super.onCleared()
    }
}
