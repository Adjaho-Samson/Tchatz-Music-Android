package com.tchatz.klassio.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modèles partagés entre le Web, Android et Supabase.
 * Les noms JSON correspondent exactement aux réponses des RPC SQL.
 */

@Serializable
data class Classroom(
    val id: String,
    @SerialName("institution_name") val institutionName: String,
    @SerialName("class_name") val className: String,
    @SerialName("field_of_study") val fieldOfStudy: String,
    val level: String = "",
    @SerialName("study_year") val studyYear: String = "",
    @SerialName("academic_year") val academicYear: String,
    @SerialName("join_code") val joinCode: String = "",
    @SerialName("is_archived") val isArchived: Boolean = false,
)

@Serializable
data class Subject(
    val id: String,
    val name: String,
    val description: String? = null,
    val position: Int = 0,
    @SerialName("document_count") val documentCount: Long = 0,
    @SerialName("last_document_at") val lastDocumentAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
)

@Serializable
data class DocumentItem(
    val id: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("subject_name") val subjectName: String = "",
    val title: String,
    val description: String? = null,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_type") val fileType: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("teacher_display_name") val teacherDisplayName: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class JoinPayload(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val classroom: Classroom,
)

@Serializable
data class ClassroomDetail(
    val classroom: Classroom,
    val subjects: List<Subject> = emptyList(),
    @SerialName("document_count") val documentCount: Long = 0,
)

@Serializable
data class PublicationContext(
    val subject: PublishSubject,
    val classroom: PublishClassroom,
    @SerialName("teacher_name_hint") val teacherNameHint: String? = null,
    @SerialName("max_upload_bytes") val maxUploadBytes: Long = 10_485_760,
    @SerialName("allowed_extensions") val allowedExtensions: List<String> = emptyList(),
    @SerialName("publications_last_hour") val publicationsLastHour: Int = 0,
)

@Serializable
data class PublishSubject(val id: String, val name: String, val description: String? = null)

@Serializable
data class PublishClassroom(
    val id: String,
    @SerialName("class_name") val className: String,
    @SerialName("institution_name") val institutionName: String,
    @SerialName("field_of_study") val fieldOfStudy: String = "",
    val level: String = "",
    @SerialName("study_year") val studyYear: String = "",
    @SerialName("academic_year") val academicYear: String = "",
)

@Serializable
data class OwnerSpace(
    val classroom: OwnerClassroom,
    val subjects: List<OwnerSubject> = emptyList(),
    val stats: OwnerStats = OwnerStats(),
)

@Serializable
data class OwnerClassroom(
    val id: String,
    @SerialName("institution_name") val institutionName: String,
    @SerialName("class_name") val className: String,
    @SerialName("field_of_study") val fieldOfStudy: String,
    val level: String = "",
    @SerialName("study_year") val studyYear: String = "",
    @SerialName("academic_year") val academicYear: String,
    @SerialName("join_code") val joinCode: String,
    @SerialName("is_archived") val isArchived: Boolean = false,
)

@Serializable
data class OwnerSubject(
    val id: String,
    val name: String,
    val description: String? = null,
    val position: Int = 0,
    @SerialName("document_count") val documentCount: Long = 0,
    @SerialName("archived_at") val archivedAt: String? = null,
    val link: OwnerLink? = null,
)

@Serializable
data class OwnerLink(
    val id: String,
    val status: String,
    @SerialName("token_hint") val tokenHint: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class OwnerStats(
    val subjects: Long = 0,
    val documents: Long = 0,
    val devices: Long = 0,
)

@Serializable
data class DocumentAccess(
    @SerialName("file_path") val filePath: String,
    val document: DocumentItem? = null,
)

@Serializable
data class SessionState(
    val token: String,
    val classroomId: String,
    val className: String,
    val institutionName: String,
    val fieldOfStudy: String,
    val level: String,
    val studyYear: String,
    val academicYear: String,
    val joinCode: String,
    val joinedAt: Long = System.currentTimeMillis(),
)
