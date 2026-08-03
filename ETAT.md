# État réel du projet

Le plan (`docs/`) décrit le jeu complet ; ce fichier dit ce qui **tourne
réellement** aujourd'hui.

---

## Ce qui est construit et vérifié

### Boucle village (builder à la Clash of Clans)
- **Grille 44×44**, placement avec collisions, déplacement gratuit, démolition
- **Construction instantanée** — la promesse centrale — avec chute, écrasement
  et poussière
- **33 bâtiments** sur 15 niveaux, 3 paliers visuels, quotas par palier de
  Bastion, bonus d'adjacence
- **Bastion 1→40** et ses trois verrous : coût, étoiles de campagne, Épreuve —
  avec un écran qui liste exactement ce qui manque
- **Bouton Améliorer** : toutes les améliorations payables en un tap
- **22 unités** entraînables instantanément
- **72 villages de campagne** + **8 Épreuves**, générés depuis une graine
- **Sauvegarde** versionnée, checksum, triple écriture

### Boucle tycoon (greffée sur le builder)
- **La production s'accumule au-dessus des bâtiments** et se ramasse au doigt.
  Un tap sur un bâtiment le récolte s'il a quelque chose à donner, sinon il
  ouvre sa fiche — un seul geste, jamais ambigu
- **Badges de récolte** en 2D au-dessus de chaque bâtiment, avec le montant, et
  en rouge clignotant quand l'entrepôt est plein
- **Bouton « Récolter »** global, et gains qui s'envolent à la collecte
- **Boutique de 6 améliorations permanentes** à coûts exponentiels
  (rendement, cadence, récolte auto, pillage, entrepôts, logistique)
- **Renaissance (rebirth)** : le village repart de zéro, la campagne est
  conservée, bonus définitif de +10 % par point
- **Gains d'absence** : la réserve se remplit hors-ligne (jusqu'à 2 h de
  production par bâtiment, extensible), à ramasser au retour

### Lisibilité
- **24 quêtes** enchaînées, avec bandeau permanent, jauge et récompense
- **Conseiller** « quoi faire maintenant ? », testé pour ne jamais être vide
- HUD : ressources avec lettre + couleur + plafond, actions fréquentes en gros
  boutons au pouce, barre du bas réservée aux destinations
- **Barres de vie** sur bâtiments et troupes pendant les raids, **nombres de
  dégâts** flottants, barres alliées en bleu pour distinguer les camps
- Sensibilité de caméra divisée par deux, zoom et rotation amortis

### Rendu
- **Cel-shading** maison (bandes quantifiées, ombres relevées et réchauffées,
  rim light) et **contours** par coque inversée
- **Occlusion ambiante cuite dans les sommets**
- Terrain en **île** : plateau constructible, marche, plage, falaise, eau
- Silhouettes **différenciées par bâtiment** (râtelier d'armes pour la caserne,
  scie pour la scierie, cheminée pour la forge…)
- Bastion refait en **château fort** : rempart crénelé, tours d'angle, herse
- Toujours **aucun asset externe** : tout est généré par code

### Qualité
| Suite | Résultat |
|---|---|
| Tests unitaires | **181 vérifications, 0 échec** |
| Tests d'intégration | **44 vérifications, 0 échec** |
| Build exporté | démarre sans erreur |
| Captures de rendu | générées et inspectées |

---

## Ce qui n'est pas construit

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

**Durée de jeu actuelle** : de l'ordre de 3 à 5 heures avant d'épuiser le
contenu — la campagne, la montée de Bastion, la boutique et un premier rebirth
tiennent. L'objectif de 10 h suppose la roadmap complète (surtout les
expéditions, les héros et le PvP).

---

## Bugs instructifs, corrigés

Notés parce qu'ils sont silencieux et coûteux à retrouver :

1. **Les CSV disparaissaient du build exporté.** Godot les importait comme
   fichiers de traduction. L'APK aurait démarré sur un jeu sans aucune donnée.
   Corrigé par `data/*.csv.import` avec `importer="keep"`, et un test de CI qui
   lance un build **exporté**.

2. **Toutes les normales étaient inversées.** Godot attend un enroulement
   horaire pour les faces avant. Tout restait visible (le culling ne dépend que
   de l'enroulement) mais le soleil n'éclairait plus rien.

3. **Couleurs de sommets en sRGB traitées comme du linéaire.** C'est ce qui
   donnait au jeu son aspect « fluo », et surtout pourquoi assombrir la palette
   ne changeait presque rien : l'erreur écrasait les corrections. Corrigé par
   une conversion `srgb_to_linear()` à la construction des maillages.

4. **Les taux de croissance de la boutique étaient en centièmes** là où
   `DataTables.grow` attend des millièmes : les coûts *baissaient* à chaque
   niveau. Attrapé par un test qui vérifiait que le prix monte.

5. **`next_pass` ne suit pas les instances** dans le renderer Compatibility :
   les contours ne s'affichaient pas. Remplacé par un second
   MultiMeshInstance qui partage la même ressource MultiMesh.

---

## Limites connues

- Pas de pathfinding : les unités vont en ligne droite et attaquent le mur qui
  les bloque.
- Les textes sont en dur dans le code.
- Aucune mesure de performance sur un vrai appareil bas de gamme.
- L'export Android n'a pas pu être exécuté dans l'environnement de
  développement (SDK Android inaccessible depuis le réseau) : c'est la CI qui
  produit et vérifie l'APK.
- Le contour double le nombre de draw calls des bâtiments. Toujours dans les
  budgets, mais c'est le premier réglage à sacrifier sur un appareil faible.
