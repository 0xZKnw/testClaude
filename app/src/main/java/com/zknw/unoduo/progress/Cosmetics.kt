package com.zknw.unoduo.progress

/** What a cosmetic dresses up. */
enum class CosmeticKind(val code: String, val label: String, val blurb: String) {
    FRAME("fr", "Cadres", "Le cercle autour de ta photo."),
    BACK("bk", "Dos de carte", "Ce que montrent la pioche et les mains adverses."),
    FELT("ft", "Tapis", "La couleur de la table."),
    TITLE("ti", "Titres", "La ligne sous ton pseudo."),
    NAME("nm", "Pseudo", "La couleur de ton nom."),
    STICKER("st", "Stickers", "Ce que tu peux balancer en partie.")
}

/**
 * How a frame is drawn. Nine primitives are enough for every ring in the catalogue,
 * which is also why they all look like they came from the same set.
 */
enum class FrameStyle { SOLID, DUO, DUAL, DASH, BEADS, NOTCH, GLOW, SHINE, SPIN }

/**
 * What a card back or a table cloth does while you look at it.
 *
 * Deliberately slow and deliberately rare: the cloth sits behind the cards and a back is
 * drawn a dozen times at once, so anything lively here would fight the game rather than
 * decorate it. Movement is also a reward — the plain ones are the early unlocks.
 */
enum class Motion { NONE, SHEEN, PULSE, DRIFT }

/**
 * One unlockable. A single flat record on purpose: the catalogue is a table, it is read
 * far more often than it is edited, and the browser build mirrors it line for line.
 *
 * Colours are packed 0xAARRGGBB longs rather than a UI type, so the whole catalogue
 * stays plain Kotlin and can be tested without an Android device.
 */
data class Cosmetic(
    val id: String,
    val kind: CosmeticKind,
    val name: String,
    /** Level that hands it over. 1 means everybody starts with it. */
    val level: Int,
    val a: Long = 0L,
    val b: Long = 0L,
    val c: Long = 0L,
    val style: FrameStyle = FrameStyle.SOLID,
    /** Backs and cloths only; everything else stands still. */
    val motion: Motion = Motion.NONE,
    /** The emoji, for a sticker. Empty for everything else. */
    val text: String = ""
) {
    val isDefault: Boolean get() = level <= 1

    /**
     * What actually gets drawn under a pseudo. "Aucun" is a real entry in the catalogue
     * rather than a null, so the picker can offer it — but it shows nothing.
     */
    val worn: String get() = if (kind == CosmeticKind.TITLE && isDefault) "" else name
}

/**
 * Everything that can be unlocked, and what hands it over.
 *
 * The rule the tests hold us to: every level from 2 to [Levels.MAX] gives at least one
 * thing. A level that hands over nothing is a level that feels broken, whatever the bar
 * says.
 */
object Cosmetics {

    // ------------------------------------------------------------------- cadres

