import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AppHeader, Alert, Field, Footer, Spinner } from '../components/ui';
import { studentApi } from '../lib/api';
import { device, studentSessions } from '../lib/session';
import { normalizeCode } from '../lib/format';

export default function JoinPage() {
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (code.length < 6 || loading) return;
    setLoading(true);
    setError(null);
    try {
      const payload = await studentApi.join(code, 'Navigateur web');
      const session = studentSessions.fromJoin(payload);
      studentSessions.save(session);
      // Enregistrement de l'appareil pour les notifications (sans donnée personnelle)
      try {
        await studentApi.registerDevice(session.token, device.id(), true, 'Navigateur web');
      } catch {
        /* non bloquant : la classe reste accessible */
      }
      navigate(`/c/${session.classroomId}`);
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <AppHeader
        right={
          <Link className="nav-link" to="/">
            Accueil
          </Link>
        }
      />
      <main className="page">
        <div className="container container-narrow">
          <h1>Rejoindre une classe</h1>
          <p className="muted small" style={{ marginTop: 6 }}>
            Saisissez le code à 8 caractères fourni par le responsable de classe.
          </p>

          <form className="card stack" style={{ marginTop: 18 }} onSubmit={submit}>
            <Field label="Code de classe" hint="Les espaces et tirets sont ignorés.">
              <input
                className="input"
                value={code}
                onChange={(event) => setCode(normalizeCode(event.target.value))}
                placeholder="NQKHWWWL"
                autoCapitalize="characters"
                autoComplete="off"
                spellCheck={false}
                inputMode="text"
                style={{ letterSpacing: '0.18em', fontWeight: 700, fontSize: '1.1rem' }}
                required
              />
            </Field>
            {error ? <Alert tone="error">{error}</Alert> : null}
            <button className="btn btn-block" type="submit" disabled={code.length < 6 || loading}>
              {loading ? <Spinner /> : null}
              {loading ? 'Connexion…' : 'Accéder à la classe'}
            </button>
            <p className="hint">
              Tchatz Klassio ne demande ni email, ni numéro de téléphone, ni mot de passe aux étudiants.
              La classe reste enregistrée dans ce navigateur.
            </p>
          </form>
        </div>
      </main>
      <Footer />
    </>
  );
}
