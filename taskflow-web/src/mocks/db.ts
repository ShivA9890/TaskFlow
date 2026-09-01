import type { Board, Column, Invite, MailLogEntry, Task, Team, User } from '../lib/types'

interface Credential {
  userId: string
  password: string
}

export interface Db {
  orgId: string
  orgName: string
  users: User[]
  credentials: Credential[]
  teams: Team[]
  invites: Invite[]
  boards: Board[]
  columns: Column[]
  tasks: Task[]
  mail: MailLogEntry[]
  remindersSent: string[]
}

const STORE_KEY = 'tf.mockdb.v1'
let counter = 0
export const nextId = (prefix: string): string =>
  `${prefix}_${Date.now().toString(36)}${(counter++).toString(36)}`

const hoursFromNow = (h: number): string => new Date(Date.now() + h * 3600_000).toISOString()

function seed(): Db {
  const orgId = 'org_northwind'
  const now = new Date().toISOString()

  const admin: User = {
    id: 'usr_admin', orgId, email: 'admin@taskflow.dev', name: 'Asha Rao',
    role: 'ADMIN', status: 'ACTIVE', timezone: 'Asia/Kolkata',
  }
  const dev1: User = {
    id: 'usr_dev1', orgId, email: 'dev1@taskflow.dev', name: 'Rohit Menon',
    role: 'MEMBER', status: 'ACTIVE', timezone: 'Asia/Kolkata',
  }
  const dev2: User = {
    id: 'usr_dev2', orgId, email: 'dev2@taskflow.dev', name: 'Priya Nair',
    role: 'MEMBER', status: 'ACTIVE', timezone: 'Asia/Kolkata',
  }

  const board: Board = { id: 'brd_platform', name: 'Platform', teamId: 'tm_core' }

  const columns: Column[] = [
    { id: 'col_todo', boardId: board.id, name: 'To do', position: 0, isTerminal: false },
    { id: 'col_prog', boardId: board.id, name: 'In progress', position: 1, isTerminal: false },
    { id: 'col_review', boardId: board.id, name: 'In review', position: 2, isTerminal: false },
    { id: 'col_done', boardId: board.id, name: 'Completed', position: 3, isTerminal: true },
  ]

  const task = (
    id: string, columnId: string, title: string, body: string,
    assigneeId: string | null, severity: Task['severity'],
    dueInHours: number | null, completed = false,
  ): Task => ({
    id, boardId: board.id, columnId, title, body, assigneeId,
    reporterId: admin.id, severity,
    dueDate: dueInHours === null ? null : hoursFromNow(dueInHours),
    position: 0, movedAt: now,
    completedAt: completed ? now : null, createdAt: now,
  })

  return {
    orgId,
    orgName: 'Northwind Labs',
    users: [admin, dev1, dev2],
    credentials: [
      { userId: admin.id, password: 'admin123' },
      { userId: dev1.id, password: 'member123' },
      { userId: dev2.id, password: 'member123' },
    ],
    teams: [{ id: 'tm_core', name: 'Core platform', memberIds: [admin.id, dev1.id, dev2.id] }],
    invites: [],
    boards: [board],
    columns,
    tasks: [
      // Still in the first column and due in 30h, so the reminder job should catch it.
      task('tsk_1', 'col_todo', 'Rotate RDS master credentials', 'Move the master secret into Secrets Manager and wire rotation.', 'usr_dev1', 'CRITICAL', 30),
      task('tsk_2', 'col_todo', 'Add DLQ alarms', 'CloudWatch alarm when either DLQ depth goes above zero.', 'usr_dev2', 'MEDIUM', 168),
      task('tsk_3', 'col_todo', 'Write Helm chart for activity-service', '', null, 'LOW', null),
      task('tsk_4', 'col_prog', 'Outbox poller batching', 'Publish in batches of 25 with backoff on SNS throttling.', 'usr_dev1', 'HIGH', 96),
      task('tsk_5', 'col_review', 'Trivy scan in Jenkins', 'Fail the build on HIGH and CRITICAL image findings.', 'usr_dev2', 'HIGH', 48),
      task('tsk_6', 'col_done', 'Bootstrap Terraform remote state', 'S3 bucket plus DynamoDB lock table.', 'usr_dev1', 'MEDIUM', -48, true),
    ],
    mail: [],
    remindersSent: [],
  }
}

let db: Db = load()

function load(): Db {
  try {
    const raw = localStorage.getItem(STORE_KEY)
    if (raw) return JSON.parse(raw) as Db
  } catch {
    // fall through to a fresh seed
  }
  const fresh = seed()
  persist(fresh)
  return fresh
}

function persist(next: Db): void {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify(next))
  } catch {
    // storage blocked; the in-memory copy still works this session
  }
}

export function getDb(): Db {
  return db
}

export function commit(): void {
  persist(db)
}

export function resetDb(): void {
  db = seed()
  persist(db)
}