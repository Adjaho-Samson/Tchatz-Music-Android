package com.tchatz.music.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchatz.music.domain.Sort
@Composable fun SettingsScreen(sort:Sort,compact:Boolean,vm:MusicViewModel,request:()->Unit,permissions:()->Unit,folder:()->Unit) {
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
  Section("Affichage");Row {Text("Liste compacte",Modifier.weight(1f));Switch(compact,vm::setCompact)};SortPicker(sort,vm::setSort)
  Section("Bibliothèque locale");Button(onClick={vm.library.refresh()}){Text("Actualiser")};TextButton(onClick=request){Text("Demander l’accès audio")};TextButton(onClick=permissions){Text("Autorisations Android")};TextButton(onClick=folder){Text("Choisir un dossier facultatif")};TextButton(onClick={vm.folder(null)}){Text("Oublier le dossier facultatif")}
  Section("Dépannage");Text("Un fichier manque ? Vérifiez l’autorisation audio et actualisez. Un déplacement peut modifier sa référence : ajoutez alors la nouvelle entrée à vos playlists. Les anciennes références restent retirables. Un même fichier exposé par MediaStore et un dossier peut apparaître deux fois.\n\nLes fichiers privés ou protégés d’une application de streaming ne sont pas accessibles. La prise en charge MP3, M4A, AAC, FLAC, WAV et autres dépend du codec et du téléphone.\n\nLa lecture continue dans un service multimédia. Les appels et pertes de focus sont gérés par ExoPlayer. Une déconnexion d’écouteurs met en pause. Un arrêt forcé par Android ou l’utilisateur ne permet pas de garantir une reprise. La file reste en mémoire pendant la vie du service ; elle n’est pas restaurée après son arrêt.")
  Section("Tchatz Music 1.0.0");Text("Android natif • Kotlin • Media3\nSans compte, publicité ni télémétrie. Aucune permission Internet. Favoris, playlists, historique et préférences sont enregistrés uniquement sur cet appareil. Désinstaller l’application efface ses données, pas vos fichiers audio.")
 }
}
