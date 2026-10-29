package com.tchatz.music.ui
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tchatz.music.domain.*
import com.tchatz.music.data.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TchatzRoot(vm:MusicViewModel=viewModel()) {
 val colors=darkColorScheme(primary=Color(0xFFF1394B),onPrimary=Color.White,background=Color(0xFF08090B),surface=Color(0xFF121418),surfaceVariant=Color(0xFF23262C),onSurface=Color(0xFFF4F4F6),onSurfaceVariant=Color(0xFFADB2BD))
 MaterialTheme(colorScheme=colors) {
 val context=LocalContext.current
 val owner=LocalLifecycleOwner.current
 val audioPermission=PermissionPolicy.audioPermission(Build.VERSION.SDK_INT)
 var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,audioPermission)==PackageManager.PERMISSION_GRANTED) }
 var denied by remember { mutableStateOf(false) }
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted=it;denied=!it;vm.library.refresh() }
 val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if(uri!=null) {
  runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);vm.folder(uri.toString()) }.onFailure { vm.message.value="Impossible de conserver l’accès à ce dossier." }
 } }
 DisposableEffect(owner) { val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) { granted=ContextCompat.checkSelfPermission(context,audioPermission)==PackageManager.PERMISSION_GRANTED;vm.library.refresh() } };owner.lifecycle.addObserver(observer);onDispose { owner.lifecycle.removeObserver(observer) } }
 val all by vm.library.tracks.collectAsStateWithLifecycle()
 val loading by vm.library.loading.collectAsStateWithLifecycle()
 val libraryError by vm.library.error.collectAsStateWithLifecycle()
 val playError by vm.playback.error.collectAsStateWithLifecycle()
 val message by vm.message.collectAsStateWithLifecycle()
 val favorites by vm.favorites.collectAsStateWithLifecycle()
 val playlists by vm.playlists.collectAsStateWithLifecycle()
 val entries by vm.entries.collectAsStateWithLifecycle()
 val history by vm.history.collectAsStateWithLifecycle()
 val sort by vm.sort.collectAsStateWithLifecycle()
 val compact by vm.compact.collectAsStateWithLifecycle()
 val player by vm.playback.state.collectAsStateWithLifecycle()
 var page by remember { mutableStateOf("Accueil") }
 var selected by remember { mutableStateOf<Playlist?>(null) }
 var query by remember { mutableStateOf("") }
 var create by remember { mutableStateOf(false) }
 var rename by remember { mutableStateOf<Playlist?>(null) }
 var delete by remember { mutableStateOf<Playlist?>(null) }
 var addTrack by remember { mutableStateOf<Track?>(null) }
 var clear by remember { mutableStateOf(false) }
 val favoriteSet=remember(favorites) { favorites.map { it.uri }.toSet() }
 var visible by remember { mutableStateOf<List<Track>>(emptyList()) }
 LaunchedEffect(all,query,sort) { visible=withContext(Dispatchers.Default) { LibraryRules.select(all,query,sort) } }
 BackHandler(page!="Accueil" || selected!=null) { if(selected!=null) selected=null else page="Accueil" }
 fun settings() { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }
 Scaffold(containerColor=colors.background,topBar={
  TopAppBar(title={ Column { Text("TCHATZ",fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge); Text("MUSIC / ${selected?.name ?: page}",style=MaterialTheme.typography.labelSmall,color=colors.primary) } },navigationIcon={ if(page!="Accueil" || selected!=null) IconButton(onClick={if(selected!=null) selected=null else page="Accueil"}){Icon(Icons.Rounded.ArrowBack,"Retour")} },actions={IconButton(onClick={vm.library.refresh()}){Icon(Icons.Rounded.Refresh,"Actualiser")};IconButton(onClick={selected=null;page="Paramètres"}){Icon(Icons.Rounded.Settings,"Paramètres")}})
 },bottomBar={ Column {
  if(player.uri!=null && page!="Lecteur") Surface(color=colors.surfaceVariant,onClick={selected=null;page="Lecteur"}) { Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
   Cover(player.artwork,Modifier.size(42.dp));Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(player.title,maxLines=1);Text(player.artist,maxLines=1,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
   IconButton(onClick=vm.playback::toggle){Icon(if(player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,"Lecture / pause")};IconButton(onClick=vm.playback::next){Icon(Icons.Rounded.SkipNext,"Suivant")}
  } }
  NavigationBar { listOf("Accueil" to Icons.Rounded.Home,"Bibliothèque" to Icons.Rounded.LibraryMusic,"Favoris" to Icons.Rounded.Favorite,"Playlists" to Icons.Rounded.QueueMusic).forEach { (name,icon) -> NavigationBarItem(selected=page==name,onClick={page=name;selected=null},icon={Icon(icon,name)},label={Text(name,style=MaterialTheme.typography.labelSmall)}) } }
 } }) { padding ->
 Column(Modifier.fillMaxSize().padding(padding)) {
 if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
 if(!granted) Surface(color=colors.surfaceVariant) { Column(Modifier.padding(16.dp)) { Text(if(denied) "Accès audio refusé" else "Votre musique, automatiquement",fontWeight=FontWeight.Bold);Text("Autorisez l’accès aux fichiers audio pour découvrir la musique du téléphone. Aucun envoi, aucun compte. Un dossier reste facultatif.",style=MaterialTheme.typography.bodySmall);Row { TextButton(onClick={permission.launch(audioPermission)}){Text("Autoriser")};TextButton(onClick={folderPicker.launch(null)}){Text("Dossier")};if(denied) TextButton(onClick={settings()}){Text("Réglages Android")} } } }
 libraryError?.let { Text(it,color=colors.primary,modifier=Modifier.padding(12.dp)) }
 when(page) {
 "Lecteur" -> PlayerScreen(player,vm,{page="File"},favoriteSet.contains(player.uri))
 "File" -> QueueScreen(player,vm)
 "Paramètres" -> SettingsScreen(sort,compact,vm,{permission.launch(audioPermission)},{settings()},{folderPicker.launch(null)})
 else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp).testTag("main-list")) {
 fun LazyListScope.songs(list:List<Track>,section:String,playlistId:Long?=null) {
  if(list.isEmpty()) item { Empty("Aucun morceau ici",if(all.isEmpty()) "Ajoutez des fichiers audio sur votre téléphone, autorisez l’accès puis actualisez. Les téléchargements privés de streaming ne sont pas accessibles." else "Ajoutez des morceaux ou modifiez votre recherche.") }
  itemsIndexed(list,key={_,t -> "$section:${t.uri}"}) { index,t -> TrackRow(t,t.uri in favoriteSet,compact,{vm.play(list,index)},{vm.favorite(t.uri)},{vm.enqueue(t)},{addTrack=t}) {
   if(playlistId!=null) {
    IconButton(onClick={vm.move(playlistId,index,index-1)},enabled=index>0){Icon(Icons.Rounded.KeyboardArrowUp,"Monter")}
    IconButton(onClick={vm.move(playlistId,index,index+1)},enabled=index<list.lastIndex){Icon(Icons.Rounded.KeyboardArrowDown,"Descendre")}
    IconButton(onClick={vm.remove(playlistId,t.uri)}){Icon(Icons.Rounded.Close,"Retirer de la playlist")}
   }
  } }
 }
 when(page) {
 "Accueil" -> {
  item { Row(Modifier.fillMaxWidth().padding(top=16.dp),verticalAlignment=Alignment.CenterVertically) { Cover(player.artwork,Modifier.size(88.dp));Column(Modifier.padding(start=16.dp).weight(1f)){Text("VOTRE ESPACE SONORE",style=MaterialTheme.typography.labelSmall,color=colors.primary);Text("La musique.\nSans limites réseau.",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("${all.size} titres • 100 % local",color=colors.onSurfaceVariant)} } }
  item { Row { TextButton(onClick={page="Bibliothèque"}){Text("Bibliothèque")};TextButton(onClick={page="Lecteur"}){Text("Lecteur")};TextButton(onClick={page="Historique"}){Text("Historique")} }; if(player.uri!=null) { Section("À l’écoute");TextButton(onClick={page="Lecteur"}){Text("${player.title} — ${player.artist}")} } }
  item { Section("Dernières écoutes") };songs(LibraryRules.resolve(history.take(5).map {it.uri},all),"home-history")
  item { Section("Récemment ajoutés") };songs(all.sortedByDescending {it.added}.take(5),"home-recent")
  item { Section("Vos favoris") };songs(all.filter {it.uri in favoriteSet}.take(5),"home-favorites")
  item { Section("Playlists");playlists.forEach { p -> TextButton(onClick={selected=p;page="Playlists"}){Text(p.name)} };OutlinedButton(onClick={create=true}){Text("Créer une playlist")} }
 }
 "Bibliothèque" -> {
  item { Section("${all.size} morceaux");OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Titre, artiste, album…")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true);SortPicker(sort,vm::setSort);Row { Button(onClick={vm.play(visible)},enabled=visible.isNotEmpty() && player.connected){Text("Tout écouter")};TextButton(onClick={vm.setCompact(!compact)}){Text(if(compact) "Pochettes" else "Liste compacte")} } }
  songs(visible,"library")
 }
 "Favoris" -> { val list=all.filter {it.uri in favoriteSet};item { Section("Favoris • ${list.size}");Button(onClick={vm.play(list)},enabled=list.isNotEmpty()){Text("Tout écouter")} };songs(list,"favorites") }
 "Historique" -> { item { Section("Dernières écoutes");TextButton(onClick={clear=true}){Text("Effacer l’historique")} };songs(LibraryRules.resolve(history.map {it.uri},all),"history") }
 "Playlists" -> {
  val p=selected
  if(p==null) {
   item { Section("Vos playlists");Button(onClick={create=true}){Text("Nouvelle playlist")} }
   if(playlists.isEmpty()) item { Empty("Votre première sélection","Créez une playlist, puis utilisez le menu d’un morceau pour l’ajouter.") }
   items(playlists,key={it.id}) { value -> Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){ TextButton(onClick={selected=value},modifier=Modifier.weight(1f)){Text(value.name)};IconButton(onClick={rename=value}){Icon(Icons.Rounded.Edit,"Renommer")};IconButton(onClick={delete=value}){Icon(Icons.Rounded.Delete,"Supprimer")}} }
  } else {
   val refs=entries.filter {it.playlistId==p.id}.sortedBy {it.position}.map {it.uri}
   val list=LibraryRules.resolve(refs,all)
   item { Section(p.name);Text("${list.size} accessibles / ${refs.size} références",color=colors.onSurfaceVariant);Button(onClick={vm.play(list)},enabled=list.isNotEmpty()){Text("Lire la playlist")};TextButton(onClick={page="Bibliothèque";selected=null}){Text("Choisir des morceaux dans la bibliothèque")} }
   // Keep unavailable references visible and removable; reorder uses database positions, not filtered indices.
   if(refs.isEmpty()) item { Empty("Playlist vide","Ajoutez des titres depuis le menu d’un morceau.") }
   itemsIndexed(refs,key={_,uri -> uri}) { i,uri -> val t=all.find {it.uri==uri};Row(verticalAlignment=Alignment.CenterVertically) {
    Column(Modifier.weight(1f).clickable(enabled=t!=null){ t?.let {vm.play(list,list.indexOf(it))} }.padding(vertical=12.dp)){Text(t?.title ?: "Fichier indisponible");Text(t?.artist ?: uri,maxLines=1,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
    IconButton(onClick={vm.move(p.id,i,i-1)},enabled=i>0){Icon(Icons.Rounded.KeyboardArrowUp,"Monter")};IconButton(onClick={vm.move(p.id,i,i+1)},enabled=i<refs.lastIndex){Icon(Icons.Rounded.KeyboardArrowDown,"Descendre")};IconButton(onClick={vm.remove(p.id,uri)}){Icon(Icons.Rounded.Close,"Retirer")}
   } }
  }
 }
 }
 item { Spacer(Modifier.height(24.dp)) }
 }
 }
 }
 }
 if(create) NameDialog("Créer une playlist",onDismiss={create=false},onSave=vm::create)
 rename?.let { p -> NameDialog("Renommer",p.name,{rename=null}){vm.rename(p.id,it)} }
 delete?.let { p -> AlertDialog(onDismissRequest={delete=null},title={Text("Supprimer ${p.name} ?")},text={Text("Les fichiers audio ne seront pas supprimés.")},confirmButton={TextButton(onClick={vm.delete(p.id);delete=null}){Text("Supprimer")}},dismissButton={TextButton(onClick={delete=null}){Text("Annuler")}}) }
 addTrack?.let { t -> AlertDialog(onDismissRequest={addTrack=null},title={Text("Ajouter à une playlist")},text={Column(Modifier.verticalScroll(rememberScrollState())) { if(playlists.isEmpty()) Text("Créez d’abord une playlist dans l’onglet Playlists.");playlists.forEach { p -> TextButton(onClick={vm.add(p.id,t.uri);addTrack=null}){Text(p.name)} } }},confirmButton={TextButton(onClick={addTrack=null}){Text("Fermer")}}) }
 if(clear) AlertDialog(onDismissRequest={clear=false},title={Text("Effacer l’historique ?")},confirmButton={TextButton(onClick={vm.clearHistory();clear=false}){Text("Effacer")}},dismissButton={TextButton(onClick={clear=false}){Text("Annuler")}})
 val notice=message ?: playError
 if(notice!=null) AlertDialog(onDismissRequest={vm.message.value=null;vm.playback.error.value=null},title={Text("Information de lecture")},text={Text(notice)},confirmButton={TextButton(onClick={vm.message.value=null;vm.playback.error.value=null}){Text("Compris")}})
 }
}
@Composable fun SortPicker(sort:Sort,onSelect:(Sort)->Unit) {
 var open by remember { mutableStateOf(false) }
 val labels=listOf("Titre","Artiste","Album","Durée","Date d’ajout")
 Box { TextButton(onClick={open=true}){Icon(Icons.Rounded.Sort,null);Text(" Trier : ${labels[sort.ordinal]}")};DropdownMenu(open,{open=false}){Sort.entries.forEach { v -> DropdownMenuItem({Text(labels[v.ordinal])},{onSelect(v);open=false}) }} }
}
