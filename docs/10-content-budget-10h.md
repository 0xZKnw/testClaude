# 10 — Budget de contenu : où sont les 10 heures

Le but n'est pas d'*étirer* 10 heures, c'est d'en **remplir** 10. Voici le compte, poste par poste.

---

## 1. Le compte

| Poste | Contenu | Heures |
|---|---|---|
| **Campagne principale** | 8 chapitres × ~8 villages + 8 Épreuves + 8 boss | **9h30** |
| **Rejouabilité des raids (3 étoiles)** | ~40 % des villages refaits pour l'objectif spécial | +2h00 |
| **Expéditions** | 24 chemins uniques × 5 min, rejouables avec bannières différentes | +3h00 |
| **Gestion / construction / layout** | Réparti dans le tout, mesuré en playtest | +2h30 |
| **Défis de défense & invasions** | 10 vagues × 8 déblocages + 6 invasions | +1h30 |
| **Forge & équipement** | Craft, fusion, optimisation des 6 slots × 3 héros | +1h00 |
| **PvP async** | Infini par nature, compté pour la première montée en ligue | +2h00 |
| **Boss de guilde** | 3 attaques/jour, infini | +1h00 |
| **Recherche** | 60 nœuds, choix, respec | +0h45 |
| **Défis hebdomadaires** | 72 combinaisons de modificateurs | infini |
| **Saisons** | 12 villages + boss + événement, toutes les 4 semaines | +4h / saison |
| **Prestige de région** | 4 modificateurs de terrain, chacun rejoue la campagne différemment | +8h / région |

**Total « première traversée » : ~23 heures.**
**Total avec endgame et saisons : sans limite pratique.**

L'objectif de « 10h minimum » est dépassé d'un facteur 2 sur le contenu solo seul. C'est volontaire : les estimations de durée de jeu sont **systématiquement optimistes** de 30 à 40 % en pré-production. On vise 23h pour en livrer 12–15 réelles.

---

## 2. Détail par chapitre

| Ch. | Nom | Bastion | Villages | Nouveau système introduit | Boss | Durée |
|---|---|---|---|---|---|---|
| 1 | La Vallée | 1–5 | 6 | Raid, construction, économie | Chef bandit | 45 min |
| 2 | Les Bois | 6–10 | 8 | **Carte du monde + expéditions**, forge | Ours-sylvain | 55 min |
| 3 | La Rivière | 11–15 | 8 | **PvP + guildes**, murs, pièges | Corsaire | 60 min |
| 4 | Les Mines | 16–20 | 9 | **Recherche**, régiments, reliques | Golem de fer | 70 min |
| 5 | Les Cimes | 21–25 | 9 | 2e et 3e héros, **défis de défense** | Roc des cimes | 75 min |
| 6 | La Faille | 26–30 | 10 | **Invasions**, boss de guilde | Seigneur de la Faille | 80 min |
| 7 | La Citadelle | 31–35 | 10 | Spécialisations, sièges | Grand Maréchal | 85 min |
| 8 | Le Trône | 36–40 | 12 | **Prestige**, endgame | Le Régent Déchu | 90 min |

**Total : 72 villages de campagne.**

### Coût de production d'un village de campagne
Avec l'éditeur interne (doc 09, M2) : **25 à 40 minutes** par village (layout + composition défensive + objectifs + test). 72 villages ≈ 40 heures de travail. C'est pour ça que l'éditeur de niveaux est prioritaire — sans lui, c'est 4× plus.

---

## 3. Rythme d'introduction des systèmes

La règle : **jamais deux systèmes majeurs en moins de 40 minutes**, et jamais plus de 55 minutes sans nouveauté. Le joueur doit toujours être en train d'apprendre quelque chose, sans être submergé.

```
H0    H1    H2    H3    H4    H5    H6    H7    H8    H9   H10
│     │     │     │     │     │     │     │     │     │     │
▼     ▼     ▼     ▼     ▼     ▼     ▼     ▼     ▼     ▼     ▼
Raid  Héros Carte Forge PvP  Guilde Rech. Défis Inva- Boss  Prestige
Build Adjac Expéd       Ligue Dons  Reliq  déf.  sion  guilde Région
```

