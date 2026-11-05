package com.tchatz.klassio.data.remote

import com.tchatz.klassio.BuildConfig
import com.tchatz.klassio.data.Classroom
import com.tchatz.klassio.data.ClassroomDetail
import com.tchatz.klassio.data.DocumentAccess
import com.tchatz.klassio.data.DocumentItem
import com.tchatz.klassio.data.JoinPayload
import com.tchatz.klassio.data.OwnerSpace
import com.tchatz.klassio.data.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client HTTP unique vers le backend Supabase.
 *
 * - Les opérations métier passent par des RPC PostgreSQL (toute la validation est serveur).
 * - Les fichiers passent par l'Edge Function `klassio-file` pour les URLs signées.
 * - La clé anon (publishable) est publique par nature : aucune clé service_role ici.
 */
class KlassioApi(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val functionsUrl: String = "$baseUrl/functions/v1",
    client: OkHttpClient? = null,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val http = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ---------------------------------------------------------------- RPC

    suspend fun rpc(name: String, args: Map<String, String?>): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            args.forEach { (k, v) ->
                when (v) {
                    null -> put(k, null as String?)
                    else -> put(k, v)
                }
            }
        }
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$name")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, body)
            body
        }
    }

    suspend fun joinClassroom(code: String, label: String, platform: String = "android"): JoinPayload {
        val raw = rpc(
            "api_join_classroom",
            mapOf("p_join_code" to code, "p_label" to label, "p_platform" to platform),
        )
        return json.decodeFromString(raw)
    }

    suspend fun classroomDetail(token: String): ClassroomDetail {
        val raw = rpc("api_student_classroom", mapOf("p_token" to token))
        return json.decodeFromString(raw)
    }

    suspend fun documents(
        token: String,
        subjectId: String? = null,
        search: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<DocumentItem> {
        val raw = rpc(
            "api_student_documents",
            mapOf(
                "p_token" to token,
                "p_subject_id" to subjectId,
                "p_search" to search?.takeIf { it.isNotBlank() },
                "p_limit" to limit.toString(),
                "p_offset" to offset.toString(),
            ),
        )
        return json.decodeFromString(raw)
    }

    /** Récupère une URL signée courte via l'Edge Function (jamais de fichier public). */
    suspend fun signedUrl(token: String, documentId: String, type: String = "student"): SignedDocument {
        val (document, url) = requestSignature(type, token, documentId, 300)
        return SignedDocument(document, url)
    }

    suspend fun signUploadResult(token: String, documentId: String): SignedDocument {
        val (document, url) = requestSignature("student", token, documentId, 3600)
        return SignedDocument(document, url)
    }

    private suspend fun requestSignature(
        type: String,
        token: String,
        documentId: String,
        expiresIn: Int,
    ): Pair<DocumentItem, String> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("document_id", documentId)
            put("expires_in", expiresIn)
            if (type == "student") put("session_token", token)
        }
        val request = Request.Builder()
            .url("$functionsUrl/klassio-file?action=sign&type=$type")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, body)
            val root = json.parseToJsonElement(body).jsonObject
            val document = json.decodeFromJsonElement(DocumentItem.serializer(), root.getValue("document"))
            val url = root.getValue("signed_url").jsonPrimitive.content
            document to url
        }
    }

    // ------------------------------------------------------ Publication

    suspend fun registerDevice(
        token: String,
        deviceId: String,
        label: String,
        notificationsEnabled: Boolean,
    ) {
        rpc(
            "api_register_device",
            mapOf(
                "p_token" to token,
                "p_device_identifier" to deviceId,
                "p_label" to label,
                "p_platform" to "android",
                "p_notifications_enabled" to notificationsEnabled.toString(),
            ),
        )
    }

    @Serializable
    data class BeginUpload(
        @SerialName("document_id") val documentId: String,
        @SerialName("file_path") val filePath: String,
        @SerialName("subject_id") val subjectId: String,
    )

    @Serializable
    data class BeginUploadRequest(
        val token: String,
        val title: String,
        val description: String?,
        val fileName: String,
        val fileSize: Long,
        val teacherName: String,
    )

    suspend fun beginUpload(request: BeginUploadRequest): BeginUpload {
        val raw = rpc(
            "api_begin_upload",
            mapOf(
                "p_token" to request.token,
                "p_title" to request.title,
                "p_description" to request.description,
                "p_file_name" to request.fileName,
                "p_file_size" to request.fileSize.toString(),
                "p_teacher_name" to request.teacherName,
            ),
        )
        return json.decodeFromString(raw)
    }

    /** Dépôt direct dans le bucket privé, puis finalisation serveur. */
    suspend fun uploadFile(
        upload: BeginUpload,
        file: File,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): SignedDocument = withContext(Dispatchers.IO) {
        val body: RequestBody = ProgressRequestBody(file, mimeType.toMediaType()) { sent, total ->
            if (total > 0) onProgress(sent.toFloat() / total)
        }
        val request = Request.Builder()
            .url("$baseUrl/storage/v1/object/documents/${upload.filePath}")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("x-upsert", "true")
            .put(body)
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
        }

        finalizeUpload(upload, file.name)
    }

    suspend fun finalizeUpload(upload: BeginUpload, fileName: String): SignedDocument = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("document_id", upload.documentId)
            put("file_name", fileName)
        }
        val request = Request.Builder()
            .url("$functionsUrl/klassio-file?action=finalize")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            val root = json.parseToJsonElement(text).jsonObject
            val document = json.decodeFromJsonElement(DocumentItem.serializer(), root.getValue("document"))
            val url = root["signed_url"]?.jsonPrimitive?.content
            SignedDocument(document, url)
        }
    }

    // -------------------------------------------------------- Download

    suspend fun downloadTo(url: String, destination: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, "")
            val body = response.body ?: throw KlassioException("EMPTY_BODY", "Réponse vide du serveur.")
            val total = body.contentLength()
            destination.parentFile?.mkdirs()
            val temp = File(destination.parentFile, destination.name + ".part")
            temp.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var sent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        sent += read
                        if (total > 0) onProgress(sent.toFloat() / total)
                    }
                }
            }
            if (destination.exists()) destination.delete()
            if (!temp.renameTo(destination)) throw KlassioException("WRITE_FAILED", "Écriture impossible.")
            destination
        }
    }

    // -------------------------------------------------- Espace responsable

    suspend fun signIn(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("email", email)
            put("password", password)
        }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=password")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            json.decodeFromString(AuthSession.serializer(), text)
        }
    }

    suspend fun signUp(email: String, password: String, fullName: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("email", email)
            put("password", password)
            put("data", buildJsonObject { put("full_name", fullName) })
        }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            val root = json.parseToJsonElement(text).jsonObject
            if (root["access_token"] == null) {
                throw KlassioException(
                    "EMAIL_CONFIRMATION_REQUIRED",
                    "Compte créé. Une confirmation par email est requise avant la connexion.",
                )
            }
            json.decodeFromString(AuthSession.serializer(), text)
        }
    }

    suspend fun refresh(refreshToken: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("refresh_token", refreshToken) }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            json.decodeFromString(AuthSession.serializer(), text)
        }
    }

    suspend fun requestPasswordReset(email: String) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("email", email) }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/recover")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, response.body?.string().orEmpty())
        }
    }

    suspend fun mySpace(accessToken: String, classroomId: String): OwnerSpace = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("p_classroom_id", classroomId) }
        val request = authed(accessToken, "rpc/api_my_space").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            json.decodeFromString(text)
        }
    }

    suspend fun createClassroom(
        accessToken: String,
        institution: String,
        className: String,
        fieldOfStudy: String,
        level: String,
        studyYear: String,
        academicYear: String,
    ): Classroom = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("p_institution_name", institution)
            put("p_class_name", className)
            put("p_field_of_study", fieldOfStudy)
            put("p_level", level)
            put("p_study_year", studyYear)
            put("p_academic_year", academicYear)
        }
        val request = authed(accessToken, "rpc/api_create_classroom").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            json.decodeFromString(text)
        }
    }

    suspend fun deleteDocument(accessToken: String, documentId: String): List<String> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("p_document_id", documentId) }
        val request = authed(accessToken, "rpc/api_delete_document").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            val path = json.parseToJsonElement(text).jsonObject["file_path"]?.jsonPrimitive?.content
            if (path.isNullOrBlank()) emptyList() else listOf(path)
        }
    }


    // ------------------------------------------------- Responsable (suite)

    @Serializable
    data class CreatedSubject(val token: String)

    @Serializable
    private data class CreatedSubjectEnvelope(
        val subject: Map<String, String> = emptyMap(),
        val link: LinkEnvelope,
    )

    @Serializable
    private data class LinkEnvelope(val id: String, val token: String)

    suspend fun listMyClassrooms(accessToken: String): List<Classroom> = withContext(Dispatchers.IO) {
        val request = authed(accessToken, "rpc/api_list_my_classrooms").get().build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            json.decodeFromList(Classroom.serializer(), text)
        }
    }

    suspend fun createSubject(
        accessToken: String,
        classroomId: String,
        name: String,
        description: String,
    ): CreatedSubject = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("p_classroom_id", classroomId)
            put("p_name", name)
            put("p_description", description.ifBlank { null })
        }
        val request = authed(accessToken, "rpc/api_create_subject").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            val envelope = json.decodeFromString(CreatedSubjectEnvelope.serializer(), text)
            CreatedSubject(envelope.link.token)
        }
    }

    suspend fun rotatePublicationLink(accessToken: String, subjectId: String): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("p_subject_id", subjectId) }
        val request = authed(accessToken, "rpc/api_rotate_publication_link").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            val root = json.parseToJsonElement(text).jsonObject
            root.getValue("link").jsonObject.getValue("token").jsonPrimitive.content
        }
    }

    suspend fun revokePublicationLink(accessToken: String, subjectId: String) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("p_subject_id", subjectId) }
        val request = authed(accessToken, "rpc/api_revoke_publication_link").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, response.body?.string().orEmpty())
        }
    }

    suspend fun regenerateJoinCode(accessToken: String, classroomId: String): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("p_classroom_id", classroomId) }
        val request = authed(accessToken, "rpc/api_regenerate_join_code").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            json.parseToJsonElement(text).jsonObject.getValue("join_code").jsonPrimitive.content
        }
    }

    suspend fun archiveClassroom(accessToken: String, classroomId: String, archived: Boolean) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("p_classroom_id", classroomId)
            put("p_archived", archived)
        }
        val request = authed(accessToken, "rpc/api_archive_classroom").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, response.body?.string().orEmpty())
        }
    }

    suspend fun deleteSubject(accessToken: String, subjectId: String): List<String> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("p_subject_id", subjectId) }
        val request = authed(accessToken, "rpc/api_delete_subject").post(payload.toString().toRequestBody(JSON_MEDIA)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, text)
            pathsOf(text)
        }
    }

    /** Supprime réellement les fichiers du bucket privé (Edge Function + service_role). */
    suspend fun purgeFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        val payload = buildJsonObject { put("paths", kotlinx.serialization.json.JsonArray(paths.map { kotlinx.serialization.json.JsonPrimitive(it) })) }
        val request = Request.Builder()
            .url("$functionsUrl/klassio-file?action=purge")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw KlassioException.fromHttp(response.code, response.body?.string().orEmpty())
        }
    }

    fun buildConfigWebUrl(): String = BuildConfig.WEB_APP_URL.trimEnd('/')

    private fun pathsOf(text: String): List<String> {
        val root = json.parseToJsonElement(text).jsonObject
        val array = root["file_paths"] ?: return emptyList()
        return (array as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
    }

    private fun authed(accessToken: String, path: String): Request.Builder = Request.Builder()
        .url("$baseUrl/rest/v1/$path")
        .header("apikey", anonKey)
        .header("Authorization", "Bearer $accessToken")
        .header("Content-Type", "application/json")

    suspend fun newUpdates(token: String, since: String): List<DocumentItem> {
        val raw = rpc("api_student_updates", mapOf("p_token" to token, "p_since" to since))
        return json.decodeFromString(raw)
    }

    companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

data class SignedDocument(val document: DocumentItem, val url: String?)

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: AuthUser? = null,
)

