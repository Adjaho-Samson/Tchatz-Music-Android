import { Link } from 'react-router-dom';
import { AppHeader, EmptyState, Footer } from '../components/ui';

export default function NotFoundPage() {
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
          <div className="card">
            <EmptyState
              icon="?"
              title="Page introuvable"
              body="Le lien utilisé n’existe pas ou n’est plus valide."
            />
            <div className="center">
              <Link className="btn" to="/">
                Retour à l’accueil
              </Link>
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </>
  );
}
