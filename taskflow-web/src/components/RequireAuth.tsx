import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/auth'

export default function RequireAuth({
  children,
  adminOnly = false,
}: {
  children: ReactNode
  adminOnly?: boolean
}) {
  const { user, loading, isAdmin } = useAuth()
  const location = useLocation()

  if (loading) {
    return <div className="p-10 font-mono text-xs text-muted">Loading workspace…</div>
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  if (adminOnly && !isAdmin) {
    return <Navigate to="/board" replace />
  }
  return <>{children}</>
}
