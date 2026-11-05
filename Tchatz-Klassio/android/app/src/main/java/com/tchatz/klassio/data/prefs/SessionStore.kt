package com.tchatz.klassio.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tchatz.klassio.data.SessionState
import kotlinx.serialization.json.Json

/**
 * Sessions étudiantes sans compte, stockées localement et chiffrées.
 * Aucun email, aucun numéro, aucun identifiant matériel n'est collecté :
 * seul un jeton opaque fourni par le serveur est conservé.
 */
class SessionStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Identifiant d'appareil aléatoire, local, non dérivé du matériel. */
    fun deviceIdentifier(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val generated = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun sessions(): List<SessionState> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SessionState>>(raw) }.getOrDefault(emptyList())
    }

    fun save(session: SessionState) {
        val updated = sessions().filterNot { it.classroomId == session.classroomId } + session
        prefs.edit().putString(KEY_SESSIONS, json.encodeToString(updated)).apply()
    }

    fun remove(classroomId: String) {
        val updated = sessions().filterNot { it.classroomId == classroomId }
        prefs.edit().putString(KEY_SESSIONS, json.encodeToString(updated)).apply()
    }

    fun clear() = prefs.edit().remove(KEY_SESSIONS).apply()

    fun notificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS, true)

    fun setNotificationsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()

    fun lastSeenUpdates(classroomId: String): String? =
        prefs.getString("$KEY_LAST_SEEN$classroomId", null)

    fun setLastSeenUpdates(classroomId: String, isoDate: String) =
        prefs.edit().putString("$KEY_LAST_SEEN$classroomId", isoDate).apply()

    // --- Espace responsable (jeton de session uniquement) ---

    fun ownerAccessToken(): String? = prefs.getString(KEY_OWNER_TOKEN, null)
    fun ownerRefreshToken(): String? = prefs.getString(KEY_OWNER_REFRESH, null)
    fun ownerEmail(): String? = prefs.getString(KEY_OWNER_EMAIL, null)

    fun saveOwnerSession(access: String, refresh: String, email: String?) {
        prefs.edit()
            .putString(KEY_OWNER_TOKEN, access)
            .putString(KEY_OWNER_REFRESH, refresh)
            .putString(KEY_OWNER_EMAIL, email)
            .apply()
    }

    fun clearOwnerSession() = prefs.edit()
        .remove(KEY_OWNER_TOKEN)
        .remove(KEY_OWNER_REFRESH)
        .remove(KEY_OWNER_EMAIL)
        .apply()

    private companion object {
        const val FILE_NAME = "klassio_secure"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SESSIONS = "student_sessions"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_LAST_SEEN = "last_seen_"
        const val KEY_OWNER_TOKEN = "owner_access_token"
        const val KEY_OWNER_REFRESH = "owner_refresh_token"
        const val KEY_OWNER_EMAIL = "owner_email"
    }
}
