# 06 — Méta, rétention & monétisation

## 1. Ce qui rend un jeu addictif (et comment on le fait sans être toxique)

L'addictivité vient de mécanismes identifiables. On les utilise **tous**, mais on refuse ceux qui reposent sur la frustration payante.

| Mécanisme | Utilisé ? | Comment |
|---|---|---|
| **Boucle de récompense variable** | Oui | Butin de raid variable ±25 %, qualité de forge, bannières d'expédition |
| **Progression visible en permanence** | Oui | Barres partout, village qui change physiquement de silhouette |
| **Objectifs gigognes** | Oui | Micro (raid) → moyen (palier) → long (chapitre) → méta (saison), toujours 3+ actifs |
| **Effet Zeigarnik** (tâche inachevée) | Oui | Il y a toujours une amélioration à 80 % payable, une quête à 2/3 |
| **Investissement visible** | Oui | Le village *est* le trophée. Layouts partageables. |
| **Pression sociale positive** | Oui | Guilde, dons, classement, boss coop |
| **Peur de rater (FOMO)** | Modéré | Saisons de 4 semaines, mais **tout le contenu de gameplay revient** ; seuls les cosmétiques sont exclusifs |
| **Frustration payante** | **Non** | Pas de timer à skipper, pas d'énergie à recharger |
| **Loot box payante** | **Non** | Voir §5 |
| **Notifications culpabilisantes** | **Non** | Voir §4 |

Le pari : un jeu qui respecte le joueur retient **mieux sur 6 mois** qu'un jeu qui l'essore sur 3 semaines. Et surtout, il est jouable 10h d'affilée — ce qui est littéralement impossible dans le modèle CoC.

---

## 2. Guildes (« Clans »)

- **30 membres max.** Créées gratuitement.
- **Dons de troupes** : un membre demande, les autres envoient des unités qui renforcent sa défense ET son attaque suivante. Boucle sociale immédiate et asynchrone.
- **Boss de guilde** : un colosse à 50 M de HP, chaque membre l'attaque 3× par jour de jeu, les dégâts s'additionnent. Récompenses par palier collectif. Coop async parfaite : personne n'a besoin d'être en ligne en même temps.
- **Guerres de guilde** : 15v15, chaque membre a 2 attaques sur le village d'en face, résolution sur 24h réelles. C'est le seul élément à horloge réelle du jeu, et il est **entièrement optionnel**.
- **Hall de guilde** : bonus passifs collectifs achetés avec les contributions.
- **Chat** : v1 = messages prédéfinis (~60) + emotes + partage de layout/replay. Chat libre en v2 avec modération automatique. Réduit le risque légal et le coût de modération à zéro au lancement.

---

## 3. Saisons (4 semaines)

Chaque saison apporte :
1. **Un modificateur global** qui change les règles (« les murs sont 2× plus solides », « les héros ont un 3e sort », « la production est doublée mais les entrepôts sont 2× plus petits »). Ça change le méta et force à repenser les builds.
2. **Une région thématique** avec 12 villages de campagne, un boss, et un décor propre.
3. **Une piste de récompenses** à 50 paliers : gratuite **de bout en bout** pour le gameplay (Essence, fragments, ressources), avec une piste **cosmétique** payante en parallèle (voir §5).
4. **Un événement scénarisé** en milieu de saison (une semaine, mécanique inédite).
5. **Un classement de guilde** avec récompenses cosmétiques.

Coût de production par saison, une fois la v1 finie : ~2 semaines de travail (12 villages en éditeur de niveau + 1 boss + du paramétrage + 8 à 12 cosmétiques). Soutenable.

---

## 4. Notifications & retour du joueur

**Philosophie** : on notifie pour donner une bonne nouvelle, jamais pour culpabiliser.

| Autorisé | Interdit |
|---|---|
| « Ton village a été attaqué — regarde le replay » | « Tes ressources débordent, tu perds de l'or ! » |
| « Le boss de guilde est à 12 % — ton clan a besoin de toi » | « Ça fait 3 jours… on te manque ? » |
| « Nouvelle saison : la Faille est ouverte » | « Ton bouclier expire dans 1h ! » |
| « Ton défi hebdo se termine demain » (1 seule fois) | Rappels quotidiens répétitifs |

