# 09 — Roadmap & production

Estimations pour **1 développeur assisté par IA**, à temps plein. Diviser par ~1,8 pour une équipe de 3 (1 dev, 1 artiste, 1 game designer).

---

## M0 — Fondations *(semaines 1–2)*

**But** : le squelette technique, pas de gameplay.

- [ ] Projet Unity 6 + URP mobile + Git LFS + `.gitignore` Unity propre
- [ ] Structure `Valdris.Core` / `Valdris.Unity` / `Valdris.Tools` (assemblies séparées)
- [ ] Grille 44×44, caméra isométrique (pan, pinch-zoom, rotation), gestes tactiles
- [ ] Placement d'un cube sur la grille, snap, validation
- [ ] Pipeline CSV → ScriptableObject
- [ ] **Build APK sur device dès la semaine 2** — ne jamais attendre pour tester sur téléphone

**Livrable** : un APK où on pose des cubes sur une grille, à 60 fps.

---

## M1 — Vertical slice *(semaines 3–8)*

**But** : la boucle complète en petit, mais avec le *feel* final. C'est le jalon qui valide ou tue le concept.

- [ ] 8 bâtiments réels (Scierie, Carrière, Maison, Entrepôt, Caserne, Tour d'archers, Mur, Bastion)
- [ ] **Effet de construction instantanée poli à fond** (doc 07 §5) — la signature du jeu
- [ ] Économie : 3 ressources, production, stockage, coûts, Bouton Contremaître
- [ ] Simulation de combat déterministe (fixed-point, flow field, spatial hash) avec 3 unités
- [ ] Raid jouable contre 3 villages PNJ, écran de butin, étoiles
- [ ] Bastion 1→5 avec la première Épreuve
- [ ] Sauvegarde locale versionnée
- [ ] Audio de base + juice complet
- [ ] Onboarding des 10 premières minutes (doc 02 §3)

**Livrable** : un APK de 30 minutes de jeu qu'on peut faire tester à 10 personnes.

**Porte de décision** : si les testeurs ne disent pas spontanément « ah, c'est instantané ? » et ne rejouent pas un raid raté, il faut revoir le design avant de continuer. Ne pas passer M1 sans cette validation.

---

## M2 — Contenu & profondeur *(semaines 9–20)*

- [ ] 46 bâtiments, 15 niveaux, 3 paliers visuels (le gros du travail art)
- [ ] Système d'adjacence + overlay
- [ ] Villageois, traits, affectation, auto-optimisation
- [ ] 18 unités + 8 sorts + Terrain d'entraînement
- [ ] 3 premiers héros + équipement + forge (mini-jeu actif)
- [ ] Carte du monde + expéditions + 30 bannières + 40 événements de nœud
- [ ] Recherche : 60 nœuds
- [ ] Bastion 1→25, chapitres 1 à 5
- [ ] Éditeur de niveaux interne (`Valdris.Tools`) — **à faire tôt**, il conditionne le rythme de production de contenu
- [ ] Simulateur d'équilibrage headless

**Livrable** : ~6h de jeu solo cohérentes.

---

## M3 — Social & PvP *(semaines 21–28)*

- [ ] Backend (Supabase), auth, cloud save
- [ ] Snapshots de village, matchmaking par CP, fallback PNJ
- [ ] Replays + validation serveur des combats
- [ ] Trophées, 9 ligues, classements
- [ ] Guildes : 30 membres, dons, hall, chat prédéfini
- [ ] Boss de guilde
- [ ] Défis de défense + mode Invasion
- [ ] Bastion 26→40, chapitres 6 à 8

**Livrable** : le jeu complet, ~10h+ de contenu.

---

## M4 — Live ops & polish *(semaines 29–34)*

- [ ] Système de saison (piste, modificateur, région)
- [ ] Saison 1 produite intégralement
- [ ] IAP + boutique + pubs récompensées opt-in
- [ ] Analytics complet + le graphe « score max disponible » (doc 02 §4)
- [ ] Localisation FR/EN, puis DE/ES
- [ ] Prestige de région + endgame
- [ ] Passe de perf : atteindre les budgets du doc 07 §2 sur le device plancher
- [ ] Passe d'accessibilité : daltonisme, taille de texte, réduction des animations, mode une main

---

## M5 — Soft launch *(semaines 35–38)*

- [ ] Sortie sur 2–3 marchés secondaires (Canada, Philippines, Pologne)
- [ ] Mesure des KPI du doc 06 §6, **en particulier le % de sessions > 2h**
- [ ] 2 à 4 itérations d'équilibrage via remote config
- [ ] Correction du top 10 des crashs
- [ ] Test A/B de l'onboarding

**Porte de décision** : D1 ≥ 40 % et sessions > 2h ≥ 6 % → on lance. Sinon, on itère avant de brûler le budget marketing.

---

## M6 — Lancement mondial *(semaine 39+)*

- [ ] Fiche Play Store : 8 captures, vidéo de 30 s dont **les 3 premières secondes montrent la construction instantanée**
- [ ] ASO : cibler « base builder no timers », « village game offline »
- [ ] Communauté : Discord, sous-reddit, partage de layouts par code
- [ ] Rythme de saison : une saison toutes les 4 semaines
- [ ] iOS 6 à 10 semaines après

---

## Répartition de l'effort

| Poste | Part | Notes |
|---|---|---|
| Programmation gameplay | 35 % | |
| Art 3D + UI | 25 % | Réductible à 12 % avec des packs achetés |
| Design de contenu (niveaux, événements, textes) | 15 % | Dépend de l'éditeur interne |
| Backend & infra | 8 % | |
| Équilibrage & playtest | 10 % | Sous-estimé par tout le monde, toujours |
| Store, légal, localisation | 7 % | |

---

## Risques et parades

| Risque | Gravité | Parade |
|---|---|---|
| **Scope creep** — 46 bâtiments, 18 unités, 8 héros, c'est énorme | Critique | Tout est priorisé P0/P1/P2. Le P2 saute sans négociation si M2 dérape. Le vertical slice reste sacré. |
| Sans timers, le joueur finit le contenu trop vite | Élevé | Prestige de région (contenu infini bon marché) + PvP + saisons. À surveiller dès le soft launch. |
| Le déterminisme casse en cours de route | Élevé | Suite de 200 replays en CI dès M1. Non négociable. |
| Perf : 300 bâtiments + 200 unités sur un téléphone bas de gamme | Élevé | Architecture data-oriented dès M1, palette-atlas, TickManager. Tester sur le device plancher **chaque semaine**. |
| Coût de production art | Moyen | Kenney (CC0) pour le slice, palette maison ensuite. Pipeline Blender scripté. |
| Un dev solo abandonne à M2 (le mur du contenu) | Moyen | Éditeur de niveaux dès le début, génération assistée par IA du contenu texte, jalons courts et livrables. |
| Modération / chat / RGPD | Moyen | Pas de chat libre en v1, analytics anonymisées, consentement explicite, politique de confidentialité prête avant M5. |
| Perte du keystore de signature | Faible mais fatal | Sauvegarde chiffrée en 2 lieux distincts dès M0 |

---

## Priorisation P0 / P1 / P2

**P0 — sans ça, le jeu n'existe pas**
Grille + placement · construction instantanée + son feel · économie 3 ressources · raid déterministe · Bastion 1→20 · 20 bâtiments · 8 unités · 1 héros · sauvegarde · onboarding.

**P1 — sans ça, le jeu est mince**
Expéditions · forge · villageois · adjacences · recherche · PvP async · guildes · 46 bâtiments · 18 unités · 3 héros · Bastion 21→40.

**P2 — le luxe, coupable en premier**
Prestige de région · 8 héros · défis hebdo · guerres de guilde · cycle jour/nuit · thèmes cosmétiques · iOS.

*(Note : le cycle jour/nuit est en P2 par prudence de scope, mais son ratio impact/coût est si bon qu'il vaut la peine de le remonter dès qu'un créneau se libère.)*
