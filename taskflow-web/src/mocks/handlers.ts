// @ts-nocheck
import { http, HttpResponse } from 'msw'
import { commit, getDb, nextId } from './db'
import type { Board, Column, Invite, MailLogEntry, Task, Team, User } from '../lib/types'

const P = '/api/v1'
const STALL_WINDOW_HOURS = 48

const json = <T,>(data: T, status = 200) => HttpResponse.json(data, { status })
const fail = (status: number, message: string) => HttpResponse.json({ message }, { status })

function actor(request: Request): User | null {
  const header = request.headers.get('Authorization')
  if (!header?.startsWith('Bearer mock.')) return null
  const userId = header.slice('Bearer mock.'.length)
  return getDb().users.find((u) => u.id === userId && u.status === 'ACTIVE') ?? null
}

const tokenFor = (u: User) => ({ accessToken: `mock.${u.id}`, refreshToken: `mockr.${u.id}` })

function sendMail(entry: Omit<MailLogEntry, 'id' | 'sentAt'>): void {
  getDb().mail.unshift({ ...entry, id: nextId('mail'), sentAt: new Date().toISOString() })
}

const admins = () => getDb().users.filter((u) => u.role === 'ADMIN' && u.status === 'ACTIVE')
const userById = (id: string | null) =>
  id ? (getDb().users.find((u) => u.id === id) ?? null) : null

function mailTaskAssigned(task: Task): void {
  const assignee = userById(task.assigneeId)
  if (!assignee) return
  sendMail({
    event: 'task.assigned',
    to: assignee.email,
    cc: [],
    subject: `[TaskFlow] Assigned to you: ${task.title}`,
    body: `Severity ${task.severity}. Due ${task.dueDate ? new Date(task.dueDate).toDateString() : 'not set'}.`,
  })
}

function mailTaskCompleted(task: Task): void {
  const assignee = userById(task.assigneeId)
  const primaryAdmin = admins()[0]
  if (!primaryAdmin) return
  sendMail({
    event: 'task.completed',
    to: primaryAdmin.email,
    cc: assignee ? [assignee.email] : [],
    subject: `[TaskFlow] Completed: ${task.title}`,
    body: `${assignee?.name ?? 'Someone'} moved this task to a completed column.`,
  })
}

function mailTaskStalled(task: Task): void {
  const assignee = userById(task.assigneeId)
  if (!assignee) return
  sendMail({
    event: 'task.stalled',
    to: assignee.email,
    cc: admins().map((a) => a.email),
    subject: `[TaskFlow] Due soon with no progress: ${task.title}`,
    body: `Due ${new Date(task.dueDate!).toLocaleString()} and still in the first column.`,
  })
}

function applyColumnChange(task: Task, columnId: string): void {
  const column = getDb().columns.find((c) => c.id === columnId)
  if (!column) return
  const wasCompleted = task.completedAt !== null
  task.columnId = columnId
  task.movedAt = new Date().toISOString()
  if (column.isTerminal) {
    task.completedAt = new Date().toISOString()
    if (!wasCompleted) mailTaskCompleted(task)
  } else {
    task.completedAt = null
  }
}

