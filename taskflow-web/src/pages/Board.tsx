import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { DndContext, PointerSensor, useDroppable, useSensor, useSensors } from '@dnd-kit/core'
import type { DragEndEvent } from '@dnd-kit/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import type { Column, Task } from '../lib/types'
import TaskCard from '../components/TaskCard'
import TaskDialog from '../components/TaskDialog'
import type { TaskDraft } from '../components/TaskDialog'

function ColumnShell({
  column,
  tasks,
  children,
}: {
  column: Column
  tasks: Task[]
  children: ReactNode
}) {
  const { setNodeRef, isOver } = useDroppable({ id: column.id })

  return (
    <section className="flex w-[300px] shrink-0 flex-col">
      <header className="mb-2 flex items-baseline gap-2">
        <h2 className="text-sm font-medium text-ink">{column.name}</h2>
        <span className="font-mono text-[11px] text-muted">{tasks.length}</span>
        {column.isTerminal && <span className="label">terminal</span>}
      </header>
      <div aria-hidden="true" className="mb-3 h-px bg-line">
        <div className="h-px bg-accent" style={{ width: `${Math.min(tasks.length / 8, 1) * 100}%` }} />
      </div>
      <div
        ref={setNodeRef}
        className={`flex min-h-[140px] flex-1 flex-col gap-2 rounded-lg p-1 transition-colors ${
          isOver ? 'bg-accentWash' : ''
        }`}
      >
        {children}
        {tasks.length === 0 && (
          <p className="px-2 py-6 text-center text-xs text-muted">Drop a task here</p>
        )}
      </div>
    </section>
  )
}

export default function BoardPage() {
  const { boardId } = useParams()
  const { isAdmin } = useAuth()
  const qc = useQueryClient()
  const [editing, setEditing] = useState<Task | 'new' | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  const boards = useQuery({ queryKey: ['boards'], queryFn: api.listBoards })
  const activeBoardId = boardId ?? boards.data?.[0]?.id

  const board = useQuery({
    queryKey: ['board', activeBoardId],
    queryFn: () => api.getBoard(activeBoardId!),
    enabled: Boolean(activeBoardId),
  })

  const users = useQuery({ queryKey: ['users'], queryFn: api.listUsers })

  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['board', activeBoardId] })
    void qc.invalidateQueries({ queryKey: ['tasks'] })
    void qc.invalidateQueries({ queryKey: ['mail'] })
  }

  const move = useMutation({
    mutationFn: (v: { id: string; columnId: string }) =>
      api.moveTask(v.id, { columnId: v.columnId }),
    onSuccess: invalidate,
  })

  const save = useMutation({
    mutationFn: async (draft: TaskDraft) => {
      if (editing === 'new') return api.createTask(activeBoardId!, draft)
      const task = editing as Task
      if (!isAdmin) return api.updateTask(task.id, { columnId: draft.columnId })
      return api.updateTask(task.id, draft)
    },
    onSuccess: () => {
      setEditing(null)
      setSaveError(null)
      invalidate()
    },
    onError: (e) => setSaveError(e instanceof ApiError ? e.message : 'Could not save the task.'),
  })

  const tasksByColumn = useMemo(() => {
    const map = new Map<string, Task[]>()
    for (const column of board.data?.columns ?? []) map.set(column.id, [])
    for (const task of board.data?.tasks ?? []) map.get(task.columnId)?.push(task)
    return map
  }, [board.data])

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }))

  const onDragEnd = (event: DragEndEvent) => {
    const taskId = String(event.active.id)
    const columnId = event.over ? String(event.over.id) : null
    if (!columnId) return
    const task = board.data?.tasks.find((t) => t.id === taskId)
    if (!task || task.columnId === columnId) return
    move.mutate({ id: taskId, columnId })
  }

  if (board.isLoading || boards.isLoading) {
    return <p className="font-mono text-xs text-muted">Loading board…</p>
  }
  if (!board.data) {
    return <p className="text-sm text-slate">No board yet. An admin needs to create one.</p>
  }

  const members = (users.data ?? []).filter((u) => u.status === 'ACTIVE')

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <h1 className="text-lg font-medium tracking-tightest">{board.data.name}</h1>
        <span className="label">{board.data.columns.length} columns</span>
        {isAdmin && (
          <button className="btn-primary ml-auto" onClick={() => setEditing('new')}>
            New task
          </button>
        )}
      </div>

      {move.isError && (
        <p className="mb-3 text-xs text-sevCritical">
          {move.error instanceof ApiError ? move.error.message : 'Could not move that task.'}
        </p>
      )}

      <DndContext sensors={sensors} onDragEnd={onDragEnd}>
        <div className="flex gap-5 overflow-x-auto pb-4">
          {board.data.columns.map((column) => {
            const tasks = tasksByColumn.get(column.id) ?? []
            return (
              <ColumnShell key={column.id} column={column} tasks={tasks}>
                {tasks.map((task) => (
                  <TaskCard
                    key={task.id}
                    task={task}
                    assignee={members.find((m) => m.id === task.assigneeId) ?? null}
                    onOpen={setEditing}
                  />
                ))}
              </ColumnShell>
            )
          })}
        </div>
      </DndContext>

      {editing && (
        <TaskDialog
          task={editing}
          columns={board.data.columns}
          members={members}
          saving={save.isPending}
          error={saveError}
          onClose={() => {
            setEditing(null)
            setSaveError(null)
          }}
          onSave={(draft) => save.mutate(draft)}
        />
      )}
    </div>
  )
}
