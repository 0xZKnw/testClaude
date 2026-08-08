package com.zknw.unoduo.game

/** User-facing rule text, kept next to the engine so the two can't drift apart. */
object Rules {

    data class Section(val title: String, val lines: List<String>)

    val sections: List<Section> = listOf(
        Section(
            "Le jeu",
            listOf(
                "Jeu classique de 108 cartes : 4 couleurs (rouge, jaune, vert, bleu) avec le 0, deux fois les 1 à 9, deux Passe, deux Sens interdit et deux +2 par couleur, plus 4 Jokers et 4 +4.",
                "7 cartes chacun. La carte de départ est toujours un chiffre.",
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
            "À deux joueurs",
            listOf(
                "Passe : l'adversaire saute son tour, tu rejoues.",
                "Sens interdit : à deux, ça fonctionne exactement comme un Passe."
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
                "Si tu as un +2 de cette couleur exactement, tu peux le poser pour renvoyer la pile et éviter le +4.",
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
                "Il n'y a pas de bouton pioche : quand tu n'as rien à poser, la carte est piochée automatiquement.",
                "Si cette carte est jouable, tu choisis : la poser, ou passer ton tour.",
                "Même chose pour un cumul que tu ne peux pas contrer : il est encaissé tout seul."
            )
        )
    )
}
