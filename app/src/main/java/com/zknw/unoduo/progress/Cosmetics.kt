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
 * How a frame is drawn.
 *
 * The first nine are geometry — a ring filled, split, dashed or swept. The last six are
 * things: fire, lightning, water, a laurel, a ring of stars, a crown. Those are the ones
 * worth climbing for, so they sit high in the catalogue and every one of them moves.
 */
enum class FrameStyle {
    SOLID, DUO, DUAL, DASH, BEADS, NOTCH, GLOW, SHINE, SPIN,
    FLAME, BOLT, WAVE, ORBIT, STARS, CROWN
}

/**
 * The motif printed on a card back or woven into a cloth. Drawn faintly and repeated —
 * a back is seen a dozen times at once and a cloth sits under the whole game, so a motif
 * that shouted would be unbearable within one round.
 */
enum class Pattern { PLAIN, RAYS, STRIPES, DOTS, WAVES, BOLTS, FLAMES, HEX, CONFETTI, CROWNS }

/**
 * What a cloth, a back, a pseudo, a title or a sticker does while you look at it.
 *
 * The first three are slow on purpose: they belong to the middle of the catalogue, the
 * cloth sits behind the cards, and a back is drawn a dozen times at once, so anything
 * lively there would fight the game rather than decorate it.
 *
 * The last two are the opposite, and they are the point of climbing: [BLAZE] burns and
 * [STORM] throws lightning. They are handed out at the very top only — a table that
 * flashed from level 30 would be a table nobody could stand by level 60.
 */
