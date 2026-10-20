SALAM-WIFI - obtenir l'APK sans Android Studio
1. Créez un compte sur github.com puis un nouveau dépôt (New repository), privé si vous voulez.
2. "uploading an existing file" : glissez TOUT le contenu du dossier SalamWifi (app, build.gradle.kts, settings.gradle.kts, gradle.properties, build.yml).
3. Onglet Actions > "set up a workflow yourself" > collez le contenu de build.yml > Commit.
   (Si le dossier .github/workflows a été envoyé automatiquement, passez cette étape.)
4. Actions > Build APK > attendez ~5 min > artefact "SALAM-WIFI-apk" en bas de page > téléchargez, dézippez, installez app-debug.apk.
