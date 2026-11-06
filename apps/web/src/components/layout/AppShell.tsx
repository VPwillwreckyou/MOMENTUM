import { useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { MobileNav } from './MobileNav';
import { AppShellContext } from './AppShellContext';
import { GoalForm } from '../goals/GoalForm';
import { CommandPalette } from '../search/CommandPalette';
import { Toasts } from '../ui/Toasts';

export function AppShell() {
  const [goalFormOpen, setGoalFormOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

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
    </AppShellContext.Provider>
  );
}
