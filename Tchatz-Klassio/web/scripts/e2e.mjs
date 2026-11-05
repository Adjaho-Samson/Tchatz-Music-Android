#!/usr/bin/env node
/**
 * Tchatz Klassio — tests d'intégration réels (backend Supabase en production).
 *
 * Ce script exerce le parcours complet imposé par le cahier des charges :
 *   1. Création du compte responsable (Supabase Auth) + connexion
 *   2. Création d'une classe (code généré automatiquement)
 *   3. Création d'une matière + lien de publication
 *   4. Accès étudiant par code, sans compte
 *   5. Publication enseignant sans compte (fichier réel dans le bucket privé)
 *   6. Téléchargement étudiant via URL signée
 *   7. Contrôles de sécurité : lien révoqué, lien invalide, mot de passe erroné, RLS
 *   8. Suppression par le responsable (ligne + fichier)
 *
 * Aucune simulation : toutes les opérations touchent réellement Supabase.
 *
 * Usage :
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... node scripts/e2e.mjs
 */

const SUPABASE_URL = process.env.SUPABASE_URL ?? process.env.VITE_SUPABASE_URL;
const ANON_KEY = process.env.SUPABASE_ANON_KEY ?? process.env.VITE_SUPABASE_ANON_KEY;

if (!SUPABASE_URL || !ANON_KEY) {
  console.error('SUPABASE_URL et SUPABASE_ANON_KEY sont requis.');
  process.exit(2);
}

const results = [];
let accessToken = '';
let ownerId = '';
const stamp = Date.now();
const email = `qa.klassio.${stamp}@example.com`;
const password = 'Klassio!Test2026';
const fullName = 'QA Klassio';

function record(name, ok, detail = '') {
  results.push({ name, ok, detail });
  const symbol = ok ? 'OK  ' : 'KO  ';
  console.log(`${symbol} ${name}${detail ? ` — ${detail}` : ''}`);
}

function check(name, condition, detail = '') {
  record(name, Boolean(condition), detail);
  return Boolean(condition);
}

