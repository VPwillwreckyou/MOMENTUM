import { Link, useLocation } from 'react-router-dom';
import { Search, Sparkles, UserRound, WifiOff } from 'lucide-react';
import { useAppShell } from './AppShellContext';
import { useAuth } from '../../state/AuthContext';
import { useOnlineStatus } from '../../lib/useOnlineStatus';
import { LogoMark } from '../ui/Logo';
import { MODIFIER_LABEL } from '../../lib/platform';

const titles: { pattern: RegExp; label: string }[] = [
  { pattern: /^\/$/, label: 'Overview' },
  { pattern: /^\/goals$/, label: 'Goals' },
  { pattern: /^\/goals\//, label: 'Goal detail' },
  { pattern: /^\/planner/, label: 'Goal architect' },
  { pattern: /^\/categories/, label: 'Categories' },
  { pattern: /^\/archive/, label: 'Archive' },
  { pattern: /^\/trash/, label: 'Recently deleted' },
  { pattern: /^\/settings/, label: 'Settings' },
  { pattern: /^\/auth/, label: 'Account' },
];

export function Topbar() {
  const { openSearch } = useAppShell();
  const { user } = useAuth();
  const online = useOnlineStatus();
  const location = useLocation();
  const title = titles.find((item) => item.pattern.test(location.pathname))?.label ?? 'MOMENTUM';

  return (
    <header className="topbar">
      <div className="topbar__mobile-brand">
        <Link to="/" aria-label="MOMENTUM home" className="topbar__mobile-logo">
          <LogoMark size={28} />
          <span>MOMENTUM</span>
        </Link>
      </div>
      <div className="topbar__context">
        <span className="topbar__breadcrumb">Workspace</span>
        <span className="topbar__slash">/</span>
        <span className="topbar__current">{title}</span>
      </div>

      <div className="topbar__actions">
        {!online ? (
          <span className="offline-pill">
            <WifiOff size={13} />
            Offline
          </span>
        ) : null}
        <button type="button" className="search-trigger" onClick={openSearch} aria-label="Search workspace">
          <Search size={16} />
          <span>Search anything</span>
          <kbd>
            <span className="search-trigger__mod">{MODIFIER_LABEL}</span>K
          </kbd>
        </button>
        <Link to="/planner" className="btn btn--ghost btn--md topbar__planner">
          <Sparkles size={16} />
          <span>AI architect</span>
        </Link>
        <Link to={user ? '/settings#account' : '/auth'} className="topbar__avatar" aria-label={user ? 'Account settings' : 'Sign in'}>
          {user ? user.name.charAt(0).toUpperCase() : <UserRound size={16} />}
          <span className={`topbar__status-dot${user ? ' topbar__status-dot--online' : ''}`} />
        </Link>
      </div>
    </header>
  );
}
