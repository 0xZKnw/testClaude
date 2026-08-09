package com.zknw.unoduo.game

/** User-facing rule text, kept next to the engine so the two can't drift apart. */
object Rules {

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
                "En revanche, un cumul de +2 ou +4 que tu ne peux pas contrer est encaissé automatiquement — inutile de le confirmer."
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
