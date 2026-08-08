# UNO Duo

UNO à deux joueurs, en local, par Bluetooth LE. Un téléphone crée le salon et affiche
un QR code, l'autre le scanne, et la partie se joue sans internet ni serveur.

## Installer l'APK

Chaque push sur la branche déclenche le workflow **Build APK**, qui publie l'APK dans la
release `apk-latest` du dépôt : <https://github.com/0xZKnw/testClaude/releases/tag/apk-latest>

Télécharge `uno-duo.apk` sur les **deux** téléphones (Android 8.0 / API 26 minimum) et
autorise l'installation depuis une source inconnue. L'APK est signé avec la clé de
debug d'Android : il s'installe directement, sans passer par le Play Store.

Une fois l'app installée, tu peux te mettre à jour depuis l'écran **Réglages** : elle
télécharge le dernier APK publié par la CI et lance l'installateur, sans repasser par le
navigateur. Le dépôt étant public, aucune configuration n'est nécessaire. Un champ jeton
reste disponible au cas où le dépôt redeviendrait privé.

### Signature de l'APK

Les builds sont signés avec une clé stable restaurée depuis les secrets du dépôt
(`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, et éventuellement
`ANDROID_KEY_ALIAS`). C'est indispensable : Android refuse de remplacer une application
par une autre signée avec une clé différente, et sans ces secrets chaque runner génère
une clé jetable. Le workflow affiche l'empreinte SHA-256 de l'APK à chaque build, ce qui
permet de vérifier d'un coup d'œil qu'elle ne change pas.

Sans secret configuré, le build fonctionne quand même mais affiche un avertissement, et
l'APK produit ne pourra qu'être installé à neuf.

## Comment jouer

1. Joueur A : *Créer une partie* → un QR code s'affiche, le téléphone devient
   découvrable en Bluetooth LE.
2. Joueur B : *Rejoindre une partie* → scanne le QR code (ou saisit le code à
   6 caractères affiché en dessous).
3. La partie démarre dès que la connexion est établie.

Autorisations demandées : Bluetooth (scan / connexion / diffusion) et caméra pour le QR.
Sur Android 11 et antérieur, la localisation doit être activée — c'est une contrainte du
système pour scanner en BLE, l'app ne lit aucune position.

## Les règles (version maison)

Jeu classique de 108 cartes : quatre couleurs avec 0 à 9, Passe, Sens interdit et +2,
plus les Jokers et les +4.

- **Cumul des +2** : un +2 peut être posé sur un +2, le total grimpe.
- **Cumul des +4** : un +4 peut être posé sur un +4. Un +4 peut aussi être posé sur un +2.
- **Contrer un +4** : celui qui pose un +4 annonce une couleur ; l'adversaire peut riposter
  avec un +2 **de cette couleur exactement** pour renvoyer la pile.
- **Encaisser** : la dernière carte de la pile était un +4 → tu pioches *et* tu sautes ton
  tour ; c'était un +2 → tu pioches mais tu joues normalement.
- **Sens interdit** : à deux joueurs, il agit comme un Passe.
- La carte de départ est toujours un chiffre, ce qui évite les cas particuliers au premier tour.

## Architecture

| Chemin | Rôle |
| --- | --- |
| `game/UnoEngine.kt` | Moteur de règles, Kotlin pur, déterministe, testé unitairement |
| `game/GameView.kt` | Instantané envoyé à chaque joueur (jamais la main adverse) |
| `net/Protocol.kt` | Messages, découpage en trames BLE, code de salon, lien QR |
| `net/BleHost.kt` | Serveur GATT + diffusion côté hôte |
| `net/BleGuest.kt` | Scan, connexion et abonnement côté invité |
| `vm/AppViewModel.kt` | Orchestration écrans + réseau ; l'hôte fait autorité |
| `ui/` | Compose : cartes dessinées au Canvas, plateau, QR, scanner |

L'hôte est la seule autorité : l'invité envoie des intentions (`play`, `draw`, `pass`) et
reçoit en retour l'état complet de la table. Aucun état de jeu n'est dupliqué, donc les
deux écrans ne peuvent pas diverger.

## Développement

```bash
./gradlew testDebugUnitTest   # règles du jeu
./gradlew assembleDebug       # APK de debug
```
