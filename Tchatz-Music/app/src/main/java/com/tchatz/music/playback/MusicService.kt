package com.tchatz.music.playback
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.tchatz.music.*
import com.tchatz.music.data.Recent
import kotlinx.coroutines.launch

class MusicService:MediaSessionService() {
 private var session:MediaSession?=null
 override fun onCreate() {
  super.onCreate()
  val player=ExoPlayer.Builder(this).build().apply {
   setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
   setHandleAudioBecomingNoisy(true)
   setWakeMode(C.WAKE_MODE_LOCAL)
  }
  val app=application as MusicApp
  var lastRecorded:String?=null
  fun record() {
   val uri=player.currentMediaItem?.mediaId ?: return
   if(player.isPlaying && uri!=lastRecorded) { lastRecorded=uri; app.scope.launch { app.db.dao().played(Recent(uri,System.currentTimeMillis())) } }
  }
  player.addListener(object:Player.Listener {
   override fun onIsPlayingChanged(isPlaying:Boolean) { record() }
   override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int) { lastRecorded=null; record() }
  })
  val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  session=MediaSession.Builder(this,player).setSessionActivity(intent).build()
 }
 override fun onGetSession(controllerInfo:MediaSession.ControllerInfo):MediaSession?=session
 override fun onDestroy() { session?.run { player.release(); release() }; session=null; super.onDestroy() }
}