export const handlers = [
  http.post(`${P}/auth/login`, async ({ request }) => {
    const { email, password } = (await request.json()) as { email: string; password: string }
    const db = getDb()
    const user = db.users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase())
    const cred = user && db.credentials.find((c) => c.userId === user.id)
    if (!user || !cred || cred.password !== password) {
      return fail(401, 'Email or password is incorrect.')
    }
    if (user.status !== 'ACTIVE') return fail(403, 'This account is disabled.')
    return json(tokenFor(user))
  }),

  http.post(`${P}/auth/register-org`, async ({ request }) => {
    const body = (await request.json()) as {
      orgName: string; name: string; email: string; password: string
    }
    const db = getDb()
    if (db.users.some((u) => u.email.toLowerCase() === body.email.toLowerCase())) {
      return fail(409, 'That email already has an account.')
    }
    const user: User = {
      id: nextId('usr'), orgId: db.orgId, email: body.email, name: body.name,
      role: 'ADMIN', status: 'ACTIVE', timezone: 'Asia/Kolkata',
    }
    db.orgName = body.orgName
    db.users.push(user)
    db.credentials.push({ userId: user.id, password: body.password })
    commit()
    return json(tokenFor(user), 201)
  }),

  http.get(`${P}/me`, ({ request }) => {
    const me = actor(request)
    return me ? json(me) : fail(401, 'Sign in to continue.')
  }),

  http.patch(`${P}/me`, async ({ request }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    const body = (await request.json()) as { name?: string; timezone?: string; password?: string }
    if (body.name) me.name = body.name
    if (body.timezone) me.timezone = body.timezone
    if (body.password) {
      const cred = getDb().credentials.find((c) => c.userId === me.id)
      if (cred) cred.password = body.password
    }
    commit()
    return json(me)
  }),

  http.get(`${P}/users`, ({ request }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    return json(getDb().users)
  }),

  http.patch(`${P}/users/:id`, async ({ request, params }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    if (me.role !== 'ADMIN') return fail(403, 'Only admins can change member access.')
    const target = getDb().users.find((u) => u.id === params.id)
    if (!target) return fail(404, 'That member no longer exists.')
    const body = (await request.json()) as { role?: User['role']; status?: User['status'] }
    if (body.role) target.role = body.role
    if (body.status) target.status = body.status
    commit()
    return json(target)
  }),

  http.get(`${P}/invites`, ({ request }) => {
    const me = actor(request)
    if (!me || me.role !== 'ADMIN') return fail(403, 'Only admins can view invites.')
    return json(getDb().invites)
  }),

  http.post(`${P}/invites`, async ({ request }) => {
    const me = actor(request)
    if (!me || me.role !== 'ADMIN') return fail(403, 'Only admins can invite members.')
    const body = (await request.json()) as { email: string; role: User['role'] }
    const db = getDb()
    if (db.users.some((u) => u.email.toLowerCase() === body.email.toLowerCase())) {
      return fail(409, 'That person is already a member.')
    }
    const invite: Invite = {
      id: nextId('inv'), email: body.email, role: body.role, token: nextId('tok'),
      expiresAt: new Date(Date.now() + 7 * 86400_000).toISOString(), acceptedAt: null,
    }
    db.invites.push(invite)
    sendMail({
      event: 'user.invited',
      to: invite.email,
      cc: [],
      subject: `[TaskFlow] ${me.name} invited you to ${db.orgName}`,
      body: `Accept your invite: http://localhost:5173/accept-invite?token=${invite.token}`,
    })
    commit()
    return json(invite, 201)
  }),

  http.post(`${P}/invites/accept`, async ({ request }) => {
    const body = (await request.json()) as { token: string; name: string; password: string }
    const db = getDb()
    const invite = db.invites.find((i) => i.token === body.token && !i.acceptedAt)
    if (!invite) return fail(404, 'This invite link is not valid or has already been used.')
    if (new Date(invite.expiresAt) < new Date()) return fail(410, 'This invite has expired.')

    const user: User = {
      id: nextId('usr'), orgId: db.orgId, email: invite.email, name: body.name,
      role: invite.role, status: 'ACTIVE', timezone: 'Asia/Kolkata',
    }
    db.users.push(user)
    db.credentials.push({ userId: user.id, password: body.password })
    invite.acceptedAt = new Date().toISOString()
    commit()
    return json(tokenFor(user), 201)
  }),

  http.get(`${P}/teams`, ({ request }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    return json(getDb().teams)
  }),

  http.post(`${P}/teams`, async ({ request }) => {
    const me = actor(request)
    if (!me || me.role !== 'ADMIN') return fail(403, 'Only admins can create teams.')
    const { name } = (await request.json()) as { name: string }
    const team: Team = { id: nextId('tm'), name, memberIds: [me.id] }
    getDb().teams.push(team)
    commit()
    return json(team, 201)
  }),

  http.post(`${P}/teams/:id/members`, async ({ request, params }) => {
    const me = actor(request)
    if (!me || me.role !== 'ADMIN') return fail(403, 'Only admins can change team membership.')
    const team = getDb().teams.find((t) => t.id === params.id)
    if (!team) return fail(404, 'That team no longer exists.')
    const { userId } = (await request.json()) as { userId: string }
    if (!team.memberIds.includes(userId)) team.memberIds.push(userId)
    commit()
    return json(team)
  }),

  http.get(`${P}/boards`, ({ request }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    return json(getDb().boards)
  }),

  http.get(`${P}/boards/:id`, ({ request, params }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    const db = getDb()
    const board = db.boards.find((b) => b.id === params.id)
    if (!board) return fail(404, 'That board no longer exists.')
    return json({
      ...board,
      columns: db.columns
        .filter((c) => c.boardId === board.id)
        .sort((a, b) => a.position - b.position),
      tasks: db.tasks.filter((t) => t.boardId === board.id),
    })
  }),

  http.post(`${P}/boards`, async ({ request }) => {
    const me = actor(request)
    if (!me || me.role !== 'ADMIN') return fail(403, 'Only admins can create boards.')
    const body = (await request.json()) as {
      name: string; teamId: string | null; columns: string[]
    }
    if (body.columns.length < 3 || body.columns.length > 6) {
      return fail(422, 'A board needs between 3 and 6 columns.')
    }
    const board: Board = { id: nextId('brd'), name: body.name, teamId: body.teamId }
    const db = getDb()
    db.boards.push(board)
    body.columns.forEach((name, i) => {
      db.columns.push({
        id: nextId('col'), boardId: board.id, name, position: i,
        isTerminal: i === body.columns.length - 1,
      })
    })
    commit()
    return json(board, 201)
  }),

  http.post(`${P}/boards/:id/columns`, async ({ request, params }) => {
    const me = actor(request)
    if (!me || me.role !== 'ADMIN') return fail(403, 'Only admins can change columns.')
    const db = getDb()
    const existing = db.columns.filter((c) => c.boardId === params.id)
    if (existing.length >= 6) return fail(422, 'A board can have at most 6 columns.')
    const { name } = (await request.json()) as { name: string }
    const terminal = existing.find((c) => c.isTerminal)
    const column: Column = {
      id: nextId('col'), boardId: params.id as string, name,
      position: existing.length - 1, isTerminal: false,
    }
    if (terminal) terminal.position = existing.length
    db.columns.push(column)
    commit()
    return json(column, 201)
  }),

  http.delete(`${P}/columns/:id`, ({ request, params }) => {
    const me = actor(request)
    if (!me || me.role !== 'ADMIN') return fail(403, 'Only admins can change columns.')
    const db = getDb()
    const column = db.columns.find((c) => c.id === params.id)
    if (!column) return fail(404, 'That column no longer exists.')
    const siblings = db.columns.filter((c) => c.boardId === column.boardId)
    if (siblings.length <= 3) return fail(422, 'A board needs at least 3 columns.')
    if (db.tasks.some((t) => t.columnId === column.id)) {
      return fail(422, 'Move the tasks out of this column before deleting it.')
    }
    db.columns = db.columns.filter((c) => c.id !== column.id)
    db.columns
      .filter((c) => c.boardId === column.boardId)
      .sort((a, b) => a.position - b.position)
      .forEach((c, i) => (c.position = i))
    commit()
    return new HttpResponse(null, { status: 204 })
  }),

  http.get(`${P}/tasks`, ({ request }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    const url = new URL(request.url)
    let result = getDb().tasks
    const boardId = url.searchParams.get('boardId')
    if (boardId) result = result.filter((t) => t.boardId === boardId)
    if (url.searchParams.get('status') === 'completed') {
      result = result.filter((t) => t.completedAt !== null)
    }
    if (url.searchParams.get('assignee') === 'me') {
      result = result.filter((t) => t.assigneeId === me.id)
    }
    return json(result)
  }),
