import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, AppHeader, EmptyState, FileBadge, Footer } from '../components/ui';
import { studentApi } from '../lib/api';
import { studentSessions } from '../lib/session';
import type { DocumentItem } from '../lib/types';
import { formatDateTime, formatSize } from '../lib/format';

export default function DocumentPage() {
  const { classroomId = '', documentId = '' } = useParams();
  const navigate = useNavigate();
  const session = studentSessions.find(classroomId);

  const [document, setDocument] = useState<DocumentItem | null>(null);
  const [signedUrl, setSignedUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [localAvailable, setLocalAvailable] = useState(false);

  const load = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    try {
      const documents = await studentApi.documents(session.token, { limit: 100 });
      const found = documents.find((item) => item.id === documentId) ?? null;
      setDocument(found);
      if (found) {
        const signed = await studentApi.signedUrl(session.token, found.id, 600);
        setSignedUrl(signed.signed_url);
        localStorage.setItem(`klassio.doc.${documentId}`, JSON.stringify(found));
      }
      setError(null);
    } catch (exception) {
      const cached = localStorage.getItem(`klassio.doc.${documentId}`);
      if (cached) {
        setDocument(JSON.parse(cached) as DocumentItem);
      } else {
        setError((exception as Error).message);
      }
    } finally {
      setLoading(false);
    }
  }, [session, documentId]);

  useEffect(() => {
    if (!session) {
      navigate('/rejoindre');
      return;
    }
    setLocalAvailable(Boolean(localStorage.getItem(`klassio.file.${documentId}`)));
    void load();
  }, [session, navigate, load, documentId]);

  if (!session) return null;

  return (
    <>
      <AppHeader
        right={
          <Link className="nav-link" to={`/c/${classroomId}/matiere/tous`}>
            Bibliothèque
          </Link>
        }
      />
      <main className="page">
        <div className="container container-narrow">
          {loading && !document ? <div className="skeleton" /> : null}
          {error ? <Alert tone="error">{error}</Alert> : null}
          {!loading && !document && !error ? (
            <div className="card">
              <EmptyState icon="?" title="Document introuvable" body="Ce document a peut-être été supprimé par le responsable." />
            </div>
          ) : null}

          {document ? (
            <div className="stack">
              <div className="card">
                <div className="row">
                  <FileBadge type={document.file_type} />
                  <div className="grow">
                    <h1 style={{ fontSize: '1.25rem' }}>{document.title}</h1>
                    <p className="small muted">{document.subject_name}</p>
                  </div>
                </div>
                {document.description ? (
                  <p className="small" style={{ marginTop: 12 }}>
                    {document.description}
                  </p>
                ) : null}
                <div className="grid" style={{ marginTop: 14 }}>
                  <div className="stat">
                    <strong style={{ fontSize: '0.95rem' }}>{document.teacher_display_name}</strong>
                    <span>Enseignant</span>
                  </div>
                  <div className="stat">
                    <strong style={{ fontSize: '0.95rem' }}>{formatDateTime(document.created_at)}</strong>
                    <span>Publié le</span>
                  </div>
                  <div className="stat">
                    <strong style={{ fontSize: '0.95rem' }}>{formatSize(document.file_size)}</strong>
                    <span>{document.file_type.toUpperCase()}</span>
                  </div>
                </div>
              </div>

              <div className="card stack-sm">
                <div className="row">
                  {signedUrl ? (
                    <a className="btn" href={signedUrl} target="_blank" rel="noreferrer">
                      Consulter le document
                    </a>
                  ) : null}
                  {localAvailable ? (
                    <button
                      className="btn btn-outline"
                      onClick={() => {
                        const raw = localStorage.getItem(`klassio.file.${documentId}`);
                        if (!raw) return;
                        const parsed = JSON.parse(raw) as { dataUrl: string; fileName: string };
                        const anchor = window.document.createElement('a');
                        anchor.href = parsed.dataUrl;
                        anchor.download = parsed.fileName;
                        anchor.click();
                      }}
                    >
                      Version hors connexion
                    </button>
                  ) : null}
                </div>
                <p className="hint">
                  Le lien de consultation est signé et expire après quelques minutes. Le fichier reste privé :
                  il n’est jamais accessible publiquement.
                </p>
              </div>
            </div>
          ) : null}
        </div>
      </main>
      <Footer />
    </>
  );
}