    val frames: List<Cosmetic> = listOf(
        frame("encre", "Encre", 1, FrameStyle.SOLID, 0xFF3B475D),
        frame("braise", "Braise", 3, FrameStyle.SOLID, 0xFFF23B2E),
        frame("menthe", "Menthe", 6, FrameStyle.SOLID, 0xFF41C258),
        frame("azur", "Azur", 9, FrameStyle.SOLID, 0xFF2E9CF2),
        frame("safran", "Safran", 12, FrameStyle.SOLID, 0xFFFFC21A),
        frame("amethyste", "Améthyste", 15, FrameStyle.SOLID, 0xFF7A5CF0),
        frame("couchant", "Couchant", 18, FrameStyle.DUO, 0xFFFF8A1E, 0xFFF23B2E),
        frame("lagon", "Lagon", 21, FrameStyle.DUO, 0xFF19B79B, 0xFF2E9CF2),
        frame("pointilles", "Pointillés", 24, FrameStyle.DASH, 0xFFF3F6FB),
        frame("barbapapa", "Barbe à papa", 27, FrameStyle.DUO, 0xFFF25DA8, 0xFF7A5CF0),
        frame("perles", "Perles", 30, FrameStyle.BEADS, 0xFF2EF2C4),
        frame("cuivre", "Cuivre", 33, FrameStyle.DUAL, 0xFFC87137, 0xFFF0A862),
        frame("argent", "Argent", 36, FrameStyle.SHINE, 0xFFA8B4C6, 0xFFFDFBF4),
        frame("feuillage", "Feuillage", 39, FrameStyle.DUO, 0xFF41C258, 0xFF19B79B),
        frame("orage", "Orage", 42, FrameStyle.NOTCH, 0xFF2E9CF2, 0xFF3B475D),
        frame("lave", "Lave", 45, FrameStyle.GLOW, 0xFFFF5A1E),
        frame("givre", "Givre", 48, FrameStyle.GLOW, 0xFF9FE8FF),
        frame("or", "Or", 51, FrameStyle.SHINE, 0xFFFFC531, 0xFFFFF3C4),
        frame("prisme", "Prisme", 55, FrameStyle.SPIN, 0xFFF23B2E, 0xFFFFC21A, 0xFF2E9CF2),
        frame("bitume", "Bitume", 58, FrameStyle.NOTCH, 0xFF9DAABF, 0xFF4A5568),
        frame("sangdencre", "Sang d'encre", 62, FrameStyle.DUAL, 0xFFC01C12, 0xFFF23B2E),
        frame("aurore", "Aurore", 66, FrameStyle.SPIN, 0xFF2EF2C4, 0xFF7A5CF0, 0xFF2E9CF2),
        frame("rubis", "Rubis", 70, FrameStyle.GLOW, 0xFFFF2D55),
        frame("emeraude", "Émeraude", 74, FrameStyle.GLOW, 0xFF2EE06A),
        frame("saphir", "Saphir", 78, FrameStyle.GLOW, 0xFF3D7DFF),
        frame("onyx", "Onyx", 82, FrameStyle.DUAL, 0xFF2B3242, 0xFF6B7688),
        frame("platine", "Platine", 86, FrameStyle.SHINE, 0xFFD8E0EC, 0xFFFDFBF4),
        frame("cendre", "Cendre ardente", 90, FrameStyle.SPIN, 0xFFFF8A1E, 0xFFC01C12, 0xFF2B1A12),
        frame("couronne", "Couronne", 95, FrameStyle.DUAL, 0xFFFFC531, 0xFFFDFBF4),
        frame("centieme", "Centième", 100, FrameStyle.SPIN, 0xFFFFC531, 0xFFF23B2E, 0xFF2E9CF2)
    )

    // ------------------------------------------------------------ dos de carte

    val backs: List<Cosmetic> = listOf(
        back("classique", "Classique", 1, 0xFF2B3242, 0xFF161A24, 0xFFF23B2E),
        back("brique", "Brique", 4, 0xFF5A1F1A, 0xFF2A0E0B, 0xFFFF8A1E),
        back("foret", "Forêt", 10, 0xFF1E4030, 0xFF0C1D16, 0xFF41C258),
        back("ocean", "Océan", 16, 0xFF16344F, 0xFF081826, 0xFF2E9CF2),
        back("dore", "Doré", 22, 0xFF4A3A12, 0xFF221A07, 0xFFFFC531),
        back("violine", "Violine", 28, 0xFF382357, 0xFF190F28, 0xFF7A5CF0),
        back("reglisse", "Réglisse", 34, 0xFF1A1A1E, 0xFF07070A, 0xFFF3F6FB),
        back("sable", "Sable", 40, 0xFF5C4B2E, 0xFF2A2113, 0xFFFFC21A),
        back("menthe", "Menthe glaciale", 46, 0xFF17423C, 0xFF091E1B, 0xFF2EF2C4, Motion.SHEEN),
        back("cerise", "Cerise noire", 52, 0xFF3E0E1E, 0xFF1B040C, 0xFFF25DA8, Motion.PULSE),
        back("cuivre", "Cuivre chaud", 57, 0xFF52341A, 0xFF24160A, 0xFFC87137, Motion.SHEEN),
        back("nuit", "Bleu de nuit", 63, 0xFF17203D, 0xFF070B1A, 0xFF6E8CFF, Motion.DRIFT),
        back("poudre", "Rose poudré", 68, 0xFF54293D, 0xFF25101B, 0xFFF25DA8, Motion.PULSE),
        back("vertdegris", "Vert-de-gris", 73, 0xFF2A423B, 0xFF121D1A, 0xFF19B79B, Motion.DRIFT),
        back("pourpre", "Pourpre royal", 80, 0xFF421338, 0xFF1D0718, 0xFFFFC531, Motion.SHEEN),
        back("orblanc", "Or blanc", 85, 0xFF3B3F49, 0xFF171A20, 0xFFD8E0EC, Motion.SHEEN),
        back("retro", "Néon rétro", 92, 0xFF201242, 0xFF0B0620, 0xFF2EF2C4, Motion.DRIFT),
        back("centfaces", "Cent faces", 98, 0xFF4A3A12, 0xFF14161D, 0xFFFFC531, Motion.SHEEN)
    )

    // -------------------------------------------------------------------- tapis

