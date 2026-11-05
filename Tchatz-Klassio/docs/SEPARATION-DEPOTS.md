# Séparation Tchatz Klassio / Tchatz Music

**Tchatz Klassio** et **Tchatz Music** sont deux produits distincts, dans **deux dépôts distincts**.
Ce dossier contient Tchatz Klassio sous forme autonome, prêt à devenir son propre dépôt.

---

## 1. Où « dérouler » ? (la question la plus fréquente)

« Dérouler » veut dire **extraire le ZIP**. Il n'y a **aucun serveur, aucun hébergement, aucune
ligne de commande obligatoire** : tout se passe sur ton ordinateur, dans un dossier ordinaire.

### Le bon emplacement

Extrais le ZIP sur ton **Bureau** (ou dans `Documents`). Par exemple :

- Windows : `C:\Users\<ton nom>\Desktop\Tchatz-Klassio`
- macOS : `/Users/<ton nom>/Desktop/Tchatz-Klassio`
- Linux : `/home/<ton nom>/Bureau/Tchatz-Klassio`

Tu dois obtenir **un seul dossier** contenant directement `android`, `web`, `supabase`, `APK`,
`docs` et `OUTILS`.

### Les trois pièges à éviter

1. **Ne pas extraire dans le ZIP lui-même.** Un ZIP n'est pas un dossier de travail : Windows
   permet d'y naviguer sans extraire, et dans ce cas rien ne fonctionne.
2. **Éviter un dossier synchronisé** (OneDrive, iCloud Drive, Google Drive). Ces dossiers
   déplacent les fichiers pendant l'envoi et peuvent bloquer Git.
3. **Éviter les chemins avec accents ou caractères spéciaux** (`C:\Users\Léa\Bureau\…` passe
   souvent, mais un chemin comme `C:\Users\Nom\Documents\Travail (2026)\…` peut poser problème).

### Comment extraire

| Système | Geste |
|---|---|
| Windows 11 | Clic droit sur le ZIP → **Extraire tout…** → choisir le Bureau → **Extraire** |
| Windows 10 | Clic droit → **Extraire tout…** (ou ouvrir avec 7-Zip) |
| macOS | **Double-clic** sur le ZIP : le dossier apparaît à côté |
| Linux | Clic droit → **Extraire ici** |

> Attention : après extraction, il y a **deux** éléments sur le Bureau — le ZIP (l'archive) et le
> dossier `Tchatz-Klassio` (le vrai contenu). C'est dans le **dossier** qu'il faut travailler.

---

## 2. Créer le dépôt GitHub en 3 étapes (sans terminal)

C'est la méthode recommandée si tu n'as jamais utilisé Git.

### Étape 1 — Créer le dépôt vide sur GitHub

1. Va sur https://github.com/new
2. **Repository name** : `Tchatz-Klassio`
3. **Ne coche rien** : pas de README, pas de `.gitignore`, pas de licence.
   (Un dépôt non vide refuse le premier envoi.)
4. Clique **Create repository**.
5. Copie l'adresse affichée, elle ressemble à
   `https://github.com/<ton-compte>/Tchatz-Klassio.git`

### Étape 2 — Lancer le script fourni

Dans le dossier extrait, ouvre le sous-dossier **`OUTILS`** :

- **Windows** : double-clique `creer-depot-github.bat`
  (une fenêtre noire s'ouvre, c'est normal : c'est le script qui travaille)
- **macOS / Linux** : double-clique `creer-depot-github.sh`
  (si macOS refuse de l'ouvrir : clic droit → **Ouvrir** → **Ouvrir**)

Le script te demande **une seule chose** : l'adresse du dépôt de l'étape 1.
Colle-la, appuie sur **Entrée**, et laisse faire.

Il prépare le dossier, enregistre la version, se connecte à GitHub et envoie tout.
Au premier envoi, GitHub peut demander tes identifiants : c'est la connexion normale à ton
compte, à faire une seule fois.

### Étape 3 — Vérifier

Ouvre `https://github.com/<ton-compte>/Tchatz-Klassio` dans ton navigateur.
Tu dois voir les dossiers `android`, `web`, `supabase`, `APK`, `docs`.

C'est terminé : Tchatz Klassio a son propre dépôt, séparé de Tchatz Music.

---

## 3. Si tu préfères les commandes (ou si le script échoue)

Le script ne fait rien de magique. Voici les mêmes opérations à la main, dans un terminal
ouvert **dans le dossier extrait** (Windows : clic droit dans le dossier → *Git Bash Here* ;
macOS : clic droit → *Services* → *Nouveau terminal au dossier*) :

```bash
git init
git add -A
git commit -m "Tchatz Klassio — version initiale"
git branch -M main
git remote add origin https://github.com/<ton-compte>/Tchatz-Klassio.git
git push -u origin main
```

---

## 4. Variante : récupérer aussi l'historique Git

L'extraction du ZIP donne les fichiers **sans** historique. Si tu veux l'historique des
développements, utilise le fichier `Tchatz-Klassio-standalone.bundle` (fourni dans le ZIP) :

```bash
git clone Tchatz-Klassio-standalone.bundle Tchatz-Klassio
cd Tchatz-Klassio
git log --oneline        # les 6 commits sont là
```

Puis, pour l'envoyer sur GitHub, ajoute simplement le dépôt distant :

```bash
git remote set-url origin https://github.com/<ton-compte>/Tchatz-Klassio.git
git push -u origin main
```

C'est la commande `git clone` qui « déroule » le bundle : il n'y a rien à extraire à la main.

---

## 5. Ce qu'il restera à faire dans l'ancien dépôt

Une fois `Tchatz-Klassio` en ligne et vérifié, le dossier `Tchatz-Klassio/` de l'ancien dépôt
`Tchatz-Music-Android` peut être supprimé : il ne contient plus rien d'unique. L'ancien dépôt
redevient alors strictement dédié à Tchatz Music.

---

## 6. Points de vigilance

- **Ne pas réutiliser** les clés, le projet Supabase ni les secrets d'un produit dans l'autre.
- Les deux applications Android ont des identifiants différents
  (`com.tchatz.klassio` pour Klassio) : elles cohabitent sans conflit sur un téléphone.
- Les workflows CI sont indépendants : celui de Klassio ne se déclenche que sur son propre dépôt.
- La création du dépôt distant doit se faire depuis **ton** compte GitHub : l'automatisation
  utilisée pendant le développement n'a pas le droit de créer des dépôts.
