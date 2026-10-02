import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import PublicLayout from './components/PublicLayout';
import { Loading } from './components/States';
import { useAuth } from './context/AuthContext';
import { ForgotPassword, Login, Register, ResetPassword } from './pages/AuthPages';
import Dashboard from './pages/Dashboard';
import NoticeList from './pages/NoticeList';
import NoticeDetails from './pages/NoticeDetails';
import NoticeForm from './pages/NoticeForm';
import Notifications from './pages/Notifications';
import Users from './pages/admin/Users';
import Departments from './pages/admin/Departments';
import Categories from './pages/admin/Categories';
import AuditLogs from './pages/admin/AuditLogs';
import Profile from './pages/Profile';
import AccountRequests from './pages/AccountRequests';
import ClassTeachers from './pages/ClassTeachers';
import Landing from './pages/public/Landing';
import PublicNotices from './pages/public/PublicNotices';
import PublicNoticeDetail from './pages/public/PublicNoticeDetail';
import InstitutionPage from './pages/public/InstitutionPage';
import { About, Admissions, Help } from './pages/public/InfoPages';

const CREATORS = ['SUPER_ADMIN', 'PRINCIPAL', 'HOD', 'FACULTY'];
const ADMIN = ['SUPER_ADMIN'];

/** Visitors get the public site's frame; signed-in members get the portal frame around the same pages. */
function RootFrame() {
  const { user, loading } = useAuth();
  if (loading) return <Loading />;
  return user ? <Layout /> : <PublicLayout />;
}

/** "/" is the public home page for visitors and the dashboard for members. */
function Home() {
  const { user } = useAuth();
  return user ? <Dashboard /> : <Landing />;
}

const guard = (element, roles) => <ProtectedRoute roles={roles}>{element}</ProtectedRoute>;

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/" element={<RootFrame />}>
        <Route index element={<Home />} />

        {/* Public: no sign-in needed. The server returns only public, live notices. */}
        <Route path="public-notices" element={<PublicNotices />} />
        <Route path="public-notices/:id" element={<PublicNoticeDetail />} />
        <Route path="admissions" element={<Admissions />} />
        <Route path="engineering" element={<InstitutionPage key="ENGINEERING" type="ENGINEERING" />} />
        <Route path="polytechnic" element={<InstitutionPage key="POLYTECHNIC" type="POLYTECHNIC" />} />
        <Route path="about" element={<About />} />
        <Route path="help" element={<Help />} />

        {/* Portal: members only (and the backend checks every request again). */}
        <Route path="notices" element={guard(<NoticeList scope="feed" title="Notices" />)} />
        <Route path="saved" element={guard(<NoticeList scope="saved" title="Saved notices" />)} />
        <Route path="history" element={guard(<NoticeList scope="history" title="Notice history" />)} />
        <Route path="my-notices" element={guard(<NoticeList scope="mine" title="My notices" showStatus />, CREATORS)} />
        <Route path="approvals" element={guard(<NoticeList scope="approvals" title="Teacher notices for approval" showStatus />, ['HOD'])} />
        <Route path="notices/new" element={guard(<NoticeForm />, CREATORS)} />
        <Route path="notices/:id/edit" element={guard(<NoticeForm />, CREATORS)} />
        <Route path="notices/:id" element={guard(<NoticeDetails />)} />
        <Route path="notifications" element={guard(<Notifications />)} />
        <Route path="account-requests" element={guard(<AccountRequests />, CREATORS)} />
        <Route path="class-teachers" element={guard(<ClassTeachers />, ['SUPER_ADMIN', 'PRINCIPAL', 'HOD'])} />
        <Route path="profile" element={guard(<Profile />)} />
        <Route path="admin/all-notices" element={guard(<NoticeList scope="all" title="All notices" showStatus />, ADMIN)} />
        <Route path="admin/users" element={guard(<Users />, ADMIN)} />
        <Route path="admin/departments" element={guard(<Departments />, ADMIN)} />
        <Route path="admin/categories" element={guard(<Categories />, ADMIN)} />
        <Route path="admin/audit-logs" element={guard(<AuditLogs />, ADMIN)} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
