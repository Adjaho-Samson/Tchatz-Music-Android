package com.tchatz.music

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.tchatz.music.domain.PermissionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.UUID

/** Exercises the real activity, MediaStore permission, library scan and home sections. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class LibraryLaunchTest {
 @get:Rule val compose = createAndroidComposeRule<MainActivity>()

 @Test fun grantedAudioAppearsAndCanAlsoBeAFavorite() {
  val instrumentation = InstrumentationRegistry.getInstrumentation()
  val app = compose.activity.application as MusicApp
  val resolver = app.contentResolver
  val title = "Tchatz test ${UUID.randomUUID()}"
  val values = ContentValues().apply {
   put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.mp3")
   put(MediaStore.Audio.Media.TITLE, title)
   put(MediaStore.Audio.Media.ARTIST, "Test")
   put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
   put(MediaStore.Audio.Media.IS_MUSIC, 1)
  }
  val uri = checkNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
  try {
   // Shell grant mirrors a user accepting the dialog without depending on system UI text.
   val permission = PermissionPolicy.audioPermission(Build.VERSION.SDK_INT)
   instrumentation.uiAutomation.executeShellCommand("pm grant ${app.packageName} $permission").use { fd ->
    FileInputStream(fd.fileDescriptor).use { it.readBytes() }
   }
   compose.activityRule.scenario.recreate()
   compose.waitUntil(timeoutMillis = 20_000) {
    compose.onAllNodes(hasText("titres • 100 % local", substring = true)).fetchSemanticsNodes().any {
     Regex("[1-9][0-9]* titres").containsMatchIn(it.config.toString())
    }
   }
   compose.onNodeWithTag("main-list").performScrollToNode(hasText(title))
   compose.onAllNodesWithContentDescription("Favori")[0].performClick()
   // The same URI now appears in both "Récemment ajoutés" and "Vos favoris".
   compose.onNodeWithTag("main-list").performScrollToNode(hasText("Vos favoris"))
   compose.waitForIdle()
  } finally {
   runBlocking { app.db.dao().unfavorite(uri.toString()) }
   resolver.delete(uri, null, null)
  }
 }
}
