import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Alert, Field, FileBadge, Progress, Spinner } from '../components/ui';
import { KlassioError, publishApi } from '../lib/api';
import type { PublishContext, SignedDocument } from '../lib/types';
import { extensionOf, formatSize } from '../lib/format';

const ACCEPTED =
  '.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.jpg,.jpeg,.png';

/**
 * Page de publication enseignant — accès par lien privé, sans compte.
 * Le jeton du lien est la seule autorisation : il ne donne accès qu'à UNE matière
 * et ne permet aucune action d'administration.
 */
export default function PublishPage() {
  const { token = '' } = useParams();
  const [context, setContext] = useState<PublishContext | null>(null);
  const [contextError, setContextError] = useState<{ code: string; message: string } | null>(null);
  const [loadingContext, setLoadingContext] = useState(true);

  const [teacherName, setTeacherName] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [published, setPublished] = useState<SignedDocument | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    setLoadingContext(true);
    try {
      const data = await publishApi.context(token);
      setContext(data);
      if (data.teacher_name_hint) setTeacherName(data.teacher_name_hint);
      setContextError(null);
    } catch (exception) {
      const err = exception as KlassioError;
      setContextError({ code: err.code, message: err.message });
    } finally {
      setLoadingContext(false);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  const fileError = useMemo(() => {
    if (!file || !context) return null;
    const extension = extensionOf(file.name);
    if (!context.allowed_extensions.includes(extension)) {
      return `Format « ${extension.toUpperCase()} » non autorisé. Formats acceptés : PDF, DOC/DOCX, PPT/PPTX, XLS/XLSX, JPG, PNG.`;
    }
    if (file.size > context.max_upload_bytes) {
      return `Fichier trop volumineux (${formatSize(file.size)}). Limite : ${formatSize(context.max_upload_bytes)}.`;
    }
    if (file.size === 0) {
      return 'Le fichier sélectionné est vide.';
    }
    return null;
  }, [file, context]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!context || !file || submitting || fileError) return;
    setSubmitting(true);
    setError(null);
    setProgress(0);
    try {
      const upload = await publishApi.beginUpload(token, {
        title,
        description,
        fileName: file.name,
        fileSize: file.size,
        teacherName,
      });
      const result = await publishApi.upload(token, upload.document_id, upload.file_path, file, setProgress);
      setPublished(result);
      setProgress(null);
      setTitle('');
      setDescription('');
      setFile(null);
      if (fileInput.current) fileInput.current.value = '';
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (exception) {
      setError((exception as Error).message);
      setProgress(null);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="publish-shell">
      <div className="container container-narrow">
        <div className="publish-header row-between">
          <div>
            <div className="brand" style={{ color: '#fff' }}>
              <span className="brand-mark" style={{ background: 'rgba(255,255,255,0.16)' }}>
                K
              </span>
              Tchatz Klassio
            </div>
            <h1 style={{ marginTop: 12 }}>Publication d’un document</h1>
            {context ? (
              <p>
                {context.classroom.institution_name} · {context.classroom.class_name} ·{' '}
                {context.subject.name}
              </p>
            ) : (
              <p>Vérification du lien de publication…</p>
            )}
          </div>
        </div>

        {loadingContext ? (
          <div className="card center" style={{ padding: 40 }}>
            <Spinner dark />
            <p className="small muted" style={{ marginTop: 12 }}>
              Vérification du lien…
            </p>
          </div>
        ) : null}

        {contextError ? (
          <div className="card stack">
            <Alert tone="error">
              <div>
                <strong>
                  {contextError.code === 'LINK_REVOKED' ? 'Lien révoqué' : 'Lien invalide'}
                </strong>
                <p style={{ marginTop: 4 }}>{contextError.message}</p>
              </div>
            </Alert>
            <p className="small muted">
              Contactez le responsable de classe pour obtenir un nouveau lien de publication. Un lien révoqué
              ne permet plus aucune publication.
            </p>
          </div>
        ) : null}

        {published ? (
          <div className="card stack">
            <Alert tone="success">
              <div>
                <strong>Document publié avec succès</strong>
                <p style={{ marginTop: 4 }}>
                  « {published.document.title} » est maintenant visible par les étudiants de{' '}
                  {published.document.subject_name ?? context?.subject.name}.
                </p>
              </div>
            </Alert>
            <div className="list-item">
              <FileBadge type={published.document.file_type} />
              <div className="grow">
                <div className="card-title">{published.document.title}</div>
                <p className="tiny muted">
                  {published.document.teacher_display_name} ·{' '}
                  {formatSize(published.document.file_size)}
                </p>
              </div>
            </div>
            <div className="row">
              <button className="btn" onClick={() => setPublished(null)}>
                Publier un autre document
              </button>
              {published.signed_url ? (
                <a className="btn btn-outline" href={published.signed_url} target="_blank" rel="noreferrer">
                  Vérifier le fichier publié
                </a>
              ) : null}
            </div>
          </div>
        ) : null}

        {context && !published ? (
          <form className="card stack" onSubmit={handleSubmit}>
            <div className="row-between">
              <div>
                <h2 style={{ fontSize: '1.05rem' }}>{context.subject.name}</h2>
                <p className="small muted">
                  {context.classroom.field_of_study} · {context.classroom.level}{' '}
                  {context.classroom.study_year} · {context.classroom.academic_year}
                </p>
              </div>
              <span className="pill">{formatSize(context.max_upload_bytes)} max</span>
            </div>

            <Field label="Votre nom d’affichage" hint="Tel qu’il apparaîtra pour les étudiants.">
              <input
                className="input"
                value={teacherName}
                onChange={(event) => setTeacherName(event.target.value)}
                placeholder="Dr. Hounkpatin"
                maxLength={80}
                required
              />
            </Field>

            <Field label="Titre du document">
              <input
                className="input"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder="Chapitre 1 — Introduction à la POO"
                maxLength={160}
                required
              />
            </Field>

            <Field label="Description (facultative)">
              <textarea
                className="textarea"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder="Support de cours, semestre 1"
                maxLength={1200}
              />
            </Field>

            <Field
              label="Fichier"
              hint="PDF, DOC/DOCX, PPT/PPTX, XLS/XLSX, JPG, PNG — la taille réelle est vérifiée sur le serveur."
            >
              <input
                ref={fileInput}
                className="input"
                type="file"
                accept={ACCEPTED}
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
            </Field>

            {file ? (
              <div className="list-item">
                <FileBadge type={extensionOf(file.name)} />
                <div className="grow">
                  <div className="card-title truncate">{file.name}</div>
                  <p className="tiny muted">{formatSize(file.size)}</p>
                </div>
              </div>
            ) : null}

            {fileError ? <Alert tone="warning">{fileError}</Alert> : null}
            {error ? <Alert tone="error">{error}</Alert> : null}

            {progress !== null ? (
              <div className="stack-sm">
                <Progress value={progress} />
                <span className="hint">
                  Envoi en cours… {progress} % {progress === 100 ? '(vérification serveur)' : ''}
                </span>
              </div>
            ) : null}

            <button
              className="btn btn-block"
              type="submit"
              disabled={
                submitting ||
                !file ||
                Boolean(fileError) ||
                teacherName.trim().length < 2 ||
                title.trim().length < 2
              }
            >
              {submitting ? <Spinner /> : null}
              {submitting ? 'Publication…' : 'Publier le document'}
            </button>

            <p className="hint">
              Ce lien ne concerne qu’une seule matière ({context.subject.name}). Il ne donne aucun accès à
              l’administration de la classe, à la liste des étudiants ou aux autres matières.
            </p>
          </form>
        ) : null}

        <p className="tiny center" style={{ marginTop: 18, color: 'rgba(255,255,255,0.75)' }}>
          Tchatz Klassio — Une solution de Tchatz
        </p>
      </div>
    </div>
  );
}
