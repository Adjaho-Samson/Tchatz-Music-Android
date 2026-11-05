import type { StudentSession } from './types';

/**
 * Sessions étudiantes sans compte (navigateur).
 *
 * Le stockage local ne contient QUE le jeton opaque fourni par le serveur
 * et les informations publiques de la classe. Aucun email, aucun identifiant
 * matériel, aucune donnée personnelle.
 */

const SESSIONS_KEY = 'klassio.student.sessions.v1';
const DEVICE_KEY = 'klassio.device.v1';
const NOTIFICATIONS_KEY = 'klassio.notifications.v1';
const LAST_SEEN_KEY = 'klassio.lastSeen.v1';

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function write(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* stockage indisponible (navigation privée) : la session reste en mémoire */
  }
}

export const studentSessions = {
  all(): StudentSession[] {
    return read<StudentSession[]>(SESSIONS_KEY, []);
  },

  /** Conversion des données publiques du serveur vers la session locale. */
  fromJoin(payload: {
    session_token: string;
    classroom: {
      id: string;
      class_name: string;
      institution_name: string;
      field_of_study: string;
      level: string;
      study_year: string;
      academic_year: string;
      join_code: string;
    };
  }): StudentSession {
    return {
      token: payload.session_token,
      classroomId: payload.classroom.id,
      classroomName: payload.classroom.class_name,
      institutionName: payload.classroom.institution_name,
      fieldOfStudy: payload.classroom.field_of_study,
      level: payload.classroom.level,
      studyYear: payload.classroom.study_year,
      academicYear: payload.classroom.academic_year,
      joinCode: payload.classroom.join_code,
      joinedAt: Date.now(),
    };
  },

  save(session: StudentSession) {
    const others = studentSessions.all().filter((item) => item.classroomId !== session.classroomId);
    write(SESSIONS_KEY, [...others, session]);
  },

  remove(classroomId: string) {
    write(
      SESSIONS_KEY,
      studentSessions.all().filter((item) => item.classroomId !== classroomId),
    );
  },

  find(classroomId: string): StudentSession | undefined {
    return studentSessions.all().find((item) => item.classroomId === classroomId);
  },
};

export const device = {
  /**
   * Identifiant local aléatoire (jamais un identifiant matériel).
   * Il permet seulement de rattacher l'appareil à la classe pour les notifications.
   */
  id(): string {
    const existing = localStorage.getItem(DEVICE_KEY);
    if (existing) return existing;
    const generated =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID() + crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(DEVICE_KEY, generated);
    return generated;
  },
};

export const notificationsEnabled = {
  get(): boolean {
    return read<boolean>(NOTIFICATIONS_KEY, true);
  },
  set(value: boolean) {
    write(NOTIFICATIONS_KEY, value);
  },
};

export const lastSeen = {
  get(classroomId: string): string | null {
    const all = read<Record<string, string>>(LAST_SEEN_KEY, {});
    return all[classroomId] ?? null;
  },
  set(classroomId: string, isoDate: string) {
    const all = read<Record<string, string>>(LAST_SEEN_KEY, {});
    write(LAST_SEEN_KEY, { ...all, [classroomId]: isoDate });
  },
};
