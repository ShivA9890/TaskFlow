import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.tsx'
import RequireAuth from './components/RequireAuth.tsx'
import Login from './pages/Login.tsx'
import RegisterOrg from './pages/RegisterOrg.tsx'
import AcceptInvite from './pages/AcceptInvite.tsx'
import BoardPage from './pages/Board.tsx'
import Completed from './pages/Completed.tsx'
import Settings from './pages/Settings.tsx'
import Members from './pages/admin/Members.tsx'
import Teams from './pages/admin/Teams.tsx'
import Boards from './pages/admin/Boards.tsx'
import MailLog from './pages/admin/MailLog.tsx'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<RegisterOrg />} />
      <Route path="/accept-invite" element={<AcceptInvite />} />

      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<Navigate to="/board" replace />} />
        <Route path="/board" element={<BoardPage />} />
        <Route path="/board/:boardId" element={<BoardPage />} />
        <Route path="/completed" element={<Completed />} />
        <Route path="/settings" element={<Settings />} />
      </Route>

      <Route
        element={
          <RequireAuth adminOnly>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/admin/members" element={<Members />} />
        <Route path="/admin/teams" element={<Teams />} />
        <Route path="/admin/boards" element={<Boards />} />
        <Route path="/admin/mail" element={<MailLog />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}