import { supabase, SUPABASE_URL, FUNCTIONS_URL } from './supabase';
import type {
  Classroom,
  ClassroomDetail,
  DocumentItem,
  JoinPayload,
  OwnerSpace,
  PublishContext,
  SignedDocument,
} from './types';

/** Erreur métier lisible (les codes viennent des RPC PostgreSQL). */
export class KlassioError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
    this.name = 'KlassioError';
  }
}

const MESSAGES: Record<string, string> = {
  CLASSROOM_NOT_FOUND: 'Aucune classe ne correspond à ce code.',
  SESSION_INVALID: 'Votre session a expiré. Rejoignez la classe à nouveau.',
  LINK_INVALID: 'Ce lien de publication n’est pas valide.',
  LINK_REVOKED: 'Ce lien a été révoqué par le responsable de classe.',
  SCHEMA_EMPTY: 'Un espace de classe contient au moins une matière.',
  FILE_TYPE_NOT_ALLOWED: 'Format de fichier non autorisé.',
  FILE_TOO_LARGE: 'Fichier trop volumineux.',
  RATE_LIMITED: 'Trop de publications sur ce lien. Réessayez dans une heure.',
  TITLE_REQUIRED: 'Le titre du document est obligatoire (2 caractères minimum).',
  TEACHER_NAME_REQUIRED: 'Indiquez votre nom d’affichage (2 caractères minimum).',
  FILE_REQUIRED: 'Sélectionnez un fichier.',
  UPLOAD_INCOMPLETE: 'L’envoi du fichier est incomplet. Réessayez.',
  FILE_SIZE_MISMATCH: 'Le fichier reçu ne correspond pas à la taille annoncée.',
  DOCUMENT_NOT_FOUND: 'Document introuvable.',
  NOT_AUTHENTICATED: 'Connexion requise.',
  ACADEMIC_YEAR_INVALID: 'Année académique invalide (format attendu : 2026-2027).',
  SUBJECT_NOT_FOUND: 'Matière introuvable.',
  SUBJECT_NOT_IN_CLASSROOM: 'Cette matière n’appartient pas à votre classe.',
  DEVICE_NOT_FOUND: 'Appareil inconnu pour cette classe.',
  DUPLICATE_SUBJECT: 'Une matière porte déjà ce nom dans cette classe.',
  REQUEST_FAILED: 'Opération impossible. Réessayez.',
};

export function humanize(raw: string | undefined): KlassioError {
  const message = raw ?? '';
  const code =
    Object.keys(MESSAGES).find((key) => message.includes(key)) ??
    (/duplicate key/i.test(message) ? 'DUPLICATE_SUBJECT' : 'REQUEST_FAILED');
  return new KlassioError(code, MESSAGES[code] ?? MESSAGES.REQUEST_FAILED);
}

async function rpc<T>(name: string, args: Record<string, unknown>): Promise<T> {
  const { data, error } = await supabase.rpc(name, args);
  if (error) throw humanize(error.message);
  return data as T;
}

// ------------------------------------------------------------------ Étudiant

export const studentApi = {
  join: (code: string, label: string) =>
    rpc<JoinPayload>('api_join_classroom', {
      p_join_code: code,
      p_label: label,
      p_platform: 'web',
    }),

  classroom: (token: string) => rpc<ClassroomDetail>('api_student_classroom', { p_token: token }),

  documents: (
    token: string,
    options: { subjectId?: string | null; search?: string; limit?: number; offset?: number } = {},
  ) =>
    rpc<DocumentItem[]>('api_student_documents', {
      p_token: token,
      p_subject_id: options.subjectId ?? null,
      p_search: options.search?.trim() ? options.search.trim() : null,
      p_limit: options.limit ?? 60,
      p_offset: options.offset ?? 0,
    }),

  registerDevice: (token: string, deviceId: string, enabled: boolean, label: string) =>
    rpc<{ device_id: string }>('api_register_device', {
      p_token: token,
      p_device_identifier: deviceId,
      p_label: label,
      p_platform: 'web',
      p_notifications_enabled: enabled,
    }),

  setNotifications: (token: string, deviceId: string, enabled: boolean) =>
    rpc<{ ok: boolean }>('api_set_notifications', {
      p_token: token,
      p_device_identifier: deviceId,
      p_enabled: enabled,
    }),

  updates: (token: string, since: string) =>
    rpc<DocumentItem[]>('api_student_updates', { p_token: token, p_since: since }),

  /** URL signée courte obtenue après vérification serveur d'appartenance à la classe. */
  signedUrl: async (token: string, documentId: string, expiresIn = 300): Promise<SignedDocument> => {
    const response = await fetch(`${FUNCTIONS_URL}/klassio-file?action=sign&type=student`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: import.meta.env.VITE_SUPABASE_ANON_KEY },
      body: JSON.stringify({ session_token: token, document_id: documentId, expires_in: expiresIn }),
    });
    const payload = await response.json();
    if (!response.ok) throw humanize(payload.error);
    return payload as SignedDocument;
  },
};

// --------------------------------------------------------------- Enseignant

