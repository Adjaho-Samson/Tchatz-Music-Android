# Tchatz Music — Android natif

Lecteur de fichiers audio locaux, Android **8.0 (API 26) et supérieur**, en français. Interface noire, grise, blanche et rouge. Aucun compte, serveur, publicité, télémétrie ni permission Internet.

## Livrables et état des vérifications

Ce dépôt est une reconstruction Kotlin native, **pas un emballage WebView**. L’ancien `Tchatz-Music.zip` trouvé dans Drive contenait une PWA HTML/CSS/JavaScript : sa documentation a été examinée, ses fonctions utiles reprises sur une architecture neuve. Ses données IndexedDB ne sont pas migrées automatiquement.

Le ZIP de livraison contient aussi `APK/Tchatz-Music-debug.apk` à côté du dossier source `Tchatz-Music/` pour une installation directe. Ce fichier est une compilation debug locale ; le pipeline permet de le reconstruire indépendamment. N’envoyez que le contenu du dossier source dans GitHub.

Consultez **[VERIFICATION.md](VERIFICATION.md)** pour les commandes effectivement exécutées, leurs résultats et les vérifications restant à faire. Un APK compilé ne prouve pas à lui seul le bon fonctionnement audio de tous les téléphones. Le workflow n’a pas été exécuté sur votre compte GitHub depuis Arena.

## Obtenir l’APK sans Android Studio ni SDK sur votre ordinateur

1. Téléchargez et décompressez le ZIP du projet.
2. Créez un dépôt GitHub (privé ou public), branche `main`.
3. Envoyez **le contenu du dossier `Tchatz-Music`**, pas le ZIP lui-même. À la racine du dépôt doivent se trouver `settings.gradle.kts`, `gradlew`, `app/`, `gradle/` et **`.github/workflows/android.yml`**. Attention : les dossiers commençant par un point sont parfois masqués par le gestionnaire de fichiers.
4. Dans GitHub, **Add file → Upload files**, glissez tous les fichiers et dossiers décompressés puis validez le commit. Vérifiez que le workflow apparaît dans l’onglet **Actions**. Si votre navigateur ignore `.github`, créez le fichier avec **Add file → Create new file**, nommez-le `.github/workflows/android.yml` et copiez le contenu fourni.
5. Autorisez GitHub Actions si GitHub le demande (Settings → Actions → General). Les exécutions peuvent consommer le quota de minutes d’un dépôt privé.
6. Ouvrez **Actions → Android APK → Run workflow → main → Run workflow**, ou faites un push sur `main`. Les pull requests sont également contrôlées.
7. Ouvrez l’exécution. Le job `debug` lance les tests JVM, Android Lint et produit l’APK. Le job `device-tests` exécute les tests instrumentés sur émulateur API 35.
8. Dans **Artifacts**, téléchargez **Tchatz-Music-debug-APK**, puis extrayez `app-debug.apk`. Il ne faut pas essayer d’installer le ZIP de l’artefact.
9. En cas d’échec, téléchargez **Android-diagnostics** et ouvrez `gradle-build.log` / les rapports HTML. Le job de tests sur appareil possède son propre artefact **Android-instrumentation**.

> Vercel peut continuer à héberger l’ancienne version web. Il ne transforme pas ce projet Kotlin en APK. La compilation Android est assurée par Gradle dans GitHub Actions.

### Installer sur le téléphone

Téléchargez l’APK sur votre téléphone (Drive ou GitHub), ou transférez-le par câble USB. Ouvrez-le dans Fichiers et autorisez temporairement **Installer des applications inconnues** pour cette application source. Installez **Tchatz Music**, puis révoquez cette autorisation d’installation si vous le souhaitez. Play Protect peut analyser l’APK debug non publié sur le Play Store. N’installez que votre propre build ou une source de confiance.

