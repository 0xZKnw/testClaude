# 08 — Architecture technique

## 1. Choix du moteur

**Recommandation : Unity 6 LTS + URP.**

| Critère | Unity 6 | Godot 4.4 | Verdict |
|---|---|---|---|
| Export APK/AAB | Mature, signature, Play Console | Fonctionne, moins d'outillage | Unity |
| Perf 3D mobile | URP mobile renderer, SRP Batcher, GPU instancing | Correct mais moins optimisé sur Vulkan mobile | Unity |
| Écosystème (SDK pub, analytics, IAP) | Tout existe en natif | À bricoler | **Unity, largement** |
| Assets low poly | Énorme catalogue | Limité | Unity |
| Coût | Gratuit jusqu'à 200 k$ de CA | Gratuit total | Godot |
| Taille du build | ~40 Mo de base | ~30 Mo | Godot |

Unity gagne sur ce qui coûte du temps (IAP, pubs, analytics, crash reporting, assets). Godot reste le plan B si la licence Unity devient un problème — l'architecture décrite ci-dessous est portable, car la logique de jeu est volontairement découplée du moteur.

**Config** : Unity 6000.x LTS · URP mobile · IL2CPP · ARM64 uniquement · Vulkan (fallback GLES3) · .NET Standard 2.1 · Managed Stripping High.

---

## 2. Architecture logicielle

### Principe directeur : la logique de jeu ne connaît pas Unity

```
Valdris.Core/          ← C# pur, zéro référence UnityEngine, testable en console
  ├─ Economy/          ressources, coûts, formules
  ├─ Village/          grille, bâtiments, adjacences, villageois
  ├─ Combat/           simulation déterministe (fixed-point)
  ├─ Progression/      bastion, recherche, quêtes
  └─ Save/             sérialisation, migration de version

Valdris.Unity/         ← présentation
  ├─ View/             MonoBehaviours qui reflètent l'état de Core
  ├─ Input/            gestes, caméra
  ├─ UI/               UI Toolkit
  └─ Services/         IAP, ads, analytics, backend

Valdris.Tools/         ← éditeur de niveaux, simulateur de balance, générateur de données
```

Bénéfices : les 10 000 raids de simulation d'équilibrage (doc 05 §7) tournent en console sans Unity, en quelques secondes. Et le serveur peut rejouer les combats avec **exactement le même code**.

### Pattern
- **État centralisé immuable-ish** : un `GameState` sérialisable, muté uniquement via des `Command` (`BuildCommand`, `UpgradeCommand`, `AssignVillagerCommand`…). Chaque commande est validée, appliquée, et émet des événements.
- **Bus d'événements** typé pour que la vue réagisse sans coupler (`OnBuildingPlaced`, `OnResourceChanged`).
- **Pas de Singleton sauvage** : un `ServiceLocator` explicite injecté au boot.
- **Pas d'`Update()` par bâtiment.** Un `TickManager` unique à 4 Hz pour l'économie, 20 Hz pour la sim de combat, avec des buckets. 300 bâtiments × `Update()` = mort assurée sur mobile.

---

## 3. Données

- Toutes les constantes de jeu en **CSV** (éditables dans un tableur, versionnés dans Git) → générés en `ScriptableObject` par un script d'éditeur.
- **Hot-reload** en dev : recharger le CSV et relancer la scène sans recompiler.
- **Remote config** en prod : les CSV sont téléchargeables → rééquilibrage sans update du store. Fallback local systématique si le réseau est absent.
- **Addressables** pour les assets par région/saison : APK de base léger, contenu téléchargé à la demande.

---

## 4. Simulation de combat déterministe

Contrainte non négociable (voir doc 05 §4).

```csharp
// Arithmétique fixed-point Q16.16 — aucun float dans Valdris.Core.Combat
public readonly struct Fix32 {
    readonly int raw;               // 16 bits entiers, 16 bits fractionnaires
    public static Fix32 operator *(Fix32 a, Fix32 b) => FromRaw((int)(((long)a.raw * b.raw) >> 16));
    // sqrt, sin/cos par table de lookup précalculée
}

// RNG dédié, seedé, jamais partagé avec la présentation
public sealed class DetRandom {           // xorshift32
    uint s;
    public DetRandom(uint seed) => s = seed == 0 ? 1u : seed;
    public uint Next() { s ^= s << 13; s ^= s >> 17; s ^= s << 5; return s; }
}
```

Règles :
- Pas de `float`, pas de `Random.Range`, pas de `Time.deltaTime` dans `Core/Combat`.
- Pas d'itération sur `Dictionary` (ordre non garanti) → `SortedList` ou tableaux indexés.
- Pas de multithreading non ordonné dans la sim.
- Pas de dépendance à la framerate : pas fixe de 50 ms, la vue interpole.

**Test de non-régression** : une suite de 200 replays enregistrés rejoués à chaque CI. Toute divergence casse le build. C'est ce qui empêche une refonte de casser silencieusement le PvP.

