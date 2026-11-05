import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, AppHeader, Field, Footer, Spinner } from '../components/ui';
import { supabase } from '../lib/supabase';
import { humanize } from '../lib/api';

export default function OwnerAuthPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<'signin' | 'signup' | 'reset'>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setInfo(null);
    try {
      if (mode === 'signin') {
        const { error: signInError } = await supabase.auth.signInWithPassword({
          email: email.trim(),
          password,
        });
        if (signInError) throw signInError;
        navigate('/responsable/classes');
      } else if (mode === 'signup') {
        const { data, error: signUpError } = await supabase.auth.signUp({
          email: email.trim(),
          password,
          options: { data: { full_name: fullName.trim() } },
        });
        if (signUpError) throw signUpError;
        if (!data.session) {
          setInfo(
            'Compte créé. Une confirmation par email est requise avant la première connexion.',
          );
          setMode('signin');
        } else {
          navigate('/responsable/classes');
        }
      } else {
        const { error: resetError } = await supabase.auth.resetPasswordForEmail(email.trim(), {
          redirectTo: `${window.location.origin}/responsable`,
        });
        if (resetError) throw resetError;
        setInfo('Un email de réinitialisation a été envoyé à cette adresse.');
        setMode('signin');
      }
    } catch (exception) {
      const message = (exception as Error).message ?? '';
      setError(humanize(message).message);
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
          <h1>Espace responsable</h1>
          <p className="muted small" style={{ marginTop: 6 }}>
            Créez et gérez vos espaces de classe, matières et liens de publication.
          </p>

          <form className="card stack" style={{ marginTop: 18 }} onSubmit={submit}>
            <div className="tag-filter">
              <button
                type="button"
                className={`tag ${mode === 'signin' ? 'active' : ''}`}
                onClick={() => setMode('signin')}
              >
                Connexion
              </button>
              <button
                type="button"
                className={`tag ${mode === 'signup' ? 'active' : ''}`}
                onClick={() => setMode('signup')}
              >
                Créer un compte
              </button>
            </div>

            {mode === 'signup' ? (
              <Field label="Nom complet">
                <input
                  className="input"
                  value={fullName}
                  onChange={(event) => setFullName(event.target.value)}
                  placeholder="Kossi Adjovi"
                  required
                  minLength={2}
                />
              </Field>
            ) : null}

            <Field label="Adresse email">
              <input
                className="input"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="responsable@universite.bj"
                autoComplete="email"
                required
              />
            </Field>

            {mode !== 'reset' ? (
              <Field label="Mot de passe" hint="6 caractères minimum.">
                <input
                  className="input"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                  minLength={6}
                  required
                />
              </Field>
            ) : null}

            {error ? <Alert tone="error">{error}</Alert> : null}
            {info ? <Alert tone="success">{info}</Alert> : null}

            <button
              className="btn btn-block"
              type="submit"
              disabled={loading || !email.includes('@') || (mode !== 'reset' && password.length < 6)}
            >
              {loading ? <Spinner /> : null}
              {mode === 'signin' ? 'Se connecter' : mode === 'signup' ? 'Créer mon compte' : 'Envoyer le lien'}
            </button>

            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => setMode(mode === 'reset' ? 'signin' : 'reset')}
            >
              {mode === 'reset' ? 'Revenir à la connexion' : 'Mot de passe oublié ?'}
            </button>

            <p className="hint">
              Seuls les responsables possèdent un compte. Les étudiants utilisent un code de classe et les
              enseignants publient via un lien privé, sans compte.
            </p>
          </form>
        </div>
      </main>
      <Footer />
    </>
  );
}
