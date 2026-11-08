import { useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { MobileNav } from './MobileNav';
import { AppShellContext } from './AppShellContext';
import { GoalForm } from '../goals/GoalForm';
import { CommandPalette } from '../search/CommandPalette';
import { Toasts } from '../ui/Toasts';
import { useMomentum } from '../../state/MomentumContext';
import { checkDeadlineNotifications } from '../../lib/notifications';
import { useInstallPrompt } from '../../lib/useInstallPrompt';

export function AppShell() {
  const [goalFormOpen, setGoalFormOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { workspace } = useMomentum();
  const { canInstall, promptInstall, dismiss: dismissInstall } = useInstallPrompt();

  // Deadline notifications
  useEffect(() => {
    const allTasks = workspace.goals.flatMap((g) => g.tasks ?? []);
    checkDeadlineNotifications(workspace.goals, allTasks);
    const interval = window.setInterval(() => {
      checkDeadlineNotifications(workspace.goals, allTasks);
    }, 60 * 60 * 1000);
    return () => window.clearInterval(interval);
  }, [workspace.goals]);

  // PWA install prompt (auto-dismiss after 30s)
  useEffect(() => {
    if (!canInstall) return;
    const timer = window.setTimeout(() => dismissInstall(), 30000);
    return () => window.clearTimeout(timer);
  }, [canInstall, dismissInstall]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
  }, [location.pathname]);

  useEffect(() => {
    const handleShortcut = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const typing =
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.tagName === 'SELECT' ||
        target?.isContentEditable;
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        if (document.querySelector('[role="dialog"]')) return;
        setGoalFormOpen(false);
        setSearchOpen(true);
        return;
      }
      if (
        event.key.toLowerCase() === 'n' &&
        !typing &&
        !event.metaKey &&
        !event.ctrlKey &&
        !event.altKey &&
        !goalFormOpen &&
        !searchOpen &&
        !document.querySelector('[role="dialog"]')
      ) {
        event.preventDefault();
        setGoalFormOpen(true);
      }
    };
    document.addEventListener('keydown', handleShortcut);
    return () => document.removeEventListener('keydown', handleShortcut);
  }, [goalFormOpen, searchOpen]);

  return (
    <AppShellContext.Provider
      value={{
        openNewGoal: () => setGoalFormOpen(true),
        openSearch: () => setSearchOpen(true),
      }}
    >
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <div className="app-layout">
        <Sidebar />
        <div className="app-main">
          <Topbar />
          <main id="main-content" className="app-content">
            <Outlet />
          </main>
          <MobileNav />
        </div>
      </div>
      <GoalForm
        open={goalFormOpen}
        onClose={() => setGoalFormOpen(false)}
        onCreated={(goal) => navigate(`/goals/${goal.id}`)}
      />
      <CommandPalette
        open={searchOpen}
        onClose={() => setSearchOpen(false)}
        onNewGoal={() => setGoalFormOpen(true)}
      />
      <Toasts />
      {canInstall ? (
        <div className="install-banner" role="status">
          <span>Install MOMENTUM for offline access and faster launch.</span>
          <button type="button" className="btn btn--primary btn--sm" onClick={() => void promptInstall()}>
            Install
          </button>
          <button type="button" className="btn btn--ghost btn--sm" onClick={dismissInstall} aria-label="Dismiss install prompt">
            ×
          </button>
        </div>
      ) : null}
    </AppShellContext.Provider>
  );
}
