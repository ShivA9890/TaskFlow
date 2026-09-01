export type Role = 'ADMIN' | 'MEMBER'
export type UserStatus = 'INVITED' | 'ACTIVE' | 'DISABLED'
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export const SEVERITIES: Severity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export interface User {
  id: string
  orgId: string
  email: string
  name: string
  role: Role
  status: UserStatus
  timezone: string
}

export interface Team {
  id: string
  name: string
  memberIds: string[]
}

export interface Column {
  id: string
  boardId: string
  name: string
  position: number
  isTerminal: boolean
}

export interface Task {
  id: string
  boardId: string
  columnId: string
  title: string
  body: string
  assigneeId: string | null
  reporterId: string
  severity: Severity
  dueDate: string | null
  position: number
  movedAt: string
  completedAt: string | null
  createdAt: string
}

export interface Board {
  id: string
  name: string
  teamId: string | null
}

export interface BoardDetail extends Board {
  columns: Column[]
  tasks: Task[]
}

export interface Invite {
  id: string
  email: string
  role: Role
  expiresAt: string
  acceptedAt: string | null
  /** Only present on the response that created the invite. */
  acceptUrl?: string | null
}
export interface MailLogEntry {
  id: string
  event: 'user.invited' | 'task.assigned' | 'task.completed' | 'task.stalled'
  to: string
  cc: string[]
  subject: string
  body: string
  sentAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
}