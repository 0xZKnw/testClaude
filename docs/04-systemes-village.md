# 04 — Systèmes du village

## 1. La grille

- **Grille carrée 44×44** au Bastion 1, extensible jusqu'à **80×80** au Bastion 40 (achat de parcelles avec de la Pierre + Essence).
- Vue **3/4 isométrique perspective légère** (FOV 28°), rotation libre par pas de 45° + rotation continue au drag à deux doigts, zoom pinch entre 12 et 55 unités de distance.
- Bâtiments de 1×1 à 5×5. Chemins auto-générés (auto-tiling) entre les bâtiments → le village a l'air vivant sans travail du joueur.
- **Décors libres** : arbres, rochers, statues, fontaines, drapeaux — coût dérisoire, pur plaisir de déco. Les joueurs qui décorent restent.

### Placement — feel
- Drag & drop avec **snap magnétique**, prévisualisation en vert/rouge, halo d'effet d'adjacence affiché en temps réel.
- **Déplacement gratuit et illimité** de tout bâtiment, à tout moment. Aucune raison de le pénaliser.
- **Mode Édition** : grille visible, tout devient déplaçable, undo/redo sur 30 actions, et le fameux copier/coller de layout.

---

## 2. Les 46 bâtiments

### Production (9)
| Bâtiment | Taille | Rôle | Débloqué |
|---|---|---|---|
| Scierie | 3×3 | Bois | B1 |
| Carrière | 3×3 | Pierre | B2 |
| Mine de fer | 3×3 | Fer | B5 |
| Maison | 2×2 | Villageois + taxe en Or | B1 |
| Ferme | 3×2 | Nourriture (entretien des troupes) | B3 |
| Puits d'essence | 2×2 | Essence lente | B14 |
| Moulin | 3×3 | Convertit Bois→Or | B8 |
| Fonderie | 4×3 | Convertit Fer→Acier (forge) | B12 |
| Grand Marché | 4×4 | Échange entre ressources, taux variable quotidien | B16 |

### Stockage (4)
Entrepôt · Grenier · Coffre-fort (Or, protégé du pillage à 100 %, cap faible) · Réserve du Régent (récupération hors-ligne)

### Militaire (10)
Caserne · Camp d'archers · Écurie · Atelier de siège · Terrain d'entraînement (XP de troupes) · Tente de héros ×2 · Forge · Autel des reliques · Quartier général (composition d'armée)

### Défense (14)
Tour d'archers · Canon · Mortier · Tour à foudre · Piège à pointes · Piège bondissant · Mine · Tour d'inferno · Balliste · Tour de soin (défensive) · Mur (segments) · Portail · Beffroi (alerte, +vision) · Sanctuaire (bonus de zone)

### Spécial (9)
Bastion · Bibliothèque (recherche) · Hall de guilde · Portail d'expédition · Statue du Régent (bonus global) · Taverne (recrutement de villageois d'élite) · Observatoire (voir les attaques entrantes / analytics du joueur) · Sanctuaire de saison · Obélisque de prestige

**Chaque bâtiment : 15 niveaux**, avec **3 changements visuels de mesh** (niv. 1, 6, 11) — pas 15 meshes, ce serait ruineux. Les niveaux intermédiaires changent des détails ajoutés (props, bannières, échafaudages permanents, matériaux) via des sous-objets activés par script.

---

## 3. Le système d'adjacence (le "puzzle" du builder)

C'est ce qui transforme le placement en gameplay. ~30 règles, toutes lisibles d'un coup d'œil via un overlay.

