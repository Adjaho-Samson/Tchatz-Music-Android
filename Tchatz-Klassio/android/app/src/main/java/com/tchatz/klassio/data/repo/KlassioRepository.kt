package com.tchatz.klassio.data.repo

import android.content.Context
import com.tchatz.klassio.data.ClassroomDetail
import com.tchatz.klassio.data.DocumentItem
import com.tchatz.klassio.data.JoinPayload
import com.tchatz.klassio.data.SessionState
import com.tchatz.klassio.data.local.CachedDocument
import com.tchatz.klassio.data.local.CachedSubject
import com.tchatz.klassio.data.local.DownloadedFile
import com.tchatz.klassio.data.local.KlassioDatabase
import com.tchatz.klassio.data.prefs.SessionStore
import com.tchatz.klassio.data.remote.AuthSession
import com.tchatz.klassio.data.remote.KlassioApi
import com.tchatz.klassio.data.remote.KlassioException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class KlassioRepository(
    private val context: Context,
    private val api: KlassioApi,
    private val db: KlassioDatabase,
    val store: SessionStore,
) {

    private val dao = db.dao()

    // ------------------------------------------------------------ Étudiant

    suspend fun join(code: String): SessionState {
        val clean = code.trim().uppercase().replace(" ", "")
        require(clean.length >= 6) { "Code de classe incomplet." }
        val payload: JoinPayload = api.joinClassroom(clean, deviceLabel())
        val c = payload.classroom
        val session = SessionState(
            token = payload.sessionToken,
            classroomId = c.id,
            className = c.className,
            institutionName = c.institutionName,
            fieldOfStudy = c.fieldOfStudy,
            level = c.level,
            studyYear = c.studyYear,
            academicYear = c.academicYear,
            joinCode = c.joinCode.ifBlank { clean },
        )
        store.save(session)
        registerDeviceQuietly(session)
        return session
    }

    suspend fun registerDeviceQuietly(session: SessionState) {
        runCatching {
            api.registerDevice(
                token = session.token,
                deviceId = store.deviceIdentifier(),
                label = deviceLabel(),
                notificationsEnabled = store.notificationsEnabled(),
            )
        }
    }

    fun sessions(): List<SessionState> = store.sessions()

    fun leave(classroomId: String) {
        store.remove(classroomId)
    }

    /** Actualise classes/matières/documents depuis Supabase (source de vérité). */
    suspend fun refresh(session: SessionState): ClassroomDetail {
        val detail = api.classroomDetail(session.token)
        dao.upsertSubjects(
            detail.subjects.map {
                CachedSubject(
                    classroomId = session.classroomId,
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    position = it.position,
                    documentCount = it.documentCount,
                )
            },
        )
        val documents = api.documents(session.token, limit = 100)
        cacheDocuments(session.classroomId, documents)
        return detail
    }

    private suspend fun cacheDocuments(classroomId: String, documents: List<DocumentItem>) {
        dao.upsertDocuments(documents.map { it.toCached(classroomId) })
        if (documents.isNotEmpty()) {
            dao.pruneDocuments(classroomId, documents.map { it.id })
        }
    }

    fun cachedSubjects(classroomId: String): Flow<List<CachedSubject>> = dao.subjects(classroomId)

    fun cachedDocuments(classroomId: String): Flow<List<CachedDocument>> = dao.documents(classroomId)

    fun searchDocuments(classroomId: String, subjectId: String?, search: String): Flow<List<CachedDocument>> =
        dao.searchDocuments(classroomId, subjectId, search.trim())

    suspend fun fetchDocuments(session: SessionState, subjectId: String?, search: String?): List<DocumentItem> {
        val documents = api.documents(session.token, subjectId, search, limit = 100)
        cacheDocuments(session.classroomId, documents)
        return documents
    }

    /**
     * Téléchargement réel hors connexion : URL signée courte puis écriture du fichier
     * dans le stockage privé de l'application (accessible sans réseau ensuite).
     */
    suspend fun download(session: SessionState, document: DocumentItem, onProgress: (Float) -> Unit): DownloadedFile {
        val signed = api.signedUrl(session.token, document.id)
        val url = signed.url ?: throw KlassioException("SIGN_FAILED", "Lien de téléchargement indisponible.")
        val target = File(downloadsDir(), safeName(document.id + "_" + document.fileName))
        api.downloadTo(url, target, onProgress)
        val record = DownloadedFile(
            documentId = document.id,
            classroomId = session.classroomId,
            title = document.title,
            subjectName = document.subjectName,
            fileName = document.fileName,
            localPath = target.absolutePath,
            mimeType = mimeFor(document.fileType),
            fileSize = target.length(),
            downloadedAt = System.currentTimeMillis(),
        )
        dao.saveDownload(record)
        return record
    }

    fun downloads(): Flow<List<DownloadedFile>> = dao.downloads()

    suspend fun downloadedFile(documentId: String): DownloadedFile? = dao.download(documentId)

    suspend fun deleteDownload(documentId: String) {
        dao.download(documentId)?.let { File(it.localPath).delete() }
        dao.deleteDownload(documentId)
    }

    fun downloadsDir(): File = File(context.filesDir, "documents").apply { mkdirs() }

    suspend fun updates(session: SessionState): List<DocumentItem> {
        val since = store.lastSeenUpdates(session.classroomId) ?: return emptyList()
        val items = api.newUpdates(session.token, since)
        if (items.isNotEmpty()) cacheDocuments(session.classroomId, items)
        return items
    }

    fun markUpdatesSeen(session: SessionState, isoDate: String = nowIso()) {
        store.setLastSeenUpdates(session.classroomId, isoDate)
    }

    fun lastSeen(session: SessionState): String? = store.lastSeenUpdates(session.classroomId)

    // ---------------------------------------------------------- Responsable

    suspend fun signIn(email: String, password: String): AuthSession {
        val auth = api.signIn(email.trim(), password)
        store.saveOwnerSession(auth.accessToken, auth.refreshToken, auth.user?.email ?: email.trim())
        return auth
    }

    suspend fun signUp(email: String, password: String, fullName: String): AuthSession {
        val auth = api.signUp(email.trim(), password, fullName.trim())
        store.saveOwnerSession(auth.accessToken, auth.refreshToken, auth.user?.email ?: email.trim())
        return auth
    }

    suspend fun resetPassword(email: String) = api.requestPasswordReset(email.trim())

    suspend fun validAccessToken(): String? {
        val token = store.ownerAccessToken() ?: return null
        val refresh = store.ownerRefreshToken()
        return token.takeIf { it.isNotBlank() } ?: refresh?.let { runCatching { api.refresh(it) }.getOrNull() }
            ?.also { store.saveOwnerSession(it.accessToken, it.refreshToken, store.ownerEmail()) }
            ?.accessToken
    }

    /** URL signée pour un document étudiant (via Edge Function, autorisation en base). */
    suspend fun signedUrl(session: SessionState, documentId: String): String? =
        api.signedUrl(session.token, documentId).url

    suspend fun refreshOwnerToken(): String? {
        val refresh = store.ownerRefreshToken() ?: return null
        val auth = api.refresh(refresh)
        store.saveOwnerSession(auth.accessToken, auth.refreshToken, store.ownerEmail())
        return auth.accessToken
    }

    fun signOut() = store.clearOwnerSession()

    fun ownerEmail(): String? = store.ownerEmail()

    // ------------------------------------------------------------- Divers

    private fun deviceLabel(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim().take(60)

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    companion object {
        fun mimeFor(extension: String): String = when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }

        fun nowIso(): String {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format.format(Date())
        }

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.FRANCE, "%.0f Ko", bytes / 1024.0)
            else -> "$bytes o"
        }
    }

    private fun DocumentItem.toCached(classroomId: String) = CachedDocument(
        classroomId = classroomId,
        id = id,
        subjectId = subjectId,
        subjectName = subjectName,
        title = title,
        description = description,
        fileName = fileName,
        fileType = fileType,
        fileSize = fileSize,
        teacherDisplayName = teacherDisplayName,
        createdAt = createdAt,
    )

    fun cachedToItem(document: CachedDocument) = DocumentItem(
        id = document.id,
        subjectId = document.subjectId,
        subjectName = document.subjectName,
        title = document.title,
        description = document.description,
        fileName = document.fileName,
        fileType = document.fileType,
        fileSize = document.fileSize,
        teacherDisplayName = document.teacherDisplayName,
        createdAt = document.createdAt,
    )
}

fun Flow<List<CachedDocument>>.asItems(repo: KlassioRepository) =
    map { list -> list.map(repo::cachedToItem) }
