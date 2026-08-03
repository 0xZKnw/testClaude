# 03 — Économie & progression

## 1. Les ressources

| Ressource | Rôle | Sources | Cap ? |
|---|---|---|---|
| **Bois** | Construction précoce, structures | Scierie, raid, expédition | Oui (entrepôt) |
| **Pierre** | Défenses, murs, bâtiments lourds | Carrière, raid | Oui |
| **Fer** | Militaire, forge, améliorations mid | Mine, raid, expédition | Oui |
| **Or** | Universel, recherche, réparations, troupes | Taxe des Maisons, raid PvP, boss | Oui |
| **Essence** | Rare : héros, reliques, paliers de Bastion | Boss, Épreuves, événements, saison | Non |
| **Cristal** *(premium)* | Boosts cosmétiques, slots, skip de rien du tout | Achat + **distribution gratuite généreuse** | Non |

Note importante : le Cristal **n'achète pas de temps** (il n'y en a pas à acheter). Voir doc 06 pour ce qu'il achète réellement.

### Répartition des revenus visée (joueur mid-game, par heure)

| Source | Part |
|---|---|
| Butin de raid (campagne + PvP) | 45 % |
| Expéditions | 20 % |
| Production passive des bâtiments | 20 % |
| Événements / quêtes / défis de défense | 15 % |

