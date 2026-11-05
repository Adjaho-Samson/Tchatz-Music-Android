import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, AppHeader, EmptyState, FileBadge, Footer } from '../components/ui';
import { studentApi, KlassioError } from '../lib/api';
import { studentSessions } from '../lib/session';
import type { DocumentItem, Subject } from '../lib/types';
import { formatDate, formatSize } from '../lib/format';

export default function SubjectPage() {
  const { classroomId = '', subjectId = 'tous' } = useParams();
  const navigate = useNavigate();
  const session = studentSessions.find(classroomId);

  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [offline, setOffline] = useState(false);
  const [downloaded, setDownloaded] = useState<Record<string, string>>({});

  const activeSubject = useMemo(
    () => (subjectId === 'tous' ? null : subjects.find((item) => item.id === subjectId) ?? null),
    [subjectId, subjects],
  );

  const load = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    try {
      const [detail, docs] = await Promise.all([
        studentApi.classroom(session.token),
        studentApi.documents(session.token, {
          subjectId: subjectId === 'tous' ? null : subjectId,
          search,
          limit: 100,
        }),
      ]);
      setSubjects(detail.subjects);
      setDocuments(docs);
      setError(null);
      setOffline(false);
      localStorage.setItem(
        `klassio.cache.docs.${classroomId}.${subjectId}`,
        JSON.stringify(docs),
      );
    } catch (exception) {
      const cached = localStorage.getItem(`klassio.cache.docs.${classroomId}.${subjectId}`);
      if (cached) {
        setDocuments(JSON.parse(cached) as DocumentItem[]);
        setOffline(true);
      } else {
        setError((exception as Error).message);
      }
    } finally {
      setLoading(false);
    }
  }, [session, classroomId, subjectId, search]);

  useEffect(() => {
    if (!session) {
      navigate('/rejoindre');
      return;
    }
    void load();
  }, [session, navigate, load]);

  useEffect(() => {
    const map: Record<string, string> = {};
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);
      if (key?.startsWith('klassio.file.')) {
        map[key.replace('klassio.file.', '')] = key;
      }
    }
    setDownloaded(map);
  }, []);

  /** Téléchargement réel via URL signée courte (bucket privé). */
  async function download(document: DocumentItem) {
    if (!session) return;
    try {
      const signed = await studentApi.signedUrl(session.token, document.id, 600);
      const response = await fetch(signed.signed_url);
      if (!response.ok) throw new KlassioError('DOWNLOAD_FAILED', 'Téléchargement impossible.');
      const blob = await response.blob();
      // Conservation locale pour consultation hors connexion
      try {
        const stored = JSON.stringify({
          title: document.title,
          fileName: document.file_name,
          type: blob.type,
          size: blob.size,
          at: Date.now(),
          dataUrl: await blobToDataUrl(blob),
        });
        localStorage.setItem(`klassio.file.${document.id}`, stored);
        setDownloaded((current) => ({ ...current, [document.id]: `klassio.file.${document.id}` }));
      } catch {
        /* quota dépassé : le fichier reste téléchargé sur le disque */
      }
      triggerDownload(blob, document.file_name);
    } catch (exception) {
      setError((exception as Error).message);
    }
  }

  function openLocal(documentId: string) {
    const raw = localStorage.getItem(`klassio.file.${documentId}`);
    if (!raw) return;
    const parsed = JSON.parse(raw) as { dataUrl: string; fileName: string };
    const anchor = window.document.createElement('a');
    anchor.href = parsed.dataUrl;
    anchor.download = parsed.fileName;
    anchor.click();
  }

  if (!session) return null;

  return (
    <>
      <AppHeader
        right={
          <Link className="nav-link" to={`/c/${classroomId}`}>
            Classe
          </Link>
        }
      />
      <main className="page">
        <div className="container">
          <h1>{activeSubject?.name ?? 'Bibliothèque documentaire'}</h1>
          <p className="muted small">
            {activeSubject?.description ?? 'Tous les documents de la classe, classés par date de publication.'}
          </p>

          <div className="card stack" style={{ marginTop: 16 }}>
            <input
              className="input"
              placeholder="Rechercher un document par titre ou nom de fichier"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <div className="tag-filter">
              <button
                className={`tag ${subjectId === 'tous' ? 'active' : ''}`}
                onClick={() => navigate(`/c/${classroomId}/matiere/tous`)}
              >
                Toutes les matières
              </button>
              {subjects.map((subject) => (
                <button
                  key={subject.id}
                  className={`tag ${subjectId === subject.id ? 'active' : ''}`}
                  onClick={() => navigate(`/c/${classroomId}/matiere/${subject.id}`)}
                >
                  {subject.name} ({subject.document_count})
                </button>
              ))}
            </div>
          </div>

          {offline ? (
            <div style={{ marginTop: 14 }}>
              <Alert tone="warning">Hors connexion : documents mis en cache local.</Alert>
            </div>
          ) : null}
          {error ? (
            <div style={{ marginTop: 14 }}>
              <Alert tone="error">{error}</Alert>
            </div>
          ) : null}

          <section style={{ marginTop: 18 }}>
            {loading ? <div className="skeleton" /> : null}
            {!loading && documents.length === 0 ? (
              <div className="card">
                <EmptyState
                  icon="↓"
                  title={search ? 'Aucun résultat' : 'Aucun document'}
                  body={
                    search
                      ? `Aucun document ne correspond à « ${search} ».`
                      : 'Les documents publiés par les enseignants apparaîtront ici.'
                  }
                />
              </div>
            ) : null}
            <div className="list">
              {documents.map((document) => (
                <article key={document.id} className="list-item">
                  <FileBadge type={document.file_type} />
                  <div className="grow">
                    <Link
                      to={`/c/${classroomId}/document/${document.id}`}
                      className="card-title"
                      style={{ color: 'inherit' }}
                    >
                      {document.title}
                    </Link>
                    <p className="tiny muted">
                      {document.subject_name} · {document.teacher_display_name} ·{' '}
                      {formatDate(document.created_at)} · {formatSize(document.file_size)}
                    </p>
                  </div>
                  <div className="row" style={{ gap: 6 }}>
                    <Link className="btn btn-outline btn-sm" to={`/c/${classroomId}/document/${document.id}`}>
                      Consulter
                    </Link>
                    {downloaded[document.id] ? (
                      <button className="btn btn-ghost btn-sm" onClick={() => openLocal(document.id)}>
                        Hors connexion
                      </button>
                    ) : (
                      <button className="btn btn-sm" onClick={() => void download(document)}>
                        Télécharger
                      </button>
                    )}
                  </div>
                </article>
              ))}
            </div>
          </section>
        </div>
      </main>
      <Footer />
    </>
  );
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

function triggerDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = window.document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  window.document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}
