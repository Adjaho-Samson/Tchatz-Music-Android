package com.tchatz.music
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.tchatz.music.data.*
import com.tchatz.music.library.LocalLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PersistenceTest {
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 private lateinit var db:MusicDatabase
 @Before fun setup() { context.deleteDatabase("test.db");db=Room.databaseBuilder(context,MusicDatabase::class.java,"test.db").build() }
 @After fun close() { db.close();context.deleteDatabase("test.db") }
 @Test fun favoritesSurviveReopen()=runBlocking {
  db.dao().favorite(Favorite("content://test/1"));db.close()
  db=Room.databaseBuilder(context,MusicDatabase::class.java,"test.db").build()
  assertEquals(1,db.dao().favorites().first().size)
  db.dao().unfavorite("content://test/1");assertTrue(db.dao().favorites().first().isEmpty())
 }
 @Test fun playlistsAppendRenameReorderCascade()=runBlocking {
  val d=db.dao();val id=d.create(Playlist(name="Voyage"))
  d.append(id,"a");d.append(id,"b");d.append(id,"a")
  assertEquals(2,d.members(id).size);d.reorder(id,0,1)
  assertEquals("b",d.members(id).first().uri)
  d.rename(id,"Route");assertEquals("Route",d.playlists().first().single().name)
  d.remove(id,"a");assertEquals(1,d.members(id).size)
  d.delete(id);assertTrue(d.entries().first().isEmpty())
 }
 @Test fun historyAndClear()=runBlocking {val d=db.dao();d.played(Recent("a",1));d.played(Recent("b",2));assertEquals("b",d.history().first().first().uri);d.clearHistory();assertTrue(d.history().first().isEmpty())}
 @Test fun missingFileIsNotAccessible()=runBlocking {
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
  val lib=LocalLibrary(context,scope)
  try { assertFalse(lib.accessible("content://media/external/audio/media/9223372036854775807")) } finally {lib.close();scope.cancel()}
 }
}