export const publishApi = {
  context: (token: string) => rpc<PublishContext>('api_publish_context', { p_token: token }),

  beginUpload: (
    token: string,
    data: {
      title: string;
      description: string;
      fileName: string;
      fileSize: number;
      teacherName: string;
    },
  ) =>
    rpc<{ document_id: string; file_path: string; subject_id: string }>('api_begin_upload', {
      p_token: token,
      p_title: data.title,
      p_description: data.description || null,
      p_file_name: data.fileName,
      p_file_size: data.fileSize,
      p_teacher_name: data.teacherName,
    }),

  /**
   * Envoi du fichier dans le bucket privé (dossier staging), puis finalisation
   * par l'Edge Function : déplacement réel + validation de la ligne en base.
   */
  upload: async (
    token: string,
    documentId: string,
    filePath: string,
    file: File,
    onProgress: (percent: number) => void,
  ): Promise<SignedDocument> => {
    const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;
    await uploadWithProgress(`${SUPABASE_URL}/storage/v1/object/documents/${filePath}`, file, anonKey, onProgress);

    const response = await fetch(`${FUNCTIONS_URL}/klassio-file?action=finalize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: anonKey },
      body: JSON.stringify({ session_token: token, document_id: documentId }),
    });
    const payload = await response.json();
    if (!response.ok) throw humanize(payload.error);
    return payload as SignedDocument;
  },
};

function uploadWithProgress(
  url: string,
  file: File,
  anonKey: string,
  onProgress: (percent: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    xhr.setRequestHeader('apikey', anonKey);
    xhr.setRequestHeader('Authorization', `Bearer ${anonKey}`);
    xhr.setRequestHeader('x-upsert', 'true');
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100));
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve();
      else reject(humanize(safeMessage(xhr.responseText)));
    };
    xhr.onerror = () => reject(new KlassioError('NETWORK', 'Connexion interrompue pendant l’envoi. Réessayez.'));
    xhr.send(file);
  });
}

function safeMessage(raw: string): string {
  try {
    const parsed = JSON.parse(raw);
    return parsed.message ?? parsed.error ?? raw;
  } catch {
    return raw;
  }
}

// --------------------------------------------------------------- Responsable

export const ownerApi = {
  classrooms: () => rpc<Classroom[]>('api_list_my_classrooms', {}),

  createClassroom: (input: {
    institution: string;
    className: string;
    fieldOfStudy: string;
    level: string;
    studyYear: string;
    academicYear: string;
  }) =>
    rpc<Classroom>('api_create_classroom', {
      p_institution_name: input.institution,
      p_class_name: input.className,
      p_field_of_study: input.fieldOfStudy,
      p_level: input.level,
      p_study_year: input.studyYear,
      p_academic_year: input.academicYear,
    }),

  space: (classroomId: string) => rpc<OwnerSpace>('api_my_space', { p_classroom_id: classroomId }),

  createSubject: (classroomId: string, name: string, description: string) =>
    rpc<{ subject: { id: string; name: string }; link: { id: string; token: string } }>(
      'api_create_subject',
      { p_classroom_id: classroomId, p_name: name, p_description: description || null },
    ),

  updateSubject: (subjectId: string, name: string, description: string) =>
    rpc<{ ok: boolean }>('api_update_subject', {
      p_subject_id: subjectId,
      p_name: name,
      p_description: description || null,
    }),

  rotateLink: (subjectId: string) =>
    rpc<{ link: { id: string; token: string } }>('api_rotate_publication_link', {
      p_subject_id: subjectId,
    }),

  revokeLink: (subjectId: string) =>
    rpc<{ ok: boolean }>('api_revoke_publication_link', { p_subject_id: subjectId }),

  regenerateJoinCode: (classroomId: string) =>
    rpc<{ join_code: string }>('api_regenerate_join_code', { p_classroom_id: classroomId }),

  archiveClassroom: (classroomId: string, archived: boolean) =>
    rpc<{ ok: boolean }>('api_archive_classroom', {
      p_classroom_id: classroomId,
      p_archived: archived,
    }),

  deleteSubject: (subjectId: string) =>
    rpc<{ ok: boolean; file_paths: string[] }>('api_delete_subject', { p_subject_id: subjectId }),

  deleteDocument: (documentId: string) =>
    rpc<{ ok: boolean; file_path: string }>('api_delete_document', { p_document_id: documentId }),

  deleteClassroom: (classroomId: string) =>
    rpc<{ ok: boolean; file_paths: string[] }>('api_delete_classroom', {
      p_classroom_id: classroomId,
    }),

  /** Suppression physique des fichiers (Edge Function détentrice de la clé service_role). */
  purgeFiles: async (paths: string[]) => {
    if (!paths?.length) return;
    await fetch(`${FUNCTIONS_URL}/klassio-file?action=purge`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: import.meta.env.VITE_SUPABASE_ANON_KEY,
      },
      body: JSON.stringify({ paths }),
    });
  },

  signedUrl: async (documentId: string): Promise<SignedDocument> => {
    const response = await fetch(`${FUNCTIONS_URL}/klassio-file?action=sign&type=owner`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: import.meta.env.VITE_SUPABASE_ANON_KEY,
        Authorization: `Bearer ${(await supabase.auth.getSession()).data.session?.access_token ?? ''}`,
      },
      body: JSON.stringify({ document_id: documentId, expires_in: 300 }),
    });
    const payload = await response.json();
    if (!response.ok) throw humanize(payload.error);
    return payload as SignedDocument;
  },
};
