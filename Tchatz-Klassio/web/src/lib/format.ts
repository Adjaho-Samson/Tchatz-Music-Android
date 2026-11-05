const EXTENSIONS: Record<string, string> = {
  pdf: 'PDF',
  doc: 'DOC',
  docx: 'DOCX',
  ppt: 'PPT',
  pptx: 'PPTX',
  xls: 'XLS',
  xlsx: 'XLSX',
  jpg: 'JPG',
  jpeg: 'JPEG',
  png: 'PNG',
};

export function extensionOf(fileName: string): string {
  return (fileName.split('.').pop() ?? '').toLowerCase();
}

export function fileLabel(type: string): string {
  return EXTENSIONS[type.toLowerCase()] ?? type.toUpperCase().slice(0, 4);
}

export function fileClass(type: string): string {
  switch (type.toLowerCase()) {
    case 'pdf':
      return 'file-pdf';
    case 'doc':
    case 'docx':
      return 'file-doc';
    case 'ppt':
    case 'pptx':
      return 'file-ppt';
    case 'xls':
    case 'xlsx':
      return 'file-xls';
    case 'jpg':
    case 'jpeg':
    case 'png':
      return 'file-img';
    default:
      return '';
  }
}

export function formatSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '—';
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} Mo`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} Ko`;
  return `${bytes} o`;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function normalizeCode(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
}

export function shareLink(url: string): Promise<void> {
  if (navigator.share) {
    return navigator.share({ title: 'Tchatz Klassio', text: 'Lien de publication', url });
  }
  return copyToClipboard(url);
}

export async function copyToClipboard(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const area = document.createElement('textarea');
    area.value = text;
    document.body.appendChild(area);
    area.select();
    document.execCommand('copy');
    document.body.removeChild(area);
  }
}

/** Ouvre un document téléchargé depuis le cache navigateur (hors connexion). */
export function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}