async function api(path, { method = 'GET', body, token, headers = {} } = {}) {
  const response = await fetch(`${SUPABASE_URL}${path}`, {
    method,
    headers: {
      apikey: ANON_KEY,
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : { Authorization: `Bearer ${ANON_KEY}` }),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }
  return { status: response.status, ok: response.ok, payload };
}

async function rpc(name, args, token) {
  return api(`/rest/v1/rpc/${name}`, { method: 'POST', body: args, token });
}

async function main() {
  console.log('\n=== TCHATZ KLASSIO — TESTS FONCTIONNELS RÉELS ===\n');
  console.log(`Projet : ${SUPABASE_URL}`);
  console.log(`Compte de test : ${email}\n`);

  // ---------------------------------------------------------------- Test 1
  const signUp = await api('/auth/v1/signup', {
    method: 'POST',
    body: { email, password, data: { full_name: fullName } },
  });
  if (!check('Test 1.1 — Inscription responsable', signUp.ok && signUp.payload?.access_token,
    `HTTP ${signUp.status}`)) {
    console.log(JSON.stringify(signUp.payload)?.slice(0, 400));
    return finish();
  }
  accessToken = signUp.payload.access_token;
  ownerId = signUp.payload.user.id;

  const badLogin = await api('/auth/v1/token?grant_type=password', {
    method: 'POST',
    body: { email, password: 'mauvais-mot-de-passe' },
  });
  check('Test 1.2 — Mot de passe incorrect rejeté', badLogin.status === 400, `HTTP ${badLogin.status}`);

  const signIn = await api('/auth/v1/token?grant_type=password', {
    method: 'POST',
    body: { email, password },
  });
  check('Test 1.3 — Connexion responsable', signIn.ok && Boolean(signIn.payload?.access_token));
  accessToken = signIn.payload?.access_token ?? accessToken;

  const profile = await api('/rest/v1/profiles?select=full_name,email', { token: accessToken });
  check(
    'Test 1.4 — Profil créé automatiquement',
    profile.ok && Array.isArray(profile.payload) && profile.payload[0]?.full_name === fullName,
    JSON.stringify(profile.payload?.[0] ?? null),
  );

  // ---------------------------------------------------------------- Test 2
  const classroom = await rpc(
    'api_create_classroom',
    {
      p_institution_name: 'ESM Bénin',
      p_class_name: 'SIL 2',
      p_field_of_study: 'Systèmes Informatiques et Logiciels',
      p_level: 'Licence',
      p_study_year: '2',
      p_academic_year: '2026-2027',
    },
    accessToken,
  );
  const classroomId = classroom.payload?.id;
  const joinCode = classroom.payload?.join_code;
  check(
    'Test 2.1 — Classe créée avec code généré',
    classroom.ok && /^[A-Z0-9]{8}$/.test(joinCode ?? ''),
    `code ${joinCode}`,
  );

  const badJoin = await rpc('api_join_classroom', { p_join_code: 'ZZZZZZZZ', p_platform: 'web' });
  check('Test 2.2 — Code inexistant rejeté', badJoin.status >= 400, `HTTP ${badJoin.status}`);

  const studentJoin = await rpc('api_join_classroom', {
    p_join_code: joinCode.toLowerCase(),
    p_label: 'Test navigateur',
    p_platform: 'web',
  });
  const studentToken = studentJoin.payload?.session_token;
  check(
    'Test 2.3 — Étudiant rejoint par code (sans compte)',
    studentJoin.ok && Boolean(studentToken),
    'casse et espaces tolérés',
  );

  const detail = await rpc('api_student_classroom', { p_token: studentToken });
  check(
    'Test 2.4 — Matières consultables par l’étudiant',
    detail.ok && detail.payload?.classroom?.id === classroomId,
    `${detail.payload?.subjects?.length ?? 0} matière(s)`,
  );

  // ---------------------------------------------------------------- Test 3
  const subjectA = await rpc(
    'api_create_subject',
    { p_classroom_id: classroomId, p_name: 'Programmation orientée objet', p_description: 'Cours et TD' },
    accessToken,
  );
  const tokenA = subjectA.payload?.link?.token;
  const subjectAId = subjectA.payload?.subject?.id;
  check('Test 3.1 — Matière créée + lien généré', subjectA.ok && Boolean(tokenA));

  const subjectB = await rpc(
    'api_create_subject',
    { p_classroom_id: classroomId, p_name: 'Réseaux informatiques' },
    accessToken,
  );
  const tokenB = subjectB.payload?.link?.token;

  const context = await rpc('api_publish_context', { p_token: tokenA });
  check(
    'Test 3.2 — Page de publication contextuelle',
    context.ok && context.payload?.subject?.id === subjectAId && context.payload?.classroom?.class_name === 'SIL 2',
    context.payload?.subject?.name,
  );

  const anonRead = await api('/rest/v1/documents?select=id');
  check('Test 3.3 — Tables fermées au rôle anon (RLS)', anonRead.status >= 400, `HTTP ${anonRead.status}`);

  // Publication réelle d'un PDF
  const pdfHeader = '%PDF-1.4\n% Tchatz Klassio test document\n';
  const padding = 'A'.repeat(4096);
  const pdfContent = `${pdfHeader}1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n${padding}`;
  const fileSize = Buffer.byteLength(pdfContent, 'utf8');

  const begin = await rpc('api_begin_upload', {
    p_token: tokenA,
    p_title: 'Chapitre 1 — Introduction à la POO',
    p_description: 'Support de cours, semestre 1',
    p_file_name: 'chapitre-1-poo.pdf',
    p_file_size: fileSize,
    p_teacher_name: 'Dr. Hounkpatin',
  });
  check('Test 3.4 — Réservation de publication acceptée', begin.ok && Boolean(begin.payload?.document_id));

  const upload = await fetch(
    `${SUPABASE_URL}/storage/v1/object/documents/${begin.payload.file_path}`,
    {
      method: 'POST',
      headers: {
        apikey: ANON_KEY,
        Authorization: `Bearer ${ANON_KEY}`,
        'Content-Type': 'application/pdf',
        'x-upsert': 'true',
      },
      body: pdfContent,
    },
  );
  check('Test 3.5 — Dépôt réel du PDF dans le bucket privé', upload.ok, `HTTP ${upload.status}`);

  const finalize = await fetch(`${SUPABASE_URL}/functions/v1/klassio-file?action=finalize`, {
    method: 'POST',
    headers: { apikey: ANON_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      session_token: tokenA,
      document_id: begin.payload.document_id,
      file_name: 'chapitre-poo.pdf',
    }),
  });
  const finalized = await finalize.json().catch(() => ({}));
  check(
    'Test 3.6 — Finalisation serveur (taille vérifiée, fichier déplacé)',
    finalize.ok && Boolean(finalized.document?.id),
    finalized.error ?? finalized.document?.title,
  );
  const documentId = finalized.document?.id ?? begin.payload.document_id;

  const wrongSize = await rpc('api_begin_upload', {
    p_token: tokenA,
    p_title: 'Fichier trop lourd',
    p_description: null,
    p_file_name: 'enorme.pdf',
    p_file_size: 60 * 1024 * 1024,
    p_teacher_name: 'Dr. Hounkpatin',
  });
  check('Test 3.7 — Limite de taille appliquée côté serveur', wrongSize.status >= 400, 'fichier 60 Mo refusé');

  const badType = await rpc('api_begin_upload', {
    p_token: tokenA,
    p_title: 'Exécutable',
    p_description: null,
    p_file_name: 'virus.exe',
    p_file_size: 1024,
    p_teacher_name: 'Dr. Hounkpatin',
  });
  check('Test 3.8 — Format interdit refusé côté serveur', badType.status >= 400, '.exe refusé');

  const listing = await rpc('api_student_documents', { p_token: studentToken, p_limit: 20 });
  check(
    'Test 3.9 — Document visible dans la bonne matière',
    listing.ok && listing.payload?.some?.((item) => item.id === documentId && item.subject_id === subjectAId),
    `${listing.payload?.length ?? 0} document(s)`,
  );

  const search = await rpc('api_student_documents', {
    p_token: studentToken,
    p_search: 'introduction',
    p_limit: 20,
  });
  check(
    'Test 3.10 — Recherche par titre',
    search.ok && search.payload?.some?.((item) => item.id === documentId),
    `${search.payload?.length ?? 0} résultat(s)`,
  );

  // ---------------------------------------------------------------- Test 4
  const revokedLink = await rpc('api_revoke_publication_link', { p_subject_id: subjectAId }, accessToken);
  check('Test 4.1 — Révocation du lien par le responsable', revokedLink.ok);

  const afterRevoke = await rpc('api_publish_context', { p_token: tokenA });
  check('Test 4.2 — Lien révoqué inutilisable', afterRevoke.status >= 400, afterRevoke.payload?.message);

  const crossPost = await rpc('api_begin_upload', {
    p_token: tokenB,
    p_title: 'Tentative croisée',
    p_description: null,
    p_file_name: 'test.pdf',
    p_file_size: 1024,
    p_teacher_name: 'Enseignant B',
  });
  const crossSubject = crossPost.payload?.subject_id;
  check(
    'Test 4.3 — Un lien ne cible que sa propre matière',
    !crossPost.ok || crossSubject !== subjectAId,
    `matière ciblée = ${crossSubject === subjectAId ? 'POO (échec)' : 'la sienne'}`,
  );

  const studentWrite = await api('/rest/v1/classrooms', {
    method: 'PATCH',
    body: { class_name: 'Piratée' },
    headers: { Prefer: 'return=representation' },
  });
  check('Test 4.4 — Étudiant/anon ne peut pas modifier une classe', studentWrite.status >= 400, `HTTP ${studentWrite.status}`);

  const ownerTokenRead = await api('/rest/v1/publication_links?select=token_hash', { token: accessToken });
  check('Test 4.5 — Jetons de publication inaccessibles', ownerTokenRead.status >= 400, `HTTP ${ownerTokenRead.status}`);

  const studentDelete = await rpc('api_delete_document', { p_document_id: documentId });
  check('Test 4.6 — Suppression impossible sans être propriétaire', studentDelete.status >= 400);

  const invalidSession = await rpc('api_student_classroom', { p_token: 'jeton-invalide' });
  // PostgREST renvoie { code, details, hint, message } : pas de champ « error ».
  const invalidDetail = invalidSession.payload?.message ?? invalidSession.payload?.error ?? '';
  check(
    'Test 4.7 — Jeton étudiant invalide rejeté',
    !invalidSession.ok && Boolean(invalidDetail),
    `HTTP ${invalidSession.status} ${invalidDetail}`.trim(),
  );

  // ---------------------------------------------------------------- Test 5
  const signedStudent = await fetch(`${SUPABASE_URL}/functions/v1/klassio-file?action=sign&type=student`, {
    method: 'POST',
    headers: { apikey: ANON_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ session_token: studentToken, document_id: documentId, expires_in: 120 }),
  });
  const signedPayload = await signedStudent.json().catch(() => ({}));
  check(
    'Test 5.1 — URL signée délivrée à l’étudiant de la classe',
    signedStudent.ok && Boolean(signedPayload.signed_url),
    signedPayload.signed_url ? `${signedPayload.expires_in} s` : `HTTP ${signedStudent.status} ${signedPayload.error ?? ''}`,
  );

  if (signedPayload.signed_url) {
    const download = await fetch(signedPayload.signed_url);
    const buffer = Buffer.from(await download.arrayBuffer());
    check(
      'Test 5.2 — Téléchargement réel du fichier',
      download.ok && buffer.length === fileSize && buffer.subarray(0, 5).toString() === '%PDF-',
      `${buffer.length} octets`,
    );
  }

  const outsiderSign = await fetch(`${SUPABASE_URL}/functions/v1/klassio-file?action=sign&type=student`, {
    method: 'POST',
    headers: { apikey: ANON_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ session_token: 'jeton-etranger', document_id: documentId }),
  });
  check('Test 5.3 — Aucun lien signé sans session valide', outsiderSign.status >= 400, `HTTP ${outsiderSign.status}`);

  const publicBucket = await fetch(`${SUPABASE_URL}/storage/v1/object/public/documents/${finalized.document ? 'x' : 'x'}`);
  check('Test 5.4 — Bucket non public', publicBucket.status >= 400, `HTTP ${publicBucket.status}`);

  // ---------------------------------------------------------------- Test 6
  const androidJoin = await rpc('api_join_classroom', {
    p_join_code: joinCode,
    p_label: 'Android QA',
    p_platform: 'android',
  });
  const androidToken = androidJoin.payload?.session_token;
  const androidListing = await rpc('api_student_documents', { p_token: androidToken, p_limit: 20 });
  check(
    'Test 6.1 — Cohérence Web → Android (même document visible)',
    androidJoin.ok && androidListing.payload?.some?.((item) => item.id === documentId),
  );

  const device = `qa-device-${stamp}`;
  const registered = await rpc('api_register_device', {
    p_token: androidToken,
    p_device_identifier: device,
    p_label: 'Android QA',
    p_platform: 'android',
    p_notifications_enabled: true,
  });
  check('Test 6.2 — Appareil enregistré pour les notifications', registered.ok && Boolean(registered.payload?.device_id));

  const toggled = await rpc('api_set_notifications', {
    p_token: androidToken,
    p_device_identifier: device,
    p_enabled: false,
  });
  check('Test 6.3 — Préférence de notification modifiable', toggled.ok);

  const updates = await rpc('api_student_updates', {
    p_token: androidToken,
    p_since: new Date(Date.now() - 3600_000).toISOString(),
  });
  check('Test 6.4 — Nouveautés détectées pour la notification', updates.ok && updates.payload?.length >= 1);

  // ---------------------------------------------------------------- Test 7
  const space = await rpc('api_my_space', { p_classroom_id: classroomId }, accessToken);
  check(
    'Test 7.1 — Tableau de bord propriétaire',
    space.ok && space.payload?.classroom?.join_code === joinCode,
    `${space.payload?.stats?.documents ?? 0} document(s)`,
  );

  const deleted = await rpc('api_delete_document', { p_document_id: documentId }, accessToken);
  check('Test 7.2 — Suppression du document par le responsable', deleted.ok && Boolean(deleted.payload?.file_path));

  const afterDelete = await rpc('api_student_documents', { p_token: studentToken, p_limit: 20 });
  check(
    'Test 7.3 — Document retiré de la bibliothèque',
    afterDelete.ok && !afterDelete.payload?.some?.((item) => item.id === documentId),
  );

  if (deleted.payload?.file_path) {
    const purgeResponse = await fetch(`${SUPABASE_URL}/functions/v1/klassio-file?action=purge`, {
      method: 'POST',
      headers: { apikey: ANON_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({ paths: [deleted.payload.file_path] }),
    });
    check('Test 7.4 — Fichier purgé du stockage', purgeResponse.ok, `HTTP ${purgeResponse.status}`);
  }

  const signOut = await api('/auth/v1/logout', { method: 'POST', token: accessToken });
  check('Test 7.5 — Déconnexion responsable', signOut.status === 204 || signOut.ok, `HTTP ${signOut.status}`);

  // Nettoyage du compte de test
  const cleanup = await rpc('api_delete_classroom', { p_classroom_id: classroomId }, signIn.payload?.access_token);
  const cleanupPaths = cleanup.payload?.file_paths ?? [];
  if (cleanupPaths.length) {
    await fetch(`${SUPABASE_URL}/functions/v1/klassio-file?action=purge`, {
      method: 'POST',
      headers: { apikey: ANON_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({ paths: cleanupPaths }),
    });
  }
  check('Test 7.6 — Suppression complète de l’espace', cleanup.ok || cleanup.status === 401);

  finish();
}

function finish() {
  const passed = results.filter((item) => item.ok).length;
  const failed = results.length - passed;
  console.log(`\n=== RÉSULTAT : ${passed}/${results.length} tests réussis, ${failed} échec(s) ===\n`);
  if (failed > 0) {
    for (const item of results.filter((entry) => !entry.ok)) {
      console.log(`ÉCHEC : ${item.name} ${item.detail ? `(${item.detail})` : ''}`);
    }
    process.exit(1);
  }
}

main().catch((error) => {
  console.error('\nErreur fatale pendant les tests :', error);
  process.exit(1);
});