---

## 4. Le contenu écrit

| Type | Volume | Notes |
|---|---|---|
| Événements de village | 40 × ~60 mots | Ton léger, second degré |
| Événements d'expédition | 40 × ~80 mots | 2–3 choix chacun |
| Dialogues de chapitre | 8 × ~300 mots | Courts, jamais bloquants, skippables |
| Descriptions (bâtiments, unités, sorts, reliques) | ~200 entrées × 20 mots | Une blague sur trois |
| Barks de villageois | 120 × 8 mots | Ce qui donne l'âme au village |
| Textes d'UI, tutoriels, quêtes | ~600 chaînes | |

**Total ≈ 22 000 mots.** Volume raisonnable, à écrire en une passe dédiée pendant M2, puis à relire à voix haute (test de fatigue : un texte qu'on ne supporte pas de relire, le joueur ne le supportera pas au 40e passage).

---

## 5. Les 20 quêtes structurantes

En plus de la campagne, un fil de quêtes qui donne des objectifs moyens (~15 min chacun) et qui **force la rotation entre verbes** :

1. Construis 5 bâtiments de production
2. Gagne un raid à 3 étoiles
3. Affecte 4 villageois
4. Termine une expédition sans perdre d'unité
5. Forge une pièce de qualité Rare
6. Déclenche 3 bonus d'adjacence simultanés
7. Repousse un défi de défense de 10 vagues
8. Atteins la ligue Argent
9. Rejoins une guilde et fais un don
10. Débloque 10 nœuds de recherche
11. Monte un héros au niveau 20
12. Survis à une Invasion sans perdre de bâtiment
13. Bats un boss avec un seul type d'unité
14. Fais 500 000 de dégâts au boss de guilde
15. Sauvegarde 3 layouts différents
16. Gagne 10 raids PvP d'affilée
17. Fusionne une pièce en Épique
18. Complète une expédition avec 5 bannières
19. Atteins Bastion 30
20. Fonde ta deuxième région

Chacune donne de l'Essence, des Cristaux et un cosmétique. Elles restent visibles en permanence dans un panneau — c'est l'effet Zeigarnik (doc 06 §1) qui travaille en continu.

---

## 6. Vérification : la session de 10h est-elle réellement tenable ?

Simulation d'une session marathon, avec la rotation des verbes et les pics d'intensité :

| Tranche | Verbe dominant | Intensité | Nouveauté |
|---|---|---|---|
| 0:00–0:45 | Bâtir + Raider | ▂▄▆ | Tout |
| 0:45–1:40 | Raider + Explorer | ▄▆▄ | Carte du monde |
| 1:40–2:40 | Raider + Défendre | ▆█▄ | PvP, murs |
| 2:40–3:50 | Administrer + Forger | ▂▄▂ | **Vallée de calme** — Recherche |
| 3:50–5:05 | Raider + Explorer | ▄▆█ | Héros, boss Roc |
| 5:05–6:25 | Défendre (Invasion) | █▆█ | **Pic** |
| 6:25–7:00 | Bâtir | ▂▂▄ | **Vallée** — refonte du village |
| 7:00–8:25 | Raider + Guilde | ▆█▆ | Boss de guilde |
| 8:25–9:55 | Tout | █▆█ | Endgame, Régent Déchu |
| 9:55–… | Prestige | ▂▄▆ | **Redémarrage** — nouvelle région, nouvelles règles |

Les alternances pic/vallée et le changement de verbe toutes les ~50 minutes sont ce qui rend la durée tenable. Le prestige à H10 est le mécanisme qui relance une nouvelle session de 10h avec des règles différentes.

---

## 7. Ce qu'il faut mesurer en playtest

- **Temps réel** entre chaque récompense « moyenne » ou plus → doit rester sous 10 min
- **Temps réel** passé sans action possible → doit être **zéro** (pilier P1)
- Moment d'abandon des testeurs, et le verbe joué juste avant
- Nombre de raids rejoués volontairement → mesure directe du pilier P2 (le gate = la compétence)
- Ressenti de fatigue déclaré toutes les 90 min (échelle 1–5) → si > 3 avant H6, la rotation des verbes est mal calibrée
