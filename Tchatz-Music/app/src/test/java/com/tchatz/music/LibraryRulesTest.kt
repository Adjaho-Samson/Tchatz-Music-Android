package com.tchatz.music
import com.tchatz.music.domain.*
import org.junit.Assert.*
import org.junit.Test
class LibraryRulesTest {
 private val tracks=listOf(Track("a","Zèbre","Alice","Live",300,1),Track("b","Alpha","Bob","Studio",100,3))
 @Test fun searchIgnoresCase() { assertEquals(listOf(tracks[0]),LibraryRules.select(tracks," ALICE ",Sort.TITLE)) }
 @Test fun searchAlbum() { assertEquals("b",LibraryRules.select(tracks,"studio",Sort.TITLE).single().uri) }
 @Test fun titleSort() { assertEquals("b",LibraryRules.select(tracks,"",Sort.TITLE).first().uri) }
 @Test fun artistSort() { assertEquals("a",LibraryRules.select(tracks,"",Sort.ARTIST).first().uri) }
 @Test fun albumSort() { assertEquals("a",LibraryRules.select(tracks,"",Sort.ALBUM).first().uri) }
 @Test fun durationSort() { assertEquals("b",LibraryRules.select(tracks,"",Sort.DURATION).first().uri) }
 @Test fun addedSort() { assertEquals("b",LibraryRules.select(tracks,"",Sort.ADDED).first().uri) }
 @Test fun emptyLibrary() { assertTrue(LibraryRules.select(emptyList(),"",Sort.TITLE).isEmpty()) }
 @Test fun noMatch() { assertTrue(LibraryRules.select(tracks,"absent",Sort.TITLE).isEmpty()) }
 @Test fun deletedReference() { assertEquals(listOf(tracks[1]),LibraryRules.resolve(listOf("missing","b"),tracks)) }
 @Test fun referenceOrder() { assertEquals(listOf("b","a"),LibraryRules.resolve(listOf("b","a"),tracks).map {it.uri}) }
 @Test fun audioPermission33() { assertEquals("android.permission.READ_MEDIA_AUDIO",PermissionPolicy.audioPermission(33)) }
 @Test fun legacyPermission32() { assertEquals("android.permission.READ_EXTERNAL_STORAGE",PermissionPolicy.audioPermission(32)) }
 @Test fun queueMove() { assertEquals(listOf("b","a"),QueueRules.move(listOf("a","b"),0,1)) }
 @Test fun invalidQueueMove() { assertEquals(listOf("a"),QueueRules.move(listOf("a"),0,5)) }
}
