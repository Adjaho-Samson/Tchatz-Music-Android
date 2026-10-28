package com.tchatz.music.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tchatz.music.MusicApp
import com.tchatz.music.data.*
import com.tchatz.music.domain.*
import com.tchatz.music.library.LocalLibrary
import com.tchatz.music.playback.PlaybackConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MusicViewModel(application:Application):AndroidViewModel(application) {
 private val app=application as MusicApp
 val dao=app.db.dao()
 val library=LocalLibrary(app,viewModelScope)
 val playback=PlaybackConnection(app,viewModelScope)
 val favorites=dao.favorites().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val playlists=dao.playlists().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val entries=dao.entries().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val history=dao.history().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val sort=app.preferences.sort.stateIn(viewModelScope,SharingStarted.Eagerly,Sort.TITLE)
 val compact=app.preferences.compact.stateIn(viewModelScope,SharingStarted.Eagerly,false)
 val message=MutableStateFlow<String?>(null)
 init { viewModelScope.launch { app.preferences.folder.collect { library.folder=it; library.refresh() } } }
 fun favorite(uri:String) { viewModelScope.launch { if(favorites.value.any { it.uri==uri }) dao.unfavorite(uri) else dao.favorite(Favorite(uri)) } }
 fun create(name:String) { if(name.isNotBlank()) viewModelScope.launch { dao.create(Playlist(name=name.trim())) } }
 fun rename(id:Long,name:String) { if(name.isNotBlank()) viewModelScope.launch { dao.rename(id,name.trim()) } }
 fun delete(id:Long) { viewModelScope.launch { dao.delete(id) } }
 fun add(id:Long,uri:String) { viewModelScope.launch { dao.append(id,uri) } }
 fun remove(id:Long,uri:String) { viewModelScope.launch { dao.remove(id,uri) } }
 fun move(id:Long,from:Int,to:Int) { viewModelScope.launch { dao.reorder(id,from,to) } }
 fun clearHistory() { viewModelScope.launch { dao.clearHistory() } }
 fun setSort(v:Sort) { viewModelScope.launch { app.preferences.sort(v) } }
 fun setCompact(v:Boolean) { viewModelScope.launch { app.preferences.compact(v) } }
 fun folder(uri:String?) { viewModelScope.launch { app.preferences.folder(uri) } }
 fun play(list:List<Track>,index:Int=0) {
  if(list.isEmpty()) return
  if(!playback.state.value.connected) { message.value="Le service audio se connecte. Réessayez dans un instant."; return }
  viewModelScope.launch {
   val selected=list[index.coerceIn(list.indices)]
   if(!library.accessible(selected.uri)) { message.value="Ce fichier a été déplacé, supprimé ou son accès a été révoqué. Actualisez la bibliothèque."; library.refresh(); return@launch }
   playback.play(list,index)
  }
 }
 fun enqueue(t:Track) { viewModelScope.launch { if(library.accessible(t.uri)) playback.enqueue(t) else message.value="Fichier inaccessible. Actualisez la bibliothèque." } }
 override fun onCleared() { library.close(); playback.close(); super.onCleared() }
}
