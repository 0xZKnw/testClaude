# 02 — Boucles de jeu

## 1. Les 6 verbes

Chaque verbe est une boucle autonome avec son propre plaisir, sa propre durée, et une **sortie** qui devient l'**entrée** d'un autre verbe. C'est le graphe de dépendances qui empêche l'ennui.

```
                  ┌──────────────┐
        ┌────────►│  1. BÂTIR    │◄────────┐
        │         └──────┬───────┘         │
        │  ressources    │ débloque        │ or, plans
        │                ▼                 │
  ┌─────┴──────┐   ┌──────────────┐   ┌────┴───────┐
  │ 2. RAIDER  │──►│ 6. ADMINIST. │──►│ 5. FORGER  │
  └─────┬──────┘   └──────┬───────┘   └────┬───────┘
        │ trophées        │ villageois     │ équipement
        ▼                 ▼                ▼
  ┌────────────┐   ┌──────────────┐        │
  │ 3. DÉFENDRE│◄──│ 4. EXPLORER  │◄───────┘
  └────────────┘   └──────────────┘
```

---

### V1 — BÂTIR *(city builder)*
**Durée d'une action** : 3 à 15 secondes. **Boucle** : 2 à 6 minutes.
Poser, améliorer, déplacer, fusionner des bâtiments sur une grille isométrique 3D librement orientable.
- Construction **instantanée** : coût payé → animation de montage 2 à 6 s (poutres qui s'assemblent, poussière, *thunk* sonore) → bâtiment actif.
- Améliorations en un tap depuis un menu "tout améliorer ce qui est possible" (**Bouton Contremaître**) — anti-clic-fatigue, essentiel pour les sessions longues.
- **Adjacences** : poser une Scierie à côté d'une Forêt = +15 % ; deux Casernes adjacentes = -10 % coût de troupes. ~30 règles d'adjacence → le placement devient un puzzle d'optimisation, pas de la déco.
- **Sortie** : capacité de production, capacité défensive, déblocages.

### V2 — RAIDER *(combat temps réel, cœur du jeu)*
**Durée** : 90 à 180 secondes par raid. **Boucle** : 3 à 5 raids d'affilée.
Attaque d'un village PNJ (campagne) ou d'un snapshot de joueur (PvP async). Déploiement manuel des troupes autour du périmètre, sorts, capacités de héros.
- **Sortie** : 65 % du revenu total en ressources, trophées, fragments d'équipement.
- Rejouabilité : les villages de campagne ont **3 objectifs** (détruire à 50 % / 100 % / en moins de 60 s) → 3 passages incitatifs.

### V3 — DÉFENDRE *(tower defense inversé + puzzle de layout)*
**Durée** : 5 à 10 minutes par session d'édition.
Le joueur ne joue pas ses défenses en direct (async), mais :
- Regarde les **replays** des attaques subies (30 s chacun, skippable, très addictif — c'est le "notification bait" le plus sain qui existe).
- Édite son layout avec un outil sérieux : copier/coller, sauvegarde de 5 layouts, **mode simulation** où il attaque son propre village avec des armées types pour tester.
- **Défis de défense** : vagues PNJ scriptées qu'on peut lancer à la demande pour tester et gagner des ressources. C'est ici qu'on récupère un vrai gameplay tower-defense actif.

### V4 — EXPLORER *(carte du monde + roguelite léger)*
**Durée** : 4 à 8 minutes par expédition.
Une carte de la vallée, révélée par nœuds. On envoie une escouade sur un chemin de 5 à 9 nœuds. Chaque nœud = un choix :
combat · événement narratif à 2-3 options · marchand · relique · camp (soin) · élite · boss de région.
- Structure **Slay the Spire simplifiée** : on garde des bonus temporaires ("bannières") pendant l'expédition, on récupère le butin à la sortie.
- **Cooldown de 60–90 s** entre deux expéditions (seul délai du jeu) — pendant lequel les 5 autres verbes sont dispo.
- **Sortie** : ressources rares, plans de bâtiments, fragments de héros, reliques permanentes.

### V5 — FORGER *(craft actif)*
**Durée** : 20 à 60 secondes par pièce.
Mini-jeu de forge actif (timing/rythme sur 3 à 5 frappes, avec une zone parfaite qui rétrécit) → qualité de l'équipement produite. Pas de RNG pur : la skill compte pour ~40 % du résultat.
- Équipement pour les héros (6 slots) et les **régiments** (bonus d'armée).
- Système de **fusion** : 3 pièces identiques → 1 pièce de rang supérieur, avec choix de l'affixe conservé.
- **Sortie** : puissance de combat, ce qui reboucle sur V2/V4.

### V6 — ADMINISTRER *(sim de villageois légère)*
**Durée** : 2 à 4 minutes.
Les villageois arrivent (via Maisons), et on les **affecte** : production, garnison, expédition, forge, recherche.
- Chaque villageois a 2 traits parmi ~14 (Robuste, Rapide, Bricoleur, Peureux…) → optimisation légère, pas de micro-management pénible.
- **Événements de village** : festival, maladie, marchand ambulant, doléances. Petits choix avec conséquences chiffrées, toutes les ~10 min.
- **Recherche** : arbre de tech à 3 branches (Économie / Militaire / Ingénierie), 60 nœuds, avancé par les villageois affectés à la Bibliothèque. **Instantané au paiement**, comme tout le reste.

---

## 2. Les boucles temporelles

### Boucle 20 secondes — le micro-plaisir
Récolter un pop de ressource · poser un bâtiment · déployer une troupe · forger une frappe.
→ **Toujours** un retour audio + visuel. C'est le tissu du jeu.

### Boucle 3 minutes — le mini-objectif
Un raid complet, ou une amélioration significative, ou un nœud d'expédition.
→ Se termine par un **écran de récompense** avec des chiffres qui montent.

### Boucle 20 minutes — la « manche »
1. Ouvrir le jeu, récolter (30 s)
2. Lancer une expédition (elle tourne en fond)
3. Faire 3 raids (5 min)
4. Dépenser tout le butin en améliorations (2 min)
5. Récupérer l'expédition, choisir la récompense
6. Forger l'équipement obtenu
7. Un événement de village tombe → choix
→ Se termine par un **palier franchi** ou un déblocage. C'est la session minimale satisfaisante.

### Boucle 60 minutes — le « chapitre »
Le jeu est découpé en **chapitres de campagne** (voir doc 10). Un chapitre = ~8 villages PNJ + 1 Épreuve de Bastion + 1 boss de région + 2 à 4 nouveaux bâtiments débloqués.
→ Structure narrative légère avec un climax : le boss. Puis une phase de "digestion" (rebuild du village avec les nouveaux jouets) qui sert de récupération avant le chapitre suivant.

### Boucle 10 heures — la session marathon
C'est le vrai sujet. Ce qui tient 10h :

| Heure | Ce qui maintient l'intérêt |
|---|---|
| H0–H1 | Découverte, tutoriel intégré, déblocages toutes les 90 s |
| H1–H3 | Rythme de déblocage soutenu, premiers héros, première guilde |
| H3–H4 | **Premier changement de rythme** : ouverture de la carte du monde (V4) |
| H4–H5 | Ouverture du PvP + classement → nouvelle motivation |
| H5–H6 | Ouverture de la forge avancée + reliques |
| H6–H7 | **Événement dynamique** : invasion à repousser (mode horde 10 min) |
| H7–H8 | Boss de guilde, coop async |
| H8–H10 | Prestige de région, Endgame (voir plus bas) |

**Règles anti-fatigue codées en dur :**
- **Rotation forcée douce** : si le joueur enchaîne 5 raids d'affilée, un événement de village ou un bonus d'expédition apparaît pour le tirer ailleurs (jamais bloquant, juste incitatif).
- **Pics d'intensité programmés** toutes les ~35 min : boss, invasion, ou fenêtre de double butin de 5 min.
- **Vallées de calme** obligatoires après chaque pic : 5–8 min de gestion/placement, musique plus douce, pas de compte à rebours.
- **Aucune notification anxiogène** pendant que le joueur joue.

### Boucle semaine — la méta
Saison de 4 semaines (voir doc 06) : piste de récompenses, classement de guilde, modificateur de saison qui change les règles, région thématique.

---

## 3. La première session (les 10 premières minutes)

Le tutoriel est **intégré, jamais modal**. Objectif : à T+90 s le joueur a déjà attaqué quelque chose.

| T | Action |
|---|---|
| 0:00 | Écran noir → caméra qui descend sur une vallée. 3 cabanes. Un villageois parle (1 bulle, 6 mots). |
| 0:15 | « Pose une Scierie. » — la grille s'illumine, un seul emplacement valide, le joueur tape. Construction instantanée. **Premier wow : c'est instantané.** |
| 0:40 | Récolte du bois (tap). Pop, particules, chiffre qui monte. |
| 0:55 | « Une Caserne. » Idem. Une troupe sort. |
| 1:20 | Des bandits attaquent 2 cabanes voisines. « Va les chercher. » |
| 1:30 | **Premier raid** : village de 4 bâtiments, déploiement de 3 unités, victoire garantie en 40 s. |
| 2:10 | Écran de butin. Retour au village. Le butin permet 4 améliorations d'un coup. |
| 3:00 | Déblocage du Bastion niveau 2 → 3 nouveaux bâtiments. |
| 5:00 | Deuxième raid, cette fois avec un vrai choix de composition. |
| 7:00 | Premier héros rejoint (gratuit, scripté). |
| 9:00 | Carte du monde entrevue (verrouillée, visible → désir). Fin du tutoriel dirigé. |

**KPI cible** : 85 % de complétion du tutoriel, D1 retention ≥ 45 %.

---

## 4. Le « jamais bloqué » — la garantie

Le HUD contient en permanence un bouton **« Quoi faire ? »** qui liste les 3 meilleures actions disponibles maintenant, calculées par un scoreur simple :

```
score(action) = valeur_attendue × urgence × (1 − fatigue_verbe)
```

où `fatigue_verbe` monte de 0,15 par exécution consécutive du même verbe et décroît de 0,3 dès qu'un autre verbe est joué. Ce scoreur sert aussi à :
- piloter la rotation douce (§2),
- alimenter les notifications push hors-session,
- détecter en télémétrie les moments où le score max est bas → **ce sont les trous de contenu à combler.**

C'est le garde-fou central du pilier P1. Si le meilleur score tombe sous un seuil, on considère que le joueur est en attente → **bug de design à corriger.**
