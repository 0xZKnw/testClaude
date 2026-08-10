package com.zknw.unoduo.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zknw.unoduo.game.Bot
import com.zknw.unoduo.game.BotMove
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.Difficulty
import com.zknw.unoduo.game.GameMod
import com.zknw.unoduo.game.GameView
import com.zknw.unoduo.game.HOST_SEAT
import com.zknw.unoduo.game.MAX_PLAYERS
import com.zknw.unoduo.game.MIN_PLAYERS
import com.zknw.unoduo.game.ordered
import com.zknw.unoduo.game.Phase
import com.zknw.unoduo.game.Seat
import com.zknw.unoduo.game.UnoEngine
import com.zknw.unoduo.net.BleGuest
import com.zknw.unoduo.net.BleHost
import com.zknw.unoduo.net.JoinLink
import com.zknw.unoduo.net.LobbyPlayer
import com.zknw.unoduo.net.NetMsg
import com.zknw.unoduo.net.RoomCode
import com.zknw.unoduo.net.Talk
import com.zknw.unoduo.profile.AvatarImage
import com.zknw.unoduo.profile.Profile
import com.zknw.unoduo.profile.ProfileStore
import com.zknw.unoduo.progress.Cosmetic
import com.zknw.unoduo.progress.CosmeticKind
import com.zknw.unoduo.progress.Cosmetics
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

enum class Screen { HOME, RULES, PROFILE, COSMETICS, SETTINGS, CREATE, SOLO, HOST, JOIN, LOBBY, GAME }

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

/** One line of chat. [id] only exists so the UI can key an animation on it. */
data class ChatLine(
    val id: Long,
    val seat: Seat,
    val name: String,
    val text: String,
    val mine: Boolean
)

/** A sticker in flight. It is removed by the view model once its animation is over. */
data class Emote(val id: Long, val seat: Seat, val sticker: String)

/** Everything that is said rather than played. Empty and hidden in a solo game. */
data class Social(
    val enabled: Boolean = false,
    val chat: List<ChatLine> = emptyList(),
    /** The lines still worth popping over the hand; each expires on its own. */
    val flash: List<ChatLine> = emptyList(),
    val emotes: List<Emote> = emptyList(),
    val open: Boolean = false,
    val unread: Int = 0
)

/**
 * Everything cosmetic the table needs, resolved once so no screen has to know the
 * catalogue exists. The felt and the back are mine alone; the rest is what the other
 * players announced about themselves.
 */
data class TableLook(
    val felt: Cosmetic = Cosmetics.defaultOf(CosmeticKind.FELT),
    val back: Cosmetic = Cosmetics.defaultOf(CosmeticKind.BACK),
    val frames: Map<Seat, Cosmetic> = emptyMap(),
    val titles: Map<Seat, Cosmetic> = emptyMap(),
    val nameColors: Map<Seat, Cosmetic> = emptyMap(),
    val levels: Map<Seat, Int> = emptyMap(),
    /** How much of the sticker catalogue I have earned; the rail offers exactly that. */
    val stickers: Int = Cosmetics.stickersAt(1).size
) {
    fun frameOf(seat: Seat): Cosmetic = frames[seat] ?: Cosmetics.defaultOf(CosmeticKind.FRAME)
    fun titleOf(seat: Seat): String = titles[seat]?.worn.orEmpty()
    fun nameColorOf(seat: Seat): Cosmetic = nameColors[seat] ?: Cosmetics.defaultOf(CosmeticKind.NAME)
}