Au lancement, l’écran explique la détection locale. Appuyez sur **Autoriser**, acceptez l’accès audio : la bibliothèque est remplie depuis MediaStore sans import titre par titre. Sélectionnez ensuite un morceau. En cas de refus, le bouton reste disponible et les réglages Android permettent de rétablir l’autorisation. Un dossier SAF est proposé comme secours, pas comme obligation.

## Fonctionnalités implémentées

- **Bibliothèque automatique** MediaStore : titres, artistes, albums, durée, date d’ajout, taille/nom, pochettes d’albums lorsqu’exposées ; valeurs de secours pour métadonnées manquantes. Tous les fichiers audio exposés sont interrogés, sans se limiter à `IS_MUSIC`.
- Chargement en IO ; observation des changements MediaStore avec temporisation 600 ms ; rafraîchissement à chaque retour au premier plan et manuel. Le dossier SAF est rescanné au retour et manuellement.
- **Recherche** insensible à la casse (titre/artiste/album/fichier) ; tri titre/artiste/album/durée/date ; liste avec pochettes ou compacte. Filtrage et tri de la bibliothèque dans `Dispatchers.Default`, rendu `LazyColumn`.
- **ExoPlayer réel**, service MediaSessionService et MediaController : lecture/pause, précédent/suivant, progression interactive, aléatoire, répétition arrêtée/liste/titre ; lecture de listes entières et ajout à la file.
- Notification et contrôles système/écran verrouillé/casques/Bluetooth via Media3 ; focus audio (appels/interruption), pause en cas de déconnexion casque. Aucune fausse boucle audio ou lecture simulée.
- **File** : affichage complet, sélection d’un titre, retrait et réorganisation par flèches.
- **Favoris**, playlists (création/renommage/suppression, ajout/retrait, ordre par flèches), historique des 100 dernières références distinctes. Confirmation avant effacement de l’historique ou suppression d’une playlist.
- Accueil : morceau courant, derniers écoutés, récents, favoris, playlists et raccourcis. Mini-lecteur commun et lecteur complet.
- **Room** : références de favoris, playlists et historique ; transactions pour l’ordre des playlists ; suppression en cascade des entrées lorsqu’une playlist est effacée. Aucune copie d’audio en base.
- **DataStore** : tri, liste compacte et URI du dossier facultatif. Autorisation de lecture persistante SAF.
- Vérification d’accès au titre sélectionné avant lecture/ajout ; erreur de codec/fichier affichée par Media3 ; aucune suppression des fichiers audio par l’application.
- Logo original dessiné en chemins vectoriels (T fusionné à une note, accents rythmiques gris), icône adaptative et monochrome Android 13+, splash et réutilisation dans l’interface ; source SVG sous `branding/`.

## Limites explicites, sans promesse excessive

- **La lecture réelle sur téléphone, les appels, les commandes Bluetooth et les autorisations révoquées doivent encore être validés avec vos appareils** : voir la checklist. Aucun téléphone n’était connecté à Arena.
- Un arrêt forcé, une suppression de processus ou une politique constructeur peut interrompre la lecture. Pas de lancement audio automatique au démarrage du téléphone. La file et la position restent en mémoire pendant la vie du service ; pas de restauration après sa destruction. Les favoris, playlists, historique et réglages persistent après redémarrage.
- Les fichiers privés/protégés de Spotify, Deezer, etc. ne sont pas accessibles. Les formats MP3, M4A, AAC, FLAC, WAV, OGG/Opus dépendent du conteneur, codec et support appareil.
- Une suppression, un déplacement ou un réindexage peut changer l’URI : on ne devine pas l’identité par un nom potentiellement ambigu. La référence de playlist reste visible comme **Fichier indisponible** et peut être retirée, puis le fichier nouvellement détecté peut être réajouté. Les favoris/historique indisponibles ne sont pas joués. Pas de correspondance automatique par empreinte audio.
- SAF et MediaStore peuvent exposer deux URI du même fichier : déduplication par URI uniquement. Pas de surveillance récursive permanente des fournisseurs SAF ; le scan se fait au retour ou sur demande. Un grand dossier distant ou un fournisseur lent ralentit le scan. Choisissez un dossier local pertinent.
- Les pochettes viennent de MediaStore. Les titres SAF sans pochette exposée utilisent le logo ; pas d’extraction de jaquettes embarquées SAF. Pas de téléchargement de métadonnées.
- Interface en liste et liste compacte, pas de grille car elle apporte peu ici. Réorganisation par boutons accessibles, pas par glisser-déposer. Pas d’égaliseur ni de paroles, non demandés.
- La sauvegarde Android distante est désactivée. Désinstaller ou effacer les données fait perdre les sélections locales (pas les fichiers originaux). Pas d’export de playlists ni de migration des données PWA.

