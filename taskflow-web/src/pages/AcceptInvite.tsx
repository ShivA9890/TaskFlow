import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import AuthShell from './AuthShell'

export default function AcceptInvite() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { adoptSession } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!token) {
    return (
      <AuthShell title="Invite link incomplete" intro="This link is missing its token.">
        <p className="text-sm text-slate">
          Ask your admin to send the invite again, or{' '}
          <Link className="text-accent" to="/login">
            sign in
          </Link>{' '}
          if you already have an account.
        </p>
      </AuthShell>
    )
  }

  const submit = async () => {
    if (!name.trim() || password.length < 8) {
      setError('Enter your name and a password of at least 8 characters.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const { accessToken } = await api.acceptInvite({ token, name, password })
      await adoptSession(accessToken)
      navigate('/board', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not accept the invite.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Finish setting up" intro="Choose how your name appears on the board.">
      <div>
        <label className="label mb-1 block" htmlFor="inviteName">
          Your name
        </label>
        <input
          id="inviteName"
          className="field"
          value={name}
          onChange={(e) => {
            setName(e.target.value)
            setError(null)
          }}
        />
      </div>
      <div>
        <label className="label mb-1 block" htmlFor="invitePassword">
          Password
        </label>
        <input
          id="invitePassword"
          type="password"
          className="field"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value)
            setError(null)
          }}
        />
      </div>
      {error && <p className="text-xs text-sevCritical">{error}</p>}
      <button className="btn-primary w-full" onClick={() => void submit()} disabled={busy}>
        {busy ? 'Joining…' : 'Join workspace'}
      </button>
    </AuthShell>
  )
}

