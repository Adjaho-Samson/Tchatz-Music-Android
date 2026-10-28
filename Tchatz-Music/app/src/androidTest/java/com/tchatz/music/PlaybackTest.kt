package com.tchatz.music
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PlaybackTest {
 @Test fun queueTransitionsAndModes() {
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
   val p=ExoPlayer.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build()
   try {
    p.setMediaItems(listOf(MediaItem.fromUri(Uri.parse("content://test/1")),MediaItem.fromUri(Uri.parse("content://test/2"))))
    assertEquals(0,p.currentMediaItemIndex)
    p.seekToNextMediaItem();assertEquals(1,p.currentMediaItemIndex)
    p.seekToPreviousMediaItem();assertEquals(0,p.currentMediaItemIndex)
    p.shuffleModeEnabled=true;assertTrue(p.shuffleModeEnabled)
    p.repeatMode=Player.REPEAT_MODE_ONE;assertEquals(Player.REPEAT_MODE_ONE,p.repeatMode)
    p.moveMediaItem(0,1);assertEquals(1,p.currentMediaItemIndex)
    p.removeMediaItem(0);assertEquals(1,p.mediaItemCount)
   } finally {p.release()}
  }
 }
}
