import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AppHeader, Footer } from '../components/ui';
import { studentSessions } from '../lib/session';
import type { StudentSession } from '../lib/types';

export default function LandingPage() {
  const [sessions, setSessions] = useState<StudentSession[]>([]);

  useEffect(() => {
    setSessions(studentSessions.all());
  }, []);

  return (
    <>
      <AppHeader
        right={
          <>
            <Link className="nav-link" to="/responsable">
              Espace responsable
            </Link>
          </>
        }
      />

      <main className="page">
        <div className="container">
          <section className="hero">
            <span className="badge">Une solution de Tchatz</span>
            <h1 style={{ marginTop: 14 }}>
              Tous les documents de ta classe,
              <br />
              organisés au même endroit.
            </h1>
            <p>
              Fini les groupes WhatsApp encombrés. Tchatz Klassio centralise les cours, TD et exercices
              de votre classe, matière par matière. Aucun compte n’est demandé aux étudiants.
            </p>
            <div className="row" style={{ marginTop: 20 }}>
              <Link to="/rejoindre" className="btn btn-white">
                Rejoindre une classe
              </Link>
              <Link
                to="/responsable"
                className="btn"
                style={{ background: 'rgba(255,255,255,0.14)', color: '#fff' }}
              >
                Je suis responsable
              </Link>
            </div>
          </section>

          {sessions.length > 0 && (
            <section style={{ marginTop: 26 }}>
              <div className="section-title">
                <h2>Mes classes sur ce navigateur</h2>
                <Link to="/rejoindre" className="btn btn-outline btn-sm">
                  Ajouter une classe
                </Link>
              </div>
              <div className="grid">
                {sessions.map((session) => (
                  <Link
                    key={session.classroomId}
                    to={`/c/${session.classroomId}`}
                    className="card card-link"
                  >
                    <div className="row-between">
                      <div>
                        <div className="card-title">{session.institutionName}</div>
                        <p className="small muted">
                          {[session.level, session.studyYear, session.fieldOfStudy]
                            .filter(Boolean)
                            .join(' · ')}
                        </p>
                      </div>
                      <span className="pill">{session.classroomName}</span>
                    </div>
                    <p className="small muted" style={{ marginTop: 10 }}>
                      Année académique {session.academicYear} · Code {session.joinCode}
                    </p>
                  </Link>
                ))}
              </div>
            </section>
          )}

          <section style={{ marginTop: 30 }}>
            <div className="section-title">
              <h2>Comment ça marche</h2>
            </div>
            <div className="grid">
              <article className="card">
                <span className="pill">Responsable</span>
                <h3 style={{ marginTop: 10 }}>Il crée l’espace de classe</h3>
                <p className="small muted" style={{ marginTop: 6 }}>
                  Établissement, filière, niveau et année académique. Un code de classe unique est généré
                  automatiquement, ainsi qu’un lien privé par matière.
                </p>
              </article>
              <article className="card">
                <span className="pill">Étudiant</span>
                <h3 style={{ marginTop: 10 }}>Il rejoint avec un code</h3>
                <p className="small muted" style={{ marginTop: 6 }}>
                  Aucune inscription, aucun mot de passe. Le code suffit pour retrouver les matières et
                  télécharger les documents.
                </p>
              </article>
              <article className="card">
                <span className="pill">Enseignant</span>
                <h3 style={{ marginTop: 10 }}>Il publie avec un lien</h3>
                <p className="small muted" style={{ marginTop: 6 }}>
                  Sans compte ni installation : il ouvre son lien, indique son nom, choisit le fichier et
                  publie dans la bonne matière.
                </p>
              </article>
            </div>
          </section>

          <section style={{ marginTop: 30 }}>
            <div className="card">
              <h3>Pensé pour les connexions instables</h3>
              <div className="grid" style={{ marginTop: 12 }}>
                <div className="stat">
                  <strong>Documents organisés</strong>
                  <span>Une bibliothèque par matière, recherche instantanée.</span>
                </div>
                <div className="stat">
                  <strong>Accès sécurisé</strong>
                  <span>Fichiers privés, liens temporaires, jetons hachés.</span>
                </div>
                <div className="stat">
                  <strong>Android et Web</strong>
                  <span>Le même backend pour toutes les plateformes.</span>
                </div>
              </div>
            </div>
          </section>
        </div>
      </main>

      <Footer />
    </>
  );
}
