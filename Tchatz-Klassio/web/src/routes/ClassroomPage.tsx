import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, AppHeader, EmptyState, Footer } from '../components/ui';
import { studentApi } from '../lib/api';
import { studentSessions } from '../lib/session';
import type { ClassroomDetail, DocumentItem } from '../lib/types';
import { formatDate, formatSize } from '../lib/format';

export default function ClassroomPage() {
  const { classroomId = '' } = useParams();
  const navigate = useNavigate();
  const session = studentSessions.find(classroomId);
  const [detail, setDetail] = useState<ClassroomDetail | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [offline, setOffline] = useState(false);

  const load = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    try {
      const [classroom, recent] = await Promise.all([
        studentApi.classroom(session.token),
        studentApi.documents(session.token, { limit: 6 }),
      ]);
      setDetail(classroom);
      setDocuments(recent);
      setError(null);
      setOffline(false);
      localStorage.setItem(`klassio.cache.${classroomId}`, JSON.stringify(classroom));
      localStorage.setItem(`klassio.cache.docs.${classroomId}`, JSON.stringify(recent));
    } catch (exception) {
      const message = (exception as Error).message;
      const cached = localStorage.getItem(`klassio.cache.${classroomId}`);
      const cachedDocs = localStorage.getItem(`klassio.cache.docs.${classroomId}`);
      if (cached) {
        setDetail(JSON.parse(cached) as ClassroomDetail);
        setDocuments(cachedDocs ? (JSON.parse(cachedDocs) as DocumentItem[]) : []);
        setOffline(true);
        setError(null);
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  }, [session, classroomId]);

  useEffect(() => {
    if (!session) {
      navigate('/rejoindre');
      return;
    }
    void load();
  }, [session, navigate, load]);

  if (!session) return null;

  return (
    <>
      <AppHeader
        right={
          <button
            className="nav-link"
            onClick={() => {
              studentSessions.remove(classroomId);
              navigate('/');
            }}
          >
            Quitter
          </button>
        }
      >
        <Link className="nav-link" to="/">
          Accueil
        </Link>
      </AppHeader>

      <main className="page">
        <div className="container">
          <div className="row-between">
            <div>
              <h1>{detail?.classroom.institution_name ?? session.institutionName}</h1>
              <p className="muted small">
                {[
                  detail?.classroom.level ?? session.level,
                  detail?.classroom.study_year ?? session.studyYear,
                  detail?.classroom.field_of_study ?? session.fieldOfStudy,
                  detail?.classroom.academic_year ?? session.academicYear,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            </div>
            <div className="row">
              <span className="pill">Code {detail?.classroom.join_code ?? session.joinCode}</span>
              <button className="btn btn-outline btn-sm" onClick={() => void load()}>
                Actualiser
              </button>
            </div>
          </div>

          {offline ? (
            <div style={{ marginTop: 14 }}>
              <Alert tone="warning">
                Hors connexion : affichage des dernières informations enregistrées. Les documents déjà
                téléchargés restent accessibles.
              </Alert>
            </div>
          ) : null}
          {error ? (
            <div style={{ marginTop: 14 }}>
              <Alert tone="error">{error}</Alert>
            </div>
          ) : null}

          <div className="grid" style={{ marginTop: 18 }}>
            <div className="stat">
              <strong>{detail?.subjects.length ?? 0}</strong>
              <span>Matières</span>
            </div>
            <div className="stat">
              <strong>{detail?.document_count ?? 0}</strong>
              <span>Documents disponibles</span>
            </div>
            <div className="stat">
              <strong>{detail?.classroom.class_name ?? session.classroomName}</strong>
              <span>Classe</span>
            </div>
          </div>

          <section style={{ marginTop: 24 }}>
            <div className="section-title">
              <h2>Matières</h2>
            </div>
            {loading && !detail ? (
              <div className="list">
                <div className="skeleton" />
                <div className="skeleton" />
              </div>
            ) : null}
            {!loading && (detail?.subjects.length ?? 0) === 0 ? (
              <div className="card">
                <EmptyState
                  icon="—"
                  title="Aucune matière pour le moment"
                  body="Le responsable de classe n’a pas encore créé de matière."
                />
              </div>
            ) : null}
            <div className="grid">
              {detail?.subjects.map((subject) => (
                <Link
                  key={subject.id}
                  to={`/c/${classroomId}/matiere/${subject.id}`}
                  className="card card-link"
                >
                  <div className="row-between">
                    <div className="card-title">{subject.name}</div>
                    <span className="pill">{subject.document_count} doc.</span>
                  </div>
                  {subject.description ? (
                    <p className="small muted" style={{ marginTop: 6 }}>
                      {subject.description}
                    </p>
                  ) : null}
                  <p className="tiny muted" style={{ marginTop: 10 }}>
                    {subject.last_document_at
                      ? `Dernier ajout le ${formatDate(subject.last_document_at)}`
                      : 'Aucun document publié'}
                  </p>
                </Link>
              ))}
            </div>
          </section>

          <section style={{ marginTop: 26 }}>
            <div className="section-title">
              <h2>Documents récents</h2>
              <Link to={`/c/${classroomId}/matiere/tous`} className="btn btn-outline btn-sm">
                Toute la bibliothèque
              </Link>
            </div>
            {documents.length === 0 ? (
              <div className="card">
                <EmptyState
                  icon="↓"
                  title="Aucun document"
                  body="Les documents publiés par les enseignants apparaîtront ici."
                />
              </div>
            ) : (
              <div className="list">
                {documents.map((document) => (
                  <Link
                    key={document.id}
                    to={`/c/${classroomId}/document/${document.id}`}
                    className="list-item card-link"
                    style={{ color: 'inherit' }}
                  >
                    <div className={`file-badge file-${document.file_type.slice(0, 3)}`}>
                      {document.file_type.slice(0, 4).toUpperCase()}
                    </div>
                    <div className="grow">
                      <div className="card-title truncate">{document.title}</div>
                      <p className="tiny muted">
                        {document.subject_name} · {document.teacher_display_name} ·{' '}
                        {formatDate(document.created_at)} · {formatSize(document.file_size)}
                      </p>
                    </div>
                    <span className="pill">Ouvrir</span>
                  </Link>
                ))}
              </div>
            )}
          </section>
        </div>
      </main>
      <Footer />
    </>
  );
}
