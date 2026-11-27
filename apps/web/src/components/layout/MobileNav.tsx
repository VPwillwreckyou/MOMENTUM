import { NavLink } from 'react-router-dom';
import { Boxes, Goal, LayoutDashboard, Plus, Settings } from 'lucide-react';
import { useAppShell } from './AppShellContext';

export function MobileNav() {
  const { openNewGoal } = useAppShell();
  return (
    <nav className="mobile-nav" aria-label="Mobile navigation">
      <NavLink to="/" end className={({ isActive }) => `mobile-nav__item${isActive ? ' is-active' : ''}`}>
        <LayoutDashboard size={19} />
        <span>Overview</span>
      </NavLink>
      <NavLink to="/goals" className={({ isActive }) => `mobile-nav__item${isActive ? ' is-active' : ''}`}>
        <Goal size={19} />
        <span>Goals</span>
      </NavLink>
      <button type="button" className="mobile-nav__add" onClick={openNewGoal} aria-label="Create a new goal">
        <Plus size={23} />
      </button>
      <NavLink to="/categories" className={({ isActive }) => `mobile-nav__item${isActive ? ' is-active' : ''}`}>
        <Boxes size={19} />
        <span>Categories</span>
      </NavLink>
      <NavLink to="/settings" className={({ isActive }) => `mobile-nav__item${isActive ? ' is-active' : ''}`}>
        <Settings size={19} />
        <span>Settings</span>
      </NavLink>
    </nav>
  );
}
