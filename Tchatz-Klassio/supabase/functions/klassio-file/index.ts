// Tchatz Klassio — Edge Function de fichiers (bucket privé "documents")
//
//   POST ?action=sign&type=student|owner  -> URL signée courte, après autorisation en base
//   POST ?action=finalize                 -> déplace staging/ vers subject/<id>/ puis valide la ligne
//   POST ?action=purge                    -> supprime des fichiers déjà retirés de la base
//
// La clé service_role reste côté serveur : elle n'est jamais exposée aux applications Android/Web.
// Aucune dépendance externe n'est importée : l'API REST de Supabase est appelée directement en HTTP.
// (Cette absence d'import garantit un démarrage immédiat de la fonction, donc pas de 503 au boot.)

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const SUPABASE_URL = (Deno.env.get('SUPABASE_URL') ?? '').replace(/\/+$/, '');
const SERVICE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
const BUCKET = 'documents';

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, 'Content-Type': 'application/json' },
  });

const serviceHeaders = {
  apikey: SERVICE_KEY,
  Authorization: `Bearer ${SERVICE_KEY}`,
  'Content-Type': 'application/json',
};

/** Appelle une fonction PostgreSQL exposée par PostgREST et renvoie sa valeur JSON. */
async function rpc<T = any>(name: string, args: Record<string, unknown>): Promise<T> {
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: 'POST',
    headers: serviceHeaders,
    body: JSON.stringify(args),
  });
  const text = await response.text();
  let payload: any = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }
  if (!response.ok) {
    const message = typeof payload === 'object' && payload ? payload.message ?? payload.error ?? '' : String(payload ?? '');
    throw new RpcError(mapError(message), message);
  }
  return payload as T;
}

class RpcError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

/** URL absolue à partir du chemin relatif renvoyé par l'API Storage. */
const absolute = (signedUrl: string | undefined | null): string | null => {
  if (!signedUrl) return null;
  if (signedUrl.startsWith('http')) return signedUrl;
  const path = signedUrl.startsWith('/object/') ? signedUrl : `/object/sign/${BUCKET}/${signedUrl.replace(/^\/+/, '')}`;
  return `${SUPABASE_URL}/storage/v1${path}`;
};

async function createSignedUrl(filePath: string, expiresIn: number): Promise<string | null> {
  const response = await fetch(`${SUPABASE_URL}/storage/v1/object/sign/${BUCKET}/${encodePath(filePath)}`, {
    method: 'POST',
    headers: serviceHeaders,
    body: JSON.stringify({ expiresIn }),
  });
  if (!response.ok) return null;
  const payload = await response.json().catch(() => null);
  return absolute(payload?.signedURL ?? payload?.signedUrl ?? null);
}

async function moveObject(from: string, to: string): Promise<string | null> {
  const response = await fetch(`${SUPABASE_URL}/storage/v1/object/move`, {
    method: 'POST',
    headers: serviceHeaders,
    body: JSON.stringify({ bucketId: BUCKET, sourceKey: from, destinationKey: to }),
  });
  if (response.ok) return null;
  const payload = await response.json().catch(() => ({}));
  return payload?.message ?? payload?.error ?? `MOVE_ERROR_${response.status}`;
}

async function removeObjects(paths: string[]): Promise<string | null> {
  if (paths.length === 0) return null;
  const response = await fetch(`${SUPABASE_URL}/storage/v1/object/${BUCKET}`, {
    method: 'DELETE',
    headers: serviceHeaders,
    body: JSON.stringify({ prefixes: paths }),
  });
  if (response.ok) return null;
  const payload = await response.json().catch(() => ({}));
  return payload?.message ?? payload?.error ?? `REMOVE_ERROR_${response.status}`;
}

