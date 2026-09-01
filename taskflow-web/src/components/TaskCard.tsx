import { useDraggable } from '@dnd-kit/core'
import type { Task, User } from '../lib/types'
import { Avatar, DueDate, SeverityMark, SeveritySpine } from './Indicators'

export default function TaskCard({
  task,
  assignee,
  onOpen,
}: {
  task: Task
  assignee: User | null
  onOpen: (task: Task) => void
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: task.id,
  })

  const style = transform
    ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` }
    : undefined

  return (
    <div ref={setNodeRef} style={style} className={`relative ${isDragging ? 'z-30 opacity-90' : ''}`}>
      <div className="card relative overflow-hidden p-3 pl-4 hover:border-slate">
        <SeveritySpine severity={task.severity} />

        <button
          type="button"
          onClick={() => onOpen(task)}
          className="block w-full text-left text-sm font-medium leading-snug text-ink"
        >
          {task.title}
        </button>

        {task.body && (
          <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-slate">{task.body}</p>
        )}

        <div className="mt-3 flex items-center gap-2">
          <SeverityMark severity={task.severity} />
          <span className="ml-auto">
            <DueDate dueDate={task.dueDate} completed={task.completedAt !== null} />
          </span>
          {assignee ? (
            <Avatar name={assignee.name} />
          ) : (
            <span className="font-mono text-[11px] text-muted">unassigned</span>
          )}
        </div>

        <button
          {...listeners}
          {...attributes}
          aria-label={`Drag ${task.title}`}
          className="absolute right-1.5 top-1.5 cursor-grab rounded p-1 font-mono text-[11px] leading-none text-muted hover:bg-canvas active:cursor-grabbing"
        >
          ⣿
        </button>
      </div>
    </div>
  )
}
