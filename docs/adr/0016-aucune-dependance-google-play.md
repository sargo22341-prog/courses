# ADR 0016 — Aucune dépendance aux services Google Play

- **Statut** : acceptée
- **Voir aussi** : [Architecture](../architecture.md#technologies), [Home Assistant](../home-assistant.md#connexion)

## Contexte

L'application doit fonctionner à l'identique sur GrapheneOS et sur tout Android sans Google. Le
scan du QR code du token Home Assistant est la seule fonctionnalité pour laquelle une bibliothèque
Google (ML Kit, scanner de codes de Play Services) aurait été le choix par défaut.

## Décision

- Aucune dépendance aux services Google Play : ni GMS, ni ML Kit, ni Firebase, ni Play Integrity.
- Scanner de QR code : **CameraX** (AndroidX) pour l'aperçu et l'analyse d'images, **ZXing**
  (open source) pour le décodage (`core/scanner`, `QrCodeAnalyzer`, `QrCodeDecoder`).
- Préférer AndroidX et des bibliothèques open source pour tout nouveau besoin.

## Conséquences

- Comportement identique avec ou sans services Google ; aucune télémétrie tierce embarquée.
- Le décodage ZXing est un peu moins tolérant qu'un modèle ML Kit sur un QR code abîmé ou mal
  éclairé ; la saisie manuelle du token reste possible.
- Les images de la caméra sont analysées en mémoire, jamais enregistrées ni envoyées.

## Alternatives écartées

- **ML Kit / scanner de codes Google** : dépend des services Google Play.
- **Saisie manuelle uniquement** : le token fait plusieurs centaines de caractères.
