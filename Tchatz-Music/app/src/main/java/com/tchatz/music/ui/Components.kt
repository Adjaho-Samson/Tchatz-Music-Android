package com.tchatz.music.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tchatz.music.R
import com.tchatz.music.domain.Track

fun time(ms:Long):String { val s=ms.coerceAtLeast(0)/1000; return "%d:%02d".format(s/60,s%60) }
@Composable fun Cover(uri:String?,modifier:Modifier=Modifier) {
 Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
  Image(painterResource(R.drawable.brand_mark),"Logo Tchatz Music",Modifier.fillMaxSize().padding(4.dp))
  if(uri!=null) AsyncImage(uri,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
 }
}
@Composable fun Section(title:String) { Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=20.dp,bottom=12.dp)) }
@Composable fun Empty(title:String,detail:String) { Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Rounded.LibraryMusic,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)); Text(title,style=MaterialTheme.typography.titleMedium); Text(detail,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp)) } }
@Composable fun TrackRow(t:Track,favorite:Boolean,compact:Boolean,onPlay:()->Unit,onFavorite:()->Unit,onQueue:()->Unit,onPlaylist:()->Unit,extra:@Composable RowScope.()->Unit = {}) {
 var menu by remember { mutableStateOf(false) }
 Row(Modifier.fillMaxWidth().clickable(onClick=onPlay).padding(vertical=if(compact) 4.dp else 8.dp),verticalAlignment=Alignment.CenterVertically) {
  if(!compact) { Cover(t.artwork,Modifier.size(52.dp)); Spacer(Modifier.width(12.dp)) }
  Column(Modifier.weight(1f)) { Text(t.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium); Text("${t.artist} • ${time(t.duration)}",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
  extra()
  IconButton(onClick=onFavorite) { Icon(if(favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,"Favori",tint=if(favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
  Box { IconButton(onClick={menu=true}) { Icon(Icons.Rounded.MoreVert,"Options du morceau") }; DropdownMenu(menu,{menu=false}) {
   DropdownMenuItem({Text("Ajouter à la file")},{onQueue();menu=false})
   DropdownMenuItem({Text("Ajouter à une playlist")},{onPlaylist();menu=false})
   DropdownMenuItem({Text("${t.album}\n${t.fileName}\n${t.size/1024} Ko\n${t.uri}")},{menu=false})
  } }
 }
}
@Composable fun NameDialog(title:String,initial:String="",onDismiss:()->Unit,onSave:(String)->Unit) {
 var name by remember { mutableStateOf(initial) }
 AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(name,{name=it},label={Text("Nom de la playlist")},singleLine=true)},confirmButton={TextButton(onClick={onSave(name);onDismiss()},enabled=name.isNotBlank()){Text("Enregistrer")}},dismissButton={TextButton(onClick=onDismiss){Text("Annuler")}})
}
