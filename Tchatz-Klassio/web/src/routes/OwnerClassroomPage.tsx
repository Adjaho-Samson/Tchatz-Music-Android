import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, AppHeader, EmptyState, Field, FileBadge, Footer, Modal, Spinner } from '../components/ui';
import { ownerApi } from '../lib/api';
import { PUBLIC_APP_URL, supabase } from '../lib/supabase';
import type { DocumentItem, OwnerSpace } from '../lib/types';
import { copyToClipboard, formatDate, formatSize, shareLink } from '../lib/format';

export default function OwnerClassroomPage() {
  const { classroomId = '' } = useParams();
  const navigate = useNavigate();

  const [space, setSpace] = useState<OwnerSpace | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [subjectFilter, setSubjectFilter] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [freshLink, setFreshLink] = useState<string | null>(null);
  const [showSubject, setShowSubject] = useState(false);
  const [subjectForm, setSubjectForm] = useState({ name: '', description: '' });
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const { data } = await supabase.auth.getSession();
    if (!data.session) {
      navigate('/responsable');
      return;
    }
    setLoading(true);
    try {
      const value = await ownerApi.space(classroomId);
      setSpace(value);
      setError(null);
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setLoading(false);
    }
  }, [classroomId, navigate]);

  const loadDocuments = useCallback(
    async (subjectId: string | null) => {
      const session = await supabase.auth.getSession();
      if (!session.data.session) return;
      try {
        // Lecture propriétaire : vérifiée côté serveur (RLS + fonction dédiée).
        const { data, error: rpcError } = await supabase.rpc('api_owner_documents', {
          p_classroom_id: classroomId,
          p_subject_id: subjectId,
        });
        if (rpcError) throw new Error(rpcError.message);
        setDocuments((data ?? []) as DocumentItem[]);
      } catch {
        setDocuments([]);
      }
    },
    [classroomId],
  );

  useEffect(() => {
    void load();
    void loadDocuments(null);
  }, [load, loadDocuments]);

  async function createSubject(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      const result = await ownerApi.createSubject(
        classroomId,
        subjectForm.name,
        subjectForm.description,
      );
      setFreshLink(`${PUBLIC_APP_URL.replace(/\/$/, '')}/p/${result.link.token}`);
      setShowSubject(false);
      setSubjectForm({ name: '', description: '' });
      setInfo('Matière créée. Copiez le lien de publication avant de fermer cet écran.');
      await load();
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function rotate(subjectId: string) {
    setBusy(true);
    try {
      const result = await ownerApi.rotateLink(subjectId);
      setFreshLink(`${PUBLIC_APP_URL.replace(/\/$/, '')}/p/${result.link.token}`);
      setInfo('Nouveau lien généré. L’ancien lien est immédiatement révoqué.');
      await load();
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function revoke(subjectId: string) {
    if (!window.confirm('Révoquer ce lien ? Aucune publication ne sera plus possible avec ce lien.')) return;
    setBusy(true);
    try {
      await ownerApi.revokeLink(subjectId);
      setInfo('Lien révoqué.');
      await load();
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function deleteSubject(subjectId: string) {
    if (!window.confirm('Supprimer cette matière et tous ses documents ?')) return;
    setBusy(true);
    try {
      const result = await ownerApi.deleteSubject(subjectId);
      await ownerApi.purgeFiles(result.file_paths ?? []);
      setInfo('Matière supprimée, fichiers retirés du stockage.');
      await load();
      await loadDocuments(subjectFilter);
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function deleteDocument(documentId: string) {
    if (!window.confirm('Supprimer définitivement ce document ?')) return;
    setBusy(true);
    try {
      const result = await ownerApi.deleteDocument(documentId);
      if (result.file_path) await ownerApi.purgeFiles([result.file_path]);
      setInfo('Document supprimé et fichier retiré du stockage.');
      await loadDocuments(subjectFilter);
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function regenerateCode() {
    setBusy(true);
    try {
      const result = await ownerApi.regenerateJoinCode(classroomId);
      setInfo(`Nouveau code de classe : ${result.join_code}`);
      await load();
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function toggleArchive(archived: boolean) {
    setBusy(true);
    try {
      await ownerApi.archiveClassroom(classroomId, archived);
      setInfo(archived ? 'Classe fermée : les étudiants n’y ont plus accès.' : 'Classe réactivée.');
      await load();
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function deleteClassroom() {
    if (
      !window.confirm(
        'Supprimer définitivement cette classe, ses matières et tous ses documents ? Cette action est irréversible.',
      )
    ) {
      return;
    }
    setBusy(true);
    try {
      const result = await ownerApi.deleteClassroom(classroomId);
      await ownerApi.purgeFiles(result.file_paths ?? []);
      navigate('/responsable/classes');
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (loading && !space) {
    return (
      <>
        <AppHeader />
        <main className="page">
          <div className="container">
            <div className="skeleton" />
          </div>
        </main>
      </>
    );
  }

  if (!space) {
    return (
      <>
        <AppHeader />
        <main className="page">
          <div className="container">
            <Alert tone="error">{error ?? 'Classe introuvable.'}</Alert>
            <p style={{ marginTop: 12 }}>
              <Link className="btn btn-outline" to="/responsable/classes">
                Retour au tableau de bord
              </Link>
            </p>
          </div>
        </main>
      </>
    );
  }

  const filteredDocuments = subjectFilter
    ? documents.filter((document) => document.subject_id === subjectFilter)
    : documents;

  return (
    <>
      <AppHeader
        right={
          <Link className="nav-link" to="/responsable/classes">
            Mes classes
          </Link>
        }
      />
      <main className="page">
        <div className="container">
          <div className="row-between">
            <div>
              <h1>{space.classroom.class_name}</h1>
              <p className="muted small">
                {space.classroom.institution_name} · {space.classroom.level}{' '}
                {space.classroom.study_year} · {space.classroom.field_of_study} ·{' '}
                {space.classroom.academic_year}
              </p>
            </div>
            <button className="btn" onClick={() => setShowSubject(true)} disabled={busy}>
              Nouvelle matière
            </button>
          </div>

          {info ? (
            <div style={{ marginTop: 14 }}>
              <Alert tone="success">{info}</Alert>
            </div>
          ) : null}
          {error ? (
            <div style={{ marginTop: 14 }}>
              <Alert tone="error">{error}</Alert>
            </div>
          ) : null}

          <div className="grid" style={{ marginTop: 18 }}>
            <div className="card">
              <p className="small muted">Code étudiant</p>
              <div className="row-between">
                <span style={{ fontSize: '1.5rem', fontWeight: 800, letterSpacing: '0.12em', color: 'var(--navy)' }}>
                  {space.classroom.join_code}
                </span>
                <div className="row" style={{ gap: 6 }}>
                  <button
                    className="btn btn-outline btn-sm"
                    onClick={() => void copyToClipboard(space.classroom.join_code)}
                  >
                    Copier
                  </button>
                  <button className="btn btn-ghost btn-sm" onClick={() => void regenerateCode()} disabled={busy}>
                    Renouveler
                  </button>
                </div>
              </div>
              <p className="hint" style={{ marginTop: 8 }}>
                Les étudiants saisissent ce code pour accéder à la classe, sans compte.
              </p>
            </div>
            <div className="stat">
              <strong>{space.stats.subjects}</strong>
              <span>Matières</span>
            </div>
            <div className="stat">
              <strong>{space.stats.documents}</strong>
              <span>Documents</span>
            </div>
            <div className="stat">
              <strong>{space.stats.devices}</strong>
              <span>Appareils rattachés</span>
            </div>
          </div>

          {freshLink ? (
            <div className="card" style={{ marginTop: 16, borderColor: 'var(--warning)' }}>
              <h3>Lien de publication</h3>
              <p className="small muted" style={{ marginTop: 6 }}>
                Ce lien n’est affiché qu’une seule fois. Transmettez-le à l’enseignant concerné.
              </p>
              <div className="link-box" style={{ marginTop: 10 }}>
                {freshLink}
              </div>
              <div className="row" style={{ marginTop: 10 }}>
                <button className="btn btn-sm" onClick={() => void copyToClipboard(freshLink)}>
                  Copier le lien
                </button>
                <button className="btn btn-outline btn-sm" onClick={() => void shareLink(freshLink)}>
                  Partager
                </button>
                <button className="btn btn-ghost btn-sm" onClick={() => setFreshLink(null)}>
                  Masquer
                </button>
              </div>
            </div>
          ) : null}

          <section style={{ marginTop: 24 }}>
            <div className="section-title">
              <h2>Matières</h2>
            </div>
            {space.subjects.length === 0 ? (
              <div className="card">
                <EmptyState
                  icon="+"
                  title="Aucune matière"
                  body="Chaque matière génère automatiquement un lien de publication privé pour l’enseignant."
                />
              </div>
            ) : null}
            <div className="list">
              {space.subjects.map((subject) => (
                <article key={subject.id} className="list-item" style={{ flexWrap: 'wrap' }}>
                  <div className="grow">
                    <div className="card-title">{subject.name}</div>
                    <p className="tiny muted">
                      {subject.document_count} document(s)
                      {subject.description ? ` · ${subject.description}` : ''}
                    </p>
                  </div>
                  {subject.link ? (
                    <span className="pill pill-success">Lien actif</span>
                  ) : (
                    <span className="pill pill-warning">Aucun lien actif</span>
                  )}
                  <div className="row" style={{ gap: 6 }}>
                    <button
                      className="btn btn-outline btn-sm"
                      onClick={() => {
                        setSubjectFilter(subject.id);
                        void loadDocuments(subject.id);
                      }}
                    >
                      Documents
                    </button>
                    <button className="btn btn-outline btn-sm" onClick={() => void rotate(subject.id)} disabled={busy}>
                      Nouveau lien
                    </button>
                    <button className="btn btn-ghost btn-sm" onClick={() => void revoke(subject.id)} disabled={busy}>
                      Révoquer
                    </button>
                    <button className="btn btn-danger btn-sm" onClick={() => void deleteSubject(subject.id)} disabled={busy}>
                      Supprimer
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section style={{ marginTop: 24 }}>
            <div className="section-title">
              <h2>Documents publiés</h2>
              <div className="row" style={{ gap: 6 }}>
                <button
                  className={`tag ${subjectFilter === null ? 'active' : ''}`}
                  onClick={() => {
                    setSubjectFilter(null);
                    void loadDocuments(null);
                  }}
                >
                  Toutes
                </button>
                {space.subjects.map((subject) => (
                  <button
                    key={subject.id}
                    className={`tag ${subjectFilter === subject.id ? 'active' : ''}`}
                    onClick={() => {
                      setSubjectFilter(subject.id);
                      void loadDocuments(subject.id);
                    }}
                  >
                    {subject.name}
                  </button>
                ))}
              </div>
            </div>
            {filteredDocuments.length === 0 ? (
              <div className="card">
                <EmptyState
                  icon="↓"
                  title="Aucun document"
                  body="Les documents publiés par les enseignants apparaîtront ici."
                />
              </div>
            ) : (
              <div className="list">
                {filteredDocuments.map((document) => (
                  <article key={document.id} className="list-item">
                    <FileBadge type={document.file_type} />
                    <div className="grow">
                      <div className="card-title truncate">{document.title}</div>
                      <p className="tiny muted">
                        {document.subject_name} · {document.teacher_display_name} ·{' '}
                        {formatDate(document.created_at)} · {formatSize(document.file_size)}
                      </p>
                    </div>
                    <button
                      className="btn btn-outline btn-sm"
                      onClick={() => {
                        void ownerApi
                          .signedUrl(document.id)
                          .then((signed) => window.open(signed.signed_url, '_blank', 'noopener'))
                          .catch((exception: Error) => setError(exception.message));
                      }}
                    >
                      Ouvrir
                    </button>
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={() => void deleteDocument(document.id)}
                      disabled={busy}
                    >
                      Supprimer
                    </button>
                  </article>
                ))}
              </div>
            )}
          </section>

          <section style={{ marginTop: 26 }}>
            <div className="card stack-sm">
              <h3>Gestion de l’espace</h3>
              <p className="small muted">
                La fermeture bloque l’accès étudiant et la publication, sans supprimer les documents.
              </p>
              <div className="row">
                <button
                  className="btn btn-outline"
                  onClick={() => void toggleArchive(!space.classroom.is_archived)}
                  disabled={busy}
                >
                  {space.classroom.is_archived ? 'Réactiver la classe' : 'Fermer la classe'}
                </button>
                <button className="btn btn-danger" onClick={() => void deleteClassroom()} disabled={busy}>
                  Supprimer la classe
                </button>
              </div>
            </div>
          </section>
        </div>
      </main>
      <Footer />

      {showSubject ? (
        <Modal title="Nouvelle matière" onClose={() => setShowSubject(false)}>
          <form className="stack" onSubmit={createSubject}>
            <Field label="Nom de la matière">
              <input
                className="input"
                value={subjectForm.name}
                onChange={(event) => setSubjectForm({ ...subjectForm, name: event.target.value })}
                placeholder="Programmation orientée objet"
                required
                minLength={2}
              />
            </Field>
            <Field label="Description (facultative)">
              <textarea
                className="textarea"
                value={subjectForm.description}
                onChange={(event) =>
                  setSubjectForm({ ...subjectForm, description: event.target.value })
                }
                placeholder="Cours et TD de POO en Java"
              />
            </Field>
            <button className="btn btn-block" type="submit" disabled={busy || subjectForm.name.trim().length < 2}>
              {busy ? <Spinner /> : null}
              Créer la matière et générer le lien
            </button>
          </form>
        </Modal>
      ) : null}
    </>
  );
}
