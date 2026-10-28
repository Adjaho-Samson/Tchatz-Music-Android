package com.tchatz.music.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.tchatz.music.playback.PlaybackState

@Composable fun PlayerScreen(p:PlaybackState,vm:MusicViewModel,onQueue:()->Unit,favorite:Boolean) {
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
  Text("LECTURE LOCALE",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
  Spacer(Modifier.height(20.dp));Cover(p.artwork,Modifier.widthIn(max=340.dp).fillMaxWidth().aspectRatio(1f));Spacer(Modifier.height(24.dp))
  Text(p.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(p.artist,color=MaterialTheme.colorScheme.onSurfaceVariant)
  var seek by remember { mutableStateOf<Float?>(null) }
  Slider(value=seek ?: p.position.toFloat().coerceAtMost(p.duration.toFloat()),onValueChange={seek=it},valueRange=0f..p.duration.coerceAtLeast(1).toFloat(),onValueChangeFinished={seek?.let {vm.playback.seek(it.toLong())};seek=null},enabled=p.uri!=null)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(time(seek?.toLong() ?: p.position));Text(time(p.duration))}
  Row(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
   IconButton(onClick=vm.playback::shuffle){Icon(Icons.Rounded.Shuffle,"Aléatoire",tint=if(p.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
   IconButton(onClick=vm.playback::previous,enabled=p.uri!=null){Icon(Icons.Rounded.SkipPrevious,"Précédent",Modifier.size(36.dp))}
   FilledIconButton(onClick=vm.playback::toggle,modifier=Modifier.size(72.dp),enabled=p.uri!=null){Icon(if(p.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,"Lecture / pause",Modifier.size(40.dp))}
   IconButton(onClick=vm.playback::next,enabled=p.uri!=null){Icon(Icons.Rounded.SkipNext,"Suivant",Modifier.size(36.dp))}
   IconButton(onClick=vm.playback::repeat){Icon(if(p.repeat==Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,"Répétition : ${p.repeat}",tint=if(p.repeat!=Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
   TextButton(onClick={p.uri?.let(vm::favorite)},enabled=p.uri!=null){Icon(if(favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null);Text(" Favori")}
   TextButton(onClick=onQueue){Icon(Icons.Rounded.QueueMusic,null);Text(" File • ${p.queue.size}")}
  }
  if(p.uri==null) Empty("Prêt pour votre musique","Sélectionnez un morceau dans la bibliothèque.")
 }
}
@Composable fun QueueScreen(p:PlaybackState,vm:MusicViewModel) {
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
  item {Section("File d’attente • ${p.queue.size}")}
  if(p.queue.isEmpty()) item { Empty("La file est vide","Lancez un morceau ou ajoutez des titres depuis leur menu.") }
  itemsIndexed(p.queue,key={i,_ -> i}) { i,item -> Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f).clickable {vm.playback.jump(i)}) {Text("${if(i==p.index) "▶ " else ""}${item.mediaMetadata.title}",color=if(i==p.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface);Text(item.mediaMetadata.artist?.toString() ?: "",style=MaterialTheme.typography.bodySmall)}
   IconButton(onClick={vm.playback.move(i,i-1)},enabled=i>0){Icon(Icons.Rounded.KeyboardArrowUp,"Monter")}
   IconButton(onClick={vm.playback.move(i,i+1)},enabled=i<p.queue.lastIndex){Icon(Icons.Rounded.KeyboardArrowDown,"Descendre")}
   IconButton(onClick={vm.playback.remove(i)}){Icon(Icons.Rounded.Close,"Retirer")}
  } }
 }
}
