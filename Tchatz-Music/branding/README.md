# Identité Tchatz Music

Symbole dessiné pour ce projet, sans glyphe de police : traverse biseautée et hampe du T en gris clair, note rouge fusionnée à la hampe, trois barres rythmiques gris métal. Fond #08090B, rouge #F1394B, gris clair #E8EAED, métal #9199A4.

- `logo.svg` : original vectoriel, fond arrondi et symbole.
- `logo-512.png` : export 512 × 512.
- Android `drawable/brand_mark.xml` : chemins vectoriels du symbole sur viewport 108 × 108.
- `mipmap-*/ic_launcher.png` : exports 48/72/96/144/192 pixels.
- `mipmap-anydpi-v26` : foreground vectoriel et fond opaque noir, centre du symbole dans le cercle sûr de masquage.
- `mipmap-anydpi-v33` : variante avec couche monochrome pour les icônes thématiques.
- Le manifeste utilise `@mipmap/ic_launcher` pour les icônes normale et ronde ; le splash utilise `@drawable/brand_mark`.

Vérifier sur launcher avec masques cercle, carré arrondi et squircle. Le thème d’icônes Android 13 peut remplacer les couleurs par celles du système, comportement normal.