/** Shown once when a round pushes the bar over a level. */
data class LevelPopup(val from: Int, val to: Int, val unlocked: List<Cosmetic>)

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
    val offline: Set<Seat> = emptySet(),
    /** Set while playing against the machine; null for a real table. */
    val solo: Difficulty? = null,
    /** The optional rules this room plays with. Empty for a standard game. */
    val mods: Set<GameMod> = emptySet(),
    /** Chat and stickers. Nothing here ever reaches the engine. */
    val social: Social = Social(),
    /** Frames, cloth, card back — resolved from the catalogue, never guessed at. */
    val look: TableLook = TableLook(),
    /** Experience the last finished round was worth, for the end-of-round panel. */
    val lastXp: Int = 0,
    /** Where the bar stood before that round, so the panel can count up from it. */
    val xpBefore: Int = 0,
    /** Set only on the round that actually crossed a level. */
    val levelUp: LevelPopup? = null
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
    private var botJob: kotlinx.coroutines.Job? = null
    private val botRng = Random(SecureRandom().nextLong())

    /** Host-side seating: a Bluetooth address on one side, a seat on the other. */
    private val seatOfKey = mutableMapOf<String, Seat>()
    private val keyOfSeat = mutableMapOf<Seat, String>()

    /** My own picture, encoded once so it is not re-compressed for every guest. */
    private var myPhoto: String? = null

    /** Only ever used to key an animation, so a plain counter is enough. */
    private var nextSocialId: Long = 1

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

    /** Puts one cosmetic on and refreshes everything that draws it. */
    fun wear(id: String) {
        val worn = profileStore.wear(id)
        _state.update { it.copy(profile = worn, look = lookFor(worn, it.players)) }
        // The others need to know: a frame and a title are worn for them, not for you.
        announceMyself()
    }

    fun openProfile() = _state.update { it.copy(screen = Screen.PROFILE) }

    fun closeProfile() = _state.update { it.copy(screen = Screen.HOME) }

    fun openCosmetics() = _state.update { it.copy(screen = Screen.COSMETICS) }

    fun closeCosmetics() = _state.update { it.copy(screen = Screen.PROFILE) }

    fun dismissLevelUp() = _state.update { it.copy(levelUp = null) }

    /** Me, as the other phones should see me. */
    private fun meAsPlayer(seat: Seat): LobbyPlayer {
        val p = _state.value.profile
        return LobbyPlayer(
            seat = seat,
            name = displayName(),
            avatar = p.avatarColor,
            frame = p.worn(CosmeticKind.FRAME).id,
            title = p.worn(CosmeticKind.TITLE).id,
            nameColor = p.worn(CosmeticKind.NAME).id,
            level = p.level
        )
    }

    /**
     * Resolves what everybody is wearing into things the UI can draw. Anything a phone
     * announced that this build does not know falls back rather than leaving a hole.
     */
    private fun lookFor(profile: Profile, players: List<LobbyPlayer>): TableLook = TableLook(
        felt = profile.worn(CosmeticKind.FELT),
        back = profile.worn(CosmeticKind.BACK),
        frames = players.associate {
            it.seat to Cosmetics.resolve(it.frame, CosmeticKind.FRAME, it.level)
        },
        titles = players.associate {
            it.seat to Cosmetics.resolve(it.title, CosmeticKind.TITLE, it.level)
        },
        nameColors = players.associate {
            it.seat to Cosmetics.resolve(it.nameColor, CosmeticKind.NAME, it.level)
        },
        levels = players.associate { it.seat to it.level },
        stickers = profile.stickers.size
    )

    /**
     * Tells the room what I look like now — after changing an outfit, and after a round
     * that pushed me up a level. Without the second case a badge would sit frozen at
     * whatever it was when the room opened, all evening.
     */
    private fun announceMyself() {
        val state = _state.value
        if (state.players.isEmpty() || state.solo != null) return
        if (state.isHost) {
            val me = meAsPlayer(HOST_SEAT)
            _state.update { s ->
                val players = (s.players.filterNot { it.seat == HOST_SEAT } + me).sortedBy { it.seat }
                s.copy(players = players, look = lookFor(s.profile, players))
            }
            broadcastLobby()
        } else {
            val p = state.profile
            guest?.send(
                NetMsg.Wearing(
                    seat = state.mySeat,
                    frame = p.worn(CosmeticKind.FRAME).id,
                    title = p.worn(CosmeticKind.TITLE).id,
                    nameColor = p.worn(CosmeticKind.NAME).id,
                    level = p.level
                )
            )
        }
    }

    fun goHome() {
        teardown()
        _state.update {
            UiState(profile = it.profile, updateToken = it.updateToken)
        }
    }

    fun openCreate() = _state.update { it.copy(screen = Screen.CREATE) }

    fun closeCreate() = _state.update { it.copy(screen = Screen.HOME) }

    fun openSolo() = _state.update { it.copy(screen = Screen.SOLO) }

    fun closeSolo() = _state.update { it.copy(screen = Screen.HOME) }

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

    fun startHosting(mods: Set<GameMod> = emptySet()) {
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
                players = listOf(meAsPlayer(HOST_SEAT)),
                look = lookFor(it.profile, listOf(meAsPlayer(HOST_SEAT))),
                photos = emptyMap(),
                offline = emptySet(),
                mods = mods,
                social = Social(enabled = true),
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

            is NetMsg.Say -> {
                val seat = seatOfKey[key] ?: return
                val text = Talk.clean(msg.text) ?: return
                // Relayed with the seat the host knows, never the one the guest claimed,
                // and never back to the sender — its own line is already on its screen.
                relay(NetMsg.Say(seat, text), except = key)
                addChat(seat, text)
            }

            is NetMsg.Emote -> {
                val seat = seatOfKey[key] ?: return
                if (Talk.sticker(msg.index) == null) return
                relay(NetMsg.Emote(seat, msg.index), except = key)
                addEmote(seat, msg.index)
            }

            is NetMsg.Wearing -> {
                // The seat the host knows, never the one the guest claimed.
                val seat = seatOfKey[key] ?: return
                _state.update { s ->
                    val players = s.players.map { player ->
                        if (player.seat != seat) player else player.copy(
                            frame = msg.frame,
                            title = msg.title,
                            nameColor = msg.nameColor,
                            level = msg.level
                        )
                    }
                    s.copy(players = players, look = lookFor(s.profile, players))
                }
                broadcastLobby()
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
            val players = (s.players.filterNot { it.seat == seat } + LobbyPlayer(
                seat = seat,
                name = name,
                avatar = hello.avatar,
                frame = hello.frame,
                title = hello.title,
                nameColor = hello.nameColor,
                level = hello.level
            )).sortedBy { it.seat }
            s.copy(
                players = players,
                look = lookFor(s.profile, players),
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
        host?.broadcast(
            NetMsg.Lobby(
                players = _state.value.players,
                started = engine != null,
                mods = _state.value.mods.ordered()
            )
        )
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
        val mods = _state.value.mods
        var e = engine
        if (e == null || e.playerCount != players.size || e.mods != mods) {
            e = UnoEngine(Random(SecureRandom().nextLong()), players.size, mods)
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

    /**
     * Closes out a finished round, exactly once.
     *
     * Two different things happen here, and they follow two different rules on purpose.
     * The tally is a record against real people, so a game against the machine does not
     * touch it — padding it would empty it of meaning. Experience is a record of time
     * played, so every finished round counts: a progression that ignored solo would
     * punish exactly the players with nobody to play against.
     */
    private fun maybeRecordRound(view: GameView) {
        if (view.phase != Phase.GAME_OVER) return
        if (view.roundId == lastRecordedRound) return
        lastRecordedRound = view.roundId

        if (_state.value.solo == null) {
            val updated = profileStore.recordRound(view.youWon, view.yourStats)
            _state.update { it.copy(profile = updated) }
        }

        val gain = profileStore.addXp(view.youWon)
        _state.update {
            it.copy(
                profile = gain.profile,
                lastXp = gain.gained,
                xpBefore = gain.before,
                levelUp = if (gain.levelledUp) {
                    LevelPopup(gain.from, gain.to, gain.unlocked)
                } else {
                    null
                },
                // A new level can hand over the very frame you are wearing by default,
                // and the table has to pick it up without waiting for a reconnection.
                look = lookFor(gain.profile, it.players)
            )
        }
        // The badge next to my avatar is on everybody's screen, so a level crossed
        // between two rounds has to travel — otherwise a whole evening of rematches
        // shows me at the level I opened the room with.
        if (gain.levelledUp) announceMyself()
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
        scheduleBot()
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

    // ------------------------------------------------------------------ solo

    /**
     * A game against the machine. It runs the very same engine as a real table — the
     * bot simply takes the second seat and is handed the same snapshot a human would
     * get, so it plays by the same rules and sees no more than you do.
     */
    fun startSolo(difficulty: Difficulty, mods: Set<GameMod> = emptySet()) {
        teardown()
        val me = meAsPlayer(HOST_SEAT)
        // A colour of its own, so the bot is never your twin at the table.
        val botColor = if (me.avatar == BOT_AVATAR) BOT_AVATAR_ALT else BOT_AVATAR
        val table = listOf(me, LobbyPlayer(1, difficulty.botName, botColor))
        _state.update {
            it.copy(
                screen = Screen.GAME,
                isHost = true,
                solo = difficulty,
                mySeat = HOST_SEAT,
                link = LinkStatus.IDLE,
                statusText = "",
                roomCode = "",
                joinLink = "",
                view = null,
                players = table,
                look = lookFor(it.profile, table),
                photos = emptyMap(),
                offline = emptySet(),
                mods = mods,
                // Nobody to talk to: the chat bar and the sticker rail stay away.
                social = Social(enabled = false),
                error = null
            )
        }
        encodeMyPhoto()
        startNewRound(firstRound = true)
    }

    /**
     * Hands the turn to the bot once it is its own, after a pause. Without the pause
     * its cards land in the same frame as yours and the table reads as a glitch rather
     * than as an opponent.
     */
    private fun scheduleBot() {
        val difficulty = _state.value.solo ?: return
        val e = engine ?: return
        if (e.phase == Phase.GAME_OVER || e.turn == HOST_SEAT) return
        botJob?.cancel()
        botJob = viewModelScope.launch {
            delay(BOT_THINK_MS)
            val current = engine ?: return@launch
            if (current.phase == Phase.GAME_OVER || current.turn == HOST_SEAT) return@launch
            val seat = current.turn
            val move = Bot.decide(current.viewFor(seat), difficulty, botRng)
            val acted = when (move) {
                is BotMove.Play -> current.playCard(seat, move.cardId, move.color)
                BotMove.Draw -> current.draw(seat)
                BotMove.Pass -> current.pass(seat)
            }
            // A refused move would leave the table frozen on the bot's turn. Drawing is
            // always available to whoever is on turn, so it is the safe way out.
            if (!acted && !current.draw(seat)) return@launch
            broadcast()
        }
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
                mods = emptySet(),
                social = Social(enabled = true),
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
                    avatar = _state.value.profile.avatarColor,
                    frame = _state.value.profile.worn(CosmeticKind.FRAME).id,
                    title = _state.value.profile.worn(CosmeticKind.TITLE).id,
                    nameColor = _state.value.profile.worn(CosmeticKind.NAME).id,
                    level = _state.value.profile.level
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
                val players = msg.players.sortedBy { p -> p.seat }
                it.copy(
                    players = players,
                    look = lookFor(it.profile, players),
                    mods = msg.mods.toSet(),
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

            is NetMsg.Say -> Talk.clean(msg.text)?.let { addChat(msg.seat, it) }

            is NetMsg.Emote -> addEmote(msg.seat, msg.index)

            NetMsg.Bye -> _state.update {
                it.copy(link = LinkStatus.LOST, error = "L'hôte a quitté la partie.")
            }

            // The guest learns outfits from the lobby broadcast the host sends next,
            // so its own copy of this message is nothing to act on.
            is NetMsg.Wearing, is NetMsg.Hello, is NetMsg.Play, NetMsg.Draw, NetMsg.Pass,
            NetMsg.Rematch, NetMsg.Start -> Unit
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
        if (_state.value.solo != null) {
            startNewRound(firstRound = false)
            return
        }
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

    // --------------------------------------------------------------- chat & stickers

    /**
     * Chat is a side channel, not part of the game: it never enters the engine and never
     * rides in a snapshot. The host relays, everybody renders, and nothing is remembered
     * past the room.
     */
    fun sendChat(raw: String) {
        val text = Talk.clean(raw) ?: return
        val seat = _state.value.mySeat
        if (_state.value.isHost) host?.broadcast(NetMsg.Say(seat, text))
        else guest?.send(NetMsg.Say(seat, text))
        addChat(seat, text)
    }

    fun sendSticker(index: Int) {
        if (Talk.sticker(index) == null) return
        val seat = _state.value.mySeat
        if (_state.value.isHost) host?.broadcast(NetMsg.Emote(seat, index))
        else guest?.send(NetMsg.Emote(seat, index))
        addEmote(seat, index)
    }

    fun openChat() = _state.update {
        it.copy(social = it.social.copy(open = true, unread = 0, flash = emptyList()))
    }

    fun closeChat() = _state.update { it.copy(social = it.social.copy(open = false)) }

    /** Relays a message to every guest but the one it came from. */
    private fun relay(msg: NetMsg, except: String) {
        seatOfKey.keys.forEach { key -> if (key != except) host?.send(key, msg) }
    }

    private fun addChat(seat: Seat, text: String) {
        val mine = seat == _state.value.mySeat
        val line = ChatLine(nextSocialId++, seat, nameOfSeat(seat), text, mine)
        _state.update { s ->
            val social = s.social
            s.copy(
                social = social.copy(
                    chat = (social.chat + line).takeLast(CHAT_HISTORY),
                    // Your own line does not need popping at you, and neither does one
                    // that arrives while the chat is already open in front of you.
                    flash = if (mine || social.open) social.flash else social.flash + line,
                    unread = if (mine || social.open) social.unread else social.unread + 1
                )
            )
        }
        if (!mine) viewModelScope.launch {
            delay(FLASH_MS)
            _state.update { s ->
                s.copy(social = s.social.copy(flash = s.social.flash.filterNot { it.id == line.id }))
            }
        }
    }

    private fun addEmote(seat: Seat, index: Int) {
        val sticker = Talk.sticker(index) ?: return
        val emote = Emote(nextSocialId++, seat, sticker)
        _state.update { it.copy(social = it.social.copy(emotes = it.social.emotes + emote)) }
        viewModelScope.launch {
            delay(EMOTE_MS)
            _state.update { s ->
                s.copy(social = s.social.copy(emotes = s.social.emotes.filterNot { it.id == emote.id }))
            }
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
        botJob?.cancel()
        botJob = null
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

    private companion object {
        /** Long enough to read the bot's move as a move, short enough not to drag. */
        const val BOT_THINK_MS = 750L

        /** How long an incoming line stays popped over the hand before it folds away. */
        const val FLASH_MS = 5_000L

        /** A sticker's whole flight, after which it is dropped from the state. */
        const val EMOTE_MS = 2_600L

        /** Deep enough to scroll back through a round, shallow enough to stay cheap. */
        const val CHAT_HISTORY = 60
        const val BOT_AVATAR = 5
        const val BOT_AVATAR_ALT = 2
    }

    private fun post(block: () -> Unit) {
        viewModelScope.launch { withContext(Dispatchers.Main) { block() } }
    }

    override fun onCleared() {
        teardown()
        super.onCleared()
    }
}
