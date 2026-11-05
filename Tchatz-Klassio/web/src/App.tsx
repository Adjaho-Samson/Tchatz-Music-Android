import { Route, Routes } from 'react-router-dom';
import LandingPage from './routes/LandingPage';
import JoinPage from './routes/JoinPage';
import ClassroomPage from './routes/ClassroomPage';
import SubjectPage from './routes/SubjectPage';
import DocumentPage from './routes/DocumentPage';
import PublishPage from './routes/PublishPage';
import OwnerAuthPage from './routes/OwnerAuthPage';
import OwnerDashboardPage from './routes/OwnerDashboardPage';
import OwnerClassroomPage from './routes/OwnerClassroomPage';
import LegalPage from './routes/LegalPage';
import NotFoundPage from './routes/NotFoundPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/rejoindre" element={<JoinPage />} />
      <Route path="/c/:classroomId" element={<ClassroomPage />} />
      <Route path="/c/:classroomId/matiere/:subjectId" element={<SubjectPage />} />
      <Route path="/c/:classroomId/document/:documentId" element={<DocumentPage />} />
      {/* Lien de publication enseignant : jeton opaque, une seule matière */}
      <Route path="/p/:token" element={<PublishPage />} />
      <Route path="/responsable" element={<OwnerAuthPage />} />
      <Route path="/responsable/classes" element={<OwnerDashboardPage />} />
      <Route path="/responsable/classes/:classroomId" element={<OwnerClassroomPage />} />
      <Route path="/legal" element={<LegalPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
