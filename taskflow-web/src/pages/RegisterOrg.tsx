import { useState } from 'react'
import type { ChangeEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import AuthShell from './AuthShell'

export default function RegisterOrg() {
  const { adoptSession } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ orgName: '', name: '', email: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (k: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [k]: e.target.value })
    setError(null)
  }

  const submit = async () => {
    if (Object.values(form).some((v) => !v.trim())) {
      setError('Fill in every field to create the workspace.')
      return
    }
    if (form.password.length < 8) {
      setError('Use at least 8 characters for the password.')
      return
    }
    setBusy(true)
    try {
      const { accessToken } = await api.registerOrg(form)
      await adoptSession(accessToken)
      navigate('/board', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not create the workspace.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell
      title="Create a workspace"
      intro="You become the first admin and can invite the rest of the team."
      footer={
        <>
          Already have an account?{' '}
          <Link className="text-accent" to="/login">
            Sign in
          </Link>
        </>
      }
    >
      <div>
        <label className="label mb-1 block" htmlFor="orgName">
          Workspace name
        </label>
        <input
          id="orgName"
          className="field"
          value={form.orgName}
          onChange={set('orgName')}
          placeholder="Northwind Labs"
        />
      </div>
      <div>
        <label className="label mb-1 block" htmlFor="name">
          Your name
        </label>
        <input id="name" className="field" value={form.name} onChange={set('name')} />
      </div>
      <div>
        <label className="label mb-1 block" htmlFor="regEmail">
          Email
        </label>
        <input id="regEmail" className="field" value={form.email} onChange={set('email')} />
      </div>
      <div>
        <label className="label mb-1 block" htmlFor="regPassword">
          Password
        </label>
        <input
          id="regPassword"
          type="password"
          className="field"
          value={form.password}
          onChange={set('password')}
        />
      </div>
      {error && <p className="text-xs text-sevCritical">{error}</p>}
      <button className="btn-primary w-full" onClick={() => void submit()} disabled={busy}>
        {busy ? 'Creating…' : 'Create workspace'}
      </button>
    </AuthShell>
  )
}
