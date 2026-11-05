package com.tchatz.klassio.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Cache hors connexion. La source de vérité reste Supabase :
 * ce cache permet uniquement de consulter les classes, matières et
 * documents déjà synchronisés, ainsi que les fichiers téléchargés.
 */

@Entity(tableName = "cached_subjects", primaryKeys = ["classroomId", "id"])
data class CachedSubject(
    val classroomId: String,
    val id: String,
    val name: String,
    val description: String?,
    val position: Int,
    val documentCount: Long,
)

@Entity(
    tableName = "cached_documents",
    primaryKeys = ["classroomId", "id"],
    indices = [Index("subjectId")],
)
data class CachedDocument(
    val classroomId: String,
    val id: String,
    val subjectId: String,
    val subjectName: String,
    val title: String,
    val description: String?,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val teacherDisplayName: String,
    val createdAt: String,
)

@Entity(tableName = "downloaded_files")
data class DownloadedFile(
    @PrimaryKey val documentId: String,
    val classroomId: String,
    val title: String,
    val subjectName: String,
    val fileName: String,
    val localPath: String,
    val mimeType: String,
    val fileSize: Long,
    val downloadedAt: Long,
)

@Dao
interface KlassioDao {
    @Query("SELECT * FROM cached_subjects WHERE classroomId = :classroomId ORDER BY position, name")
    fun subjects(classroomId: String): Flow<List<CachedSubject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubjects(subjects: List<CachedSubject>)

    @Query("DELETE FROM cached_subjects WHERE classroomId = :classroomId")
    suspend fun clearSubjects(classroomId: String)

    @Query("SELECT * FROM cached_documents WHERE classroomId = :classroomId ORDER BY createdAt DESC")
    fun documents(classroomId: String): Flow<List<CachedDocument>>

    @Query("SELECT * FROM cached_documents WHERE classroomId = :classroomId AND subjectId = :subjectId ORDER BY createdAt DESC")
    fun documentsBySubject(classroomId: String, subjectId: String): Flow<List<CachedDocument>>

    @Query(
        """
        SELECT * FROM cached_documents
        WHERE classroomId = :classroomId
          AND (:subjectId IS NULL OR subjectId = :subjectId)
          AND (:search = '' OR title LIKE '%' || :search || '%' OR fileName LIKE '%' || :search || '%')
        ORDER BY createdAt DESC
        """,
    )
    fun searchDocuments(classroomId: String, subjectId: String?, search: String): Flow<List<CachedDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<CachedDocument>)

    @Query("DELETE FROM cached_documents WHERE classroomId = :classroomId AND id NOT IN (:keep)")
    suspend fun pruneDocuments(classroomId: String, keep: List<String>)

    @Query("SELECT * FROM downloaded_files ORDER BY downloadedAt DESC")
    fun downloads(): Flow<List<DownloadedFile>>

    @Query("SELECT * FROM downloaded_files WHERE documentId = :documentId")
    suspend fun download(documentId: String): DownloadedFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownload(file: DownloadedFile)

    @Query("DELETE FROM downloaded_files WHERE documentId = :documentId")
    suspend fun deleteDownload(documentId: String)

    @Query("SELECT * FROM downloaded_files")
    suspend fun allDownloads(): List<DownloadedFile>
}

@Database(
    entities = [CachedSubject::class, CachedDocument::class, DownloadedFile::class],
    version = 1,
    exportSchema = true,
)
abstract class KlassioDatabase : RoomDatabase() {
    abstract fun dao(): KlassioDao

    companion object {
        fun build(context: Context): KlassioDatabase =
            Room.databaseBuilder(context, KlassioDatabase::class.java, "klassio.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
