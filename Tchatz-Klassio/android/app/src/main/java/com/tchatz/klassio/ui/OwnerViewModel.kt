package com.tchatz.klassio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchatz.klassio.data.Classroom
import com.tchatz.klassio.data.OwnerSpace
import com.tchatz.klassio.data.remote.KlassioApi
import com.tchatz.klassio.data.remote.KlassioException
import com.tchatz.klassio.data.repo.KlassioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OwnerUiState(
    val email: String? = null,
    val accessToken: String? = null,
    val signingIn: Boolean = false,
    val creating: Boolean = false,
    val classrooms: List<Classroom> = emptyList(),
    val space: OwnerSpace? = null,
    val lastCreatedLink: String? = null,
    val error: String? = null,
    val info: String? = null,
    val loading: Boolean = false,
)

/**
 * Espace responsable : authentification Supabase Auth + actions réelles en base.
 * Aucune donnée n'est simulée : chaque action appelle une RPC ou l'API Storage.
 */
class OwnerViewModel(
    private val repo: KlassioRepository,
    private val api: KlassioApi,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OwnerUiState(
            email = repo.ownerEmail(),
            accessToken = repo.store.ownerAccessToken(),
        ),
    )
    val state: StateFlow<OwnerUiState> = _state.asStateFlow()

    val isSignedIn: Boolean get() = _state.value.accessToken != null

    fun dismissMessages() {
        _state.value = _state.value.copy(error = null, info = null, lastCreatedLink = null)
    }

    fun signIn(email: String, password: String) {
        if (_state.value.signingIn) return
        _state.value = _state.value.copy(signingIn = true, error = null)
        viewModelScope.launch {
            try {
                val auth = repo.signIn(email, password)
                _state.value = _state.value.copy(
                    signingIn = false,
                    accessToken = auth.accessToken,
                    email = auth.user?.email ?: email,
                )
                loadClassrooms()
            } catch (e: Exception) {
                _state.value = _state.value.copy(signingIn = false, error = friendly(e))
            }
        }
    }

    fun signUp(email: String, password: String, fullName: String) {
        if (_state.value.signingIn) return
        _state.value = _state.value.copy(signingIn = true, error = null)
        viewModelScope.launch {
            try {
                val auth = repo.signUp(email, password, fullName)
                _state.value = _state.value.copy(
                    signingIn = false,
                    accessToken = auth.accessToken,
                    email = auth.user?.email ?: email,
                    info = "Compte créé. Bienvenue sur Tchatz Klassio.",
                )
                loadClassrooms()
            } catch (e: Exception) {
                _state.value = _state.value.copy(signingIn = false, error = friendly(e))
            }
        }
    }

    fun sendReset(email: String) {
        viewModelScope.launch {
            try {
                repo.resetPassword(email)
                _state.value = _state.value.copy(info = "Un email de réinitialisation a été envoyé.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    fun signOut() {
        repo.signOut()
        _state.value = OwnerUiState()
    }

    fun createClassroom(
        institution: String,
        className: String,
        field: String,
        level: String,
        studyYear: String,
        academicYear: String,
    ) {
        val token = _state.value.accessToken ?: return
        if (_state.value.creating) return
        _state.value = _state.value.copy(creating = true, error = null)
        viewModelScope.launch {
            try {
                api.createClassroom(token, institution, className, field, level, studyYear, academicYear)
                _state.value = _state.value.copy(creating = false, info = "Classe créée avec succès.")
                loadClassrooms()
            } catch (e: Exception) {
                _state.value = _state.value.copy(creating = false, error = friendly(e))
            }
        }
    }

    fun loadClassrooms() {
        val token = _state.value.accessToken ?: return
        viewModelScope.launch {
            try {
                val raw = api.listMyClassrooms(token)
                _state.value = _state.value.copy(classrooms = raw)
            } catch (e: Exception) {
                // Jeton expiré : tentative de rafraîchissement unique
                val refreshed = runCatching { repo.refreshOwnerToken() }.getOrNull()
                if (refreshed != null) {
                    _state.value = _state.value.copy(accessToken = refreshed)
                    loadClassrooms()
                } else {
                    _state.value = _state.value.copy(error = friendly(e))
                }
            }
        }
    }

    fun openSpace(classroomId: String) {
        val token = _state.value.accessToken ?: return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try {
                val space = api.mySpace(token, classroomId)
                _state.value = _state.value.copy(space = space, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    fun createSubject(name: String, description: String) {
        val token = _state.value.accessToken ?: return
        val space = _state.value.space ?: return
        viewModelScope.launch {
            try {
                val result = api.createSubject(token, space.classroom.id, name, description)
                val base = repo.api.buildConfigWebUrl()
                _state.value = _state.value.copy(
                    lastCreatedLink = "$base/p/${result.token}",
                    info = "Matière créée. Partagez le lien avec l'enseignant.",
                )
                openSpace(space.classroom.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    fun rotateLink(subjectId: String) {
        val token = _state.value.accessToken ?: return
        val space = _state.value.space ?: return
        viewModelScope.launch {
            try {
                val newToken = api.rotatePublicationLink(token, subjectId)
                _state.value = _state.value.copy(
                    lastCreatedLink = "${repo.api.buildConfigWebUrl()}/p/$newToken",
                    info = "Nouveau lien généré. L'ancien lien est révoqué.",
                )
                openSpace(space.classroom.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    fun revokeLink(subjectId: String) {
        val token = _state.value.accessToken ?: return
        val space = _state.value.space ?: return
        viewModelScope.launch {
            try {
                api.revokePublicationLink(token, subjectId)
                _state.value = _state.value.copy(info = "Lien révoqué : aucune publication n'est plus possible.")
                openSpace(space.classroom.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    fun regenerateJoinCode() {
        val token = _state.value.accessToken ?: return
        val space = _state.value.space ?: return
        viewModelScope.launch {
            try {
                val code = api.regenerateJoinCode(token, space.classroom.id)
                _state.value = _state.value.copy(info = "Nouveau code de classe : $code")
                openSpace(space.classroom.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    fun deleteDocument(documentId: String) {
        val token = _state.value.accessToken ?: return
        val space = _state.value.space ?: return
        viewModelScope.launch {
            try {
                val paths = api.deleteDocument(token, documentId)
                if (paths.isNotEmpty()) api.purgeFiles(paths)
                _state.value = _state.value.copy(info = "Document supprimé et fichier retiré du stockage.")
                openSpace(space.classroom.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    fun deleteSubject(subjectId: String) {
        val token = _state.value.accessToken ?: return
        val space = _state.value.space ?: return
        viewModelScope.launch {
            try {
                val paths = api.deleteSubject(token, subjectId)
                if (paths.isNotEmpty()) api.purgeFiles(paths)
                _state.value = _state.value.copy(info = "Matière supprimée.")
                openSpace(space.classroom.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    fun archiveClassroom(archived: Boolean) {
        val token = _state.value.accessToken ?: return
        val space = _state.value.space ?: return
        viewModelScope.launch {
            try {
                api.archiveClassroom(token, space.classroom.id, archived)
                _state.value = _state.value.copy(
                    info = if (archived) "Classe fermée : les étudiants n'y ont plus accès." else "Classe réactivée.",
                )
                loadClassrooms()
                openSpace(space.classroom.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is KlassioException -> e.message ?: "Opération impossible."
        is java.net.UnknownHostException, is java.io.IOException -> "Pas de connexion internet."
        else -> e.message ?: "Une erreur est survenue."
    }
}