    val felts: List<Cosmetic> = listOf(
        felt("nuit", "Table de nuit", 1, 0xFF2A3444, 0xFF171D27, 0xFF080A10),
        felt("feutre", "Feutre vert", 5, 0xFF27503A, 0xFF14301F, 0xFF06130C),
        felt("bordeaux", "Bordeaux", 11, 0xFF54202A, 0xFF2E1017, 0xFF120508),
        felt("encre", "Encre bleue", 17, 0xFF22375C, 0xFF111D35, 0xFF050A14),
        felt("cendre", "Cendre", 23, 0xFF3D4148, 0xFF212429, 0xFF0B0C0E),
        felt("prune", "Prune", 29, 0xFF3E2B57, 0xFF221733, 0xFF0C0714),
        felt("profonde", "Forêt profonde", 35, 0xFF1E4231, 0xFF0F2519, 0xFF040D08),
        felt("sable", "Sable chaud", 41, 0xFF54452C, 0xFF2E2617, 0xFF120E07),
        felt("cuivre", "Cuivre", 47, 0xFF5A3A22, 0xFF301D11, 0xFF130A05),
        felt("abysse", "Abysse", 54, 0xFF16323A, 0xFF0A1B21, 0xFF02080B, Motion.DRIFT),
        felt("braise", "Braise", 60, 0xFF5E2E18, 0xFF33170B, 0xFF140803, Motion.PULSE),
        felt("jade", "Jade", 67, 0xFF1D4A45, 0xFF0F2926, 0xFF040F0E, Motion.SHEEN),
        felt("nebuleuse", "Nébuleuse", 75, 0xFF3A2660, 0xFF1C1236, 0xFF070414, Motion.DRIFT),
        felt("crepuscule", "Crépuscule", 84, 0xFF5A3352, 0xFF2E1A2C, 0xFF120810, Motion.PULSE),
        felt("obsidienne", "Obsidienne", 93, 0xFF262A33, 0xFF12141A, 0xFF030406, Motion.SHEEN),
        felt("cercle", "Cercle des cent", 99, 0xFF4C3E18, 0xFF231C0B, 0xFF0A0803, Motion.DRIFT)
    )

    // ------------------------------------------------------------------- titres

    val titles: List<Cosmetic> = listOf(
        // Wearing none is a choice, so it is an entry rather than a special case.
        title("aucun", "Aucun", 1),
        title("chair", "Chair à pioche", 2),
        title("douze", "Toujours 12 cartes", 7),
        title("piochetout", "Pioche-tout", 13),
        title("distributeur", "Distributeur de +2", 19),
        title("empileur", "Empileur compulsif", 25),
        title("toxique", "Ami toxique", 31),
        title("mainlegere", "Main légère", 37),
        title("compteur", "Compte les cartes (mal)", 43),
        title("sanspitie", "Sans pitié", 49),
        title("briscard", "Vieux briscard", 53),
        title("chasseur", "Chasseur de +4", 56),
        title("briseur", "Briseur d'amitiés", 59),
        title("stratege", "Stratège du dimanche", 61),
        title("passecasse", "Ça passe ou ça casse", 64),
        title("espionchef", "Espion en chef", 69),
        title("coeurdepierre", "Cœur de pierre", 71),
        title("tempete", "Tempête de +4", 76),
        title("intouchable", "Intouchable", 79),
        title("requin", "Requin de table", 81),
        title("maitrecumul", "Maître du cumul", 83),
        title("legende", "Légende du salon", 87),
        title("javaisunquatre", "J'avais un +4", 89),
        title("mangeur", "Mangeur de pioche", 91),
        title("increvable", "Increvable", 94),
        title("cauchemar", "Cauchemar récurrent", 96),
        title("centurion", "Centurion", 100)
    )

    // ------------------------------------------------------------------- pseudo

    val names: List<Cosmetic> = listOf(
        name("blanc", "Blanc", 1, 0xFFF3F6FB),
        name("rouge", "Rouge", 8, 0xFFFF4A3D),
        name("vert", "Vert", 14, 0xFF4EDE6A),
        name("bleu", "Bleu", 20, 0xFF3FB0FF),
        name("jaune", "Jaune", 26, 0xFFFFD23F),
        name("rose", "Rose", 32, 0xFFFF6FB8),
        name("turquoise", "Turquoise", 38, 0xFF2FD9BD),
        name("violet", "Violet", 44, 0xFF9B7BFF),
        name("or", "Or", 50, 0xFFFFE27A, 0xFFFFB200),
        name("braise", "Braise", 65, 0xFFFFD23F, 0xFFFF8A1E, 0xFFF23B2E),
        name("glacier", "Glacier", 72, 0xFFD8F6FF, 0xFF6FD4FF, 0xFF2E6CF2),
        name("neon", "Néon", 77, 0xFFB6FF3F, 0xFF2EF2C4),
        name("prisme", "Prisme", 88, 0xFFFF5DA8, 0xFF9B7BFF, 0xFF3FB0FF),
        name("centieme", "Centième", 97, 0xFFFFF3C4, 0xFFFFC531, 0xFFFF8A1E)
    )

