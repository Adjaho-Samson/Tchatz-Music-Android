import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, AppHeader, EmptyState, Field, Footer, Modal, Spinner } from '../components/ui';
import { ownerApi } from '../lib/api';
import { supabase } from '../lib/supabase';
import type { Classroom } from '../lib/types';
import { formatDate } from '../lib/format';

export default function OwnerDashboardPage() {
  const navigate = useNavigate();
  const [classrooms, setClassrooms] = useState<Classroom[]>([]);
  const [email, setEmail] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [info, setInfo] = useState<string | null>(null);

  const [form, setForm] = useState({
    institution: '',
    className: '',
    fieldOfStudy: '',
    level: 'Licence',
    studyYear: '2',
    academicYear: `${new Date().getFullYear()}-${new Date().getFullYear() + 1}`,
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await ownerApi.classrooms();
      setClassrooms(list);
      setError(null);
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const { data: subscription } = supabase.auth.onAuthStateChange((_event, session) => {
      if (!session) navigate('/responsable');
      setEmail(session?.user.email ?? null);
    });

    void supabase.auth.getSession().then(({ data }) => {
      if (!data.session) {
        navigate('/responsable');
        return;
      }
      setEmail(data.session.user.email ?? null);
      void load();
    });

    return () => subscription.subscription.unsubscribe();
  }, [navigate, load]);

  async function createClassroom(event: React.FormEvent) {
    event.preventDefault();
    setCreating(true);
    setError(null);
    try {
      const created = await ownerApi.createClassroom(form);
      setInfo(`Classe créée. Code étudiant : ${created.join_code}`);
      setShowCreate(false);
      setForm({
        institution: '',
        className: '',
        fieldOfStudy: '',
        level: 'Licence',
        studyYear: '2',
        academicYear: form.academicYear,
      });
      await load();
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setCreating(false);
    }
  }

  async function signOut() {
    await supabase.auth.signOut();
    navigate('/');
  }

  return (
    <>
      <AppHeader
        right={
          <>
            <span className="nav-link" style={{ cursor: 'default' }}>
              {email ?? ''}
            </span>
            <button className="btn btn-outline btn-sm" onClick={() => void signOut()}>
              Se déconnecter
            </button>
          </>
        }
      >
        <Link className="nav-link active" to="/responsable/classes">
          Mes classes
        </Link>
      </AppHeader>

      <main className="page">
        <div className="container">
          <div className="row-between">
            <div>
              <h1>Tableau de bord</h1>
              <p className="muted small">Gérez vos espaces de classe et leurs publications.</p>
            </div>
            <button className="btn" onClick={() => setShowCreate(true)}>
              Nouvelle classe
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

          <section style={{ marginTop: 20 }}>
            {loading ? <div className="skeleton" /> : null}
            {!loading && classrooms.length === 0 ? (
              <div className="card">
                <EmptyState
                  icon="+"
                  title="Aucune classe"
                  body="Créez votre premier espace : un code étudiant unique sera généré automatiquement."
                />
              </div>
            ) : null}
            <div className="grid">
              {classrooms.map((classroom) => (
                <Link
                  key={classroom.id}
                  to={`/responsable/classes/${classroom.id}`}
                  className="card card-link"
                >
                  <div className="row-between">
                    <div className="card-title">{classroom.institution_name}</div>
                    <span className="pill">{classroom.join_code}</span>
                  </div>
                  <p className="small muted" style={{ marginTop: 6 }}>
                    {classroom.class_name} · {classroom.level} {classroom.study_year} ·{' '}
                    {classroom.field_of_study}
                  </p>
                  <p className="tiny muted" style={{ marginTop: 8 }}>
                    Année {classroom.academic_year} · {classroom.subject_count ?? 0} matières ·{' '}
                    {classroom.document_count ?? 0} documents
                    {classroom.created_at ? ` · créée le ${formatDate(classroom.created_at)}` : ''}
                  </p>
                  {classroom.is_archived ? (
                    <span className="pill pill-warning" style={{ marginTop: 10 }}>
                      Classe fermée
                    </span>
                  ) : null}
                </Link>
              ))}
            </div>
          </section>
        </div>
      </main>
      <Footer />

      {showCreate ? (
        <Modal title="Créer un espace de classe" onClose={() => setShowCreate(false)}>
          <form className="stack" onSubmit={createClassroom}>
            <Field label="Établissement">
              <input
                className="input"
                value={form.institution}
                onChange={(event) => setForm({ ...form, institution: event.target.value })}
                placeholder="ESM Bénin"
                required
                minLength={2}
              />
            </Field>
            <Field label="Nom de la classe">
              <input
                className="input"
                value={form.className}
                onChange={(event) => setForm({ ...form, className: event.target.value })}
                placeholder="SIL 2"
                required
                minLength={2}
              />
            </Field>
            <Field label="Filière">
              <input
                className="input"
                value={form.fieldOfStudy}
                onChange={(event) => setForm({ ...form, fieldOfStudy: event.target.value })}
                placeholder="Systèmes Informatiques et Logiciels"
                required
                minLength={2}
              />
            </Field>
            <div className="row" style={{ alignItems: 'flex-start' }}>
              <div style={{ flex: 1 }}>
                <Field label="Niveau">
                  <select
                    className="select"
                    value={form.level}
                    onChange={(event) => setForm({ ...form, level: event.target.value })}
                  >
                    {['Licence', 'Master', 'BTS', 'DUT', 'Doctorat', 'Autre'].map((level) => (
                      <option key={level} value={level}>
                        {level}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>
              <div style={{ flex: 1 }}>
                <Field label="Année du niveau">
                  <input
                    className="input"
                    value={form.studyYear}
                    onChange={(event) => setForm({ ...form, studyYear: event.target.value })}
                    placeholder="2"
                    required
                  />
                </Field>
              </div>
            </div>
            <Field label="Année académique" hint="Format attendu : 2026-2027">
              <input
                className="input"
                value={form.academicYear}
                onChange={(event) => setForm({ ...form, academicYear: event.target.value })}
                pattern="\d{4}-\d{4}"
                required
              />
            </Field>
            <button
              className="btn btn-block"
              type="submit"
              disabled={
                creating ||
                form.institution.trim().length < 2 ||
                form.className.trim().length < 2 ||
                form.fieldOfStudy.trim().length < 2 ||
                !/^\d{4}-\d{4}$/.test(form.academicYear)
              }
            >
              {creating ? <Spinner /> : null}
              {creating ? 'Création…' : 'Créer la classe'}
            </button>
          </form>
        </Modal>
      ) : null}
    </>
  );
}
