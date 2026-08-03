# 07 — Direction artistique

## 1. Le style : low poly « palette-atlas »

Le choix technique et le choix esthétique sont **le même choix**. Le style low poly retenu est celui qui permet à un village entier de 300 bâtiments de tourner en **3 à 8 draw calls** sur un téléphone à 150 €.

### Le principe
- **Aucune texture UV classique.** Tous les meshes partagent une seule texture-palette de **256×256 px** (une grille de 16×16 aplats de couleur).
- Les UV de chaque face pointent vers **un seul pixel** de cette palette.
- Conséquence : **un seul material pour tout le jeu** (ou 3 : opaque / transparent / émissif) → GPU Instancing + SRP Batcher fusionnent tout.
- Changer la charte graphique du jeu = redessiner une image de 256×256. Changement de saison = swap de palette, coût nul.

### Look
- Formes **anguleuses assumées**, pas de smooth shading sauf sur les rondeurs volontaires (dômes, arbres).
- **Silhouettes fortes et distinctes** : on doit identifier une Caserne d'un Entrepôt à 3 mètres de l'écran, sans lire de texte.
- Couleurs saturées mais harmonisées : palette de base de 48 teintes, dérivée d'un accord chaud (bois/terre/or) contre un accent froid (bleu essence).
- **Contour** : pas d'outline post-process (coûteux). À la place, un léger **rim light** dans le shader et une occlusion ambiante peinte dans les vertex colors.

---

## 2. Budgets techniques

| Élément | Triangles | Notes |
|---|---|---|
| Bâtiment 1×1 | 150–400 | |
| Bâtiment 3×3 | 500–1 200 | |
| Bastion (5×5, niv. max) | ≤ 3 000 | Pièce maîtresse, on peut se lâcher |
| Unité | 250–600 | |
| Héros | 900–1 500 | |
| Arbre / rocher / prop | 40–150 | Instanciés en masse |
| **Scène complète (village max)** | **≤ 450 000 tris** | |

| Métrique | Budget |
|---|---|
| Draw calls (village complet) | ≤ 60, cible 20 |
| SetPass calls | ≤ 12 |
| Framerate | 60 fps cible / 30 fps plancher garanti |
| RAM | ≤ 900 Mo sur device 3 Go |
| Taille APK (base AAB) | ≤ 150 Mo, reste en Addressables |

---

## 3. Éclairage

- **Une seule directional light** (le soleil), ombres en cascade unique, distance d'ombre 40 m.
- **Baked GI : non** (le village change tout le temps). À la place : **occlusion ambiante en vertex color** cuite dans le mesh au moment de l'export, plus une SH ambiante simple.
- **Cycle jour/nuit** sur 24 min de jeu : c'est gratuit (rotation de la lumière + lerp de 4 couleurs + skybox gradient), et ça donne un énorme sentiment de vie sur une session longue. Les fenêtres s'allument la nuit (émissif via la palette). **Feature à fort ratio impact/coût — à faire tôt.**
- Fog exponentiel léger pour la profondeur, couleur pilotée par l'heure.

---

## 4. Animation

- Bâtiments : **pas de skinning**. Animations par script/DOTween sur les transforms (rotation de moulin, fumée, bannières via shader vertex sinus). Coût CPU quasi nul.
- Unités : **skinning simple**, 12 à 20 os max, 6 animations (idle, marche, attaque ×2, dégât, mort). Utiliser l'**Animator optimisé** ou, mieux, du **vertex animation texture (VAT)** pour les unités en masse → animation gratuite en GPU, permet 200+ unités simultanées.
- Héros : rig plus riche (28 os), 12 animations, dont 2 capacités spectaculaires.
- **Juice** obligatoire partout : squash & stretch sur les impacts, easing `OutBack` sur les apparitions d'UI, screen shake court (< 0,12 s) sur les explosions, hit-stop de 40 ms sur les coups de héros.

---

## 5. Effets

- Particules : **VFX simples en shader** plutôt que Shuriken quand c'est possible. Budget 6 systèmes actifs max hors combat, 20 en combat.
- Fumée/poussière : quads billboardés avec un noise animé — pas de textures fumée réalistes, ça casse le style.
- **Effet de construction** : la pièce maîtresse du feel. Le bâtiment apparaît en 2 à 6 s : sol qui s'aplanit → échafaudage → pièces qui tombent du ciel avec un léger overshoot → poussière → *thunk* sonore + shake. C'est le moment où le joueur comprend qu'il n'y a pas de timer. **À polir en priorité absolue.**

---

## 6. UI

- **Diegetic quand c'est possible** : les ressources flottent au-dessus des bâtiments, pas seulement dans une barre.
- Style : cartes arrondies, ombres douces, mêmes couleurs que la palette 3D. Pas de skeuomorphisme médiéval chargé.
- **Safe areas** gérées (encoches, gestes système), tout jouable **au pouce d'une seule main** en mode portrait pour la gestion, paysage pour le raid. *(Décision : portrait obligatoire pour le village, paysage optionnel pour le raid — le portrait maximise le confort en session longue.)*
- Taille de cible tactile minimum : **48 dp**.
- **Mode une main** : les boutons critiques dans le tiers inférieur de l'écran.
- Police : une seule famille, 2 graisses, lisible à 12 sp minimum.

---

## 7. Audio

- **Musique adaptative** en 3 couches (calme / activité / combat), crossfade selon le verbe actif. C'est essentiel sur 10h : une boucle unique devient insupportable après 40 min.
- **6 pistes de village** minimum, en rotation aléatoire non répétitive, plus 3 pistes de combat et 1 thème de boss.
- **SFX** : ~180 sons. Chaque action a un son. Variation de pitch aléatoire ±6 % sur les sons répétitifs pour éviter la fatigue auditive.
- **Ambiance** : oiseaux, vent, marteaux, brouhaha de village, dont l'intensité suit la population. Change avec le cycle jour/nuit (grillons la nuit).
- Ducking automatique de la musique sous les SFX importants.

---

## 8. Pipeline de production art

1. **Modélisation** dans Blender. Un fichier `.blend` maître par catégorie.
2. Script Python Blender qui **assigne les UV vers la palette** automatiquement à partir du nom du material (`MAT_wood_dark` → pixel [3,7]).
3. Cuisson de l'**AO en vertex colors** via un second script.
4. Export FBX batché vers `Assets/Art/`.
5. Import Unity via un **AssetPostprocessor** qui applique automatiquement le material partagé, la compression et les settings.

**Le résultat** : ajouter un bâtiment au jeu = modéliser + lancer un script + glisser dans Unity. ~45 minutes par bâtiment une fois le pipeline en place. 46 bâtiments × 3 paliers visuels = 138 meshes ≈ 100 h de travail art. C'est le poste de coût art principal, et il est prévisible.

### Achat vs production
Les asset packs low poly de qualité existent (Synty, Kenney, KayKit). **Recommandation** : partir d'un pack de base pour le vertical slice (gain de 6 semaines), puis reconvertir progressivement vers la palette maison pour l'identité visuelle. Kenney est CC0, donc utilisable sans contrainte, y compris en prod.
