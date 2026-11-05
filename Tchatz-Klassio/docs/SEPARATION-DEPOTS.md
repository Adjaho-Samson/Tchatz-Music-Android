# Séparation Tchatz Klassio / Tchatz Music

**Tchatz Klassio** et **Tchatz Music** sont deux produits distincts et doivent vivre dans
**deux dépôts distincts**. Ce dossier contient Tchatz Klassio sous forme autonome.

## Ce qui a été fait

- Tchatz Klassio a été **extrait** de l'ancien dépôt (`Tchatz-Music-Android`) avec son
  **historique complet conservé** : arborescence propre (`android/`, `web/`, `supabase/`,
  `APK/`, `docs/`), workflow CI dédié, README propre.
- Vérification faite : **aucune référence** à Tchatz Music ne subsiste dans le code, les
  ressources, la configuration ou les workflows de Klassio.
- Chaque produit a désormais son propre backend, ses propres clés et son propre cycle de vie.

## Deux façons de récupérer le dépôt autonome

### A. Par l'archive Git complète (recommandé, historique inclus)

Le fichier `Tchatz-Klassio-standalone.bundle` (à la racine de ce livrable) contient tout
l'historique. Il suffit de le « dérouler » dans un dossier :

```bash
git clone Tchatz-Klassio-standalone.bundle Tchatz-Klassio
cd Tchatz-Klassio
git log --oneline          # historique complet conservé
```

### B. Par les fichiers (sans historique)

Copier simplement le contenu de ce dossier dans un nouveau dossier, puis :

```bash
git init
git add -A
git commit -m "Tchatz Klassio — version initiale"
```

## Publier sur GitHub

1. Sur GitHub : **New repository** → nom `Tchatz-Klassio` → ne rien initialiser.
2. Depuis le dossier obtenu en A ou B :

```bash
git remote add origin https://github.com/Adjaho-Samson/Tchatz-Klassio.git
git branch -M main
git push -u origin main
```

> La création du dépôt distant doit se faire depuis votre compte GitHub : l'automatisation
> utilisée ici n'a pas le droit de créer des dépôts (elle est limitée au contenu du dépôt
> existant). Tout le reste est prêt.

## Ce qu'il reste à faire dans l'ancien dépôt

Une fois le nouveau dépôt en place et vérifié, le dossier `Tchatz-Klassio/` de l'ancien dépôt
`Tchatz-Music-Android` peut être supprimé : il ne contient plus rien d'unique. L'ancien dépôt
redevient alors strictement dédié à Tchatz Music.

## Points de vigilance

- **Ne pas réutiliser** les clés, le projet Supabase ni les secrets d'un produit dans l'autre.
- Les deux applications Android ont des identifiants différents
  (`com.tchatz.klassio` pour Klassio) : elles cohabitent sans conflit sur un téléphone.
- Les workflows CI sont indépendants : celui de Klassio ne se déclenche que sur son propre
  dépôt.
