package com.zknw.unoduo.game

/** User-facing rule text, kept next to the engine so the two can't drift apart. */
object Rules {

    /**
     * One round in this many hides the +50, rolled fresh at the start of every round.
     *
     * Lives here rather than next to the code that rolls it because the odds are a rule
     * players are told, and a number quoted in the rule text must be the number used.
     */
    const val JACKPOT_ODDS = 100

    data class Section(val title: String, val lines: List<String>)

    val sections: List<Section> = listOf(
        Section(
            "Le jeu",
            listOf(
                "Jeu classique de 108 cartes : 4 couleurs (rouge, jaune, vert, bleu) avec le 0, deux fois les 1 à 9, deux Passe, deux Sens interdit et deux +2 par couleur, plus 4 Jokers et 4 +4.",
                "De $MIN_PLAYERS à $MAX_PLAYERS joueurs. 7 cartes chacun, la carte de départ est toujours un chiffre.",
                "Premier joueur sans carte : manche gagnée."
            )
        ),
        Section(
            "Poser une carte",
            listOf(
                "Même couleur, même chiffre ou même symbole que la carte du dessus.",
                "Le Joker et le +4 se posent quand tu veux ; tu choisis la couleur juste avant de poser.",
                "Ta main est rangée par couleur, et les cartes jouables sont surlignées en doré."
            )
        ),
        Section(
            "Passe et Sens interdit",
            listOf(
                "Passe : le joueur suivant saute son tour.",
                "Sens interdit : le tour repart dans l'autre sens. La flèche au-dessus de ta main indique le sens en cours.",
                "À deux joueurs, il n'y a pas de sens à inverser : le Sens interdit fonctionne alors exactement comme un Passe, et tu rejoues."
            )
        ),
        Section(
            "Cumuls (règle maison)",
            listOf(
                "Les +2 se cumulent : +2 sur +2, le total grimpe.",
                "Les +4 se cumulent aussi : +4 sur +4.",
                "Un +4 peut être posé sur un +2 en cours."
            )
        ),
        Section(
            "Contrer un +4",
            listOf(
                "Quand quelqu'un pose un +4, il annonce une couleur.",
                "Si tu as un +2 de cette couleur exactement, tu peux le poser pour renvoyer la pile au joueur suivant et éviter le +4.",
                "Un +2 d'une autre couleur ne contre pas un +4."
            )
        ),
        Section(
            "Encaisser la pile",
            listOf(
                "Tu ne peux pas contrer ? Tu pioches tout le cumul.",
                "Si la dernière carte posée sur la pile était un +4 : tu pioches ET tu sautes ton tour.",
                "Si c'était un +2 : tu pioches mais tu joues normalement dans la foulée."
            )
        ),
        Section(
            "Piocher",
            listOf(
                "Rien à poser ? Touche la pioche au centre de la table.",
                "Si la carte piochée est jouable, tu choisis : la poser, ou passer ton tour.",
                "En revanche, un cumul de +2 ou +4 que tu ne peux pas contrer est encaissé automatiquement — inutile de le confirmer.",
                "Si tu peux le contrer mais que tu préfères ne pas surenchérir, touche la pioche : elle affiche « Encaisser +N » et tu prends la pile plutôt que de la faire grossir."
            )
        ),
        Section(
            "La carte qui n'existe pas",
            listOf(
                "Environ une manche sur $JACKPOT_ODDS, une carte de trop est glissée au hasard dans la pioche : le +50.",
                "C'est du vrai hasard, retiré à chaque manche indépendamment. Ce n'est pas « toutes les $JACKPOT_ODDS manches » : elle peut sortir deux fois de suite, ou jamais de la soirée.",
                "Elle n'est jamais distribuée. Il faut tomber dessus en piochant, et tant qu'elle est en main, personne d'autre ne sait qu'elle existe.",
                "Elle se joue comme un +4 — tu annonces une couleur — sauf qu'elle pose cinquante cartes sur la pile.",
                "Un +2 de la couleur annoncée la renvoie quand même. Cinquante cartes, mais pour quelqu'un d'autre.",
                "Si la pioche et la défausse réunies ne font pas cinquante cartes, la victime prend tout ce qui reste. C'est déjà largement suffisant."
            )
        ),
        Section(
            "Niveaux et cosmétiques",
            listOf(
                "Chaque manche terminée rapporte de l'expérience : 25 XP gagnée, 10 XP perdue. On monte donc même quand ça se passe mal, et une partie contre le bot compte aussi.",
                "Chaque niveau coûte un peu plus que le précédent, toujours du même écart : 20 XP pour le premier, 5 de plus à chaque fois. Le niveau 100 est le dernier.",
                "Chaque niveau, du 2 au 100, débloque au moins un cosmétique, et 43 niveaux en donnent deux ou plus : 156 en tout, répartis en cadres d'avatar, dos de carte, tapis, titres, couleurs de pseudo et stickers.",
                "Les cadres les plus hauts ne sont plus des cercles de couleur : flammes, éclairs, vagues, satellite, constellation, et une couronne dorée au niveau 100.",
                "Tout ce que tu portes est vu par les autres. Le cadre, le titre et la couleur du pseudo restent sur toi ; le tapis et le dos de la pioche appartiennent au joueur dont c'est le tour, et changent de main avec lui.",
                "La main de chaque joueur est dessinée avec son dos de carte à lui, donc tu vois ce que les autres ont débloqué.",
                "Remettre les statistiques à zéro n'efface ni l'expérience ni les cosmétiques."
            )
        ),
        Section(
            "Parties personnalisées",
            listOf(
                "En créant un salon tu choisis entre une partie normale et une partie personnalisée. Une partie personnalisée ajoute les mods que tu coches — autant que tu veux, ils se cumulent.",
                "Les mods n'ajoutent que des cartes : le jeu de base ne change pas, il y en a simplement plus dans le paquet.",
                "Les mods actifs sont affichés dans le salon avant le lancement, et le solo les propose aussi."
            )
        ),
        Section(
            "Mod « ${GameMod.DRAW_EIGHT.label} »",
            listOf(
                "${Deck.DRAW_EIGHTS} +8 rejoignent le paquet.",
                "Ils se comportent exactement comme des +4 : cumul avec les +4 et les +8, et le porteur annonce une couleur.",
                "Un +2 de la couleur annoncée les contre, comme pour un +4.",
                "Encaisser une pile terminée par un +8 coûte aussi le tour."
            )
        ),
        Section(
            "Mod « ${GameMod.DRAW_TWELVE.label} »",
            listOf(
                "Une seule carte +12 pour toute la partie.",
                "Elle n'est jamais distribuée : elle est glissée au hasard dans la pioche une fois les mains faites. On ne peut donc que tomber dessus en piochant.",
                "Une fois en main, elle se joue comme un +4 : cumul avec les +4, +8 et +12, tu annonces une couleur, et un +2 de cette couleur la contre.",
                "Encaisser une pile terminée par un +12 coûte aussi le tour."
            )
        ),
        Section(
            "Mod « ${GameMod.SPY.label} »",
            listOf(
                "${Deck.SPIES} cartes Espion rejoignent le paquet. C'est un Joker ordinaire : tu la poses quand tu veux et tu annonces une couleur.",
                "En prime, elle te montre une carte au hasard de la main du joueur suivant.",
                "Toi seul la vois : elle apparaît face visible au bout de sa main sur ton écran, et lui ne sait même pas laquelle a fuité.",
                "Tu la vois jusqu'à ce qu'il la pose.",
                "Chaque espion a ses propres informations : si tu as déjà vu toutes ses cartes, l'Espion ne fait que changer la couleur."
            )
        ),
        Section(
            "Mod « ${GameMod.DOUBLE_PLAY.label} »",
            listOf(
                "${Deck.DOUBLE_PLAYS} cartes Coup double rejoignent le paquet. Elles se posent sur n'importe quoi, comme un Joker.",
                "Tu annonces une couleur, puis tu poses deux cartes de plus en suivant les règles habituelles.",
                "Une carte d'attaque (Passe, Sens interdit, +2, +4, +8) met fin au coup double sur-le-champ : la main passe au joueur suivant.",
                "Tu peux t'arrêter avant d'avoir posé tes deux cartes, et si tu n'as rien de jouable le coup double s'arrête tout seul.",
                "Pas de pioche pendant un coup double : il se joue avec la main que tu as."
            )
        )
    )
}