@Serializable
data class AuthUser(val id: String, val email: String? = null)

class KlassioException(val code: String, message: String) : Exception(message) {
    companion object {
        fun fromHttp(status: Int, rawBody: String): KlassioException {
            val known = listOf(
                "CLASSROOM_NOT_FOUND", "SESSION_INVALID", "LINK_INVALID", "LINK_REVOKED",
                "FILE_TYPE_NOT_ALLOWED", "FILE_TOO_LARGE", "RATE_LIMITED", "TITLE_REQUIRED",
                "TEACHER_NAME_REQUIRED", "UPLOAD_INCOMPLETE", "FILE_SIZE_MISMATCH",
                "DOCUMENT_NOT_FOUND", "NOT_AUTHENTICATED", "invalid_credentials",
                "email_exists", "user_already_exists", "weak_password",
            )
            val body = rawBody.lowercase()
            val found = known.firstOrNull { body.contains(it.lowercase()) }
            val message = when {
                found == "CLASSROOM_NOT_FOUND" -> "Aucune classe ne correspond à ce code."
                found == "SESSION_INVALID" -> "Cette session a expiré. Rejoignez la classe à nouveau."
                found == "LINK_INVALID" -> "Ce lien de publication n'est pas valide."
                found == "LINK_REVOKED" -> "Ce lien a été révoqué par le responsable."
                found == "FILE_TYPE_NOT_ALLOWED" -> "Format de fichier non autorisé."
                found == "FILE_TOO_LARGE" -> "Fichier trop volumineux."
                found == "RATE_LIMITED" -> "Trop de publications. Réessayez plus tard."
                found == "invalid_credentials" -> "Email ou mot de passe incorrect."
                found == "email_exists" || found == "user_already_exists" -> "Un compte existe déjà avec cet email."
                found == "weak_password" -> "Mot de passe trop faible (6 caractères minimum)."
                found == "NOT_AUTHENTICATED" -> "Connexion requise."
                status == 0 || status >= 500 -> "Connexion instable. Réessayez."
                else -> "Opération impossible ($status)."
            }
            return KlassioException(found ?: "HTTP_$status", message)
        }
    }
}

/** Corps de requête qui expose la progression réelle de l'envoi. */
private class ProgressRequestBody(
    private val file: File,
    private val mediaType: okhttp3.MediaType,
    private val onProgress: (Long, Long) -> Unit,
) : RequestBody() {

    override fun contentType(): okhttp3.MediaType = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: okio.BufferedSink) {
        val total = file.length()
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var written = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                sink.write(buffer, 0, read)
                written += read
                onProgress(written, total)
            }
        }
    }
}

/** Encode un segment d'URL (chemins Storage). */
fun encodePath(path: String): String = path.split('/').joinToString("/") { segment ->
    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}

/** Utilitaire de test : construit un corps multipart (non utilisé en production). */
fun multipartFor(file: File, mime: String): MultipartBody.Part =
    MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mime.toMediaType()))

/** Convertit un objet JSON en carte de chaînes (utile pour les tests). */
fun JsonObject.asStringMap(): Map<String, String> =
    entries.associate { (key, value) -> key to value.jsonPrimitive.content }