## Architecture et fichiers

```text
.github/workflows/android.yml      APK debug, tests JVM, lint et job émulateur
settings.gradle.kts               dépôt racine, module :app
build.gradle.kts                  plugins versionnés
app/build.gradle.kts              SDK et dépendances
app/schemas/                      schéma Room exporté, version 1
app/src/main/AndroidManifest.xml  permissions, activité et service
app/src/main/java/com/tchatz/music/
  MusicApp.kt                     composition des dépendances, base et préférences
  MainActivity.kt                 point d’entrée Compose/splash
  domain/                         modèle Track, règles recherche/tri/références
  library/                        MediaStore, ContentObserver, SAF, accès URI
  data/                           Room, DAO transactionnel, DataStore
  playback/                       service ExoPlayer, session, contrôleur et état
  ui/                             ViewModel, composants, écrans et navigation
app/src/main/res/                 icônes adaptatives, logo vectoriel, thème
app/src/test/                     tests JVM
app/src/androidTest/              tests Room et transitions ExoPlayer
branding/                         sources et exports du logo
scripts/                          contrôles statiques du dépôt
```

Couches séparées sans framework d’injection supplémentaire ; les singletons appartiennent à l’Application, les ressources d’écran au ViewModel. La connexion au service est libérée au nettoyage du ViewModel ; le service garde son propre lecteur. Navigation par états Compose et retour système gérés, pas de site embarqué.

Versions stables épinglées et utilisées ensemble : JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin/Compose compiler 2.1.20, KSP 2.1.20-1.0.32, Compose BOM 2025.04.01, Material 3, Media3 1.6.1, Room 2.7.1, DataStore 1.1.4. Ce choix est conservateur ; il ne prétend pas être le dernier de chaque bibliothèque au 28 octobre 2026. Le wrapper officiel est inclus (scripts Unix/Windows et JAR), distribution téléchargée depuis Gradle. `gradlew` possède le bit exécutable dans le ZIP ; le workflow le rétablit si un upload web GitHub le perd.

### Autorisations

- Android 13+ : `READ_MEDIA_AUDIO` au moment du scan, demandé après explication.
- Android 8 à 12L : `READ_EXTERNAL_STORAGE` limité dans le manifeste à API 32.
- Service : `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`.
- SAF : accès uniquement au dossier choisi et à ses enfants, autorisation persistante de lecture ; aucun accès global `MANAGE_EXTERNAL_STORAGE`.
- Les notifications multimédias avec session bénéficient de l’exemption Android 13 : pas de demande de permission de notifications généraliste pour cette seule fonction.

### Schéma et migrations

Le schéma Room initial **v1** est versionné dans `app/schemas`. Pas de migration antérieure à inventer pour cette nouvelle base. **Ne pas utiliser `fallbackToDestructiveMigration`**. Pour toute évolution, augmenter `version`, écrire `Migration(1,2)` ou une AutoMigration appropriée, l’enregistrer dans `Room.databaseBuilder(...).addMigrations(...)`, exporter le nouveau JSON et tester avec `MigrationTestHelper` sur les anciens schémas. L’ancien projet web n’a pas de schéma Room compatible.

## Développement local facultatif

Aucune installation locale n’est nécessaire pour le parcours GitHub. Pour un développeur : JDK 17 + SDK Android 35, définir `ANDROID_HOME`, puis :