enum class Motion { NONE, SHEEN, PULSE, DRIFT, BLAZE, STORM }

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
    /** Backs and cloths only. */
    val pattern: Pattern = Pattern.PLAIN,
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
        frame("braise", "Braise", 2, FrameStyle.SOLID, 0xFFF23B2E),
        frame("menthe", "Menthe", 4, FrameStyle.SOLID, 0xFF41C258),
        frame("azur", "Azur", 6, FrameStyle.SOLID, 0xFF2E9CF2),
        frame("safran", "Safran", 8, FrameStyle.SOLID, 0xFFFFC21A),
        frame("amethyste", "Améthyste", 10, FrameStyle.SOLID, 0xFF7A5CF0),
        frame("couchant", "Couchant", 12, FrameStyle.DUO, 0xFFFF8A1E, 0xFFF23B2E),
        frame("lagon", "Lagon", 14, FrameStyle.DUO, 0xFF19B79B, 0xFF2E9CF2),
        frame("pointilles", "Pointillés", 16, FrameStyle.DASH, 0xFFF3F6FB),
        frame("barbapapa", "Barbe à papa", 18, FrameStyle.DUO, 0xFFF25DA8, 0xFF7A5CF0),
        frame("perles", "Perles", 20, FrameStyle.BEADS, 0xFF2EF2C4),
        frame("cuivre", "Cuivre", 22, FrameStyle.DUAL, 0xFFC87137, 0xFFF0A862),
        frame("argent", "Argent", 24, FrameStyle.SHINE, 0xFFA8B4C6, 0xFFFDFBF4),
        frame("feuillage", "Feuillage", 26, FrameStyle.DUO, 0xFF41C258, 0xFF19B79B),
        frame("orage", "Orage", 28, FrameStyle.NOTCH, 0xFF2E9CF2, 0xFF3B475D),
        // Fire, from here on the frames are things rather than shapes.
        frame("flammeches", "Flammèches", 30, FrameStyle.FLAME, 0xFFFF8A1E, 0xFFFFD23F),
        frame("lave", "Lave", 33, FrameStyle.GLOW, 0xFFFF5A1E),
        frame("etincelle", "Étincelle", 36, FrameStyle.BOLT, 0xFFFFE27A, 0xFFFDFBF4),
        frame("givre", "Givre", 39, FrameStyle.GLOW, 0xFF9FE8FF),
        frame("ressac", "Ressac", 42, FrameStyle.WAVE, 0xFF2E9CF2, 0xFF9FE8FF),
        frame("bitume", "Bitume", 45, FrameStyle.NOTCH, 0xFF9DAABF, 0xFF4A5568),
        frame("satellite", "Satellite", 48, FrameStyle.ORBIT, 0xFF2EF2C4, 0xFF1D3A4A),
        frame("or", "Or", 51, FrameStyle.SHINE, 0xFFFFC531, 0xFFFFF3C4),
        frame("sangdencre", "Sang d'encre", 54, FrameStyle.DUAL, 0xFFC01C12, 0xFFF23B2E),
        frame("constellation", "Constellation", 57, FrameStyle.STARS, 0xFFFDFBF4, 0xFF2E4B8C),
        frame("prisme", "Prisme", 60, FrameStyle.SPIN, 0xFFF23B2E, 0xFFFFC21A, 0xFF2E9CF2),
        frame("brasier", "Brasier", 63, FrameStyle.FLAME, 0xFFF23B2E, 0xFFFFC531),
        frame("aurore", "Aurore", 66, FrameStyle.SPIN, 0xFF2EF2C4, 0xFF7A5CF0, 0xFF2E9CF2),
        frame("rubis", "Rubis", 69, FrameStyle.GLOW, 0xFFFF2D55),
        frame("foudre", "Foudre", 72, FrameStyle.BOLT, 0xFFB98BFF, 0xFFFDFBF4),
        frame("emeraude", "Émeraude", 75, FrameStyle.GLOW, 0xFF2EE06A),
        frame("maree", "Marée", 78, FrameStyle.WAVE, 0xFF19B79B, 0xFFD8F6FF),
        frame("saphir", "Saphir", 81, FrameStyle.GLOW, 0xFF3D7DFF),
        frame("onyx", "Onyx", 84, FrameStyle.DUAL, 0xFF2B3242, 0xFF6B7688),
        frame("anneau", "Anneau d'or", 87, FrameStyle.ORBIT, 0xFFFFC531, 0xFF4A3A12),
        frame("cendre", "Cendre ardente", 90, FrameStyle.SPIN, 0xFFFF8A1E, 0xFFC01C12, 0xFF2B1A12),
        frame("lactee", "Voie lactée", 93, FrameStyle.STARS, 0xFFFDFBF4, 0xFF4A2E8C),
        frame("platine", "Platine", 96, FrameStyle.SHINE, 0xFFD8E0EC, 0xFFFDFBF4),
        frame("enfer", "Enfer", 98, FrameStyle.FLAME, 0xFFFFF3C4, 0xFFFF5A1E),
        // The last one in the game, and the only crown: gold points, and a shine that
        // sweeps round them.
        frame("couronne", "Couronne", 100, FrameStyle.CROWN, 0xFFFFC531, 0xFFFFF3C4, 0xFFDC9200)
    )

    // ------------------------------------------------------------ dos de carte

    val backs: List<Cosmetic> = listOf(
        back("classique", "Classique", 1, 0xFF2B3242, 0xFF161A24, 0xFFF23B2E),
        back("brique", "Brique", 3, 0xFF5A1F1A, 0xFF2A0E0B, 0xFFFF8A1E),
        back("foret", "Forêt", 5, 0xFF1E4030, 0xFF0C1D16, 0xFF41C258, Pattern.STRIPES),
        back("ocean", "Océan", 7, 0xFF16344F, 0xFF081826, 0xFF2E9CF2, Pattern.WAVES),
        back("dore", "Doré", 9, 0xFF4A3A12, 0xFF221A07, 0xFFFFC531, Pattern.RAYS),
        back("violine", "Violine", 11, 0xFF382357, 0xFF190F28, 0xFF7A5CF0, Pattern.DOTS),
        back("reglisse", "Réglisse", 13, 0xFF1A1A1E, 0xFF07070A, 0xFFF3F6FB, Pattern.STRIPES),
        back("sable", "Sable", 15, 0xFF5C4B2E, 0xFF2A2113, 0xFFFFC21A, Pattern.HEX),
        back("confettis", "Confettis", 17, 0xFF2A2440, 0xFF14101F, 0xFFF25DA8, Pattern.CONFETTI),
        back("menthe", "Menthe glaciale", 19, 0xFF17423C, 0xFF091E1B, 0xFF2EF2C4, Pattern.WAVES, Motion.SHEEN),
        back("cerise", "Cerise noire", 21, 0xFF3E0E1E, 0xFF1B040C, 0xFFF25DA8, Pattern.DOTS, Motion.PULSE),
        back("cuivre", "Cuivre chaud", 23, 0xFF52341A, 0xFF24160A, 0xFFC87137, Pattern.RAYS, Motion.SHEEN),
        back("nuit", "Bleu de nuit", 25, 0xFF17203D, 0xFF070B1A, 0xFF6E8CFF, Pattern.DOTS, Motion.DRIFT),
        back("brasier", "Brasier", 29, 0xFF4A1A0C, 0xFF200806, 0xFFFF8A1E, Pattern.FLAMES, Motion.PULSE),
        back("poudre", "Rose poudré", 32, 0xFF54293D, 0xFF25101B, 0xFFF25DA8, Pattern.HEX, Motion.PULSE),
        back("tonnerre", "Tonnerre", 35, 0xFF1E2136, 0xFF0A0C18, 0xFFFFE27A, Pattern.BOLTS, Motion.SHEEN),
        back("vertdegris", "Vert-de-gris", 38, 0xFF2A423B, 0xFF121D1A, 0xFF19B79B, Pattern.STRIPES, Motion.DRIFT),
        back("abysses", "Abysses", 41, 0xFF0E2A3A, 0xFF04121C, 0xFF3D7DFF, Pattern.WAVES, Motion.DRIFT),
        back("pourpre", "Pourpre royal", 47, 0xFF421338, 0xFF1D0718, 0xFFFFC531, Pattern.CROWNS, Motion.SHEEN),
        back("orblanc", "Or blanc", 53, 0xFF3B3F49, 0xFF171A20, 0xFFD8E0EC, Pattern.RAYS, Motion.SHEEN),
        back("retro", "Néon rétro", 59, 0xFF201242, 0xFF0B0620, 0xFF2EF2C4, Pattern.BOLTS, Motion.STORM),
        back("fournaise", "Fournaise", 65, 0xFF3A0B06, 0xFF190403, 0xFFFF5A1E, Pattern.FLAMES, Motion.BLAZE),
        back("carnaval", "Carnaval", 71, 0xFF2E1240, 0xFF13061C, 0xFFFFC531, Pattern.CONFETTI, Motion.PULSE),
        back("centfaces", "Cent faces", 83, 0xFF4A3A12, 0xFF14161D, 0xFFFFC531, Pattern.CROWNS, Motion.BLAZE),
        // The deck of the last level. Nobody who has one is hiding it.
        back("centurion", "Dos du centurion", 100, 0xFF5C4708, 0xFF1A1204, 0xFFFFD75A, Pattern.CROWNS, Motion.BLAZE)
    )

    // -------------------------------------------------------------------- tapis

    val felts: List<Cosmetic> = listOf(
        felt("nuit", "Table de nuit", 1, 0xFF2A3444, 0xFF171D27, 0xFF080A10),
        felt("feutre", "Feutre vert", 27, 0xFF27503A, 0xFF14301F, 0xFF06130C, Pattern.STRIPES),
        felt("bordeaux", "Bordeaux", 31, 0xFF54202A, 0xFF2E1017, 0xFF120508, Pattern.RAYS),
        felt("encre", "Encre bleue", 34, 0xFF22375C, 0xFF111D35, 0xFF050A14, Pattern.HEX),
        felt("cendre", "Cendre", 37, 0xFF3D4148, 0xFF212429, 0xFF0B0C0E, Pattern.DOTS),
        felt("prune", "Prune", 40, 0xFF3E2B57, 0xFF221733, 0xFF0C0714, Pattern.CONFETTI),
        felt("profonde", "Forêt profonde", 43, 0xFF1E4231, 0xFF0F2519, 0xFF040D08, Pattern.STRIPES),
        felt("sable", "Sable chaud", 46, 0xFF54452C, 0xFF2E2617, 0xFF120E07, Pattern.RAYS),
        felt("cuivre", "Cuivre", 49, 0xFF5A3A22, 0xFF301D11, 0xFF130A05, Pattern.HEX, Motion.SHEEN),
        felt("abysse", "Abysse", 52, 0xFF16323A, 0xFF0A1B21, 0xFF02080B, Pattern.WAVES, Motion.DRIFT),
        felt("braise", "Braise", 55, 0xFF5E2E18, 0xFF33170B, 0xFF140803, Pattern.FLAMES, Motion.PULSE),
        felt("orageuse", "Table orageuse", 58, 0xFF243050, 0xFF121A2E, 0xFF05080F, Pattern.BOLTS, Motion.STORM),
        felt("jade", "Jade", 61, 0xFF1D4A45, 0xFF0F2926, 0xFF040F0E, Pattern.WAVES, Motion.SHEEN),
        felt("volcan", "Volcan", 64, 0xFF5A1F10, 0xFF2C0D07, 0xFF100301, Pattern.FLAMES, Motion.BLAZE),
        felt("nebuleuse", "Nébuleuse", 67, 0xFF3A2660, 0xFF1C1236, 0xFF070414, Pattern.DOTS, Motion.DRIFT),
        felt("recif", "Récif", 70, 0xFF13424A, 0xFF092329, 0xFF030D10, Pattern.WAVES, Motion.PULSE),
        felt("crepuscule", "Crépuscule", 73, 0xFF5A3352, 0xFF2E1A2C, 0xFF120810, Pattern.RAYS, Motion.PULSE),
        felt("foudroyee", "Table foudroyée", 76, 0xFF2A2440, 0xFF141020, 0xFF06040C, Pattern.BOLTS, Motion.STORM),
        felt("obsidienne", "Obsidienne", 79, 0xFF262A33, 0xFF12141A, 0xFF030406, Pattern.HEX, Motion.SHEEN),
        felt("fete", "Table de fête", 82, 0xFF3A2050, 0xFF1C0F28, 0xFF08040E, Pattern.CONFETTI, Motion.PULSE),
        felt("couronnee", "Table couronnée", 85, 0xFF4A3A12, 0xFF241C09, 0xFF0A0803, Pattern.CROWNS, Motion.BLAZE),
        felt("cercle", "Cercle des cent", 99, 0xFF5A4614, 0xFF2A2109, 0xFF0C0902, Pattern.CROWNS, Motion.STORM),
        // The last cloth in the game: gold, crowned, and on fire.
        felt("trone", "Trône", 100, 0xFF6B5310, 0xFF2E2408, 0xFF0D0A02, Pattern.CROWNS, Motion.BLAZE)
    )

    // ------------------------------------------------------------------- titres

    /**
     * The line under a pseudo.
     *
     * The early ones are jokes and are printed plain — that is the joke. Colour starts at
     * 50, the gradient sweeps from 62, and the last handful burn or flash. Keeping the
     * bottom half deliberately drab is what makes the top half mean anything: a title
     * that moves says its owner has been at this a long time.
     */
    val titles: List<Cosmetic> = listOf(
        // Wearing none is a choice, so it is an entry rather than a special case.
        title("aucun", "Aucun", 1),
        title("chair", "Chair à pioche", 2),
        title("douze", "Toujours 12 cartes", 5),
        title("piochetout", "Pioche-tout", 9),
        title("distributeur", "Distributeur de +2", 13),
        title("empileur", "Empileur compulsif", 17),
        title("toxique", "Ami toxique", 21),
        title("mainlegere", "Main légère", 25),
        title("compteur", "Compte les cartes (mal)", 29),
        title("balance", "Balance ton +4", 33),
        title("sanspitie", "Sans pitié", 37),
        title("briscard", "Vieux briscard", 41),
        title("chasseur", "Chasseur de +4", 44),
        // Colour from here: the line stops being a caption and starts being a rank.
        title("briseur", "Briseur d'amitiés", 50, 0xFFFF6FB8),
        title("stratege", "Stratège du dimanche", 56, 0xFF3FB0FF),
        // And from here it moves.
        title("passecasse", "Ça passe ou ça casse", 62, 0xFFFFD23F, 0xFFFF5A1E, motion = Motion.SHEEN),
        title("espionchef", "Espion en chef", 68, 0xFFD8F6FF, 0xFF2FD9BD, motion = Motion.SHEEN),
        title("coeurdepierre", "Cœur de pierre", 74, 0xFFD8E0EC, 0xFF8A94A6, motion = Motion.SHEEN),
        title("tempete", "Tempête de +4", 77, 0xFFFDFBF4, 0xFFB98BFF, 0xFF6E8CFF, Motion.STORM),
        title("intouchable", "Intouchable", 80, 0xFF9FE8FF, 0xFFFDFBF4, motion = Motion.SHEEN),
        title("requin", "Requin de table", 86, 0xFFB8C6DA, 0xFF3D7DFF, motion = Motion.SHEEN),
        title("maitrecumul", "Maître du cumul", 88, 0xFFFFD23F, 0xFFFF8A1E, 0xFFC01C12, Motion.BLAZE),
        title("javaisunquatre", "J'avais un +4", 89, 0xFFFFE27A, 0xFFFFB200, motion = Motion.SHEEN),
        title("mangeur", "Mangeur de pioche", 91, 0xFFB6FF3F, 0xFF2EE06A, motion = Motion.SHEEN),
        title("karma", "Karma en attente", 92, 0xFFFF6FB8, 0xFF9B7BFF, 0xFF3D7DFF, Motion.STORM),
        title("increvable", "Increvable", 94, 0xFFFDFBF4, 0xFF2EE06A, 0xFF19B79B, Motion.BLAZE),
        title("legende", "Légende du salon", 95, 0xFFFFF3C4, 0xFFFFC531, 0xFFDC9200, Motion.BLAZE),
        title("cauchemar", "Cauchemar récurrent", 97, 0xFFB98BFF, 0xFF6B2ACF, 0xFFFF2D55, Motion.STORM),
        title("dieudutapis", "Dieu du tapis", 99, 0xFFFFF3C4, 0xFFFF8A1E, 0xFFC01C12, Motion.BLAZE),
        // The last line in the game, in the same gold as the crown and the throne.
        title("centurion", "Centurion", 100, 0xFFFFF3C4, 0xFFFFC531, 0xFFDC9200, Motion.BLAZE)
    )

    // ------------------------------------------------------------------- pseudo

    val names: List<Cosmetic> = listOf(
        name("blanc", "Blanc", 1, 0xFFF3F6FB),
        name("rouge", "Rouge", 6, 0xFFFF4A3D),
        name("vert", "Vert", 11, 0xFF4EDE6A),
        name("bleu", "Bleu", 16, 0xFF3FB0FF),
        name("jaune", "Jaune", 26, 0xFFFFD23F),
        name("rose", "Rose", 31, 0xFFFF6FB8),
        name("turquoise", "Turquoise", 43, 0xFF2FD9BD),
        name("violet", "Violet", 50, 0xFF9B7BFF),
        // From here the letters catch the light as it passes.
        name("or", "Or", 56, 0xFFFFE27A, 0xFFFFB200, motion = Motion.SHEEN),
        name("braise", "Braise", 62, 0xFFFFD23F, 0xFFFF8A1E, 0xFFF23B2E, Motion.SHEEN),
        name("glacier", "Glacier", 68, 0xFFD8F6FF, 0xFF6FD4FF, 0xFF2E6CF2, Motion.SHEEN),
        name("neon", "Néon", 74, 0xFFB6FF3F, 0xFF2EF2C4, motion = Motion.SHEEN),
        // And from here they stop merely catching the light.
        name("foudre", "Foudre", 80, 0xFFFDFBF4, 0xFFB98BFF, 0xFF6E8CFF, Motion.STORM),
        name("prisme", "Prisme", 86, 0xFFFF5DA8, 0xFF9B7BFF, 0xFF3FB0FF, Motion.SHEEN),
        name("magma", "Magma", 92, 0xFFFFF3C4, 0xFFFF5A1E, 0xFFC01C12, Motion.BLAZE),
        name("abysse", "Abysse", 95, 0xFF2FD9BD, 0xFF2E6CF2, 0xFF1B1046, Motion.STORM),
        name("centieme", "Centième", 97, 0xFFFFF3C4, 0xFFFFC531, 0xFFFF8A1E, Motion.BLAZE),
        name("couronne", "Couronné", 100, 0xFFFDFBF4, 0xFFFFC531, 0xFFDC9200, Motion.BLAZE)
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
        sticker("sanglot", "Sanglot", 15, "😭"),
        sticker("clown", "Clown", 20, "🤡"),
        // A thrown sticker crosses the table on its own; from here it does not do it
        // quietly. The motion is what it drags along behind it.
        sticker("eclair", "Éclair", 28, "⚡", Motion.STORM),
        sticker("couronne", "Couronne", 34, "👑", Motion.SHEEN),
        sticker("trefle", "Trèfle", 40, "🍀"),
        sticker("glacon", "Glaçon", 46, "🥶"),
        sticker("vague", "Vague", 52, "🌊", Motion.DRIFT),
        sticker("salut", "Salut militaire", 58, "🫡"),
        sticker("crane", "Crâne", 64, "💀", Motion.PULSE),
        sticker("bombe", "Bombe", 70, "💣", Motion.BLAZE),
        sticker("poignee", "Poignée de main", 76, "🤝"),
        sticker("gobe", "Gobe-mouches", 88, "😐", Motion.PULSE),
        sticker("trophee", "Trophée", 90, "🏆", Motion.BLAZE)
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
        pattern: Pattern = Pattern.PLAIN,
        motion: Motion = Motion.NONE
    ) = Cosmetic(
        "bk.$id", CosmeticKind.BACK, name, level, top, bottom, oval,
        motion = motion, pattern = pattern
    )

    private fun felt(
        id: String,
        name: String,
        level: Int,
        light: Long,
        mid: Long,
        dark: Long,
        pattern: Pattern = Pattern.PLAIN,
        motion: Motion = Motion.NONE
    ) = Cosmetic(
        "ft.$id", CosmeticKind.FELT, name, level, light, mid, dark,
        motion = motion, pattern = pattern
    )

    private fun title(
        id: String,
        name: String,
        level: Int,
        a: Long = 0L,
        b: Long = 0L,
        c: Long = 0L,
        motion: Motion = Motion.NONE
    ) = Cosmetic("ti.$id", CosmeticKind.TITLE, name, level, a, b, c, motion = motion)

    private fun name(
        id: String,
        name: String,
        level: Int,
        a: Long,
        b: Long = 0L,
        c: Long = 0L,
        motion: Motion = Motion.NONE
    ) = Cosmetic("nm.$id", CosmeticKind.NAME, name, level, a, b, c, motion = motion)

    private fun sticker(
        id: String,
        name: String,
        level: Int,
        emoji: String,
        motion: Motion = Motion.NONE
    ) = Cosmetic("st.$id", CosmeticKind.STICKER, name, level, motion = motion, text = emoji)
}
