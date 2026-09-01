import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'

export default function Teams() {
  const qc = useQueryClient()
  const teams = useQuery({ queryKey: ['teams'], queryFn: api.listTeams })
  const users = useQuery({ queryKey: ['users'], queryFn: api.listUsers })
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const invalidate = () => qc.invalidateQueries({ queryKey: ['teams'] })

  const create = useMutation({
    mutationFn: () => api.createTeam({ name }),
    onSuccess: () => {
      setName('')
      void invalidate()
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not create the team.'),
  })

  const addMember = useMutation({
    mutationFn: (v: { teamId: string; userId: string }) =>
      api.addTeamMember(v.teamId, { userId: v.userId }),
    onSuccess: invalidate,
  })

  return (
    <div className="max-w-2xl">
      <h1 className="mb-1 text-lg font-medium tracking-tightest">Teams</h1>
      <p className="mb-5 text-sm text-slate">A board belongs to one team.</p>

      <div className="card mb-6 flex flex-wrap items-end gap-3 p-5">
        <div className="min-w-[220px] flex-1">
          <label className="label mb-1 block" htmlFor="teamName">
            Team name
          </label>
          <input
            id="teamName"
            className="field"
            value={name}
            placeholder="Core platform"
            onChange={(e) => {
              setName(e.target.value)
              setError(null)
            }}
          />
        </div>
        <button
          className="btn-primary"
          disabled={create.isPending}
          onClick={() => (name.trim() ? create.mutate() : setError('Give the team a name.'))}
        >
          Create team
        </button>
        {error && <p className="w-full text-xs text-sevCritical">{error}</p>}
      </div>

      <div className="space-y-3">
        {(teams.data ?? []).map((t) => {
          const notInTeam = (users.data ?? []).filter((u) => !t.memberIds.includes(u.id))
          return (
            <div key={t.id} className="card p-4">
              <div className="flex items-baseline gap-2">
                <h2 className="text-sm font-medium">{t.name}</h2>
                <span className="font-mono text-[11px] text-muted">
                  {t.memberIds.length} people
                </span>
              </div>
              <p className="mt-2 text-xs text-slate">
                {t.memberIds.map((id) => users.data?.find((u) => u.id === id)?.name ?? id).join(', ')}
              </p>
              {notInTeam.length > 0 && (
                <select
                  className="field mt-3 max-w-xs py-1 text-xs"
                  value=""
                  onChange={(e) =>
                    e.target.value && addMember.mutate({ teamId: t.id, userId: e.target.value })
                  }
                >
                  <option value="">Add someone…</option>
                  {notInTeam.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name}
                    </option>
                  ))}
                </select>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
