import { createClient } from '@supabase/supabase-js';

/**
 * Client Supabase Web.
 *
 * - Seule la clé publique « publishable/anon » est utilisée.
 * - La clé service_role ne doit JAMAIS apparaître côté navigateur.
 * - Toutes les opérations métier passent par des RPC protégées (RLS + vérifications serveur).
 */
const url = import.meta.env.VITE_SUPABASE_URL ?? 'https://hohxfwemcvpvtqnwwvds.supabase.co';
const anonKey =
  import.meta.env.VITE_SUPABASE_ANON_KEY ?? 'sb_publishable_yjOOCQ0FYRA993w5vu2hKg_gPEJlN9H';

export const supabase = createClient(url, anonKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
    storageKey: 'tchatz-klassio-auth',
  },
});

export const SUPABASE_URL = url;
export const PUBLIC_APP_URL =
  import.meta.env.VITE_PUBLIC_APP_URL ??
  (typeof window !== 'undefined' ? window.location.origin : 'https://klassio.vercel.app');

export const FUNCTIONS_URL = `${url}/functions/v1`;
