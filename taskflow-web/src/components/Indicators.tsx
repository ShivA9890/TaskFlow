
import type { Severity } from '../lib/types'

const SEVERITY_STYLES: Record<Severity, string> = {
  LOW: 'text-sevLow',
  MEDIUM: 'text-sevMedium',
  HIGH: 'text-sevHigh',
  CRITICAL: 'text-sevCritical',
}

const SEVERITY_SPINE: Record<Severity, string> = {
  LOW: 'bg-sevLow/30',
  MEDIUM: 'bg-sevMedium',
  HIGH: 'bg-sevHigh',
  CRITICAL: 'bg-sevCritical',
}

export function SeverityMark({ severity }: { severity: Severity }) {
  return (
    <span
      className={`font-mono text-[11px] uppercase tracking-[0.08em] ${SEVERITY_STYLES[severity]}`}
    >
      {severity}
    </span>
  )
}

export function SeveritySpine({ severity }: { severity: Severity }) {
  return (
    <span
      aria-hidden="true"
      className={`absolute inset-y-0 left-0 w-[3px] rounded-l-lg ${SEVERITY_SPINE[severity]}`}
    />
  )
}

const STALL_WINDOW_HOURS = 48

export function DueDate({
  dueDate,
  completed,
}: {
  dueDate: string | null
  completed: boolean
}) {
  if (!dueDate) return <span className="font-mono text-[11px] text-muted">no due date</span>

  const hoursLeft = Math.round((new Date(dueDate).getTime() - Date.now()) / 3600_000)
  const stamp = new Date(dueDate).toLocaleDateString(undefined, {
    day: '2-digit',
    month: 'short',
  })

  if (completed) {
    return <span className="font-mono text-[11px] text-muted">{stamp}</span>
  }
  if (hoursLeft < 0) {
    return (
      <span className="rounded bg-sevCritical/10 px-1.5 py-0.5 font-mono text-[11px] text-sevCritical">
        overdue {Math.abs(hoursLeft)}h
      </span>
    )
  }
  if (hoursLeft <= STALL_WINDOW_HOURS) {
    return (
      <span className="rounded bg-sevHigh/10 px-1.5 py-0.5 font-mono text-[11px] text-sevHigh">
        due in {hoursLeft}h
      </span>
    )
  }
  return <span className="font-mono text-[11px] text-slate">{stamp}</span>
}

export function Avatar({ name }: { name: string }) {
  const initials = name
    .split(' ')
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()
  return (
    <span
      title={name}
      className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accentWash font-mono text-[10px] text-accentInk"
    >
      {initials}
    </span>
  )
}
