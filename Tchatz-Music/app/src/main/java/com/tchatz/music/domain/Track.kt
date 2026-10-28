package com.tchatz.music.domain

data class Track(val uri: String, val title: String, val artist: String = "Artiste inconnu", val album: String = "Album inconnu", val duration: Long = 0, val added: Long = 0, val artwork: String? = null, val fileName: String = "", val size: Long = 0)
enum class Sort { TITLE, ARTIST, ALBUM, DURATION, ADDED }
object LibraryRules {
 fun select(tracks: List<Track>, query: String, sort: Sort): List<Track> {
  val q = query.trim()
  val found = tracks.filter { q.isEmpty() || listOf(it.title,it.artist,it.album,it.fileName).any { v -> v.contains(q,true) } }
  return when(sort) {
   Sort.TITLE -> found.sortedBy { it.title.lowercase() }
   Sort.ARTIST -> found.sortedWith(compareBy<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
   Sort.ALBUM -> found.sortedBy { it.album.lowercase() }
   Sort.DURATION -> found.sortedBy { it.duration }
   Sort.ADDED -> found.sortedByDescending { it.added }
  }
 }
 fun resolve(uris: List<String>, tracks: List<Track>): List<Track> { val map=tracks.associateBy { it.uri }; return uris.mapNotNull { map[it] } }
}
object PermissionPolicy { fun audioPermission(sdk: Int) = if (sdk >= 33) "android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE" }
object QueueRules {
 fun move(items: List<String>, from: Int, to: Int): List<String> = items.toMutableList().apply { if(from in indices && to in indices) add(to,removeAt(from)) }
}