### Perf de la sim
- Jusqu'à 200 unités + 300 bâtiments à 20 Hz.
- **Spatial hashing** (grille de 4×4 unités) pour les requêtes de ciblage — pas de `Physics.OverlapSphere`, pas de collider Unity dans la sim.
- Pathfinding : **flow field** calculé une fois par cible de zone, pas d'A* par unité. C'est ce qui permet 200 unités sans effort.
- Si besoin : Burst + Jobs sur la boucle de ciblage uniquement (avec ordonnancement déterministe).

---

## 5. Sauvegarde

- Format : **binaire compact** (MessagePack ou un writer maison) + gzip. Un village complet ≈ 40–80 Ko.
- **Versionné** avec des migrations explicites (`SaveMigration_v3_to_v4`). Non négociable : casser les saves des joueurs en live est le pire incident possible.
- **Triple écriture** : `save.dat` + `save.bak` + un slot de secours, avec checksum. Restauration automatique si corruption.
- Sauvegarde à chaque commande importante + toutes les 30 s + sur `OnApplicationPause`.
- **Cloud save** : Google Play Games Services (gratuit) en v1, backend maison en v2.

---

## 6. Backend

### v1 (jusqu'au soft launch) — minimal
- **Supabase** (Postgres + auth + storage, généreux gratuitement) ou **PlayFab**.
- Tables : `players`, `village_snapshots`, `battle_reports`, `guilds`, `guild_members`, `leaderboards`, `config`.
- 4 endpoints : `sync_save`, `publish_snapshot`, `find_opponent`, `submit_battle`.
- Validation des combats : **par échantillonnage** (10 % des combats rejoués) au début, 100 % ensuite.

### v2 (si ça marche) — Nakama
[Nakama](https://heroiclabs.com/nakama/) (open source, Go) gère guildes, chat, classements, matchmaking et stockage. Self-hostable sur un VPS à 20 €/mois pour les premiers 50 k joueurs. Passer à Nakama quand le trafic le justifie, pas avant.

### Coût estimé
| Étape | Coût mensuel |
|---|---|
| Dev / soft launch (< 1 k DAU) | 0–25 € |
| 10 k DAU | ~80 € |
| 100 k DAU | ~600 € |

---

## 7. Performance mobile — les règles dures

| Règle | Pourquoi |
|---|---|
| Zéro `GameObject.Find`, zéro `Camera.main` en boucle | Coût scandaleux |
| **Object pooling** obligatoire (unités, projectiles, particules, popups de dégâts, éléments de liste UI) | Le GC est l'ennemi n°1 sur Android |
| **0 allocation par frame** en régime établi, vérifié au Profiler | Les hitches de GC ruinent le feel |
| UI Toolkit plutôt qu'uGUI pour les écrans complexes | Moins de draw calls, meilleure séparation |
| Canvas uGUI restants découpés par fréquence de mise à jour | Un canvas qui change = tout le canvas re-batché |
| Textures ASTC 6×6, mipmaps off pour l'UI | |
| Audio : `.ogg`, streaming pour la musique, décompressé en RAM pour les SFX courts | |
| LOD à 2 niveaux sur les bâtiments 3×3+ | |
| Frustum + occlusion custom par cellule de grille | |
| Cap à 60 fps, `Application.targetFrameRate` + option 30 fps « économie de batterie » | 10h de jeu = la batterie compte vraiment |
| Détection de thermal throttling (Adaptive Performance) → baisse auto de qualité | Idem, spécifique aux sessions longues |

**Device cible plancher** : Snapdragon 665 / Adreno 610 / 3 Go RAM (≈ Redmi Note 8). Si ça tourne à 30 fps stable là-dessus, ça tourne partout.

---

## 8. Build & CI

- **GitHub Actions** avec [GameCI](https://game.ci/) : build AAB + APK à chaque merge sur `main`.
- Tests unitaires `Valdris.Core` (rapides, sans Unity) sur chaque PR + la suite de 200 replays déterministes.
- Signature via un keystore en secret GitHub. **Le keystore doit être sauvegardé hors du repo, en 2 endroits** — le perdre signifie ne plus jamais pouvoir mettre à jour l'app.
- Distribution de test : Firebase App Distribution ou piste interne Play Console.
- **Version bump automatique** + changelog généré depuis les commits.

## 9. Localisation

Prévue dès le départ, sinon c'est un enfer plus tard :
- Toutes les chaînes dans des tables (Unity Localization Package), **zéro string en dur**.
- Clés sémantiques (`building.sawmill.name`), pas de phrases comme clés.
- Prévoir **+40 % de largeur** pour l'allemand et le russe dans les layouts UI.
- Pluriels et genres gérés par la table, pas par concaténation.
- v1 : FR + EN. Puis DE, ES, PT-BR, RU, TR.
