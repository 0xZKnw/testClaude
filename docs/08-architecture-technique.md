# 08 — Architecture technique

> Ce document décrit l'architecture **telle qu'implémentée** dans `game/`.
> Les parties non encore construites sont signalées « à venir ».

## 1. Choix du moteur : Godot 4

Le plan initial recommandait Unity. La décision a été renversée pour une raison
concrète et vérifiable : **Unity ne peut pas produire d'APK en intégration
continue sans identifiants de licence Unity** (secrets `UNITY_EMAIL`,
`UNITY_PASSWORD`, `UNITY_LICENSE`). Godot n'a pas cette contrainte : la CI sort
un APK dès le premier push, sans compte ni secret.

| Critère | Godot 4.4 | Unity 6 |
|---|---|---|
| APK en CI sans secret | **Oui** | Non — licence obligatoire |
| Licence / redevances | Aucune (MIT) | Gratuit sous 200 k$ de CA |
| Taille du moteur dans l'APK | ~30 Mo | ~40 Mo |
| SDK pub / IAP / analytics | Plugins communautaires | Natif, plus mature |
| Catalogue d'assets 3D | Limité | Énorme |

Les deux faiblesses de Godot (SDK monétisation, catalogue d'assets) ne coûtent
rien ici : la monétisation n'arrive qu'au jalon M4, et **le jeu ne consomme
aucun asset externe** — toute la 3D est générée par code (§6).

**Configuration** : Godot 4.4.1 · renderer `gl_compatibility` (le plus
compatible et le plus rapide pour du low poly) · ARM64 · portrait · export
Android sans build Gradle (template pré-compilé).

---

## 2. Organisation du code

```
game/
├── core/          logique pure — aucune dépendance à la scène ni au rendu
│   ├── fix.gd            virgule fixe Q16.16
│   ├── det_random.gd     xorshift32 seedé
│   ├── data_tables.gd    CSV -> tables, courbes de progression
│   ├── village.gd        grille d'occupation + bâtiments
│   ├── player_state.gd   état sauvegardé
│   ├── economy.gd        production, plafonds, coûts, adjacences
│   ├── progression.gd    les 40 paliers et leurs trois verrous
│   ├── combat_sim.gd     simulation de raid déterministe
│   ├── village_gen.gd    génération procédurale des villages ennemis
│   ├── campaign.gd       72 niveaux + 8 Épreuves
│   ├── advisor.gd        scoreur « quoi faire maintenant ? »
│   ├── save_system.gd    sauvegarde versionnée, triple écriture
│   ├── game_db.gd        autoload DB
│   └── game.gd           autoload Game — orchestrateur, seul mutateur d'état
├── view/          rendu 3D
├── ui/            interface
├── scenes/main.gd point d'entrée
├── data/*.csv     toutes les constantes de jeu
└── tests/         suites headless
```

**Règle structurante** : `core/` ne référence jamais un nœud de scène. C'est ce
qui permet de rejouer 10 000 combats en console pour l'équilibrage, et à un
serveur de revalider un rapport de combat avec exactement le même code.

**Un seul mutateur** : toute modification de l'état passe par l'autoload
`Game`, qui émet des signaux. La vue lit et affiche, elle ne décide jamais.

**Un seul fichier de scène** (`main.tscn`, 4 lignes). Tout le reste est
construit par code : les scènes Godot fusionnent mal dans Git, et une interface
générée reste lisible en revue de code.

---

## 3. Déterminisme du combat

Contrainte non négociable (docs/05 §4), respectée dans `core/combat_sim.gd` :

- **aucun float** dans la boucle — arithmétique Q16.16 via `Fix`
- **aucun `randi()`/`randf()`** — uniquement `DetRandom` seedé
- **pas fixe de 1/20 s**, indépendant du framerate ; la vue interpole
- **aucune itération sur Dictionary** dont l'ordre compterait

Le simulateur expose `queue_deploy()`, `step()`, `run_to_end()` et une fonction
statique `replay(village, tables, seed, inputs)`. Un rapport de combat se
résume à `(seed, snapshot, liste d'inputs)` — le serveur rejoue et recalcule le
butin lui-même.

Une **empreinte** est mise à jour à chaque tick (positions, PV, destruction).
Deux exécutions identiques doivent produire la même valeur : c'est ce que
vérifie `test_combat_determinism`, et c'est ce qui casse le build si une
refonte brise silencieusement le PvP.

Autres choix de simulation : ciblage par préférence avec départage par index
(déterministe), interception par les murs sur la case suivante (au lieu d'un
pathfinding complet), dégâts continus `dps × dt` plutôt que des cooldowns.

---

## 4. Données

Tout est dans `data/*.csv`, éditable dans un tableur : coûts, PV, dégâts,
portées, paliers de déblocage, quotas par bâtiment. **Rééquilibrer le jeu ne
demande aucune recompilation.**

Les courbes sont calculées en **entiers** (multiplication/division répétée,
`DataTables.grow`) et jamais avec `pow()` en float : les valeurs dérivées
alimentent la simulation, qui doit rester bit à bit reproductible.

> **Piège Godot rencontré** : les `.csv` sont happés par l'importateur de
> traductions et ne survivent pas à l'export — l'APK démarre sans aucune
> donnée. Les fichiers `data/*.csv.import` forcent `importer="keep"`. Un test
> de CI lance un **build exporté** justement pour attraper ce genre de bug.

---

## 5. Sauvegarde

JSON compact dans une enveloppe `{version, checksum, body}`, checksum FNV-1a.
**Triple écriture** avec rotation (`.save` → `.bak` → `.old`) et restauration
automatique si le fichier principal est corrompu. Chaîne de migrations
explicite dans `SaveSystem._migrate()` — casser les sauvegardes en production
est le pire incident possible sur un jeu de gestion.

Sauvegarde automatique toutes les 30 s, à chaque fin de raid, et sur
`NOTIFICATION_APPLICATION_PAUSED`.

**À venir** : sauvegarde cloud (Google Play Games Services), puis backend.

---

## 6. Rendu

**Aucun asset 3D n'est embarqué.** Chaque bâtiment, unité et décor est assemblé
par code dans `view/low_poly.gd` à partir de boîtes, troncs de pyramide, toits
et cylindres, la couleur étant écrite dans les **sommets**. Un seul
`StandardMaterial3D` avec `vertex_color_use_as_albedo` sert pour tout le jeu.

Les bâtiments sont regroupés par (silhouette, taille, palier visuel, couleur)
dans des `MultiMeshInstance3D` : un village de 300 bâtiments tient en une
vingtaine de draw calls au lieu de 300.

Trois paliers visuels par bâtiment (niveaux 1-5, 6-10, 11+) — produire 15
maillages par bâtiment serait ruineux et l'œil ne lit que les changements
francs de silhouette.

> **Deux pièges de géométrie corrigés**, notés ici parce qu'ils sont
> silencieux et coûteux à diagnostiquer :
> 1. **Normales inversées.** Godot considère comme face avant un triangle
>    enroulé dans le sens horaire ; la normale est donc `(c-a)×(b-a)`. Avec
>    l'autre sens, tout reste *visible* (le culling ne dépend que de
>    l'enroulement) mais plus rien n'est éclairé par le soleil — la scène est
>    plate et sombre, en lumière ambiante seule.
> 2. **Liseré sur les toits.** La face supérieure des murs affleurait sous
>    l'avant-toit. Une corniche fine sous chaque toit ferme la jonction.

Picking sans collider : intersection analytique du rayon caméra avec le plan
`y = 0`. Exact, et gratuit.

---

## 7. Performance mobile

| Règle | État |
|---|---|
| Regroupement en MultiMesh | fait |
| Un seul material, couleurs aux sommets | fait |
| Pas d'`_process()` par bâtiment | fait — tick centralisé (économie 0,25 s, combat 20 Hz) |
| Reconstruction des lots seulement au changement de village | fait |
| Pool d'effets (poussière) | fait |
| Marge de terrain pour masquer le bord | fait |
| LOD sur les gros bâtiments | à venir |
| Occlusion par cellule de grille | à venir |
| Détection de throttling thermique | à venir (essentiel pour les sessions longues) |

**Appareil plancher visé** : Snapdragon 665 / Adreno 610 / 3 Go de RAM. Le
renderer `gl_compatibility` et le regroupement en MultiMesh sont les deux
décisions qui rendent cette cible atteignable.

---

## 8. Tests et CI

Trois niveaux, tous exécutables sans écran :

| Suite | Commande | Ce qu'elle protège |
|---|---|---|
| Unitaire | `godot --headless --path game --script res://tests/run_tests.gd` | virgule fixe, RNG, grille, économie, progression, **déterminisme**, replay, sauvegarde |
| Intégration | `godot --headless --path game res://tests/integration.tscn` | la partie jouable de bout en bout via `Game` |
| Build exporté | export Linux + lancement | les fichiers absents du paquet |
| Captures | `xvfb-run godot --path game --rendering-driver opengl3 res://tests/screenshot.tscn` | le rendu, sans appareil |

Le workflow `.github/workflows/android.yml` enchaîne : tests → smoke test sur
build exporté → APK signé en debug → artefact téléchargeable. **Aucun secret
n'est requis** : la clé de debug est générée à la volée, comme le fait Android
Studio.

---

## 9. Ce qui reste à construire

Backend (Supabase puis Nakama), PvP asynchrone et validation serveur des
combats, guildes, saisons, IAP, localisation (toutes les chaînes sont
actuellement en dur dans le code — à externaliser avant la première traduction),
héros, expéditions, forge, villageois. Voir la roadmap en docs/09.
