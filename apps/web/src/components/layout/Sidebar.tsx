import { NavLink, Link } from 'react-router-dom';
import {
  Archive,
  Boxes,
  Command,
  Goal,
  LayoutDashboard,
  Plus,
  Settings,
  Trash2,
  UserRound,
} from 'lucide-react';
import { Logo } from '../ui/Logo';
import { useAppShell } from './AppShellContext';
import { useMomentum } from '../../state/MomentumContext';
import { useAuth } from '../../state/AuthContext';

const primaryNav = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/goals', label: 'Goals', icon: Goal },
  { to: '/categories', label: 'Categories', icon: Boxes },
];

const secondaryNav = [
  { to: '/archive', label: 'Archive', icon: Archive },
  { to: '/trash', label: 'Recently deleted', icon: Trash2 },
];

export function Sidebar() {
  const { openNewGoal } = useAppShell();
  const { goals, categories } = useMomentum();
  const { user } = useAuth();
  const activeGoals = goals.filter((goal) => !goal.deletedAt && !goal.archivedAt && goal.status === 'active').length;

  return (
    <aside className="sidebar">
      <div className="sidebar__brand">
        <Link to="/" aria-label="MOMENTUM home">
          <Logo />
        </Link>
        <span className="sidebar__tagline">Personal command center</span>
      </div>

      <button type="button" className="sidebar__new" onClick={openNewGoal}>
        <span className="sidebar__new-icon">
          <Plus size={17} strokeWidth={2.4} />
        </span>
        <span>New goal</span>
        <kbd>N</kbd>
      </button>

      <nav className="sidebar__nav" aria-label="Main navigation">
        <span className="sidebar__section-label">Command</span>
        {primaryNav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
          >
            <item.icon size={17} strokeWidth={1.9} />
            <span>{item.label}</span>
          </NavLink>
        ))}
        <div className="sidebar__counts">
          <span>{activeGoals} active</span>
          <span>{categories.length} categories</span>
        </div>

        <span className="sidebar__section-label sidebar__section-label--spaced">Library</span>
        {secondaryNav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
          >
            <item.icon size={17} strokeWidth={1.9} />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="sidebar__footer">
        <div className="sidebar__account">
          <Link to={user ? '/settings#account' : '/auth'} className="account-chip">
            <span className="account-chip__avatar">
              {user ? user.name.charAt(0).toUpperCase() : <UserRound size={15} />}
            </span>
            <span className="account-chip__copy">
              <strong>{user ? user.name : 'Local workspace'}</strong>
              <span>{user ? 'Signed in' : 'Sign in to sync'}</span>
            </span>
          </Link>
          <Link to="/settings" className="sidebar__settings" aria-label="Settings">
            <Settings size={17} />
          </Link>
        </div>
        <div className="sidebar__legal">
          <Command size={12} />
          <span>Local-first · v{__APP_VERSION__}</span>
        </div>
      </div>
    </aside>
  );
}