const encodePath = (path: string) => path.split('/').map(encodeURIComponent).join('/');

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function mapError(message: string): string {
  const known = [
    'SESSION_INVALID',
    'DOCUMENT_NOT_FOUND',
    'LINK_INVALID',
    'LINK_REVOKED',
    'UPLOAD_INCOMPLETE',
    'FILE_SIZE_MISMATCH',
    'NOT_AUTHENTICATED',
    'SUBJECT_NOT_FOUND',
  ];
  return known.find((code) => message.includes(code)) ?? 'REQUEST_FAILED';
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS });
  if (req.method !== 'POST') return json({ error: 'METHOD_NOT_ALLOWED' }, 405);

  if (!SUPABASE_URL || !SERVICE_KEY) {
    return json({ error: 'SERVER_MISCONFIGURED' }, 500);
  }

  const url = new URL(req.url);
  const action = url.searchParams.get('action') ?? '';

  let payload: Record<string, any> = {};
  try {
    payload = (await req.json()) ?? {};
  } catch {
    payload = {};
  }

  try {
    // ---------------------------------------------------------------- sign
    if (action === 'sign') {
      const type = url.searchParams.get('type') === 'owner' ? 'owner' : 'student';
      const documentId = String(payload.document_id ?? '');
      const expiresIn = Math.min(3600, Math.max(60, Number(payload.expires_in ?? 300) || 300));
      if (!UUID.test(documentId)) return json({ error: 'DOCUMENT_REQUIRED' }, 400);

      const data =
        type === 'owner'
          ? await rpc<any>('api_owner_document_access', { p_document_id: documentId })
          : await rpc<any>('api_student_document_access', {
              p_token: String(payload.session_token ?? ''),
              p_document_id: documentId,
            });

      const signedUrl = await createSignedUrl(String(data?.file_path ?? ''), expiresIn);
      if (!signedUrl) return json({ error: 'SIGN_FAILED' }, 500);

      return json({ document: data?.document ?? null, signed_url: signedUrl, expires_in: expiresIn });
    }

    // ------------------------------------------------------------ finalize
    if (action === 'finalize') {
      const documentId = String(payload.document_id ?? '');
      const token = String(payload.session_token ?? '');
      if (!UUID.test(documentId)) return json({ error: 'DOCUMENT_REQUIRED' }, 400);

      // 1. Contrôle du jeton, de la taille réelle du fichier et du propriétaire du lien
      const auth = await rpc<any>('api_confirm_upload', { p_token: token, p_document_id: documentId });
      const from = String(auth?.file_path ?? '');
      if (!from) return json({ error: 'UPLOAD_INCOMPLETE' }, 400);

      // 2. Déplacement réel du fichier dans le bucket privé
      const subjectId = auth?.document?.subject_id;
      const fileType = String(auth?.document?.file_type ?? 'pdf').replace(/[^a-z0-9]/gi, '');
      const to = `subject/${subjectId}/${crypto.randomUUID()}.${fileType}`;
      const moveError = await moveObject(from, to);
      if (moveError) return json({ error: 'MOVE_FAILED', detail: moveError }, 500);

      // 3. Validation définitive de la ligne document
      try {
        const finalized = await rpc<any>('api_finalize_upload', {
          p_token: token,
          p_document_id: documentId,
          p_new_path: to,
        });
        const signedUrl = await createSignedUrl(to, 3600);
        return json({ document: finalized?.document ?? null, signed_url: signedUrl });
      } catch (error) {
        await removeObjects([to]);
        throw error;
      }
    }

    // --------------------------------------------------------------- purge
    if (action === 'purge') {
      const paths = Array.isArray(payload.paths)
        ? (payload.paths as unknown[]).filter((p): p is string => typeof p === 'string' && p.length > 0)
        : [];
      const error = await removeObjects(paths.slice(0, 100));
      if (error) return json({ error: 'PURGE_FAILED', detail: error }, 500);
      return json({ removed: paths.length });
    }

    return json({ error: 'UNKNOWN_ACTION' }, 400);
  } catch (error) {
    if (error instanceof RpcError) return json({ error: error.code, detail: error.message }, 400);
    return json({ error: 'UNEXPECTED', detail: String(error) }, 500);
  }
});
