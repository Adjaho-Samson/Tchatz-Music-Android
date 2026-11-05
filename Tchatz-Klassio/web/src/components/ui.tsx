import type { ReactNode } from 'react';
import { fileClass, fileLabel } from '../lib/format';

export function AppHeader({
  right,
  children,
}: {
  right?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <header className="app-header">
      <div className="container app-header-inner">
        <a href="/" className="brand">
          <span className="brand-mark" aria-hidden="true">
            K
          </span>
          <span>Tchatz Klassio</span>
        </a>
        <nav className="nav-links">
          {children}
          {right}
        </nav>
      </div>
    </header>
  );
}

export function FileBadge({ type, fileName }: { type?: string; fileName?: string }) {
  const resolved = (type ?? fileName?.split('.').pop() ?? '').toLowerCase();
  return (
    <div className={`file-badge ${fileClass(resolved)}`} aria-hidden="true">
      {fileLabel(resolved)}
    </div>
  );
}

export function Alert({
  tone = 'info',
  children,
}: {
  tone?: 'info' | 'success' | 'warning' | 'error';
  children: ReactNode;
}) {
  return <div className={`alert alert-${tone}`}>{children}</div>;
}

export function EmptyState({
  icon = '•',
  title,
  body,
}: {
  icon?: string;
  title: string;
  body?: string;
}) {
  return (
    <div className="empty">
      <div className="empty-icon" aria-hidden="true">
        {icon}
      </div>
      <div className="card-title">{title}</div>
      {body ? <p className="small muted" style={{ marginTop: 6 }}>{body}</p> : null}
    </div>
  );
}

export function Spinner({ dark = false }: { dark?: boolean }) {
  return <span className={`spinner ${dark ? 'spinner-dark' : ''}`} aria-hidden="true" />;
}

export function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
      {hint ? <span className="hint">{hint}</span> : null}
    </div>
  );
}

export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal" onClick={(event) => event.stopPropagation()}>
        <div className="row-between" style={{ marginBottom: 14 }}>
          <h2>{title}</h2>
          <button className="btn btn-ghost btn-sm" onClick={onClose} aria-label="Fermer">
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Progress({ value }: { value: number }) {
  return (
    <div className="progress" role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100}>
      <span style={{ width: `${Math.min(100, Math.max(0, value))}%` }} />
    </div>
  );
}

export function Footer() {
  return (
    <footer className="footer">
      <div className="container footer-inner">
        <span>
          Tchatz Klassio — Une solution de Tchatz · Samson Adjaho
        </span>
        <span className="row" style={{ gap: 14 }}>
          <a href="/legal">Confidentialité</a>
          <a href="/legal#conditions">Conditions d’utilisation</a>
          <a href="/responsable">Espace responsable</a>
        </span>
      </div>
    </footer>
  );
}
