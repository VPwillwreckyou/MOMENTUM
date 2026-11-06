import { createContext, useContext } from 'react';

interface AppShellActions {
  openNewGoal: () => void;
  openSearch: () => void;
}

export const AppShellContext = createContext<AppShellActions | null>(null);

export function useAppShell(): AppShellActions {
  const context = useContext(AppShellContext);
  if (!context) throw new Error('useAppShell must be used inside AppShell');
  return context;
}
