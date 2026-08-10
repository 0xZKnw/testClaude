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
  {
    title: 'Niveaux et cosmétiques',
    lines: [
      "Chaque manche terminée rapporte de l'expérience : 25 XP gagnée, 10 XP perdue. On monte donc même quand ça se passe mal, et une partie contre le bot compte aussi.",
      'Chaque niveau coûte un peu plus que le précédent, toujours du même écart : 20 XP pour le premier, 5 de plus à chaque fois. Le niveau 100 est le dernier.',
      "Chaque niveau, du 2 au 100, débloque au moins un cosmétique : cadres d'avatar, dos de carte, tapis, titres, couleurs de pseudo et stickers.",
      'Le cadre, le titre et la couleur du pseudo sont vus par les autres joueurs. Le dos de carte et le tapis ne changent que ton écran à toi.',
      "Remettre les statistiques à zéro n'efface ni l'expérience ni les cosmétiques.",
    ],
  },
  {
    title: 'Parties personnalisées',
    lines: [
      'En créant un salon tu choisis entre une partie normale et une partie personnalisée. Une partie personnalisée ajoute les mods que tu coches — autant que tu veux, ils se cumulent.',
      "Les mods n'ajoutent que des cartes : le jeu de base ne change pas, il y en a simplement plus dans le paquet.",
      'Les mods actifs sont affichés dans le salon avant le lancement, et le solo les propose aussi.',
    ],
  },
  {
    title: 'Mod « Les +8 »',
    lines: [
      '2 +8 rejoignent le paquet.',
      'Ils se comportent exactement comme des +4 : cumul avec les +4 et les +8, et le porteur annonce une couleur.',
      'Un +2 de la couleur annoncée les contre, comme pour un +4.',
      'Encaisser une pile terminée par un +8 coûte aussi le tour.',
    ],
  },
  {
    title: 'Mod « Le +12 »',
    lines: [
      'Une seule carte +12 pour toute la partie.',
      "Elle n'est jamais distribuée : elle est glissée au hasard dans la pioche une fois les mains faites. On ne peut donc que tomber dessus en piochant.",
      'Une fois en main, elle se joue comme un +4 : cumul avec les +4, +8 et +12, tu annonces une couleur, et un +2 de cette couleur la contre.',
      'Encaisser une pile terminée par un +12 coûte aussi le tour.',
    ],
  },
  {
    title: 'Mod « Espion »',
    lines: [
      "3 cartes Espion rejoignent le paquet. C'est un Joker ordinaire : tu la poses quand tu veux et tu annonces une couleur.",
      'En prime, elle te montre une carte au hasard de la main du joueur suivant.',
      "Toi seul la vois : elle apparaît face visible au bout de sa main sur ton écran, et lui ne sait même pas laquelle a fuité.",
      "Tu la vois jusqu'à ce qu'il la pose.",
      "Chaque espion a ses propres informations : si tu as déjà vu toutes ses cartes, l'Espion ne fait que changer la couleur.",
    ],
  },
  {
    title: 'Mod « Coup double »',
    lines: [
      "3 cartes Coup double rejoignent le paquet. Elles se posent sur n'importe quoi, comme un Joker.",
      'Tu annonces une couleur, puis tu poses deux cartes de plus en suivant les règles habituelles.',
      "Une carte d'attaque (Passe, Sens interdit, +2, +4, +8) met fin au coup double sur-le-champ : la main passe au joueur suivant.",
      "Tu peux t'arrêter avant d'avoir posé tes deux cartes, et si tu n'as rien de jouable le coup double s'arrête tout seul.",
      'Pas de pioche pendant un coup double : il se joue avec la main que tu as.',
    ],
  },
];
