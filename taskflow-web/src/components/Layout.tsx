import type { ReactNode } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { Avatar } from './Indicators'

function NavItem({ to, children }: { to: string; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `rounded-md px-3 py-1.5 text-sm transition-colors ${
          isActive ? 'bg-accentWash text-accentInk' : 'text-slate hover:text-ink'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

export default function Layout() {
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-20 border-b border-line bg-surface/90 backdrop-blur">
        <div className="mx-auto flex max-w-[1400px] flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3">
          <span className="font-mono text-sm font-medium tracking-tightest text-ink">
            task<span className="text-accent">flow</span>
          </span>

          <nav className="flex flex-wrap items-center gap-1">
            <NavItem to="/board">Board</NavItem>
            <NavItem to="/completed">Completed</NavItem>
            {isAdmin && (
              <>
                <NavItem to="/admin/boards">Boards</NavItem>
                <NavItem to="/admin/members">Members</NavItem>
                <NavItem to="/admin/teams">Teams</NavItem>
                {/* <NavItem to="/admin/mail">Mail log</NavItem> */}
              </>
            )}
            <NavItem to="/settings">Settings</NavItem>
          </nav>

          <div className="ml-auto flex items-center gap-3">
            <span className="label hidden sm:inline">{user?.role}</span>
            {user && <Avatar name={user.name} />}
            <button
              className="btn-ghost px-2 py-1 text-xs"
              onClick={() => {
                logout()
                navigate('/login', { replace: true })
              }}
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-[1400px] px-5 py-6">
        <Outlet />
      </main>
    </div>
  )
}