La production passive est volontairement **minoritaire** : elle sert de filet pour le joueur qui revient, pas de moteur. Elle sature son cap en ~6 h hors-ligne — au-delà, aucune perte de valeur ressentie n'est acceptable, donc l'excédent tombe dans une **Réserve du Régent** (récupérable jusqu'à 24 h, avec rendement dégressif). On ne punit jamais l'absence, on récompense juste la présence bien plus fort.

---

## 2. Les formules

### Coût d'amélioration d'un bâtiment
```
coût(n) = base × 1.34^(n-1) × mult_catégorie
```
- `n` = niveau cible (1 → 15 max par bâtiment)
- `mult_catégorie` : Production 1.0 · Défense 1.25 · Militaire 1.15 · Spécial 1.6

### Rendement de production
```
prod(n) = base_prod × 1.22^(n-1)
```
Croissance du coût (1.34) > croissance du rendement (1.22) → **le passif décroche volontairement**, ce qui force le retour au raid. C'est le levier n°1 pour empêcher l'idle-farming.

### Capacité de stockage
```
cap(n) = 800 × 1.40^(n-1)   (par entrepôt, cumulable)
```

### Butin de raid
```
butin = min(stock_cible × taux_pillage, cap_butin(bastion_attaquant))
taux_pillage = 0.18 (PvP) | 0.55 (campagne PNJ)
bonus_étoiles = ×1.0 / ×1.35 / ×1.8   (1/2/3 étoiles)
```
Le `×1.8` à 3 étoiles est agressif **exprès** : rejouer un raid pour le perfectionner doit être clairement rentable. C'est là que vit le pilier « le gate c'est la compétence ».

### Puissance de combat (pour matchmaking et calibrage)
```
CP = Σ(niveau_troupe × poids) + Σ(CP_héros) + Σ(CP_équipement) + 0.4 × Σ(niveau_défenses)
```

---

## 3. Les 40 paliers de Bastion

Le Bastion (hôtel de ville) est le compteur maître. **40 niveaux**, groupés en 8 chapitres de 5.

Pour monter d'un palier il faut **3 conditions cumulatives** :
1. **Coût en ressources** (formule ci-dessus, `mult` = 2.2 — c'est cher)
2. **Prérequis structurel** : ex. « 4 bâtiments de production au niveau ≥ N-1 »
3. **Épreuve du Bastion réussie** — un défi de combat unique et scénarisé, rejouable à volonté sans coût

L'Épreuve est le cœur du système. Exemples :
- **Bastion 5** — « Le Siège » : détruire un village avec seulement 8 unités.
- **Bastion 11** — « Le Défilé » : défense pure, tenir 12 vagues avec un budget de tours limité.
- **Bastion 18** — « À l'aveugle » : raid sans voir les défenses avant de déployer (une passe de reconnaissance de 10 s d'abord).
- **Bastion 26** — « Le Miroir » : attaquer une copie exacte de ton propre village.
- **Bastion 33** — « Un seul héros » : boss solo, gestion de cooldowns.
- **Bastion 40** — « Le Régent » : gauntlet de 4 combats enchaînés sans réparation.

**Anti-frustration** : après 3 échecs sur une même Épreuve, le jeu propose (sans l'imposer) un **modificateur d'assistance** (+15 % de dégâts, cumulable jusqu'à 3 fois) et un **conseil ciblé** basé sur la cause de l'échec détectée en télémétrie. La récompense reste identique — on ne punit pas le joueur qui a besoin d'aide, on veut juste qu'il continue.

### Répartition des paliers

| Chapitre | Bastion | Déblocages majeurs | Durée cible |
|---|---|---|---|
| 1 — La Vallée | 1–5 | Bases, 1er héros, campagne | 45 min |
| 2 — Les Bois | 6–10 | Carte du monde (V4), forge | 55 min |
| 3 — La Rivière | 11–15 | PvP async, guildes, murs | 60 min |
| 4 — Les Mines | 16–20 | Recherche, régiments, reliques | 70 min |
| 5 — Les Cimes | 21–25 | 2e héros, défis de défense, artefacts | 75 min |
| 6 — La Faille | 26–30 | Invasions, boss de guilde | 80 min |
| 7 — La Citadelle | 31–35 | Spécialisations, sièges | 85 min |
| 8 — Le Trône | 36–40 | Endgame, prestige de région | 90 min |

**Total campagne principale : ~9h30.** Le reste (10h+) vient du contenu latéral, du PvP et de l'endgame — détail exact en doc 10.

---

## 4. Rythme des récompenses

Cadence cible, mesurée en télémétrie :

| Type | Fréquence cible |
|---|---|
| Micro (pop de ressource, tap) | toutes les 8–20 s |
| Petite (amélioration, unité, pièce) | toutes les 60–90 s |
| Moyenne (nouveau bâtiment, niveau de héros) | toutes les 6–10 min |
| Grande (palier de Bastion, nouveau système) | toutes les 45–60 min |
| Majeure (chapitre, région, saison) | toutes les 1h30–4h |

**Règle des 90 secondes** : le joueur ne doit jamais passer plus de 90 s sans une récompense "petite" ou plus. Si la télémétrie montre des trous > 150 s, c'est un défaut de calibrage à corriger.

---

## 5. Anti-inflation et endgame

Le danger d'un jeu sans timer, c'est que le joueur épuise le contenu. Trois soupapes :

### 5.1 — Le Prestige de Région (débloqué Bastion 30)
Le joueur peut « coloniser » une nouvelle région : il repart d'un village vierge, mais garde héros, recherche, reliques et un **multiplicateur permanent** (+8 % par région, cumulatif). Chaque région a un **modificateur de terrain** qui change les règles :
- *Marais* : production −25 %, mais butin de raid +50 %
- *Toundra* : pas de bois, la pierre remplace tout
- *Volcan* : les bâtiments prennent des dégâts passifs, la forge est 2× plus efficace
- *Archipel* : grille fragmentée en îlots, l'adjacence devient un puzzle serré

C'est le vrai contenu infini, et il est **cheap à produire** (réutilise 90 % des assets, change les paramètres).

### 5.2 — Les Ligues PvP
Après Bastion 15, classement en 9 ligues. Le contenu généré par les joueurs (leurs layouts) est infini et gratuit à produire.

### 5.3 — Les Défis Hebdomadaires
Contraintes de raid tournantes générées par un template (12 templates × 6 modificateurs = 72 combinaisons) : « pas de héros », « 2× vitesse », « une seule unité type », « le village se répare »… Récompenses en Essence et en cosmétiques.

---

## 6. Table de calibrage (extrait, Bastion 1→10)

| Bastion | Coût Or | Coût Bois | Coût Pierre | CP village attendu | Temps cumulé |
|---|---|---|---|---|---|
| 2 | 400 | 600 | 200 | 90 | 4 min |
| 3 | 900 | 1 300 | 500 | 180 | 10 min |
| 4 | 2 000 | 2 800 | 1 200 | 340 | 20 min |
| 5 | 4 300 | 6 000 | 2 800 | 620 | 45 min |
| 6 | 9 200 | 12 500 | 6 200 | 1 100 | 1h10 |
| 7 | 19 000 | 25 000 | 13 000 | 1 900 | 1h25 |
| 8 | 39 000 | 51 000 | 27 000 | 3 200 | 1h40 |
| 9 | 78 000 | 102 000 | 56 000 | 5 300 | 1h55 |
| 10 | 155 000 | 203 000 | 114 000 | 8 700 | 2h15 |

Ces valeurs sont un **point de départ à valider en playtest**, pas une vérité. Toutes les constantes vivent dans des ScriptableObjects / un CSV hot-reloadable (voir doc 08) : rééquilibrer ne doit jamais demander une recompilation.
