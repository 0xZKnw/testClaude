package com.zknw.unoduo.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.GameView
import com.zknw.unoduo.game.HOST_SEAT
import com.zknw.unoduo.game.MAX_PLAYERS
import com.zknw.unoduo.game.MIN_PLAYERS
import com.zknw.unoduo.game.Phase
import com.zknw.unoduo.game.Seat
import com.zknw.unoduo.game.UnoEngine
import com.zknw.unoduo.net.BleGuest
import com.zknw.unoduo.net.BleHost
import com.zknw.unoduo.net.JoinLink
import com.zknw.unoduo.net.LobbyPlayer
import com.zknw.unoduo.net.NetMsg
import com.zknw.unoduo.net.RoomCode
import com.zknw.unoduo.profile.AvatarImage
import com.zknw.unoduo.profile.Profile
import com.zknw.unoduo.profile.ProfileStore
import com.zknw.unoduo.update.Updater
import com.zknw.unoduo.BuildConfig
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

enum class Screen { HOME, RULES, PROFILE, SETTINGS, HOST, JOIN, LOBBY, GAME }

/** Where the in-app updater is in its little state machine. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val commit: String) : UpdateState
    data class Found(val release: Updater.Available) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Ready(val path: String, val commit: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

enum class LinkStatus { IDLE, ADVERTISING, SEARCHING, CONNECTING, CONNECTED, LOST }

data class UiState(
    val screen: Screen = Screen.HOME,
    val isHost: Boolean = false,
    val roomCode: String = "",
    val joinLink: String = "",
    val link: LinkStatus = LinkStatus.IDLE,
    val statusText: String = "",
    val error: String? = null,
    val view: GameView? = null,
    val inputLocked: Boolean = false,
    val update: UpdateState = UpdateState.Idle,
    val updateToken: String = "",
    val profile: Profile = Profile(),
    /** Everyone in the room, host first, in join order. */
    val players: List<LobbyPlayer> = emptyList(),
    /** Base64 thumbnails by seat, kept out of the game snapshots. */
    val photos: Map<Seat, String> = emptyMap(),
    val mySeat: Seat = HOST_SEAT,
    /** Seats whose phone has dropped off mid-game. */
    val offline: Set<Seat> = emptySet()
) {
    val playerName: String get() = profile.name
    val canStart: Boolean get() = isHost && players.size >= MIN_PLAYERS
    val roomFull: Boolean get() = players.size >= MAX_PLAYERS
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("unoduo", Context.MODE_PRIVATE)

    private val profileStore = ProfileStore(prefs)

    private val _state = MutableStateFlow(
        UiState(
            updateToken = prefs.getString("updateToken", "") ?: "",
            profile = profileStore.load()
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var host: BleHost? = null
    private var guest: BleGuest? = null

    private var engine: UnoEngine? = null
    private var lastRecordedRound: Int = -1
    private var nextStarter: Seat = HOST_SEAT
    private var rematch = mutableSetOf<Seat>()
    private var unlockJob: kotlinx.coroutines.Job? = null

    /** Host-side seating: a Bluetooth address on one side, a seat on the other. */
    private val seatOfKey = mutableMapOf<String, Seat>()
    private val keyOfSeat = mutableMapOf<Seat, String>()

    /** My own picture, encoded once so it is not re-compressed for every guest. */
    private var myPhoto: String? = null

    // ------------------------------------------------------------- navigation

    fun saveProfile(name: String, avatarColor: Int, photoUri: String?) {
        profileStore.saveIdentity(name.trim().take(14), avatarColor, photoUri)
        _state.update { it.copy(profile = profileStore.load()) }
        myPhoto = null
        encodeMyPhoto()
    }

    fun resetStats() {
        _state.update { it.copy(profile = profileStore.resetStats()) }
    }

    fun openProfile() = _state.update { it.copy(screen = Screen.PROFILE) }

    fun closeProfile() = _state.update { it.copy(screen = Screen.HOME) }

    fun goHome() {
        teardown()
        _state.update {
            UiState(profile = it.profile, updateToken = it.updateToken)
        }
    }

    fun openRules() = _state.update { it.copy(screen = Screen.RULES) }

    fun closeRules() = _state.update { it.copy(screen = Screen.HOME) }

    fun openSettings() = _state.update { it.copy(screen = Screen.SETTINGS) }

    fun closeSettings() = _state.update { it.copy(screen = Screen.HOME, update = UpdateState.Idle) }

    /** Shrinks the picked photo down to something a BLE link can carry. */
    private fun encodeMyPhoto() {
        val uri = _state.value.profile.photoUri ?: return
        if (myPhoto != null) return
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                AvatarImage.encode(getApplication(), uri)
            }
            myPhoto = encoded
            val seat = _state.value.mySeat
            if (encoded != null) {
                _state.update { it.copy(photos = it.photos + (seat to encoded)) }
                shareMyPhoto()
            }
        }
    }

    private fun shareMyPhoto() {
        val encoded = myPhoto ?: return
        val seat = _state.value.mySeat
        if (_state.value.isHost) host?.broadcast(NetMsg.Photo(seat, encoded))
        else guest?.send(NetMsg.Photo(seat, encoded))
    }

    // --------------------------------------------------------------- updating

    fun setUpdateToken(token: String) {
        val trimmed = token.trim()
        prefs.edit().putString("updateToken", trimmed).apply()
        _state.update { it.copy(updateToken = trimmed, update = UpdateState.Idle) }
    }

    fun checkForUpdate() {
        if (_state.value.update is UpdateState.Checking) return
        _state.update { it.copy(update = UpdateState.Checking) }
        viewModelScope.launch {
            val token = _state.value.updateToken.takeIf { it.isNotBlank() }
            val next = when (val outcome = Updater.check(BuildConfig.GIT_SHA, token)) {
                is Updater.Outcome.UpToDate -> UpdateState.UpToDate(outcome.commit)
                is Updater.Outcome.Ready -> UpdateState.Found(outcome.release)
                is Updater.Outcome.Failed -> UpdateState.Failed(outcome.reason)
            }
            _state.update { it.copy(update = next) }
        }
    }

    fun downloadUpdate() {
        val found = _state.value.update as? UpdateState.Found ?: return
        _state.update { it.copy(update = UpdateState.Downloading(0f)) }
        viewModelScope.launch {
            val token = _state.value.updateToken.takeIf { it.isNotBlank() }
            val result = Updater.download(found.release, token, getApplication()) { progress ->
                _state.update { current ->
                    if (current.update is UpdateState.Downloading) {
                        current.copy(update = UpdateState.Downloading(progress))
                    } else {
                        current
                    }
                }
            }
            val next = result.fold(
                onSuccess = { UpdateState.Ready(it.absolutePath, found.release.commit) },
                onFailure = { UpdateState.Failed(it.message ?: "Téléchargement impossible") }
            )
            _state.update { it.copy(update = next) }
        }
    }

    fun resetUpdate() = _state.update { it.copy(update = UpdateState.Idle) }

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
                mySeat = HOST_SEAT,
                roomCode = code,
                joinLink = JoinLink.build(code, name),
                link = LinkStatus.IDLE,
                statusText = "Démarrage du salon…",
                view = null,
                players = listOf(LobbyPlayer(HOST_SEAT, name, it.profile.avatarColor)),
                photos = emptyMap(),
                offline = emptySet(),
                error = null
            )
        }
        encodeMyPhoto()
        val bleHost = BleHost(getApplication(), code, MAX_PLAYERS - 1, hostListener)
        host = bleHost
        bleHost.start()
    }

    private val hostListener = object : BleHost.Listener {
        override fun onAdvertising() = post {
            _state.update {
                it.copy(link = LinkStatus.ADVERTISING, statusText = lobbyStatus(it))
            }
        }

        override fun onGuestReady(key: String) = post {
            // Nothing to do until the guest introduces itself: the seat is handed out
            // on Hello, once the room code has been checked.
        }

        override fun onGuestGone(key: String) = post { handleGuestGone(key) }

        override fun onMessage(key: String, msg: NetMsg) = post { handleFromGuest(key, msg) }

        override fun onError(message: String) = post {
            _state.update { it.copy(error = message, link = LinkStatus.IDLE) }
        }
    }

    private fun lobbyStatus(s: UiState): String = when {
        s.players.size >= MAX_PLAYERS -> "Salon complet — tu peux lancer"
        s.players.size >= MIN_PLAYERS -> "${s.players.size} joueurs — tu peux lancer"
        else -> "En attente d'un joueur…"
    }

    private fun handleGuestGone(key: String) {
        val seat = seatOfKey[key] ?: return
        if (engine != null) {
            // Mid-game: hold the seat. A BLE drop is usually a hiccup, and the same
            // phone reconnecting must find its own hand rather than a fresh one.
            _state.update {
                it.copy(
                    offline = it.offline + seat,
                    error = "${nameOfSeat(seat)} s'est déconnecté. La partie attend son retour."
                )
            }
            return
        }
        seatOfKey.remove(key)
        keyOfSeat.remove(seat)
        _state.update { s ->
            val players = s.players.filterNot { it.seat == seat }
            s.copy(
                players = players,
                photos = s.photos - seat,
                statusText = lobbyStatus(s.copy(players = players))
            )
        }
        host?.reopenRoom()
        broadcastLobby()
    }

    private fun handleFromGuest(key: String, msg: NetMsg) {
        when (msg) {
            is NetMsg.Hello -> welcomeGuest(key, msg)

            is NetMsg.Photo -> {
                val seat = seatOfKey[key] ?: return
                val data = msg.png.takeIf { it.length <= AvatarImage.MAX_ENCODED_CHARS } ?: return
                _state.update { it.copy(photos = it.photos + (seat to data)) }
                // Everyone needs it, and the sender already has its own copy.
                seatOfKey.forEach { (other, otherSeat) ->
                    if (otherSeat != seat) host?.send(other, NetMsg.Photo(seat, data))
                }
            }

            is NetMsg.Play -> withSeat(key) { seat, e ->
                if (e.playCard(seat, msg.cardId, msg.color)) broadcast() else resend(seat)
            }

            NetMsg.Draw -> withSeat(key) { seat, e ->
                if (e.draw(seat)) broadcast() else resend(seat)
            }

            NetMsg.Pass -> withSeat(key) { seat, e ->
                if (e.pass(seat)) broadcast() else resend(seat)
            }

            NetMsg.Rematch -> {
                val seat = seatOfKey[key] ?: return
                rematch += seat
                if (rematch.size >= (engine?.playerCount ?: Int.MAX_VALUE)) {
                    startNewRound(firstRound = false)
                } else {
                    broadcast()
                }
            }

            NetMsg.Bye -> handleGuestBye(key)

            // The host is the one who decides when to deal, and never receives these.
            NetMsg.Start, is NetMsg.Welcome, is NetMsg.Lobby, is NetMsg.State -> Unit
        }
    }

    private inline fun withSeat(key: String, block: (Seat, UnoEngine) -> Unit) {
        val seat = seatOfKey[key] ?: return
        val e = engine ?: return
        block(seat, e)
    }

    private fun handleGuestBye(key: String) {
        val seat = seatOfKey[key] ?: return
        if (engine != null) {
            endGame("${nameOfSeat(seat)} a quitté la partie.")
        } else {
            handleGuestGone(key)
        }
    }

    private fun welcomeGuest(key: String, hello: NetMsg.Hello) {
        if (hello.code != _state.value.roomCode) {
            host?.send(key, NetMsg.Welcome(ok = false, reason = "Mauvais code de salon"))
            return
        }
        val name = hello.name.ifBlank { "Joueur" }

        // A phone that was already seated is reconnecting: give it its seat back rather
        // than a new one, so its hand and its score survive the drop.
        val existing = seatOfKey[key]
        val seat = existing ?: firstFreeSeat() ?: run {
            host?.send(key, NetMsg.Welcome(ok = false, reason = "Salon complet"))
            return
        }
        if (existing == null && engine != null) {
            host?.send(key, NetMsg.Welcome(ok = false, reason = "La partie a déjà commencé"))
            return
        }

        seatOfKey[key] = seat
        keyOfSeat[seat] = key
        _state.update { s ->
            val players = (s.players.filterNot { it.seat == seat } +
                LobbyPlayer(seat, name, hello.avatar)).sortedBy { it.seat }
            s.copy(
                players = players,
                offline = s.offline - seat,
                link = LinkStatus.CONNECTED,
                statusText = lobbyStatus(s.copy(players = players)),
                error = null
            )
        }
        engine?.setName(seat, name)
        engine?.setAvatar(seat, hello.avatar)

        host?.send(key, NetMsg.Welcome(ok = true, seat = seat))
        broadcastLobby()
        // Catch the newcomer up on everyone's face, then tell the others about theirs.
        _state.value.photos.forEach { (photoSeat, data) ->
            if (photoSeat != seat) host?.send(key, NetMsg.Photo(photoSeat, data))
        }
        if (engine != null) broadcast()
    }

    private fun firstFreeSeat(): Seat? {
        val taken = _state.value.players.map { it.seat }.toSet()
        return (1 until MAX_PLAYERS).firstOrNull { it !in taken }
    }

    private fun broadcastLobby() {
        host?.broadcast(NetMsg.Lobby(_state.value.players, started = engine != null))
    }

    private fun nameOfSeat(seat: Seat): String =
        _state.value.players.firstOrNull { it.seat == seat }?.name ?: "Un joueur"

    /** Host taps "Démarrer": the door closes and the cards go out. */
    fun startGame() {
        if (!_state.value.canStart || engine != null) return
        host?.closeRoom()
        startNewRound(firstRound = true)
    }

    private fun startNewRound(firstRound: Boolean) {
        val players = _state.value.players.sortedBy { it.seat }
        if (players.size < MIN_PLAYERS) return
        var e = engine
        if (e == null || e.playerCount != players.size) {
            e = UnoEngine(Random(SecureRandom().nextLong()), players.size)
            engine = e
        }
        players.forEach { p ->
            e.setName(p.seat, p.name)
            e.setAvatar(p.seat, p.avatar)
        }
        val starter = if (firstRound) HOST_SEAT else nextStarter
        nextStarter = (starter + 1) % players.size
        rematch = mutableSetOf()
        e.startRound(starter)
        broadcastLobby()
        broadcast()
    }

    /** Adds a finished round to this device's totals, exactly once. */
    private fun maybeRecordRound(view: GameView) {
        if (view.phase != Phase.GAME_OVER) return
        if (view.roundId == lastRecordedRound) return
        lastRecordedRound = view.roundId
        val updated = profileStore.recordRound(view.youWon, view.yourStats)
        _state.update { it.copy(profile = updated) }
    }

    private fun broadcast() {
        val e = engine ?: return
        // The player on turn must always have a real choice: anything forced (drawing
        // because nothing is playable, eating a stack you cannot counter) happens here.
        e.autoAdvance()
        val hostView = e.viewFor(HOST_SEAT, rematch)
        _state.update {
            it.copy(screen = Screen.GAME, view = hostView, inputLocked = false)
        }
        maybeRecordRound(hostView)
        keyOfSeat.forEach { (seat, key) ->
            host?.send(key, NetMsg.State(e.viewFor(seat, rematch)))
        }
    }

    private fun resend(seat: Seat) {
        val e = engine ?: return
        val key = keyOfSeat[seat] ?: return
        host?.send(key, NetMsg.State(e.viewFor(seat, rematch)))
    }

    private fun endGame(reason: String) {
        engine = null
        _state.update { it.copy(link = LinkStatus.LOST, error = reason) }
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
                players = emptyList(),
                photos = emptyMap(),
                offline = emptySet(),
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
            guest?.send(
                NetMsg.Hello(
                    code = _state.value.roomCode,
                    name = displayName(),
                    avatar = _state.value.profile.avatarColor
                )
            )
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
                    _state.update {
                        it.copy(
                            screen = if (it.view == null) Screen.LOBBY else it.screen,
                            mySeat = msg.seat,
                            link = LinkStatus.CONNECTED,
                            statusText = "Dans le salon"
                        )
                    }
                    myPhoto?.let { guest?.send(NetMsg.Photo(msg.seat, it)) } ?: encodeMyPhoto()
                }
            }

            is NetMsg.Lobby -> _state.update {
                it.copy(
                    players = msg.players.sortedBy { p -> p.seat },
                    screen = if (it.view == null && it.link == LinkStatus.CONNECTED) {
                        Screen.LOBBY
                    } else {
                        it.screen
                    }
                )
            }

            is NetMsg.Photo -> {
                val data = msg.png.takeIf { it.length <= AvatarImage.MAX_ENCODED_CHARS } ?: return
                _state.update { it.copy(photos = it.photos + (msg.seat to data)) }
            }

            is NetMsg.State -> {
                unlockJob?.cancel()
                _state.update {
                    it.copy(
                        screen = Screen.GAME,
                        link = LinkStatus.CONNECTED,
                        view = msg.view,
                        mySeat = msg.view.youAre,
                        inputLocked = false
                    )
                }
                maybeRecordRound(msg.view)
            }

            NetMsg.Bye -> _state.update {
                it.copy(link = LinkStatus.LOST, error = "L'hôte a quitté la partie.")
            }

            is NetMsg.Hello, is NetMsg.Play, NetMsg.Draw, NetMsg.Pass, NetMsg.Rematch,
            NetMsg.Start -> Unit
        }
    }

    // ------------------------------------------------------------ game input

    fun playCard(cardId: Int, color: CardColor?) {
        val view = _state.value.view ?: return
        if (!view.yourTurn || _state.value.inputLocked) return
        if (cardId !in view.legal) return
        if (_state.value.isHost) {
            val e = engine ?: return
            if (e.playCard(HOST_SEAT, cardId, color)) broadcast()
        } else {
            lockInput()
            guest?.send(NetMsg.Play(cardId, color))
        }
    }

    /** Tapping the deck. Only a normal draw: penalties resolve on their own. */
    fun drawCard() {
        val view = _state.value.view ?: return
        if (!view.canDraw || _state.value.inputLocked) return
        if (_state.value.isHost) {
            val e = engine ?: return
            if (e.draw(HOST_SEAT)) broadcast()
        } else {
            lockInput()
            guest?.send(NetMsg.Draw)
        }
    }

    fun passTurn() {
        val view = _state.value.view ?: return
        if (!view.canPass || _state.value.inputLocked) return
        if (_state.value.isHost) {
            val e = engine ?: return
            if (e.pass(HOST_SEAT)) broadcast()
        } else {
            lockInput()
            guest?.send(NetMsg.Pass)
        }
    }

    fun requestRematch() {
        if (_state.value.isHost) {
            rematch += HOST_SEAT
            if (rematch.size >= (engine?.playerCount ?: Int.MAX_VALUE)) {
                startNewRound(firstRound = false)
            } else {
                broadcast()
            }
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
        if (_state.value.isHost) host?.broadcast(NetMsg.Bye) else guest?.send(NetMsg.Bye)
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
        rematch = mutableSetOf()
        seatOfKey.clear()
        keyOfSeat.clear()
        nextStarter = HOST_SEAT
        lastRecordedRound = -1
    }

    private fun post(block: () -> Unit) {
        viewModelScope.launch { withContext(Dispatchers.Main) { block() } }
    }

    override fun onCleared() {
        teardown()
        super.onCleared()
    }
}
