import { useEffect, useState } from 'react'
import { SEVERITIES } from '../lib/types'
import type { Column, Severity, Task, User } from '../lib/types'
import { useAuth } from '../lib/auth'

export interface TaskDraft {
  title: string
  body: string
  assigneeId: string | null
  severity: Severity
  dueDate: string | null
  columnId: string
}

const toDateInput = (iso: string | null) => (iso ? iso.slice(0, 10) : '')

export default function TaskDialog({
  task,
  columns,
  members,
  onClose,
  onSave,
  saving,
  error,
}: {
  task: Task | 'new'
  columns: Column[]
  members: User[]
  onClose: () => void
  onSave: (draft: TaskDraft) => void
  saving: boolean
  error: string | null
}) {
  const { isAdmin } = useAuth()
  const isNew = task === 'new'
  const existing = isNew ? null : task

  const [draft, setDraft] = useState<TaskDraft>({
    title: existing?.title ?? '',
    body: existing?.body ?? '',
    assigneeId: existing?.assigneeId ?? null,
    severity: existing?.severity ?? 'MEDIUM',
    dueDate: existing?.dueDate ?? null,
    columnId: existing?.columnId ?? columns[0]?.id ?? '',
  })
  const [titleError, setTitleError] = useState<string | null>(null)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const submit = () => {
    if (!draft.title.trim()) {
      setTitleError('Give the task a title.')
      return
    }
    setTitleError(null)
    onSave(draft)
  }

  const readOnly = !isAdmin

  return (
    <div className="fixed inset-0 z-40 flex items-start justify-center overflow-y-auto bg-ink/30 p-4 sm:p-10">
      <div className="card w-full max-w-xl p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="label">{isNew ? 'New task' : readOnly ? 'Task details' : 'Edit task'}</h2>
          <button className="btn-ghost px-2 py-1 text-xs" onClick={onClose}>
            Close
          </button>
        </div>

        <div className="space-y-4">
          <div>
            <label className="label mb-1 block" htmlFor="task-title">
              Title
            </label>
            <input
              id="task-title"
              className="field"
              value={draft.title}
              disabled={readOnly}
              placeholder="Rotate RDS master credentials"
              onChange={(e) => {
                setDraft({ ...draft, title: e.target.value })
                if (titleError) setTitleError(null)
              }}
            />
            {titleError && <p className="mt-1 text-xs text-sevCritical">{titleError}</p>}
          </div>

          <div>
            <label className="label mb-1 block" htmlFor="task-body">
              Description
            </label>
            <textarea
              id="task-body"
              rows={4}
              className="field resize-y"
              value={draft.body}
              disabled={readOnly}
              placeholder="What has to be true for this to be done?"
              onChange={(e) => setDraft({ ...draft, body: e.target.value })}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label mb-1 block" htmlFor="task-assignee">
                Assigned to
              </label>
              <select
                id="task-assignee"
                className="field"
                value={draft.assigneeId ?? ''}
                disabled={readOnly}
                onChange={(e) => setDraft({ ...draft, assigneeId: e.target.value || null })}
              >
                <option value="">Nobody yet</option>
                {members.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="label mb-1 block" htmlFor="task-severity">
                Severity
              </label>
              <select
                id="task-severity"
                className="field"
                value={draft.severity}
                disabled={readOnly}
                onChange={(e) => setDraft({ ...draft, severity: e.target.value as Severity })}
              >
                {SEVERITIES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="label mb-1 block" htmlFor="task-due">
                Due date
              </label>
              <input
                id="task-due"
                type="date"
                className="field"
                value={toDateInput(draft.dueDate)}
                disabled={readOnly}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    dueDate: e.target.value
                      ? new Date(`${e.target.value}T17:00:00`).toISOString()
                      : null,
                  })
                }
              />
            </div>

            <div>
              <label className="label mb-1 block" htmlFor="task-column">
                Column
              </label>
              <select
                id="task-column"
                className="field"
                value={draft.columnId}
                onChange={(e) => setDraft({ ...draft, columnId: e.target.value })}
              >
                {columns.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {error && <p className="text-xs text-sevCritical">{error}</p>}

          <div className="flex items-center justify-end gap-2 pt-1">
            <button className="btn-ghost" onClick={onClose}>
              Cancel
            </button>
            <button className="btn-primary" onClick={submit} disabled={saving}>
              {saving ? 'Saving…' : isNew ? 'Create task' : 'Save changes'}
            </button>
          </div>
          {readOnly && (
            <p className="text-xs text-muted">
              You can move this task between columns. Editing details is admin-only.
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
