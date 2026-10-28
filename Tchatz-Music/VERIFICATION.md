# Vérification de livraison — 28 octobre 2026

## Environnement réellement utilisé

Terminal Linux Arena, environ 2 Go de RAM, OpenJDK Temurin 17.0.21, SDK Android platform 35 / build-tools 35.0.0 téléchargés dans le terminal. **Aucun SDK ni Android Studio nécessaire sur l’ordinateur du destinataire.** Aucun téléphone connecté, `/dev/kvm` absent : pas d’exécution d’émulateur dans cet environnement.

## Contrôles

Le bilan final et les sommes de contrôle sont dans `verification/RESULTATS.txt`. Les journaux Gradle finaux, le rapport JUnit XML et le rapport Android Lint sont fournis dans `verification/`.

- `scripts/check_project.py` : exécuté, structure/XML/permissions/module/wrapper/chemins de CI contrôlés.
- `:app:testDebugUnitTest` : tests JVM réels, voir XML JUnit. Recherche, cinq tris, états vides, références supprimées, politique de permissions et déplacement de file.
- `:app:assembleDebug` : compilation réelle et signature debug, sortie `app/build/outputs/apk/debug/app-debug.apk` ; APK copié dans `APK/` du ZIP.
- `:app:lintDebug` : voir rapport final. Les avertissements de versions plus récentes disponibles ne sont pas des échecs de compilation. Les versions ont été volontairement épinglées ensemble.
- `:app:assembleDebugAndroidTest` : compile les tests instrumentés, **ne les exécute pas**.
- `:app:connectedDebugAndroidTest` : **non exécuté ici**. Tests préparés Room/persistance/fichiers absents/ExoPlayer/état vide Compose. Le job GitHub `device-tests` est prévu pour les exécuter sur API 35.
- `apksigner verify --verbose --print-certs` : inspection de la signature de l’APK fourni ; aucune clé privée incluse dans le ZIP.

## Incidents réels et corrections

1. Une première compilation avec 2 Go de heap et un daemon Kotlin séparé saturait l’environnement 2 Go. Processus arrêtés, un seul worker et compilateur Kotlin en processus configurés.
2. Le paquet complet d’icônes Material Extended entraînait une erreur mémoire au dexage. Remplacé par Material Icons Core + neuf sources d’icônes sélectionnées, avec licence Apache.
3. Lint a signalé `windowLightNavigationBar` (API 27) dans un thème min API 26. Attribut retiré, gestion des barres sombres par l’activité.
4. Une invocation cumulant compilation, lint, dex et compilation des tests Android a ensuite perdu son daemon faute de mémoire. Les dernières vérifications locales sont donc séparées en trois invocations Gradle ; GitHub utilise un runner plus doté.

Ce rapport ne transforme pas les essais ratés en réussites. Seuls les résultats finaux consignés dans `RESULTATS.txt` et leurs journaux sont à retenir pour le code livré.

## Ce qui n’est pas vérifié

- Exécution du workflow sur votre dépôt GitHub (aucun dépôt modifié/créé depuis Arena).
- Lecture/décodage audio réel sur appareil, Android 8 et autres variantes constructeur.
- Service en veille prolongée, interruption par appel et reprise du focus, Bluetooth/casque et écran verrouillé.
- Interaction réelle avec le sélecteur SAF et révocation de permissions dans Android.
- Fluidité mesurée sur plusieurs milliers de morceaux et audit complet d’accessibilité.

La checklist reproductible **[TESTS-APPAREIL.md](TESTS-APPAREIL.md)** couvre ces cas. Les tests instrumentés fournis restent à exécuter ; aucun résultat fictif n’est annoncé. L’application est une implémentation native compilable, pas une certification de recette matérielle achevée.