```sh
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# appareil/émulateur connecté :
./gradlew :app:connectedDebugAndroidTest
# compiler les tests instrumentés sans les exécuter :
./gradlew :app:assembleDebugAndroidTest
python3 scripts/check_project.py
```

Sortie exacte : **`app/build/outputs/apk/debug/app-debug.apk`**. Le module réel est `:app`. Le debug est signé automatiquement avec la clé debug du runner ; aucune clé privée à fournir.

## Mises à jour et signature

Conservez `applicationId=com.tchatz.music`, augmentez `versionCode` à chaque nouvelle publication et ajustez `versionName`. Une mise à jour Android exige **la même signature**. La clé debug d’un runner GitHub éphémère peut changer : deux builds debug de runs distincts ne sont donc pas garantis installables l’un sur l’autre. Désinstaller puis installer fonctionne, mais efface favoris/playlists. Pour une utilisation durable, adoptez une signature release stable **avant** de constituer vos playlists importantes.

Le workflow fourni ne publie que le debug ; il ne prétend pas produire une release de production. `assembleRelease` produit une release non signée en l’absence de configuration de signature. Pour une production : créer un keystore hors dépôt, le conserver en lieu sûr, définir des GitHub Actions Secrets (`ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), reconstituer le keystore temporairement dans un job release protégé, ajouter un `signingConfig` qui lit les variables d’environnement, puis appeler `:app:assembleRelease`. Ne commitez **jamais** la clé ni les mots de passe ; ne les imprimez pas dans les logs. Protégez les secrets des PR non fiables, séparez ce job du debug et sauvegardez la clé hors GitHub. Play App Signing est recommandé pour une distribution Play Store.

## Résoudre les erreurs courantes

- **Aucun workflow** : `.github` absent ou fichiers dans un sous-dossier ; remettre la racine correcte.
- **Permission denied gradlew** : `chmod +x gradlew` (déjà dans le workflow).
- **SDK location not found** : job `setup-android` absent ; en local définir `ANDROID_HOME`. Ne pas envoyer `local.properties` d’un autre ordinateur.
- **Java incompatible** : utiliser JDK **17** et les versions Gradle/AGP fournies, ne pas changer un composant seul.
- **Mémoire insuffisante** : un worker et compilation Kotlin en processus sont configurés. Sur un runner suffisamment doté, on peut passer `org.gradle.jvmargs` à `-Xmx2g`. Ne pas lancer plusieurs compilations en parallèle sur un environnement 2 Go.
- **Dépendances non téléchargées** : relancer après contrôle réseau ; Google Maven et Maven Central sont nécessaires pour compiler, pas pour écouter ensuite.
- **Lint/test échoué** : lire `Android-diagnostics`, corriger la cause ; ne pas désactiver aveuglément les contrôles.
- **Émulateur/KVM indisponible** : consulter le job `device-tests`; le job APK reste indépendant. Une panne d’émulateur n’est pas une preuve de réussite des tests.
- **Application non installée** : Android trop ancien (<8), APK non extrait, stockage insuffisant, ou ancienne installation signée autrement. La désinstallation efface les données locales.
- **Bibliothèque vide** : placer des fichiers audio réellement locaux dans Music/Download, vérifier les autorisations, actualiser ; si Android ne les indexe pas, choisir le dossier facultatif via SAF.
- **Lecture arrêtée** : fichier retiré, codec non supporté, casque déconnecté, focus pris par une autre application ou restriction batterie constructeur. Réessayer depuis l’interface après avoir vérifié la cause.

## Ressources tierces

Neuf icônes Material sélectionnées sont livrées en source sous `app/src/main/java/androidx/compose/material/icons/rounded/` pour éviter d’embarquer la totalité de `material-icons-extended`. Sources AndroidX/Google, Apache 2.0, en-têtes préservés ; licence dans `NOTICE-APACHE-2.0.txt`. Les autres viennent de `material-icons-core`. Le logo de marque sous `branding/` est dessiné spécialement pour ce projet.
