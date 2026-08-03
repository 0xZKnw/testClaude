# Valdris — projet Godot

Projet **Godot 4.4**. Aucune dépendance externe, aucun asset à télécharger :
toute la 3D est générée par code.

## Lancer

```bash
godot --path game                    # jouer (fenêtre)
godot --path game --resolution 720x1280   # au format téléphone
```

## Tester

```bash
# Tests unitaires : virgule fixe, RNG, grille, économie, progression,
# déterminisme du combat, replay, sauvegarde
godot --headless --path game --script res://tests/run_tests.gd

# Intégration : une partie jouée de bout en bout via l'autoload Game
godot --headless --path game res://tests/integration.tscn

# Captures d'écran, sans appareil ni écran
xvfb-run -a godot --path game --rendering-driver opengl3 \
  --resolution 720x1280 res://tests/screenshot.tscn
```

Les deux premières commandes renvoient un code de sortie non nul en cas
d'échec — c'est ce que la CI utilise.

## Exporter

L'APK est produit par la CI à chaque push (`.github/workflows/android.yml`),
sans aucun secret à configurer. En local, il faut le SDK Android et les
templates d'export de Godot :

```bash
godot --headless --path game --export-debug "Android" ../build/valdris.apk
godot --headless --path game --export-debug "Linux"   ../build/valdris.x86_64
```

## Organisation

| Dossier | Rôle |
|---|---|
| `core/` | Logique pure, sans dépendance à la scène. Testable en console. |
| `view/` | Rendu 3D : maillages générés, caméra, village, raid. |
| `ui/` | Interface, entièrement construite par code. |
| `data/` | Toutes les constantes de jeu, en CSV. |
| `scenes/` | Un seul fichier de scène : le point d'entrée. |
| `tests/` | Suites headless + outil de capture. |

## Deux règles à ne pas casser

**1. `core/` ne référence jamais un nœud de scène.**
C'est ce qui permet de rejouer des milliers de combats en console pour
l'équilibrage, et à un serveur de revalider un rapport de combat PvP avec
exactement le même code que le client.

**2. La simulation de combat reste déterministe.**
Pas de `float`, pas de `randi()`, pas d'itération sur un `Dictionary` dont
l'ordre compte, pas de dépendance au framerate. `test_combat_determinism`
vérifie qu'une même graine et les mêmes entrées produisent la même empreinte.
S'il casse, le PvP asynchrone n'est plus validable côté serveur.

## Rééquilibrer sans toucher au code

Tout est dans `data/buildings.csv` et `data/units.csv` : coûts, PV, dégâts,
portées, paliers de déblocage, quotas. Éditez, relancez, c'est appliqué.

> Les fichiers `.csv.import` contenant `importer="keep"` sont **nécessaires** :
> sans eux, Godot importe les CSV comme fichiers de traduction et les données
> disparaissent du build exporté.
