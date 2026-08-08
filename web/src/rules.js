// The rule text, word for word from the Android app's Rules.kt — the two versions play
// the same game, so they must also explain it the same way.

export const RULES = [
  {
    title: 'Le jeu',
    lines: [
      'Jeu classique de 108 cartes : 4 couleurs (rouge, jaune, vert, bleu) avec le 0, deux fois les 1 à 9, deux Passe, deux Sens interdit et deux +2 par couleur, plus 4 Jokers et 4 +4.',
      'De 2 à 5 joueurs. 7 cartes chacun, la carte de départ est toujours un chiffre.',
      'Premier joueur sans carte : manche gagnée.',
    ],
  },
  {
    title: 'Poser une carte',
    lines: [
      'Même couleur, même chiffre ou même symbole que la carte du dessus.',
      'Le Joker et le +4 se posent quand tu veux ; tu choisis la couleur juste avant de poser.',
      'Ta main est rangée par couleur, et les cartes jouables sont surlignées en doré.',
    ],
  },
  {
    title: 'Passe et Sens interdit',
    lines: [
      'Passe : le joueur suivant saute son tour.',
      "Sens interdit : le tour repart dans l'autre sens. La flèche au-dessus de ta main indique le sens en cours.",
      "À deux joueurs, il n'y a pas de sens à inverser : le Sens interdit fonctionne alors exactement comme un Passe, et tu rejoues.",
    ],
  },
  {
    title: 'Cumuls (règle maison)',
    lines: [
      'Les +2 se cumulent : +2 sur +2, le total grimpe.',
      'Les +4 se cumulent aussi : +4 sur +4.',
      'Un +4 peut être posé sur un +2 en cours.',
    ],
  },
  {
    title: 'Contrer un +4',
    lines: [
      "Quand quelqu'un pose un +4, il annonce une couleur.",
      'Si tu as un +2 de cette couleur exactement, tu peux le poser pour renvoyer la pile au joueur suivant et éviter le +4.',
      "Un +2 d'une autre couleur ne contre pas un +4.",
    ],
  },
  {
    title: 'Encaisser la pile',
    lines: [
      'Tu ne peux pas contrer ? Tu pioches tout le cumul.',
      'Si la dernière carte posée sur la pile était un +4 : tu pioches ET tu sautes ton tour.',
      "Si c'était un +2 : tu pioches mais tu joues normalement dans la foulée.",
    ],
  },
  {
    title: 'Piocher',
    lines: [
      'Rien à poser ? Touche la pioche au centre de la table.',
      'Si la carte piochée est jouable, tu choisis : la poser, ou passer ton tour.',
      "En revanche, un cumul de +2 ou +4 que tu ne peux pas contrer est encaissé automatiquement — inutile de le confirmer.",
    ],
  },
];
