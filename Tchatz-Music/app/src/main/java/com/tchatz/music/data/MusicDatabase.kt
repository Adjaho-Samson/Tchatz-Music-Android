package com.tchatz.music.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Favorite(@PrimaryKey val uri: String)
@Entity data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(primaryKeys = ["playlistId", "uri"], foreignKeys = [ForeignKey(entity=Playlist::class, parentColumns=["id"], childColumns=["playlistId"], onDelete=ForeignKey.CASCADE)])
data class PlaylistTrack(val playlistId: Long, val uri: String, val position: Int)
@Entity data class Recent(@PrimaryKey val uri: String, val playedAt: Long)
@Dao interface MusicDao {
 @Query("SELECT * FROM Favorite") fun favorites(): Flow<List<Favorite>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun favorite(value: Favorite)
 @Query("DELETE FROM Favorite WHERE uri=:uri") suspend fun unfavorite(uri:String)
 @Query("SELECT * FROM Playlist ORDER BY name COLLATE NOCASE") fun playlists(): Flow<List<Playlist>>
 @Insert suspend fun create(value: Playlist): Long
 @Query("UPDATE Playlist SET name=:name WHERE id=:id") suspend fun rename(id:Long,name:String)
 @Query("DELETE FROM Playlist WHERE id=:id") suspend fun delete(id:Long)
 @Query("SELECT * FROM PlaylistTrack ORDER BY position") fun entries(): Flow<List<PlaylistTrack>>
 @Query("SELECT * FROM PlaylistTrack WHERE playlistId=:id ORDER BY position") suspend fun members(id:Long):List<PlaylistTrack>
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun add(value: PlaylistTrack)
 @Query("DELETE FROM PlaylistTrack WHERE playlistId=:id AND uri=:uri") suspend fun remove(id:Long,uri:String)
 @Query("UPDATE PlaylistTrack SET position=:position WHERE playlistId=:id AND uri=:uri") suspend fun position(id:Long,uri:String,position:Int)
 @Transaction suspend fun append(id:Long,uri:String) { add(PlaylistTrack(id,uri,(members(id).maxOfOrNull { it.position } ?: -1)+1)) }
 @Transaction suspend fun reorder(id:Long,from:Int,to:Int) {
  val list=members(id).toMutableList()
  if(from !in list.indices || to !in list.indices) return
  list.add(to,list.removeAt(from)); list.forEachIndexed { i,v -> position(id,v.uri,i) }
 }
 @Query("SELECT * FROM Recent ORDER BY playedAt DESC LIMIT 100") fun history():Flow<List<Recent>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun played(value:Recent)
 @Query("DELETE FROM Recent") suspend fun clearHistory()
}
@Database(entities=[Favorite::class,Playlist::class,PlaylistTrack::class,Recent::class],version=1,exportSchema=true)
abstract class MusicDatabase: RoomDatabase() { abstract fun dao():MusicDao }
