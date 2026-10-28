# Checklist de recette Android — à exécuter, pas des résultats acquis

Les tests instrumentés automatisés ne remplacent pas ces scénarios matériels. Prévoir Android 8/12L et Android 13/15+, au moins un casque filaire et un périphérique Bluetooth. Aucun de ces scénarios n’a été déclaré réussi dans Arena.

| Domaine | Manipulation | Résultat attendu |
|---|---|---|
| Installation | Installer l’APK, regarder le launcher et lancer | Icône T-note originale, splash même identité, aucune WebView |
| Premier lancement | Appareil avec MP3/FLAC/WAV locaux, accepter l’accès | Explication avant demande, bibliothèque automatique sans import manuel |
| Refus | Refuser la permission | État explicatif, bouton Autoriser, dossier facultatif et réglages accessibles |
| Refus permanent | Refuser plusieurs fois puis ouvrir réglages Android | Retour depuis réglages recharge l’autorisation et la bibliothèque |
| Révocation | Révoquer l’accès audio depuis Android ; relancer | Aucun crash, bibliothèque vidée des entrées MediaStore, explication affichée ; le service peut être tué par Android |
| SAF | Refuser audio, choisir un dossier local avec sous-dossiers | Titres trouvés récursivement, lecture et accès conservé après relance |
| SAF révoqué | Révoquer accès dossier / retirer carte SD | Erreur claire, choix de dossier disponible, aucun crash |
| Données absentes | Audio sans ID3/album/jaquette | Nom ou titre de secours, artiste inconnu, logo de secours |
| Formats | MP3, AAC/M4A, FLAC, WAV et fichier corrompu | Formats supportés jouent ; erreur lisible pour codec/fichier invalide |
| Recherche | Chercher titre/artiste/album puis changer les cinq tris | Résultats cohérents, compte total indiqué ; état vide si aucun résultat |
| Lecture | Lire une liste, pause, reprise, seek, suivant/précédent | Audio réel, barre suit la progression ; seek change la position |
| Modes | Activer aléatoire, parcourir les trois répétitions | Commandes reflètent l’état Media3 ; répétition titre/listes cohérente |
| Arrière-plan | Accueil Android puis verrouillage 5 minutes | Lecture maintenue tant que le système autorise le service, notification et contrôles verrouillés |
| Focus | Recevoir un appel, utiliser une autre application audio | Pause/atténuation et reprise éventuelle conformes au focus système, pas de double audio inattendu |
| Sortie | Débrancher un casque / déconnecter Bluetooth | Pause en cas de sortie devenue bruyante ; commandes écouteurs fonctionnelles |
| File | Ajouter, retirer, monter/descendre, sélectionner | Ordre réel ExoPlayer modifié ; bon titre courant après réorganisation |
| Suppression en file | Supprimer un fichier suivant avec le gestionnaire de fichiers | Actualisation de bibliothèque ; lecture du fichier disparu échoue proprement ; suivant/retrait possibles |
| Persistance | Favori, créer/renommer playlist, ajouter/ordonner ; arrêter puis redémarrer téléphone | Données présentes ; ni service ni lecture redémarrés automatiquement |
| Références cassées | Déplacer un titre de playlist puis actualiser | Référence indisponible visible et retirable ; nouvelle entrée réajoutable sans crash |
| Historique | Jouer plusieurs titres, retour accueil ; effacer avec confirmation | Les dernières références écoutées remontent ; effacement effectif après relance |
| Confidentialité | Mode avion, lecture ; inspection permissions Android | Aucune dépendance réseau, aucune permission Internet, aucun envoi |
| Gros volume | Plusieurs milliers de titres, recherche rapide et défilement | Pas d’ANR ; observer mémoire/latence sur appareil. Performance non mesurée dans Arena |
| Accessibilité | TalkBack, taille police augmentée, orientation paysage | Contrôles nommés, zones cliquables utilisables, lecteur défilant ; corriger les éventuels débordements observés |
| Mise à jour | Installer build signé de la même clé et versionCode augmenté | Données conservées. Avec clé différente Android refuse l’update, ne pas promettre l’inverse |

## Automatisation fournie

- JVM : recherche/tris/états vides/références supprimées/politique de permissions et ordre de file.
- Android Room : favoris après fermeture/réouverture, playlists/doublons/ordre/renommage/retrait/cascade, historique/effacement.
- Android : URI supprimée inaccessible, transitions de file et modes ExoPlayer sans décodage simulé.
- Compose : état vide explicatif affiché.
- CI : `device-tests` sous émulateur API 35 ; étendre la matrice pour les autres API après la première exécution.

Les tests de transitions ExoPlayer ne prétendent pas tester un fichier réel ou une interruption téléphonique. Ces derniers relèvent de la recette ci-dessus.
