# Maquette : jouer avec un iPhone, sans serveur

Une version iOS de l'app coûterait 99 $/an de compte développeur Apple. L'alternative
est une page web : Safari ne fait pas de Bluetooth, mais il fait du WebRTC, qui est du
pair-à-pair. Le seul obstacle est l'**appairage** — normalement confié à un serveur, et
ici transporté par QR code.

Ce dossier existe pour répondre à une seule question avant d'écrire quoi que ce soit
de sérieux : **est-ce que ça tient ?**

## Ce qui est déjà vérifié, ici, pour de vrai

Deux vrais navigateurs, deux vraies `RTCPeerConnection`, et entre les deux rien d'autre
que les chaînes qu'un QR code transporterait.

```
node web/spike/pairing-test.mjs   # le protocole d'appairage
node web/spike/page-test.mjs      # la page elle-même, pilotée comme par deux personnes
node web/spike/dist-test.mjs      # le fichier unique livré
```

Résultats :

| | |
|---|---|
| Appairage sans aucun serveur | fonctionne, canal de données ouvert, messages dans les deux sens |
| QR de l'hôte (URL + offre) | 183 octets — la limite est 2953 |
| QR de la réponse | 154 octets |
| Pire cas simulé, 8 adresses réseau | 480 octets |

La description brute fait 587 octets et jusqu'à 1493 dans le pire cas ; `sdp-codec.js`
la réduit en ne gardant que ce qui varie d'une session à l'autre et en reconstruisant
le reste à l'identique de l'autre côté.

## Ce qui n'est PAS vérifié

- **Safari.** Le moteur WebKit n'est pas téléchargeable dans l'environnement où ces
  tests tournent. C'est précisément ce que la page sert à tester sur un vrai iPhone.
- **Deux appareils réellement séparés.** Ici les deux pairs sont sur la même machine.
  Sur un vrai réseau, chaque navigateur cache son IP locale derrière un nom mDNS
  (`....local`) qu'il faut résoudre en multicast — ça marche sur un WiFi domestique,
  et c'est bloqué par l'isolation des clients d'un WiFi public.
- **Android natif.** L'app devra embarquer libwebrtc, environ 10 Mo.

## Comment tester

1. Construire le fichier unique : `node web/build.mjs`
2. Déposer `web/dist/test-liaison.html` sur n'importe quel hébergeur statique en
   **HTTPS** — Vercel, GitHub Pages, peu importe. HTTPS est obligatoire : sans lui le
   navigateur refuse WebRTC.
3. Sur le premier téléphone : ouvrir la page, *Créer la partie*. Un QR s'affiche.
4. Sur l'iPhone : le scanner avec l'appareil photo. La page s'ouvre et affiche un
   deuxième QR, la réponse.
5. Revenir au premier téléphone : *Ouvrir la caméra* et scanner cette réponse.

Les deux téléphones doivent être sur le **même WiFi**.

Si « canal de données » passe au vert et qu'un aller-retour s'affiche en millisecondes,
la voie web est ouverte et l'app iOS payante devient inutile. Si ça bloque sur ICE,
c'est le réseau. Si ça bloque avant, c'est Safari — et là, seule l'app native reste.
