import { useState } from 'react'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'

const ZONES = ['Asia/Kolkata', 'UTC', 'Europe/London', 'America/New_York', 'Asia/Singapore']

export default function Settings() {
  const { user, refreshUser } = useAuth()
  const [name, setName] = useState(user?.name ?? '')
  const [timezone, setTimezone] = useState(user?.timezone ?? 'UTC')
  const [password, setPassword] = useState('')
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    if (!name.trim()) {
      setError('Your name cannot be empty.')
      return
    }
    if (password && password.length < 8) {
      setError('Use at least 8 characters for the new password.')
      return
    }
    setBusy(true)
    setError(null)
    setStatus(null)
    try {
      await api.updateMe({ name, timezone, ...(password ? { password } : {}) })
      await refreshUser()
      setPassword('')
      setStatus('Saved.')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save your settings.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="max-w-md">
      <h1 className="mb-1 text-lg font-medium tracking-tightest">Personal settings</h1>
      <p className="mb-5 text-sm text-slate">Only you can see this page.</p>

      <div className="card space-y-4 p-5">
        <div>
          <label className="label mb-1 block" htmlFor="sName">
            Name
          </label>
          <input id="sName" className="field" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label className="label mb-1 block" htmlFor="sEmail">
            Email
          </label>
          <input id="sEmail" className="field bg-canvas" value={user?.email ?? ''} disabled />
          <p className="mt-1 text-xs text-muted">
            Email is your sign-in and cannot be changed here.
          </p>
        </div>
        <div>
          <label className="label mb-1 block" htmlFor="sZone">
            Time zone
          </label>
          <select
            id="sZone"
            className="field"
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
          >
            {ZONES.map((z) => (
              <option key={z} value={z}>
                {z}
              </option>
            ))}
          </select>
          <p className="mt-1 text-xs text-muted">Used for due dates and reminder emails.</p>
        </div>
        <div>
          <label className="label mb-1 block" htmlFor="sPass">
            New password
          </label>
          <input
            id="sPass"
            type="password"
            className="field"
            value={password}
            placeholder="Leave blank to keep current"
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        {error && <p className="text-xs text-sevCritical">{error}</p>}
        {status && <p className="text-xs text-sevMedium">{status}</p>}
        <button className="btn-primary" onClick={() => void save()} disabled={busy}>
          {busy ? 'Saving…' : 'Save changes'}
        </button>
      </div>
    </div>
  )
}