http.post(`${P}/boards/:id/tasks`, async ({ request, params }) => {
    const me = actor(request)
    if (!me || me.role !== 'ADMIN') return fail(403, 'Only admins can create tasks.')
    const body = (await request.json()) as Omit<
      Task, 'id' | 'boardId' | 'reporterId' | 'position' | 'movedAt' | 'completedAt' | 'createdAt'
    >
    if (!body.title?.trim()) return fail(422, 'Give the task a title.')
    const now = new Date().toISOString()
    const task: Task = {
      ...body, id: nextId('tsk'), boardId: params.id as string, reporterId: me.id,
      position: 0, movedAt: now, completedAt: null, createdAt: now,
    }
    getDb().tasks.push(task)
    mailTaskAssigned(task)
    commit()
    return json(task, 201)
  }),

  http.patch(`${P}/tasks/:id`, async ({ request, params }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    const task = getDb().tasks.find((t) => t.id === params.id)
    if (!task) return fail(404, 'That task no longer exists.')

    const body = (await request.json()) as Partial<Task>
    if (me.role === 'MEMBER' && Object.keys(body).some((k) => k !== 'columnId')) {
      return fail(403, 'Members can move tasks between columns, but cannot edit task details.')
    }

    const previousAssignee = task.assigneeId
    Object.assign(task, body)

    if (body.columnId) applyColumnChange(task, body.columnId)
    if (body.assigneeId && body.assigneeId !== previousAssignee) mailTaskAssigned(task)
    commit()
    return json(task)
  }),

  http.post(`${P}/tasks/:id/move`, async ({ request, params }) => {
    const me = actor(request)
    if (!me) return fail(401, 'Sign in to continue.')
    const task = getDb().tasks.find((t) => t.id === params.id)
    if (!task) return fail(404, 'That task no longer exists.')
    const { columnId } = (await request.json()) as { columnId: string }
    applyColumnChange(task, columnId)
    commit()
    return json(task)
  }),

  http.get(`${P}/_dev/emails`, () => json(getDb().mail)),

  http.post(`${P}/_dev/run-reminders`, () => {
    const db = getDb()
    const firstColumnIds = new Set(db.columns.filter((c) => c.position === 0).map((c) => c.id))
    const cutoff = Date.now() + STALL_WINDOW_HOURS * 3600_000
    let sent = 0
    for (const task of db.tasks) {
      if (task.completedAt || !task.dueDate || !task.assigneeId) continue
      if (!firstColumnIds.has(task.columnId)) continue
      if (new Date(task.dueDate).getTime() > cutoff) continue
      const key = `${task.id}#${task.dueDate}`
      if (db.remindersSent.includes(key)) continue
      db.remindersSent.push(key)
      mailTaskStalled(task)
      sent++
    }
    commit()
    return json({ sent })
  }),
]