import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'

const DEFAULT_COLUMNS = ['To do', 'In progress', 'Completed']

export default function Boards() {
  const qc = useQueryClient()
  const boards = useQuery({ queryKey: ['boards'], queryFn: api.listBoards })
  const teams = useQuery({ queryKey: ['teams'], queryFn: api.listTeams })

  const [name, setName] = useState('')
  const [teamId, setTeamId] = useState('')
  const [columns, setColumns] = useState<string[]>(DEFAULT_COLUMNS)
  const [error, setError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: () =>
      api.createBoard({ name, teamId: teamId || null, columns: columns.map((c) => c.trim()) }),
    onSuccess: () => {
      setName('')
      setColumns(DEFAULT_COLUMNS)
      setError(null)
      void qc.invalidateQueries({ queryKey: ['boards'] })
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not create the board.'),
  })

  const submit = () => {
    if (!name.trim()) return setError('Give the board a name.')
    if (columns.some((c) => !c.trim())) return setError('Every column needs a name.')
    create.mutate()
  }

  return (
    <div className="max-w-2xl">
      <h1 className="mb-1 text-lg font-medium tracking-tightest">Boards</h1>
      <p className="mb-5 text-sm text-slate">
        A board carries between 3 and 6 columns. The last column marks work as done.
      </p>

      <div className="card mb-6 space-y-4 p-5">
        <div className="flex flex-wrap gap-3">
          <div className="min-w-[200px] flex-1">
            <label className="label mb-1 block" htmlFor="boardName">
              Board name
            </label>
            <input
              id="boardName"
              className="field"
              value={name}
              placeholder="Platform"
              onChange={(e) => {
                setName(e.target.value)
                setError(null)
              }}
            />
          </div>
          <div className="min-w-[200px] flex-1">
            <label className="label mb-1 block" htmlFor="boardTeam">
              Team
            </label>
            <select
              id="boardTeam"
              className="field"
              value={teamId}
              onChange={(e) => setTeamId(e.target.value)}
            >
              <option value="">No team</option>
              {(teams.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <div className="mb-2 flex items-center gap-2">
            <span className="label">Columns</span>
            <span className="font-mono text-[11px] text-muted">{columns.length}/6</span>
          </div>
          <div className="space-y-2">
            {columns.map((c, i) => (
              <div key={i} className="flex items-center gap-2">
                <span className="w-6 font-mono text-[11px] text-muted">{i + 1}</span>
                <input
                  className="field"
                  value={c}
                  aria-label={`Column ${i + 1} name`}
                  onChange={(e) => {
                    const next = [...columns]
                    next[i] = e.target.value
                    setColumns(next)
                    setError(null)
                  }}
                />
                <button
                  className="btn-ghost px-2 py-1 text-xs"
                  disabled={columns.length <= 3}
                  onClick={() => setColumns(columns.filter((_, idx) => idx !== i))}
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
          <button
            className="btn-ghost mt-3 text-xs"
            disabled={columns.length >= 6}
            onClick={() => setColumns([...columns, ''])}
          >
            Add column
          </button>
        </div>

        {error && <p className="text-xs text-sevCritical">{error}</p>}
        <button className="btn-primary" onClick={submit} disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create board'}
        </button>
      </div>

      <p className="label mb-2">Existing boards</p>
      <ul className="space-y-2">
        {(boards.data ?? []).map((b) => (
          <li key={b.id} className="card flex items-center gap-3 p-3">
            <span className="text-sm font-medium">{b.name}</span>
            <span className="label">
              {teams.data?.find((t) => t.id === b.teamId)?.name ?? 'no team'}
            </span>
            <Link className="ml-auto text-xs text-accent" to={`/board/${b.id}`}>
              Open
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

