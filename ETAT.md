# État réel du projet

Mis à jour à la fin de la session d'implémentation initiale.
Le plan (`docs/`) décrit le jeu complet ; ce fichier dit ce qui **tourne
réellement** aujourd'hui.

---

## Ce qui est construit et vérifié

### Cœur de jeu
- **Grille 44×44**, placement avec collisions, déplacement gratuit, démolition
- **Construction instantanée** — la promesse centrale du jeu — avec son
  animation de chute, écrasement et poussière
- **5 ressources**, production continue, plafonds de stockage, progression
  hors-ligne dégressive jusqu'à 24 h
- **20 bâtiments** sur 15 niveaux, 3 paliers visuels, quotas par palier de
  Bastion, bonus d'adjacence
- **Bastion 1→40** avec ses trois verrous : coût, étoiles de campagne, Épreuve
  — et un écran qui liste précisément ce qui manque
- **Bouton Contremaître** : toutes les améliorations payables en un tap
- **18 unités** entraînables instantanément, capacité de camp liée au Bastion
- **72 villages de campagne** + **8 Épreuves**, générés procéduralement depuis
  une graine
- **Sauvegarde** versionnée, checksum, triple écriture, restauration testée
- **Conseiller « quoi faire ? »** : scoreur qui garantit qu'aucun état de
  partie ne laisse le joueur sans action

### Combat
- Simulation **déterministe** en virgule fixe Q16.16, 20 Hz, RNG seedé
- Ciblage par préférence, interception par les murs, soigneurs, défenses
- Étoiles (50 % / Bastion / 100 %), butin avec bonus ×1,8 à 3 étoiles
- **Replay** : `(village + graine + entrées)` rejoue le combat à l'identique —
  la brique sur laquelle reposera la validation serveur du PvP

### Rendu et interface
- Maillages low poly **générés par code**, zéro asset externe
- Regroupement en MultiMesh, un seul material pour tout le jeu
- Caméra isométrique tactile : pan, pinch, rotation à deux doigts
- HUD, catalogue de construction, fiche de bâtiment, armée, Bastion, campagne,
  HUD de raid, écran de butin

### Qualité
| Suite | Résultat |
|---|---|
| Tests unitaires | **145 vérifications, 0 échec** |
| Tests d'intégration | **44 vérifications, 0 échec** |
| Build exporté | démarre sans erreur |
| Captures de rendu | générées et inspectées |

---

## Ce qui n'est pas construit

Rien de tout ceci n'est commencé — c'est du plan, pas du code :

| Domaine | Où c'est spécifié |
|---|---|
| Héros, équipement, forge | docs/05 §3, docs/02 V5 |
| Expéditions et carte du monde | docs/02 V4 |
| Villageois, traits, affectation | docs/04 §4 |
| Arbre de recherche (60 nœuds) | docs/04 §6 |
| PvP asynchrone, ligues, replays d'attaque | docs/05 §4 |
| Guildes, boss de guilde, saisons | docs/06 |
| Backend, sauvegarde cloud, validation serveur | docs/08 §5 |
| Monétisation, analytics | docs/06 §5 |
| Audio (musique, ~180 SFX) | docs/07 §7 |
| Localisation (chaînes encore en dur) | docs/08 §9 |
| Tutoriel scénarisé des 10 premières minutes | docs/02 §3 |

**Durée de jeu actuelle** : de l'ordre d'une à deux heures avant d'épuiser ce
qui est implémenté — la campagne et la montée de Bastion tiennent, mais les
cinq autres verbes de jeu (explorer, forger, administrer, défendre activement,
jouer en guilde) manquent. L'objectif de 10 h suppose la roadmap complète.

---

## Deux bugs instructifs, corrigés

Notés parce qu'ils sont silencieux et coûteux à retrouver :

1. **Les CSV disparaissaient du build exporté.** Godot les importait comme
   fichiers de traduction. L'APK aurait démarré sur un jeu sans aucune donnée.
   Corrigé par `data/*.csv.import` avec `importer="keep"`, et un test de CI qui
   lance un build **exporté** — pas seulement le projet ouvert.

2. **Toutes les normales étaient inversées.** Godot attend un enroulement
   horaire pour les faces avant. Conséquence : tout restait visible (le culling
   ne dépend que de l'enroulement) mais le soleil n'éclairait plus rien. La
   scène était plate et sombre, sans le moindre message d'erreur.

---

## Limites connues

- Pas de pathfinding : les unités vont en ligne droite et attaquent le mur qui
  les bloque. Suffisant et lisible, mais un vrai flow field améliorerait les
  attaques sur les enceintes complexes.
- Les textes sont en dur dans le code — à externaliser avant toute traduction.
- Aucune mesure de performance sur un vrai appareil bas de gamme n'a encore été
  faite ; les budgets de docs/07 §2 restent des cibles, pas des constats.
- L'export Android n'a pas pu être exécuté dans l'environnement de
  développement (SDK Android inaccessible depuis le réseau) : c'est la CI qui
  produit et vérifie l'APK.
