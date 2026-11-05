import { Link } from 'react-router-dom';
import { AppHeader, Footer } from '../components/ui';

export default function LegalPage() {
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
        <div className="container container-narrow stack">
          <div>
            <h1>Politique de confidentialité</h1>
            <p className="muted small" style={{ marginTop: 6 }}>
              Dernière mise à jour : novembre 2026 — Tchatz Klassio, une solution de Tchatz.
            </p>
          </div>

          <section className="card stack-sm">
            <h2>Principe de minimisation</h2>
            <p className="small">
              Tchatz Klassio collecte uniquement les données nécessaires au partage des documents
              pédagogiques. Aucun profil publicitaire, aucun traçage comportemental, aucun identifiant
              matériel n’est utilisé.
            </p>

            <h3>Étudiants</h3>
            <p className="small">
              Aucun compte, aucun email, aucun numéro de téléphone n’est demandé. L’accès repose sur un code
              de classe et un jeton de session opaque stocké sur l’appareil ou dans le navigateur. Le code de
              classe permet de retrouver l’espace après une réinstallation.
            </p>

            <h3>Enseignants</h3>
            <p className="small">
              Les enseignants n’ont pas de compte. Lors d’une publication, seul le nom d’affichage saisi est
              conservé, afin d’identifier l’auteur du document.
            </p>

            <h3>Responsables</h3>
            <p className="small">
              Un compte est nécessaire (email, mot de passe, nom complet) pour gérer les espaces de classe.
              Les mots de passe sont gérés par Supabase Auth (hachage côté serveur) et ne sont jamais stockés
              en clair par Tchatz Klassio.
            </p>

            <h3>Fichiers et données techniques</h3>
            <p className="small">
              Les documents sont stockés dans un espace privé. L’accès se fait uniquement via des liens
              temporaires signés, délivrés après vérification de l’appartenance à la classe. Les jetons de
              publication sont stockés sous forme hachée (SHA-256) et ne peuvent pas être reconstitués.
            </p>

            <h3>Notifications</h3>
            <p className="small">
              Elles sont strictement facultatives et contrôlées par l’utilisateur dans les paramètres de
              l’application. Sur Android, elles peuvent être désactivées à tout moment dans les réglages du
              système.
            </p>

            <h3>Vos droits</h3>
            <p className="small">
              Le responsable peut à tout moment supprimer un document, une matière ou l’intégralité d’un
              espace de classe. La suppression entraîne le retrait effectif des fichiers du stockage. Pour
              toute demande relative à un compte responsable, contactez l’administrateur de la plateforme.
            </p>
          </section>

          <section className="card stack-sm" id="conditions">
            <h2>Conditions d’utilisation</h2>
            <p className="small">
              Tchatz Klassio est un service de partage de ressources pédagogiques. En l’utilisant, vous vous
              engagez à ne publier que des contenus pédagogiques licites, dont vous détenez les droits ou
              l’autorisation de diffusion.
            </p>
            <p className="small">
              Sont interdits : contenus illicites, haineux, harcelants ou portant atteinte aux droits de
              tiers, ainsi que tout usage visant à contourner les mesures de sécurité de la plateforme.
            </p>
            <p className="small">
              Les liens de publication sont strictement privés. Le responsable de classe peut les révoquer à
              tout moment : un lien révoqué ne permet plus aucune publication. Le responsable peut également
              retirer tout document inapproprié.
            </p>
            <p className="small">
              Le service est fourni « en l’état », avec une disponibilité dépendante du réseau et de
              l’hébergement. Tchatz Klassio ne saurait être tenu responsable de l’usage fait des contenus
              publiés par les utilisateurs.
            </p>
          </section>

          <p className="small muted">
            Éditeur : Tchatz Klassio — Samson Adjaho (Tchatz). Signature produit : « Une solution de Tchatz ».
          </p>
        </div>
      </main>
      <Footer />
    </>
  );
}