Max **2 notifications par jour**, désactivables par catégorie, silencieuses la nuit selon le fuseau du device.

**Retour de session** : un écran de résumé « pendant ton absence » — attaques subies, ressources de la Réserve, dons reçus, avancée du boss de guilde. 8 secondes, puis on joue.

---

## 5. Monétisation

Objectif : **ARPDAU ~0,08–0,15 $** avec une conversion de 3–5 %, sans dégrader l'expérience F2P. Le pari est le volume et la rétention longue, pas la ponction sur les baleines.

### Ce qui est vendu

| Produit | Prix | Contenu |
|---|---|---|
| **Passe de saison cosmétique** | 5,49 € | Skins de bâtiments, effets de troupes, cadre de profil, emotes. **Zéro impact gameplay.** |
| **Packs de Cristal** | 1,09 → 54,99 € | Monnaie premium |
| **Pack de Régent** (one-shot) | 9,99 € | +2 slots de layout, +1 slot de sort, un skin, 1 500 Cristaux |
| **Bundles cosmétiques** | 2,99 → 14,99 € | Thèmes de village complets (Nordique, Désert, Cristal, Steampunk) |
| **Supprimer les pubs** | 3,99 € | (les pubs sont déjà opt-in, voir plus bas) |

### Ce que le Cristal achète
- Cosmétiques (le gros)
- Slots supplémentaires (layouts, sorts, villageois d'élite)
- **Boosts de production** ×2 pendant 30 min de jeu (pas de temps réel)
- Rerolls de la Taverne, refresh du Grand Marché
- **Rien qui saute une attente** — il n'y en a pas
- **Rien qui donne un avantage PvP direct** : pas de troupes achetables, pas de stats achetables

### Publicité — **opt-in strict**
- **Aucune pub imposée.** Jamais d'interstitiel, jamais de pub entre deux raids.
- **Pubs récompensées volontaires** : doubler le butin d'un raid, reroll une bannière d'expédition, +1 tentative de boss de guilde. Max 8 par jour.
- C'est moins rentable à court terme et nettement mieux pour la rétention et les notes du store.

### Distribution gratuite de Cristal
~150 Cristaux/heure de jeu via quêtes, paliers, succès et défis. Un joueur F2P assidu déverrouille un thème cosmétique tous les ~10 jours. Ça donne une raison de jouer et ça fait goûter à la boutique.

### Conformité
- Pas de loot box payante → conforme aux régulations belge/néerlandaise et aux évolutions UE.
- Probabilités affichées pour toute source aléatoire, même gratuite (obligatoire Google Play).
- Classification cible **PEGI 7 / ESRB E10+**, contrôle parental sur les achats, pas de chat libre en v1.

---

## 6. Les KPI qu'on surveille

| KPI | Cible | Signal si raté |
|---|---|---|
| D1 retention | ≥ 45 % | Tutoriel ou premier wow raté |
| D7 | ≥ 22 % | Chapitre 2–3 trop lent |
| D30 | ≥ 10 % | Manque de méta / endgame |
| Session moyenne | ≥ 22 min | Trous de contenu (voir scoreur doc 02 §4) |
| Sessions/jour | 3–4 | — |
| **% de sessions > 2h** | **≥ 8 %** | **Le KPI signature du projet.** S'il est bas, la promesse « 10h » n'est pas tenue. |
| Temps avant 1er raid | < 100 s | Tutoriel trop bavard |
| Conversion payante | 3–5 % | — |
| Taux de complétion du chapitre 1 | ≥ 70 % | Onboarding |

**Instrumentation** : événement analytique sur chaque action de verbe, avec le `score` du scoreur (doc 02 §4). Un graphe « score max disponible au cours du temps » par joueur révèle immédiatement les trous de contenu. C'est l'outil de pilotage n°1 du projet.
