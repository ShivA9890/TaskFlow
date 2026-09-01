import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import type { Role } from '../../lib/types'

export default function Members() {
  const qc = useQueryClient()
  const users = useQuery({ queryKey: ['users'], queryFn: api.listUsers })
  const invites = useQuery({ queryKey: ['invites'], queryFn: api.listInvites })

  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('MEMBER')
  const [error, setError] = useState<string | null>(null)
  // The accept link is returned once, on creation. Held here so the admin can copy it.
  const [lastLink, setLastLink] = useState<{ email: string; url: string } | null>(null)

  const invite = useMutation({
    mutationFn: () => api.createInvite({ email, role }),
    onSuccess: (created) => {
      setLastLink(
        created.acceptUrl ? { email: created.email, url: created.acceptUrl } : null,
      )
      setEmail('')
      setError(null)
      void qc.invalidateQueries({ queryKey: ['invites'] })
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not send the invite.'),
  })

  const updateUser = useMutation({
    mutationFn: (v: { id: string; status?: 'ACTIVE' | 'DISABLED'; role?: Role }) =>
      api.updateUser(v.id, { status: v.status, role: v.role }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not update that member.'),
  })

  const send = () => {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Enter a valid email address.')
      return
    }
    invite.mutate()
  }

  return (
    <div className="max-w-3xl">
      <h1 className="mb-1 text-lg font-medium tracking-tightest">Members</h1>
      <p className="mb-5 text-sm text-slate">Invite people and control what they can reach.</p>

      <div className="card mb-6 p-5">
        <p className="label mb-3">Invite someone</p>
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[220px] flex-1">
            <label className="label mb-1 block" htmlFor="inviteEmail">
              Email
            </label>
            <input
              id="inviteEmail"
              className="field"
              value={email}
              placeholder="name@company.com"
              onChange={(e) => {
                setEmail(e.target.value)
                setError(null)
              }}
            />
          </div>
          <div>
            <label className="label mb-1 block" htmlFor="inviteRole">
              Role
            </label>
            <select
              id="inviteRole"
              className="field"
              value={role}
              onChange={(e) => setRole(e.target.value as Role)}
            >
              <option value="MEMBER">Member</option>
              <option value="ADMIN">Admin</option>
            </select>
          </div>
          <button className="btn-primary" onClick={send} disabled={invite.isPending}>
            {invite.isPending ? 'Sending…' : 'Send invite'}
          </button>
        </div>
        {error && <p className="mt-2 text-xs text-sevCritical">{error}</p>}

        {lastLink && (
          <div className="mt-4 rounded-md border border-line bg-canvas p-3">
            <p className="label mb-1">Invite link for {lastLink.email}</p>
            <p className="break-all font-mono text-[11px] text-slate">{lastLink.url}</p>
            <div className="mt-2 flex items-center gap-2">
              <button
                className="btn-ghost px-2 py-1 text-xs"
                onClick={() => void navigator.clipboard.writeText(lastLink.url)}
              >
                Copy link
              </button>
              <span className="text-xs text-muted">
                Shown once. The server keeps only a hash of the token.
              </span>
            </div>
          </div>
        )}
      </div>

      <div className="card mb-6 overflow-x-auto">
        <table className="w-full min-w-[560px] border-collapse">
          <thead>
            <tr>
              <th className="table-head">Name</th>
              <th className="table-head">Email</th>
              <th className="table-head">Role</th>
              <th className="table-head">Status</th>
              <th className="table-head" />
            </tr>
          </thead>
          <tbody>
            {(users.data ?? []).map((u) => (
              <tr key={u.id}>
                <td className="table-cell font-medium">{u.name}</td>
                <td className="table-cell font-mono text-xs text-slate">{u.email}</td>
                <td className="table-cell">
                  <select
                    className="field py-1 text-xs"
                    value={u.role}
                    onChange={(e) => updateUser.mutate({ id: u.id, role: e.target.value as Role })}
                  >
                    <option value="MEMBER">MEMBER</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                </td>
                <td className="table-cell font-mono text-xs">{u.status}</td>
                <td className="table-cell text-right">
                  <button
                    className="btn-ghost px-2 py-1 text-xs"
                    onClick={() =>
                      updateUser.mutate({
                        id: u.id,
                        status: u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                      })
                    }
                  >
                    {u.status === 'ACTIVE' ? 'Disable' : 'Enable'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <p className="label mb-2">Pending invites</p>
      {(invites.data ?? []).filter((i) => !i.acceptedAt).length === 0 ? (
        <p className="text-sm text-slate">No invites waiting.</p>
      ) : (
        <ul className="space-y-2">
          {(invites.data ?? [])
            .filter((i) => !i.acceptedAt)
            .map((i) => (
              <li key={i.id} className="card flex flex-wrap items-center gap-3 p-3 text-sm">
                <span className="font-mono text-xs">{i.email}</span>
                <span className="label">{i.role}</span>
                <span className="ml-auto text-xs text-muted">
                  expires {new Date(i.expiresAt).toLocaleDateString()}
                </span>
              </li>
            ))}
        </ul>
      )}
    </div>
  )
}
