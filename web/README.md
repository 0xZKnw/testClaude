# UNO sur le web

Le jeu complet, jouable dans un navigateur — iPhone compris — et connecté en direct
d'un téléphone à l'autre, **sans aucun serveur**.

**En ligne : https://uno-test-liaison.vercel.app**

## Pourquoi cette version existe

Safari ne fait pas de Bluetooth, donc l'app Android ne pourra jamais parler à un iPhone.
Une app iOS native coûterait 99 $/an de compte développeur Apple. Le web contourne les
deux : Safari fait du WebRTC, qui est du pair-à-pair, et il ne manquait que l'appairage —
normalement confié à un serveur, ici transporté par QR code.

## Ce sont bien les mêmes règles, et c'est prouvé

Le moteur existe désormais deux fois : en Kotlin pour l'app, en JavaScript ici. Des
règles « équivalentes » ne suffisent pas — la seule façon d'en être sûr est de faire
jouer les deux moteurs aux mêmes parties et de comparer.

C'est ce que font les tests. `src/random.js` reproduit le générateur aléatoire de Kotlin
au bit près, ce qui donne des mélanges identiques ; les deux moteurs rejouent alors
240 parties complètes (2 à 5 joueurs, moteur seul puis bot aux trois niveaux) et chacun
des 22 767 états intermédiaires est comparé. Le résultat est verrouillé par une empreinte
dans `test/trace-reference.sha256`.

```
node web/test/random-conformance.mjs   # le générateur, contre une capture de Kotlin
node web/test/engine-conformance.mjs   # les 240 parties, contre l'empreinte
node web/test/game-test.mjs            # la page : solo, puis deux navigateurs appairés
node web/spike/pairing-test.mjs        # l'appairage seul, et la taille des QR
```

## Comment on joue

1. Un joueur ouvre le site et fait *Créer une partie* : un QR s'affiche.
2. Les autres le scannent avec l'appareil photo de leur téléphone. La page s'ouvre chez
   eux et affiche un QR de réponse.
3. L'hôte scanne chaque réponse, puis lance quand tout le monde est là.

Tous les téléphones doivent être sur le **même WiFi** : sans serveur relais, seules les
adresses locales sont échangées. L'isolation des clients, courante sur un WiFi public,
bloque la connexion — un partage de connexion depuis l'un des téléphones la contourne.

`diagnostic.html` teste la liaison toute seule, sans le jeu, et dit où ça bloque le cas
échéant : le réseau, ou le navigateur.

## Ce qui reste non vérifié

Safari lui-même. Le moteur WebKit n'est pas téléchargeable dans l'environnement où ces
tests tournent, donc tout ce qui est écrit ici a été vérifié sur Chromium. C'est
précisément ce que le premier essai sur un vrai iPhone tranchera.
