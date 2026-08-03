# 05 — Combat, héros & PvP

## 1. Le raid — anatomie

**Durée** : 90 à 180 s. **Objectif** : détruire un maximum du village adverse.

### Phases
1. **Reconnaissance (illimitée)** — le joueur tourne autour du village, voit les défenses, les portées affichées en surbrillance au tap. Aucun chrono ici : la réflexion est gratuite, l'exécution est chronométrée.
2. **Déploiement** — tap sur le périmètre pour poser des unités. Le stock d'unités est visible en bas. Le chrono (180 s) démarre au premier déploiement.
3. **Exécution** — les unités agissent en autonomie selon leur IA de ciblage. Le joueur intervient via : déploiements additionnels, **sorts** (3 slots), **capacités de héros** (2 par héros, cooldown).
4. **Résultat** — % de destruction, étoiles (50 % / 100 % / bonus d'objectif), butin, replay sauvegardé.

### Ce qui rend le raid rejouable
- **3 objectifs par village de campagne** (détruire 50 %, 100 %, et un objectif spécial : « sans perdre d'unité », « en moins de 75 s », « sans héros »).
- **Bonus ×1.8 de butin à 3 étoiles** (doc 03) : refaire un raid mal joué est objectivement rentable.
- **Rejouabilité gratuite** : recommencer un raid de campagne ne coûte **rien**. Zéro friction sur le retry — c'est un jeu de skill, pas un jeu de patience.

---

## 2. Les unités — 18 types

| Catégorie | Unités | Rôle |
|---|---|---|
| **Infanterie** | Milicien, Épéiste, Berserker, Gardien (tank) | Absorbe, cible le plus proche |
| **Distance** | Archer, Arbalétrier, Fronde | DPS fragile, portée 4–7 |
| **Cavalerie** | Éclaireur, Lancier, Chevalier | Rush, ignore les murs à basse hauteur |
| **Siège** | Bélier, Catapulte, Trébuchet | Cible bâtiments/murs uniquement, très lent |
| **Support** | Guérisseur, Sapeur (creuse les murs), Porte-bannière (aura) | Multiplicateurs |
| **Spécial** | Mineur (passe sous les murs), Golem d'essence | Débloqué tardivement |

Chaque unité : **niveau 1→12** (Terrain d'entraînement), 3 paliers visuels, une **IA de ciblage** déclarée en donnée :
```
ciblage: { priorité: [Défense, Ressource, Toute], portée: 4.5,
           ignore_murs: false, aggro_radius: 6 }
```

### Sorts (8)
Rage · Soin · Foudre · Gel · Saut (ouvre les murs) · Invisibilité · Duplication · Séisme.
3 slots équipables, rechargés entre les raids (gratuitement).

### Composition d'armée
Capacité en "places" (croît avec le Bastion). Les unités **existent en stock** et se reconstituent **instantanément** contre de l'Or + Nourriture. Perdre une armée coûte des ressources, jamais du temps.

---

## 3. Les héros — 8

Le héros est l'ancre de puissance et d'identité. Il est **présent au raid, à l'expédition et en défense** (garnison).

| Héros | Archétype | Capacité 1 | Capacité 2 (ultime) |
|---|---|---|---|
| **Bram, le Régent** | Polyvalent | Charge | Bannière : +30 % dégâts de zone |
| **Sela, l'Archère** | DPS distance | Tir perçant | Pluie de flèches |
| **Korr, le Bâtisseur** | Support | Répare les alliés | Érige une tour temporaire |
| **Vane, l'Ombre** | Assassin | Blink | Marque : dégâts ×2 sur une cible |
| **Ysolde, la Mage** | Contrôle | Mur de glace | Météore |
| **Thane, le Colosse** | Tank | Provocation | Invulnérabilité 5 s + onde |
| **Pip, la Sapeuse** | Utilitaire | Tunnel | Démolition de 4 murs |
| **Sombrelame** | Endgame | Vol de vie | Invocation de spectres |

- **Niveaux 1→50**, progression par Essence + fragments (expéditions, boss, saison).
- **6 slots d'équipement** (arme, armure, casque, bottes, amulette, relique) forgés en V5.
- **Aucun héros n'est payant.** Tous s'obtiennent en jouant. Le premier à T+7 min, le dernier vers Bastion 34.

---

## 4. PvP asynchrone

### Pourquoi asynchrone
Le PvP synchrone demande matchmaking temps réel, serveurs autoritatifs, gestion de latence et de déconnexion. Hors budget, hors scope, et incompatible avec le fait qu'un joueur puisse jouer 10h sans dépendre de la présence d'autres gens.

### Mécanique
1. Chaque joueur publie un **snapshot** de son village (layout + niveaux + garnison) à chaque changement significatif.
2. On attaque un snapshot, pas un joueur. Le défenseur n'est jamais dérangé.
3. Il reçoit un **replay** consultable au retour → boucle V3 (défendre) alimentée.
4. **Trophées** : gain/perte selon l'écart de ligue, 9 ligues (Bronze → Régent).
5. **Loot protégé** : le Coffre-fort protège 100 % de son contenu ; on ne peut jamais tout perdre. La perte max par attaque est plafonnée à 18 % du stock hors coffre.

### Matchmaking
Fenêtre sur le CP (doc 03) : `CP_cible ∈ [0.85×CP, 1.2×CP]`, élargie progressivement si le pool est vide, avec **fallback sur des villages PNJ générés** au bon CP. Le joueur ne voit jamais « aucun adversaire trouvé ».

### Anti-cheat
Le combat est **déterministe** : même seed + mêmes inputs = même résultat, bit à bit.
- Le client envoie `{seed, snapshot_id, liste d'inputs horodatés}`, pas le résultat.
- Le serveur **rejoue** la simulation headless (le même code C# compilé côté serveur, ou revalidé par batch) et calcule le butin lui-même.
- Toute divergence → résultat rejeté, joueur flaggé, 3 flags = shadow-ban de classement.

**Conséquence technique majeure** : la simulation de combat doit utiliser de l'**arithmétique déterministe** (fixed-point Q16.16 en `int`, pas de `float`), un RNG **xorshift seedé** dédié à la sim, et un pas fixe à 20 Hz. Cette contrainte est structurante : elle doit être posée dès la première ligne du système de combat, pas rétro-ajoutée. Voir doc 08 §4.

---

## 5. Défis de défense (gameplay TD actif)

Pour que « Défendre » soit un vrai verbe et pas de la contemplation :
- Le joueur lance quand il veut une **vague scriptée** contre son propre village.
- Il joue activement : il peut déclencher des pièges manuellement, dépenser de l'Or pour réparer en direct, et utiliser son héros en garnison.
- 10 vagues de difficulté croissante, récompense en Fer et en Essence.
- **Mode Invasion** (événement, ~toutes les 35 min de jeu) : 10 minutes, vagues massives, tout le village est en jeu. C'est le pic d'intensité programmé de la boucle 10h.

---

## 6. Expéditions (combat de la carte du monde)

Format différent du raid, exprès — la variété est le but.
- Escouade de 5 unités + 1 héros, **combat auto-résolu avec interventions** (le joueur choisit les cibles prioritaires et déclenche les capacités ; pas de placement).
- Durée d'un combat : 20 à 40 s. Un chemin d'expédition = 5 à 9 nœuds = 4 à 8 min.
- **Bannières** : bonus temporaires cumulés pendant l'expédition (ex. « +20 % dégâts, −10 % HP »), 30 bannières, choix à chaque nœud → chaque expédition a une identité.
- Mort de l'escouade = expédition perdue, on garde 50 % du butin accumulé. Pas de perte d'unités permanente.

---

## 7. Équilibrage — méthode

- Toutes les stats en **CSV → ScriptableObject** générés, hot-reloadables en éditeur et en build de dev.
- Un **simulateur headless** (mode batch Unity, ou un port C# console) qui joue 10 000 raids IA vs layouts types et sort : taux de victoire, étoiles moyennes, durée moyenne, unité la plus/moins utilisée.
- Règle de santé : **aucune unité ne doit représenter > 22 % ni < 4 %** de l'usage total au-delà de Bastion 15.
- Passe d'équilibrage à chaque jalon, pas seulement à la fin.