Exemples :
| Règle | Effet |
|---|---|
| Scierie adjacente à ≥3 tuiles de Forêt | +15 % bois |
| 2 Casernes adjacentes | −10 % coût de troupes |
| Maison adjacente à une Ferme | +1 villageois max |
| Maison adjacente à une Mine | −20 % taxe (les gens n'aiment pas le bruit) |
| Tour d'archers dans le rayon d'un Beffroi | +12 % portée |
| Mortier entouré de ≥6 murs | +20 % dégâts |
| Forge adjacente à la Fonderie | +1 qualité garantie au craft |
| Statue dans un rayon de 5 | +5 % production de tout le rayon |
| Sanctuaire de saison au centre du village | bonus de saison ×1.5 |

**Overlay d'adjacence** : un bouton affiche des liens lumineux entre bâtiments synergiques et des croix rouges sur les anti-synergies. Lisible en 1 seconde (pilier P4).

---

## 4. Villageois

- Population = f(Maisons). De 4 (B1) à ~180 (B40).
- Chaque villageois : nom généré, **2 traits** parmi 14, un niveau (1→10, monte en travaillant).
- **Affectation** : glisser un villageois sur un bâtiment. Un bâtiment sans villageois tourne à 40 % de rendement.
- **Traits** : Robuste (+HP en garnison) · Rapide (+vitesse de récolte) · Bricoleur (+craft) · Érudit (+recherche) · Chanceux (+chance de butin) · Peureux (−garnison, +production) · Insomniaque (produit hors-ligne) · Cuisinier · Mineur né · Charismatique (+recrutement) · Vétéran · Superstitieux · Ambidextre · Têtu.
- **Taverne** : recrutement de villageois d'élite (3 traits, niveau de départ 3) contre de l'Or + Essence. Rotation de 3 candidats toutes les 15 min de jeu.
- **Auto-affectation intelligente** : un bouton « Optimiser » qui place les villageois au mieux selon un solveur glouton. Obligatoire pour les sessions longues — le micro-management manuel devient de la corvée après 2h.

### Événements de village
Toutes les ~10 min de jeu, un événement avec 2–3 choix chiffrés et une conséquence claire :
- *Festival* : −500 Or → +20 % production pendant 10 min
- *Doléance* : un villageois demande une Maison de niveau supérieur, sinon −1 moral
- *Marchand ambulant* : offre un troc à taux inhabituel, 90 s pour décider
- *Épidémie* : 3 villageois inactifs 3 min, ou payer 200 Essence
- *Déserteur* : un villageois d'un village raidé demande l'asile → recrue gratuite

~40 événements écrits, pondérés par le contexte (pas de festival si le joueur est fauché).

---

## 5. Défense & layout

- Le village du joueur est **snapshotté** régulièrement (voir doc 05) et sert de cible PvP.
- **5 slots de layout** sauvegardables + import/export par **code texte** (énorme pour la communauté, coût de dev quasi nul).
- **Mode Simulation** : le joueur attaque son propre village avec une des 8 armées types de l'IA. Résultat chiffré : « ton layout tombe à 2 étoiles contre le rush d'archers ». C'est un outil, et c'est aussi du gameplay.
- **Bouclier de reconstruction** : après une défaite, 20 min de jeu sans pouvoir être attaqué (temps de jeu, pas temps réel — cohérent avec le pilier P1).

---

## 6. Recherche

Arbre à 3 branches, **60 nœuds**, payé en Or + Essence + points de recherche (générés par les villageois en Bibliothèque). **Effet instantané au paiement.**

- **Économie (20)** : rendements, caps, taux de pillage, adjacences renforcées, réduction de coûts
- **Militaire (22)** : dégâts, HP, vitesse, capacités d'unités, slots de composition, capacités de héros
- **Ingénierie (18)** : défenses, murs, pièges, bonus de forge, taille de grille, qualité de craft

Contrainte de choix : **on ne peut pas tout prendre avant Bastion 35**. Les nœuds terminaux de chaque branche sont mutuellement exclusifs par paires (ex. « +25 % dégâts de siège » OU « les murs coûtent 50 % moins » ) → identité de build, rejouabilité en prestige.

---

## 7. Le Bouton Contremaître

Détail petit mais critique pour les sessions de 10h : un bouton qui liste **toutes les améliorations actuellement payables**, triées par ratio valeur/coût, avec un « tout améliorer » en un tap et une confirmation récapitulative.

Sans lui, le joueur fait 400 taps par heure pour améliorer des murs. Avec lui, il en fait 4 et garde son attention pour le gameplay intéressant. **C'est la feature qui rend le zéro-timer viable sur la durée.**
