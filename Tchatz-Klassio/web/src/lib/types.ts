/** Types partagés avec le backend Supabase (les noms suivent les RPC SQL). */

export interface Classroom {
  id: string;
  institution_name: string;
  class_name: string;
  field_of_study: string;
  level: string;
  study_year: string;
  academic_year: string;
  join_code: string;
  is_archived?: boolean;
  created_at?: string;
  subject_count?: number;
  document_count?: number;
}

export interface Subject {
  id: string;
  name: string;
  description: string | null;
  position: number;
  document_count: number;
  last_document_at?: string | null;
  archived_at?: string | null;
  link?: OwnerLink | null;
}

export interface OwnerLink {
  id: string;
  status: string;
  token_hint: string | null;
  created_at: string;
}

export interface DocumentItem {
  id: string;
  subject_id: string;
  subject_name: string;
  title: string;
  description: string | null;
  file_name: string;
  file_type: string;
  file_size: number;
  teacher_display_name: string;
  created_at: string;
}

export interface JoinPayload {
  session_token: string;
  expires_at: string;
  classroom: Classroom;
}

export interface StudentSession {
  token: string;
  classroomId: string;
  classroomName: string;
  institutionName: string;
  fieldOfStudy: string;
  level: string;
  studyYear: string;
  academicYear: string;
  joinCode: string;
  joinedAt: number;
}

export interface ClassroomDetail {
  classroom: Classroom;
  subjects: Subject[];
  document_count: number;
}

export interface PublishContext {
  subject: { id: string; name: string; description: string | null };
  classroom: {
    id: string;
    class_name: string;
    institution_name: string;
    field_of_study: string;
    level: string;
    study_year: string;
    academic_year: string;
  };
  teacher_name_hint: string | null;
  max_upload_bytes: number;
  allowed_extensions: string[];
  publications_last_hour: number;
}

export interface OwnerSpace {
  classroom: Classroom;
  subjects: Subject[];
  stats: { subjects: number; documents: number; devices: number };
}

export interface SignedDocument {
  document: DocumentItem;
  signed_url: string;
  expires_in: number;
}

export interface ApiErrorShape {
  code: string;
  message: string;
}
