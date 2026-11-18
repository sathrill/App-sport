# Carnet d'entraînement

Carnet de suivi sportif hors ligne pour Android : séances, programme prévu, routines de mobilité, bloc kiné quotidien et bilans. Les données restent sur le téléphone ; l'onglet « Claude » sert à les échanger avec Claude (export puis code d'import).

- `web/` : l'application (HTML autonome, polices embarquées, données de départ dans `seed.js`).
- `android/` : coque Android (WebView) qui embarque `web/`.
- À chaque envoi sur `main`, GitHub Actions compile l'APK et le publie dans **Releases**, et met la version web en ligne sur GitHub Pages.

Dernier APK : `releases/latest/download/Carnet.apk`.

La clé de signature `android/app/carnet.keystore` est volontairement dans le dépôt pour que chaque version s'installe par-dessus la précédente sans effacer les données. Elle ne protège rien d'autre ; n'installe que les APK publiés ici.