    // ---------------------------------------------------------------- stickers

    /**
     * The six everybody starts with are the ones the rail has always had; the rest are
     * earned. Order matters: it is the order they appear in the rail.
     */
    val stickers: List<Cosmetic> = listOf(
        sticker("chat", "Chat charmé", 1, "😻"),
        sticker("rire", "Fou rire", 1, "😹"),
        sticker("caca", "Bouse", 1, "💩"),
        sticker("pleure", "Chat triste", 1, "😿"),
        sticker("peur", "Chat terrifié", 1, "🙀"),
        sticker("doigt", "Doigt d'honneur", 1, "🖕"),
        sticker("feu", "En feu", 10, "🔥"),
        sticker("sanglot", "Sanglot", 20, "😭"),
        sticker("clown", "Clown", 30, "🤡"),
        sticker("couronne", "Couronne", 40, "👑"),
        sticker("trefle", "Trèfle", 50, "🍀"),
        sticker("glacon", "Glaçon", 60, "🥶"),
        sticker("salut", "Salut militaire", 70, "🫡"),
        sticker("crane", "Crâne", 80, "💀"),
        sticker("poignee", "Poignée de main", 90, "🤝")
    )

    /** Every cosmetic there is, in catalogue order. */
    val all: List<Cosmetic> = frames + backs + felts + titles + names + stickers

    private val byId: Map<String, Cosmetic> = all.associateBy { it.id }

    fun of(kind: CosmeticKind): List<Cosmetic> = all.filter { it.kind == kind }

    fun find(id: String): Cosmetic? = byId[id]

    /** The first thing of its kind, worn by anybody who has not chosen otherwise. */
    fun defaultOf(kind: CosmeticKind): Cosmetic = of(kind).first { it.isDefault }

    /**
     * Resolves a stored choice. An unknown id — a save from a newer build, or one that
     * was renamed — falls back rather than leaving a hole in the screen.
     */
    fun resolve(id: String, kind: CosmeticKind, level: Int): Cosmetic {
        val wanted = byId[id]
        return if (wanted != null && wanted.kind == kind && wanted.level <= level) wanted
        else defaultOf(kind)
    }

    fun unlocked(kind: CosmeticKind, level: Int): List<Cosmetic> =
        of(kind).filter { it.level <= level }

    /** What reaching [level] hands over, in catalogue order. */
    fun rewardsAt(level: Int): List<Cosmetic> = all.filter { it.level == level }

    /** Everything owned at [level], all kinds together. */
    fun ownedAt(level: Int): List<Cosmetic> = all.filter { it.level <= level }

    /** The stickers the rail should show at [level]. */
    fun stickersAt(level: Int): List<Cosmetic> = stickers.filter { it.level <= level }

    private fun frame(
        id: String,
        name: String,
        level: Int,
        style: FrameStyle,
        a: Long,
        b: Long = 0L,
        c: Long = 0L
    ) = Cosmetic("fr.$id", CosmeticKind.FRAME, name, level, a, b, c, style)

    private fun back(
        id: String,
        name: String,
        level: Int,
        top: Long,
        bottom: Long,
        oval: Long,
        motion: Motion = Motion.NONE
    ) = Cosmetic("bk.$id", CosmeticKind.BACK, name, level, top, bottom, oval, motion = motion)

    private fun felt(
        id: String,
        name: String,
        level: Int,
        light: Long,
        mid: Long,
        dark: Long,
        motion: Motion = Motion.NONE
    ) = Cosmetic("ft.$id", CosmeticKind.FELT, name, level, light, mid, dark, motion = motion)

    private fun title(id: String, name: String, level: Int) =
        Cosmetic("ti.$id", CosmeticKind.TITLE, name, level)

    private fun name(id: String, name: String, level: Int, a: Long, b: Long = 0L, c: Long = 0L) =
        Cosmetic("nm.$id", CosmeticKind.NAME, name, level, a, b, c)

    private fun sticker(id: String, name: String, level: Int, emoji: String) =
        Cosmetic("st.$id", CosmeticKind.STICKER, name, level, text = emoji)
}
