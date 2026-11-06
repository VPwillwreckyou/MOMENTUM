import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { MomentumProvider, useMomentum } from './state/MomentumContext';
import { AuthProvider } from './state/AuthContext';
import { AppShell } from './components/layout/AppShell';
import { DashboardPage } from './pages/DashboardPage';
import { GoalsPage } from './pages/GoalsPage';
import { GoalDetailPage } from './pages/GoalDetailPage';
import { PlannerPage } from './pages/PlannerPage';
import { CategoriesPage } from './pages/CategoriesPage';
import { ArchivePage } from './pages/ArchivePage';
import { TrashPage } from './pages/TrashPage';
import { SettingsPage } from './pages/SettingsPage';
import { AuthPage } from './pages/AuthPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { Onboarding } from './components/onboarding/Onboarding';
import { AppLoading } from './components/ui/Loading';
import { ErrorBoundary } from './components/ErrorBoundary';

function Application() {
  const { loaded, settings, storageError } = useMomentum();

  if (!loaded) return <AppLoading />;

  if (!settings.onboardingComplete) {
    return (
      <>
        {storageError ? <StorageBanner message={storageError} /> : null}
        <Onboarding />
      </>
    );
  }

  return (
    <>
      {storageError ? <StorageBanner message={storageError} /> : null}
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="goals" element={<GoalsPage />} />
          <Route path="goals/:goalId" element={<GoalDetailPage />} />
          <Route path="planner" element={<PlannerPage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="archive" element={<ArchivePage />} />
          <Route path="trash" element={<TrashPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="auth" element={<AuthPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </>
  );
}

function StorageBanner({ message }: { message: string }) {
  return (
    <div className="storage-banner" role="alert">
      <AlertTriangle size={15} />
      <span>
        <strong>Local storage problem.</strong> {message} Changes may not survive a restart. Export a backup as soon as possible.
      </span>
    </div>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <MomentumProvider>
        <AuthProvider>
          <BrowserRouter>
            <Application />
          </BrowserRouter>
        </AuthProvider>
      </MomentumProvider>
    </ErrorBoundary>
  );
}
