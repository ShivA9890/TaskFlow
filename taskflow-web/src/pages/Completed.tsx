import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { SeverityMark } from '../components/Indicators'

export default function Completed() {
  const { isAdmin } = useAuth()
  const tasks = useQuery({
    queryKey: ['tasks', 'completed', isAdmin],
    queryFn: () =>
      api.listTasks({ status: 'completed', ...(isAdmin ? {} : { assignee: 'me' as const }) }),
  })
  const users = useQuery({ queryKey: ['users'], queryFn: api.listUsers })

  const nameOf = (id: string | null) => users.data?.find((u) => u.id === id)?.name ?? 'Unassigned'

  if (tasks.isLoading) return <p className="font-mono text-xs text-muted">Loading…</p>

  const rows = tasks.data ?? []

  return (
    <div>
      <h1 className="mb-1 text-lg font-medium tracking-tightest">Completed</h1>
      <p className="mb-5 text-sm text-slate">
        {isAdmin ? 'Everything the team has finished.' : 'Tasks you have finished.'}
      </p>

      {rows.length === 0 ? (
        <div className="card p-10 text-center">
          <p className="text-sm text-slate">Nothing finished yet.</p>
          <p className="mt-1 text-xs text-muted">
            Move a task into the last column and it will show up here.
          </p>
        </div>
      ) : (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[640px] border-collapse">
            <thead>
              <tr>
                <th className="table-head">Task</th>
                <th className="table-head">Assignee</th>
                <th className="table-head">Severity</th>
                <th className="table-head">Completed</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((t) => (
                <tr key={t.id}>
                  <td className="table-cell font-medium">{t.title}</td>
                  <td className="table-cell text-slate">{nameOf(t.assigneeId)}</td>
                  <td className="table-cell">
                    <SeverityMark severity={t.severity} />
                  </td>
                  <td className="table-cell font-mono text-xs text-slate">
                    {t.completedAt ? new Date(t.completedAt).toLocaleDateString() : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
