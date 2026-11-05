package com.tchatz.klassio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchatz.klassio.data.DocumentItem
import com.tchatz.klassio.data.SessionState
import com.tchatz.klassio.data.local.CachedDocument
import com.tchatz.klassio.data.local.CachedSubject
import com.tchatz.klassio.data.local.DownloadedFile
import com.tchatz.klassio.data.remote.KlassioException
import com.tchatz.klassio.data.repo.KlassioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudentUiState(
    val loading: Boolean = true,
    val joining: Boolean = false,
    val refreshing: Boolean = false,
    val sessions: List<SessionState> = emptyList(),
    val active: SessionState? = null,
    val subjects: List<CachedSubject> = emptyList(),
    val documents: List<CachedDocument> = emptyList(),
    val downloads: List<DownloadedFile> = emptyList(),
    val error: String? = null,
    val offline: Boolean = false,
    val search: String = "",
    val subjectFilter: String? = null,
    val downloading: Map<String, Float> = emptyMap(),
    val notificationsEnabled: Boolean = true,
    val pendingClassroomId: String? = null,
)

class StudentViewModel(private val repo: KlassioRepository) : ViewModel() {

    private val _state = MutableStateFlow(StudentUiState(sessions = repo.sessions()))
    val state: StateFlow<StudentUiState> = _state.asStateFlow()

    private val downloadsFlow = repo.downloads()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        _state.value = _state.value.copy(downloads = downloadsFlow.value)
        viewModelScope.launch {
            downloadsFlow.collect { list -> _state.value = _state.value.copy(downloads = list) }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(notificationsEnabled = repo.store.notificationsEnabled())
        }
    }

    fun open(session: SessionState) {
        _state.value = _state.value.copy(active = session, loading = true, error = null, offline = false)
        viewModelScope.launch {
            repo.cachedSubjects(session.classroomId).collect { subjects ->
                _state.value = _state.value.copy(subjects = subjects)
            }
        }
        viewModelScope.launch {
            repo.searchDocuments(session.classroomId, _state.value.subjectFilter, _state.value.search)
                .collect { docs -> _state.value = _state.value.copy(documents = docs) }
        }
        refresh()
    }

    fun refresh() {
        val session = _state.value.active ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            try {
                repo.refresh(session)
                repo.registerDeviceQuietly(session)
                _state.value = _state.value.copy(offline = false, error = null, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    offline = _state.value.subjects.isNotEmpty(),
                    error = if (_state.value.subjects.isEmpty()) friendly(e) else null,
                    loading = false,
                )
            } finally {
                _state.value = _state.value.copy(refreshing = false)
            }
        }
    }

    fun join(code: String, onJoined: (SessionState) -> Unit) {
        if (_state.value.joining) return
        _state.value = _state.value.copy(joining = true, error = null)
        viewModelScope.launch {
            try {
                val session = repo.join(code)
                _state.value = _state.value.copy(joining = false, sessions = repo.sessions())
                open(session)
                onJoined(session)
            } catch (e: Exception) {
                _state.value = _state.value.copy(joining = false, error = friendly(e))
            }
        }
    }

    fun setSearch(value: String) {
        _state.value = _state.value.copy(search = value)
        resubscribeDocuments()
    }

    fun setSubjectFilter(subjectId: String?) {
        _state.value = _state.value.copy(subjectFilter = subjectId)
        resubscribeDocuments()
    }

    private fun resubscribeDocuments() {
        val session = _state.value.active ?: return
        viewModelScope.launch {
            repo.searchDocuments(session.classroomId, _state.value.subjectFilter, _state.value.search)
                .collect { docs -> _state.value = _state.value.copy(documents = docs) }
        }
    }

    fun leave(classroomId: String) {
        repo.leave(classroomId)
        _state.value = _state.value.copy(
            sessions = repo.sessions(),
            active = _state.value.active?.takeIf { it.classroomId != classroomId },
        )
    }

    fun download(document: CachedDocument, onMessage: (String) -> Unit) {
        val session = _state.value.active ?: return
        if (_state.value.downloading.containsKey(document.id)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(downloading = _state.value.downloading + (document.id to 0f))
            try {
                repo.download(session, repo.cachedToItem(document)) { progress ->
                    _state.value = _state.value.copy(
                        downloading = _state.value.downloading + (document.id to progress),
                    )
                }
                onMessage("« ${document.title} » enregistré sur l'appareil.")
            } catch (e: Exception) {
                onMessage(friendly(e))
            } finally {
                val updated = _state.value.downloading - document.id
                _state.value = _state.value.copy(downloading = updated)
            }
        }
    }

    suspend fun signedUrlFor(documentId: String): String? = runCatching {
        val session = _state.value.active ?: return null
        repo.signedUrl(session, documentId)
    }.getOrNull()

    fun downloadedFor(documentId: String): DownloadedFile? =
        _state.value.downloads.firstOrNull { it.documentId == documentId }

    fun deleteDownload(documentId: String) {
        viewModelScope.launch { repo.deleteDownload(documentId) }
    }

    fun setNotifications(enabled: Boolean) {
        repo.store.setNotificationsEnabled(enabled)
        _state.value = _state.value.copy(notificationsEnabled = enabled)
        viewModelScope.launch {
            _state.value.active?.let { repo.registerDeviceQuietly(it) }
        }
    }

    /** Demande d'ouverture d'une classe depuis une notification. */
    fun rememberPendingClassroom(classroomId: String) {
        _state.value = _state.value.copy(pendingClassroomId = classroomId)
    }

    fun clearPendingClassroom() {
        _state.value = _state.value.copy(pendingClassroomId = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun documentsForSubject(subjectId: String): List<DocumentItem> =
        _state.value.documents.filter { it.subjectId == subjectId }.map(repo::cachedToItem)

    private fun friendly(e: Exception): String = when (e) {
        is KlassioException -> e.message ?: "Opération impossible."
        is java.net.UnknownHostException, is java.io.IOException ->
            "Pas de connexion. Les documents déjà téléchargés restent disponibles."
        else -> e.message ?: "Une erreur est survenue."
    }
}
