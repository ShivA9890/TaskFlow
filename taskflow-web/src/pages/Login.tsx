import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import AuthShell from './AuthShell'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    if (!email.trim() || !password) {
      setError('Enter your email and password.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await login(email, password)
      navigate('/board', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reach the server.')
    } finally {
      setBusy(false)
    }
  }

  const fill = (e: string, p: string) => {
    setEmail(e)
    setPassword(p)
    setError(null)
  }

  return (
    <AuthShell
      title="Sign in"
      intro="Use your work email."
      footer={
        <>
          Starting a new workspace?{' '}
          <Link className="text-accent" to="/register">
            Create one
          </Link>
        </>
      }
    >
      <div>
        <label className="label mb-1 block" htmlFor="email">
          Email
        </label>
        <input
          id="email"
          className="field"
          autoComplete="username"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value)
            setError(null)
          }}
          onKeyDown={(e) => e.key === 'Enter' && void submit()}
        />
      </div>
      <div>
        <label className="label mb-1 block" htmlFor="password">
          Password
        </label>
        <input
          id="password"
          type="password"
          className="field"
          autoComplete="current-password"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value)
            setError(null)
          }}
          onKeyDown={(e) => e.key === 'Enter' && void submit()}
        />
      </div>
      {error && <p className="text-xs text-sevCritical">{error}</p>}
      <button className="btn-primary w-full" onClick={() => void submit()} disabled={busy}>
        {busy ? 'Signing in…' : 'Sign in'}
      </button>

      <div className="border-t border-line pt-4">
        <p className="label mb-2">Seed accounts</p>
        <div className="flex gap-2">
          <button
            className="btn-ghost flex-1 text-xs"
            onClick={() => fill('admin@taskflow.dev', 'admin123')}
          >
            Admin
          </button>
          <button
            className="btn-ghost flex-1 text-xs"
            onClick={() => fill('dev1@taskflow.dev', 'member123')}
          >
            Member
          </button>
        </div>
      </div>
    </AuthShell>
  )
}
