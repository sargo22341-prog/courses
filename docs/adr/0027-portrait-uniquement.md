# ADR 0027 — Interface en portrait uniquement

- **Statut** : acceptée
- **Voir aussi** : [Interface](../interface.md#orientation), [Limites connues](../limites-connues.md#interface)

## Contexte

L'application s'utilise d'une main, téléphone tenu droit, dans un magasin. Tourner le téléphone en
poche ou en poussant un chariot faisait basculer l'écran en paysage : champ d'ajout et clavier
occupaient alors presque toute la hauteur, pour une mise en page qui n'apporte rien à une liste.

## Décision

- `MainActivity` (seule activité) déclare `android:screenOrientation="portrait"` dans le manifeste.
- Les vérifications Lint `LockedOrientationActivity` et `DiscouragedApi`, qui signalent justement
  ce verrou, sont ignorées sur cette seule activité (`tools:ignore`), pas dans toute l'application.
- `ScreenOrientationTest` (instrumenté) vérifie que le verrou reste déclaré.

## Conséquences

- Sur téléphone, l'écran ne tourne plus ; aucune mise en page paysage n'est maintenue.
- Pour une application qui cible Android 16 ou plus, Android **ignore** ce verrou sur les grands
  écrans (plus petite largeur ≥ 600 dp : tablettes, pliables ouverts, fenêtres de bureau) ; il n'y a
  pas de moyen d'y déroger en ciblant l'API 37. Les écrans Compose continuent donc de s'adapter à
  la taille de la fenêtre.

## Alternatives écartées

- **Verrou dans le code** (`requestedOrientation`) : même effet, mais appliqué après la création de
  l'activité, donc une rotation visible au lancement.
- **`userPortrait` / `sensorPortrait`** : autorisent le portrait inversé, inutile pour cet usage.
