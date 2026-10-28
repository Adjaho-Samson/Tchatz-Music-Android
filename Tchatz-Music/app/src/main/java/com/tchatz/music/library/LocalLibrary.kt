package com.tchatz.music.library

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.tchatz.music.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LocalLibrary(private val context:Context, private val scope:CoroutineScope) {
 val tracks=MutableStateFlow<List<Track>>(emptyList())
 val loading=MutableStateFlow(false)
 val error=MutableStateFlow<String?>(null)
 private var job:Job?=null
 var folder:String?=null
 private val observer=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange:Boolean) { refresh(600) } }
 init { context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,true,observer) }
 fun refresh(delayMs:Long=0) { job?.cancel(); job=scope.launch {
  delay(delayMs); loading.value=true
  try { error.value=null; tracks.value=withContext(Dispatchers.IO) { scan() } }
  catch(e:Exception) { if(e is CancellationException) throw e; error.value="Accès audio impossible. Vérifiez les autorisations ou le dossier sélectionné." }
  finally { loading.value=false }
 } }
 private fun scan():List<Track> {
  val result=mutableListOf<Track>()
  if(ContextCompat.checkSelfPermission(context,PermissionPolicy.audioPermission(Build.VERSION.SDK_INT))==PackageManager.PERMISSION_GRANTED) {
   val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
   val cols=arrayOf("_id","title","artist","album","duration","date_added","album_id","_display_name","_size")
   context.contentResolver.query(base,cols,null,null,null)?.use { c ->
    fun text(key:String)=c.getString(c.getColumnIndexOrThrow(key))?.takeUnless { it.isBlank() || it=="<unknown>" }
    fun num(key:String)=c.getLong(c.getColumnIndexOrThrow(key))
    while(c.moveToNext()) {
     val name=text("_display_name") ?: "Audio"
     result+=Track(ContentUris.withAppendedId(base,num("_id")).toString(), text("title") ?: name.substringBeforeLast('.'), text("artist") ?: "Artiste inconnu",text("album") ?: "Album inconnu",num("duration").coerceAtLeast(0),num("date_added"),ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"),num("album_id")).toString(),name,num("_size"))
    }
   }
  }
  folder?.let { tree ->
   try {
    val root=DocumentFile.fromTreeUri(context,Uri.parse(tree))
    val pending=java.util.ArrayDeque<DocumentFile>(); if(root!=null) pending.add(root)
    while(pending.isNotEmpty()) {
     val dir=pending.removeFirst()
     for(f in dir.listFiles()) {
      if(f.isDirectory) { pending.add(f); continue }
      val name=f.name ?: "Audio"
      if(f.type?.startsWith("audio/")!=true && name.substringAfterLast('.').lowercase() !in setOf("mp3","m4a","aac","flac","wav","ogg","opus")) continue
      val r=MediaMetadataRetriever()
      try {
       r.setDataSource(context,f.uri)
       fun meta(k:Int)=r.extractMetadata(k)?.takeIf { it.isNotBlank() }
       result+=Track(f.uri.toString(),meta(7) ?: name.substringBeforeLast('.'),meta(2) ?: "Artiste inconnu",meta(1) ?: "Album inconnu",meta(9)?.toLongOrNull() ?: 0,f.lastModified()/1000,null,name,f.length())
      } catch(_:Exception) { result+=Track(f.uri.toString(),name,fileName=name,size=f.length()) }
      finally { r.release() }
     }
    }
   } catch(_:SecurityException) { error.value="L’accès au dossier a été révoqué. Choisissez-le à nouveau." }
  }
  return result.distinctBy { it.uri }
 }
 suspend fun accessible(uri:String):Boolean=withContext(Dispatchers.IO) { runCatching { context.contentResolver.openAssetFileDescriptor(Uri.parse(uri),"r")?.use { true } ?: false }.getOrDefault(false) }
 fun close() { job?.cancel(); context.contentResolver.unregisterContentObserver(observer) }
}
