import type {
  AuthResponse, Board, BoardDetail, Column, Invite, MailLogEntry,
  Role, Severity, Task, Team, User,
} from './types'

const BASE: string = import.meta.env.VITE_API_BASE ?? '/api/v1'
const TOKEN_KEY = 'tf.accessToken'

export class ApiError extends Error {
  public status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`${BASE}${path}`, { ...init, headers })

  // Access tokens last 15 minutes. Rather than plumbing refresh through every
  // caller, clear the session and let the route guard bounce to /login.
  if (res.status === 401 && token) {
    setToken(null)
    window.location.href = '/login'
    throw new ApiError(401, 'Your session expired. Sign in again.')
  }

  if (res.status === 204) return undefined as T
  const text = await res.text()
  const payload = text ? JSON.parse(text) : null

  if (!res.ok) {
    throw new ApiError(res.status, payload?.message ?? `Request failed (${res.status})`)
  }
  return payload as T
}

const get = <T,>(p: string) => request<T>(p)
const post = <T,>(p: string, body?: unknown) =>
  request<T>(p, { method: 'POST', body: body ? JSON.stringify(body) : undefined })
const patch = <T,>(p: string, body: unknown) =>
  request<T>(p, { method: 'PATCH', body: JSON.stringify(body) })
const del = (p: string) => request<void>(p, { method: 'DELETE' })

export const api = {
  registerOrg: (b: { orgName: string; name: string; email: string; password: string }) =>
    post<AuthResponse>('/auth/register-org', b),
  login: (b: { email: string; password: string }) => post<AuthResponse>('/auth/login', b),
  me: () => get<User>('/me'),
  updateMe: (b: { name?: string; timezone?: string; password?: string }) => patch<User>('/me', b),

  listUsers: () => get<User[]>('/users'),
  updateUser: (id: string, b: { role?: Role; status?: 'ACTIVE' | 'DISABLED' }) =>
    patch<User>(`/users/${id}`, b),

  listInvites: () => get<Invite[]>('/invites'),
  createInvite: (b: { email: string; role: Role }) => post<Invite>('/invites', b),
  acceptInvite: (b: { token: string; name: string; password: string }) =>
    post<AuthResponse>('/invites/accept', b),

  listTeams: () => get<Team[]>('/teams'),
  createTeam: (b: { name: string }) => post<Team>('/teams', b),
  addTeamMember: (teamId: string, b: { userId: string }) =>
    post<Team>(`/teams/${teamId}/members`, b),

  listBoards: () => get<Board[]>('/boards'),
  getBoard: (id: string) => get<BoardDetail>(`/boards/${id}`),
  createBoard: (b: { name: string; teamId: string | null; columns: string[] }) =>
    post<Board>('/boards', b),
  addColumn: (boardId: string, b: { name: string }) =>
    post<Column>(`/boards/${boardId}/columns`, b),
  deleteColumn: (columnId: string) => del(`/columns/${columnId}`),

  createTask: (
    boardId: string,
    b: {
      title: string
      body: string
      assigneeId: string | null
      severity: Severity
      dueDate: string | null
      columnId: string
    },
  ) => post<Task>(`/boards/${boardId}/tasks`, b),
  updateTask: (
    id: string,
    b: Partial<Pick<Task, 'title' | 'body' | 'assigneeId' | 'severity' | 'dueDate' | 'columnId'>>,
  ) => patch<Task>(`/tasks/${id}`, b),
  moveTask: (id: string, b: { columnId: string }) => post<Task>(`/tasks/${id}/move`, b),
  listTasks: (q: { boardId?: string; status?: 'completed'; assignee?: 'me' }) => {
    const params = new URLSearchParams()
    if (q.boardId) params.set('boardId', q.boardId)
    if (q.status) params.set('status', q.status)
    if (q.assignee) params.set('assignee', q.assignee)
    return get<Task[]>(`/tasks?${params.toString()}`)
  },

  // dev-only: stands in for SES and the reminder Lambda
  mailLog: () => get<MailLogEntry[]>('/_dev/emails'),
  runReminders: () => post<{ sent: number }>('/_dev/run-reminders'),
}