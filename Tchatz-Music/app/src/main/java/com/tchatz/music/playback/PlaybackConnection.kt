package com.tchatz.music.playback
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.*
import com.tchatz.music.domain.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class PlaybackState(val title:String="Aucun morceau",val artist:String="",val uri:String?=null,val artwork:String?=null,val playing:Boolean=false,val position:Long=0,val duration:Long=0,val shuffle:Boolean=false,val repeat:Int=Player.REPEAT_MODE_OFF,val queue:List<MediaItem> = emptyList(),val index:Int=-1,val connected:Boolean=false)
class PlaybackConnection(context:Context,private val scope:CoroutineScope) {
 val state=MutableStateFlow(PlaybackState())
 val error=MutableStateFlow<String?>(null)
 private val future=MediaController.Builder(context,SessionToken(context,ComponentName(context,MusicService::class.java))).buildAsync()
 var controller:MediaController?=null; private set
 private val listener=object:Player.Listener {
  override fun onEvents(player:Player,events:Player.Events) { update(events.contains(Player.EVENT_TIMELINE_CHANGED)) }
  override fun onPlayerError(e:PlaybackException) { error.value="Lecture impossible : fichier inaccessible ou codec non pris en charge (${e.errorCodeName}). Retirez-le de la file ou passez au suivant."; update() }
 }
 private val ticker:Job
 init {
  future.addListener({ runCatching { future.get() }.onSuccess { controller=it; it.addListener(listener); update(true) }.onFailure { error.value="Connexion au service audio impossible." } },ContextCompat.getMainExecutor(context))
  ticker=scope.launch { while(isActive) { update(); delay(500) } }
 }
 private fun update(rebuildQueue:Boolean=false) { controller?.let { p ->
  val item=p.currentMediaItem
  state.value=PlaybackState(item?.mediaMetadata?.title?.toString() ?: "Aucun morceau",item?.mediaMetadata?.artist?.toString() ?: "",item?.mediaId,item?.mediaMetadata?.artworkUri?.toString(),p.isPlaying,p.currentPosition.coerceAtLeast(0),p.duration.coerceAtLeast(0),p.shuffleModeEnabled,p.repeatMode,if(rebuildQueue) (0 until p.mediaItemCount).map { p.getMediaItemAt(it) } else state.value.queue,p.currentMediaItemIndex,true)
 } }
 fun play(tracks:List<Track>,index:Int=0) { controller?.let { p -> if(tracks.isNotEmpty()) { error.value=null; p.setMediaItems(tracks.map { item(it) },index.coerceIn(tracks.indices),0); p.prepare(); p.play() } } }
 fun enqueue(t:Track) { controller?.let { it.addMediaItem(item(t)); if(it.playbackState==Player.STATE_IDLE) it.prepare() } }
 fun toggle() { controller?.let { if(it.isPlaying) it.pause() else { if(it.playbackState==Player.STATE_IDLE) it.prepare(); it.play() } } }
 fun next() { controller?.let { it.seekToNextMediaItem(); if(it.playbackState==Player.STATE_IDLE && it.mediaItemCount>0) { it.prepare(); it.play() } } }
 fun previous() { controller?.let { it.seekToPrevious(); if(it.playbackState==Player.STATE_IDLE && it.mediaItemCount>0) { it.prepare(); it.play() } } }
 fun seek(v:Long) { controller?.seekTo(v) }
 fun shuffle() { controller?.let { it.shuffleModeEnabled=!it.shuffleModeEnabled } }
 fun repeat() { controller?.let { it.repeatMode=when(it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
 fun remove(index:Int) { controller?.let { if(index in 0 until it.mediaItemCount) it.removeMediaItem(index) } }
 fun move(from:Int,to:Int) { controller?.let { if(from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from,to) } }
 fun jump(index:Int) { controller?.let { it.seekToDefaultPosition(index); if(it.playbackState==Player.STATE_IDLE) it.prepare(); it.play() } }
 fun close() { ticker.cancel(); controller?.removeListener(listener); MediaController.releaseFuture(future) }
 private fun item(t:Track)=MediaItem.Builder().setMediaId(t.uri).setUri(t.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).setArtworkUri(t.artwork?.let(Uri::parse)).build()).build()
}
