import { NavLink, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { Building2, LogOut } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { DisclaimerBanner } from './DisclaimerBanner';

/**
 * App shell with an accessible skip link, a role-aware primary nav, and the
 * standing advisory disclaimer. Nav uses semantic landmarks and keyboard-operable
 * links (SOW 4.5 / WCAG).
 */
export function Layout({ children }: { children: ReactNode }) {
  const { user, isStaff, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `rounded px-3 py-2 text-sm font-medium ${isActive ? 'bg-brand-500 text-white' : 'text-slate-700 hover:bg-slate-100'}`;

  return (
    <div className="min-h-screen">
      <a href="#main" className="skip-link">Skip to main content</a>
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-2">
            <Building2 className="h-6 w-6 text-brand-500" aria-hidden="true" />
            <div>
              <p className="text-sm font-bold leading-tight text-brand-700">AIP Pre-Plan Check Assistant</p>
              <p className="text-xs text-slate-500">City of Los Angeles · LADBS</p>
            </div>
          </div>
          <nav aria-label="Primary" className="flex flex-wrap items-center gap-1">
            <NavLink to="/dashboard" className={navClass}>My Projects</NavLink>
            <NavLink to="/projects/new" className={navClass}>New Project</NavLink>
            {isStaff && <NavLink to="/staff" className={navClass}>Staff Review</NavLink>}
            {isAdmin && <NavLink to="/admin/rules" className={navClass}>Rules</NavLink>}
            <NavLink to="/profile" className={navClass}>{user?.name ?? 'Account'}</NavLink>
            <button
              onClick={() => { logout(); navigate('/login'); }}
              className="ml-1 inline-flex items-center gap-1 rounded px-3 py-2 text-sm text-slate-600 hover:bg-slate-100"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" /> Sign out
            </button>
          </nav>
        </div>
      </header>
      <DisclaimerBanner />
      <main id="main" className="mx-auto max-w-6xl px-4 py-6">{children}</main>
    </div>
  );
}
