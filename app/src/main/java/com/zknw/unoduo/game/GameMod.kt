package com.zknw.unoduo.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An optional rule the host can switch on when creating a room. Any number of them can
 * be on at once — each one only ever *adds* cards to the deck, so they never contradict
 * each other and the base game underneath is untouched.
 *
 * The order of the entries is the order the extra cards are added to the deck, and
 * therefore the order their ids are handed out. It must not change: it is what keeps
 * the Android and web engines dealing the very same game from the same seed.
 */
@Serializable
enum class GameMod(val label: String, val blurb: String) {

    @SerialName("d8")
    DRAW_EIGHT(
        "Les +8",
        "Deux +8 rejoignent le paquet. Ils fonctionnent exactement comme des +4, en " +
            "plus lourd : ils se cumulent avec les +4 et les +8, et un +2 de la couleur " +
            "annoncée les contre."
    ),

    @SerialName("x2")
    DOUBLE_PLAY(
        "Coup double",
        "Trois cartes en plus. Tu annonces une couleur, puis tu poses deux cartes de " +
            "suite. Une carte d'attaque met fin au coup double : la pile part chez le " +
            "voisin."
    ),

    @SerialName("sp")
    SPY(
        "Espion",
        "Trois cartes en plus. Un Joker ordinaire — tu annonces une couleur — sauf " +
            "qu'il te montre une carte au hasard du joueur suivant. Toi seul la vois, " +
            "et tu la vois jusqu'à ce qu'il la pose."
    );

    companion object {
        /** Parses the wire codes back, ignoring anything an older build does not know. */
        fun of(codes: Collection<String>): Set<GameMod> =
            entries.filter { it.code in codes }.toSet()
    }

    /** The short code used on the wire and in the QR link. */
    val code: String
        get() = when (this) {
            DRAW_EIGHT -> "d8"
            DOUBLE_PLAY -> "x2"
            SPY -> "sp"
        }
}

/** The mods in declaration order, which is the order the deck and the UI both use. */
fun Set<GameMod>.ordered(): List<GameMod> = GameMod.entries.filter { it in this }
