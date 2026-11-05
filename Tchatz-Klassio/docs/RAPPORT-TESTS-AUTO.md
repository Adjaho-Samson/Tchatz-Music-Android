# Rapport de tests — Tchatz Klassio

- Date : 2026-11-05T03:26:20Z
- Commit : f1f2d40386ebbe10c0c56af83132f80d07918796
- Backend : https://hohxfwemcvpvtqnwwvds.supabase.co

```

=== TCHATZ KLASSIO — TESTS FONCTIONNELS RÉELS ===

Projet : https://hohxfwemcvpvtqnwwvds.supabase.co
Compte de test : qa.klassio.1793849168927@example.com

OK   Test 1.1 — Inscription responsable — HTTP 200
OK   Test 1.2 — Mot de passe incorrect rejeté — HTTP 400
OK   Test 1.3 — Connexion responsable
OK   Test 1.4 — Profil créé automatiquement — {"full_name":"QA Klassio","email":"qa.klassio.1793849168927@example.com"}
OK   Test 2.1 — Classe créée avec code généré — code TWJYYB6P
OK   Test 2.2 — Code inexistant rejeté — HTTP 400
OK   Test 2.3 — Étudiant rejoint par code (sans compte) — casse et espaces tolérés
OK   Test 2.4 — Matières consultables par l’étudiant — 0 matière(s)
OK   Test 3.1 — Matière créée + lien généré
OK   Test 3.2 — Page de publication contextuelle — Programmation orientée objet
OK   Test 3.3 — Tables fermées au rôle anon (RLS) — HTTP 401
OK   Test 3.4 — Réservation de publication acceptée
OK   Test 3.5 — Dépôt réel du PDF dans le bucket privé — HTTP 200
KO   Test 3.6 — Finalisation serveur (taille vérifiée, fichier déplacé)
OK   Test 3.7 — Limite de taille appliquée côté serveur — fichier 60 Mo refusé
OK   Test 3.8 — Format interdit refusé côté serveur — .exe refusé
OK   Test 3.9 — Document visible dans la bonne matière — 1 document(s)
OK   Test 3.10 — Recherche par titre — 1 résultat(s)
OK   Test 4.1 — Révocation du lien par le responsable
OK   Test 4.2 — Lien révoqué inutilisable — LINK_REVOKED
OK   Test 4.3 — Un lien ne cible que sa propre matière — matière ciblée = la sienne
OK   Test 4.4 — Étudiant/anon ne peut pas modifier une classe — HTTP 400
OK   Test 4.5 — Jetons de publication inaccessibles — HTTP 403
OK   Test 4.6 — Suppression impossible sans être propriétaire
KO   Test 4.7 — Jeton étudiant invalide rejeté
KO   Test 5.1 — URL signée délivrée à l’étudiant de la classe — ? s
OK   Test 5.3 — Aucun lien signé sans session valide — HTTP 503
OK   Test 5.4 — Bucket non public — HTTP 400
OK   Test 6.1 — Cohérence Web → Android (même document visible)
OK   Test 6.2 — Appareil enregistré pour les notifications
OK   Test 6.3 — Préférence de notification modifiable
OK   Test 6.4 — Nouveautés détectées pour la notification
OK   Test 7.1 — Tableau de bord propriétaire — 2 document(s)
OK   Test 7.2 — Suppression du document par le responsable
OK   Test 7.3 — Document retiré de la bibliothèque
KO   Test 7.4 — Fichier purgé du stockage — HTTP 503
OK   Test 7.5 — Déconnexion responsable — HTTP 204
OK   Test 7.6 — Suppression complète de l’espace

=== RÉSULTAT : 34/38 tests réussis, 4 échec(s) ===

ÉCHEC : Test 3.6 — Finalisation serveur (taille vérifiée, fichier déplacé) 
ÉCHEC : Test 4.7 — Jeton étudiant invalide rejeté 
ÉCHEC : Test 5.1 — URL signée délivrée à l’étudiant de la classe (? s)
ÉCHEC : Test 7.4 — Fichier purgé du stockage (HTTP 503)
```
