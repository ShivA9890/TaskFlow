import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import type { MailLogEntry } from '../../lib/types'

const EVENT_COPY: Record<MailLogEntry['event'], string> = {
  'user.invited': 'Invite sent',
  'task.assigned': 'Task assigned',
  'task.completed': 'Task completed',
  'task.stalled': 'Due soon, no progress',
}

export default function MailLog() {
  const qc = useQueryClient()
  const mail = useQuery({ queryKey: ['mail'], queryFn: api.mailLog })

  const runReminders = useMutation({
    mutationFn: api.runReminders,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mail'] }),
  })

  return (
    <div className="max-w-3xl">
      <h1 className="mb-1 text-lg font-medium tracking-tightest">Mail log</h1>
      <p className="mb-5 text-sm text-slate">
        Every email the notification service would send. This page disappears once SES is live.
      </p>

      <div className="card mb-6 flex flex-wrap items-center gap-3 p-4">
        <div>
          <p className="text-sm font-medium">Reminder job</p>
          <p className="text-xs text-slate">
            Finds tasks due within 48 hours that never left the first column.
          </p>
        </div>
        <button
          className="btn-primary ml-auto"
          onClick={() => runReminders.mutate()}
          disabled={runReminders.isPending}
        >
          {runReminders.isPending ? 'Running…' : 'Run now'}
        </button>
        {runReminders.data && (
          <p className="w-full font-mono text-xs text-slate">
            {runReminders.data.sent} reminder(s) sent. Re-running sends nothing new — the job is
            idempotent.
          </p>
        )}
      </div>

      {(mail.data ?? []).length === 0 ? (
        <div className="card p-10 text-center">
          <p className="text-sm text-slate">No mail yet.</p>
          <p className="mt-1 text-xs text-muted">
            Assign a task, complete one, or invite a member to trigger an email.
          </p>
        </div>
      ) : (
        <ul className="space-y-2">
          {(mail.data ?? []).map((m) => (
            <li key={m.id} className="card p-4">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded bg-accentWash px-1.5 py-0.5 font-mono text-[11px] text-accentInk">
                  {m.event}
                </span>
                <span className="text-xs text-slate">{EVENT_COPY[m.event]}</span>
                <span className="ml-auto font-mono text-[11px] text-muted">
                  {new Date(m.sentAt).toLocaleTimeString()}
                </span>
              </div>
              <p className="mt-2 text-sm font-medium">{m.subject}</p>
              <p className="mt-1 font-mono text-[11px] text-slate">
                to {m.to}
                {m.cc.length > 0 && ` · cc ${m.cc.join(', ')}`}
              </p>
              <p className="mt-2 text-xs text-slate">{m.body}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

