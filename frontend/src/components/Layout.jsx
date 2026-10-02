import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { NotificationApi, RegistrationApi } from '../services/api';
import { ThemeToggle } from './ThemeControl';
import { ROLE_LABEL } from '../utils/format';

const CREATORS = ['SUPER_ADMIN', 'PRINCIPAL', 'HOD', 'FACULTY'];
const REVIEWERS = CREATORS;
const CLASS_MANAGERS = ['SUPER_ADMIN', 'PRINCIPAL', 'HOD'];

const Icon = ({ d }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
    <path d={d} fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);
const ICONS = {
  home: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  notices: 'M6 3h9l4 4v14H6zM14 3v5h5M9 12h7M9 16h7',
  create: 'M12 5v14M5 12h14',
  saved: 'M7 3h10v18l-5-4-5 4z',
  alerts: 'M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0',
  menu: 'M4 7h16M4 12h16M4 17h16',
};

export default function Layout() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [requests, setRequests] = useState(0);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => { setOpen(false); }, [location.pathname]);
  useEffect(() => {
    const load = () => {
      NotificationApi.unread().then((r) => setUnread(r.count)).catch(() => {});
      if (REVIEWERS.includes(user.role)) RegistrationApi.count().then((r) => setRequests(r.count)).catch(() => {});
    };
    load();
    const t = setInterval(load, 60000);
    return () => clearInterval(t);
  }, [location.pathname]);

  const can = (roles) => roles.includes(user.role);
  return (
    <div className="app-shell">
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="brand">College notice board<small>{ROLE_LABEL[user.role]}{user.departmentName ? `, ${user.departmentName}` : ''}</small></div>
        <nav aria-label="Main">
          <NavLink to="/" end>Dashboard</NavLink>
          <NavLink to="/notices">Notices</NavLink>
          <NavLink to="/saved">Saved notices</NavLink>
          <NavLink to="/public-notices">Public notices</NavLink>
          <NavLink to="/history">Notice history</NavLink>
          {can(CREATORS) && <NavLink to="/my-notices">My notices</NavLink>}
          {can(CREATORS) && <NavLink to="/notices/new">Create notice</NavLink>}
          {can(['HOD']) && <NavLink to="/approvals">Notice approvals</NavLink>}
          {can(REVIEWERS) && <NavLink to="/account-requests">Account requests{requests > 0 ? ` (${requests})` : ''}</NavLink>}
          {can(CLASS_MANAGERS) && <NavLink to="/class-teachers">Class teachers</NavLink>}
          <NavLink to="/notifications">Notifications{unread > 0 ? ` (${unread})` : ''}</NavLink>
          {can(['SUPER_ADMIN']) && (
            <>
              <div className="nav-group">Administration</div>
              <NavLink to="/admin/all-notices">All notices</NavLink>
              <NavLink to="/admin/users">Users</NavLink>
              <NavLink to="/admin/departments">Departments</NavLink>
              <NavLink to="/admin/categories">Categories</NavLink>
              <NavLink to="/admin/audit-logs">Audit logs</NavLink>
            </>
          )}
          <div className="nav-group">Account</div>
          <NavLink to="/profile">Profile</NavLink>
        </nav>
      </aside>
      {open && <div className="backdrop" onClick={() => setOpen(false)} />}
      <div className="main">
        <header className="topbar">
          <span className="ms-auto small text-secondary">{user.fullName}</span>
          <ThemeToggle />
          <button className="btn btn-sm btn-light hide-mobile" onClick={() => navigate('/notifications')}>
            Notifications {unread > 0 && <span className="badge text-bg-success">{unread}</span>}
          </button>
          <button className="btn btn-sm btn-outline-secondary" onClick={() => { logout(); navigate('/'); }}>Sign out</button>
        </header>
        <main className="content"><Outlet /></main>
        <nav className="bottom-nav" aria-label="Quick">
          <NavLink to="/" end><Icon d={ICONS.home} /><span>Home</span></NavLink>
          <NavLink to="/notices" end><Icon d={ICONS.notices} /><span>Notices</span></NavLink>
          {can(CREATORS)
            ? <NavLink to="/notices/new"><Icon d={ICONS.create} /><span>Create</span></NavLink>
            : <NavLink to="/saved"><Icon d={ICONS.saved} /><span>Saved</span></NavLink>}
          <NavLink to="/notifications">
            <span className="bn-icon"><Icon d={ICONS.alerts} />{unread > 0 && <span className="bn-badge">{unread > 99 ? '99+' : unread}</span>}</span>
            <span>Alerts</span><span className="visually-hidden">{unread > 0 ? `, ${unread} unread` : ''}</span>
          </NavLink>
          <button type="button" onClick={() => setOpen(true)} aria-label="More pages"><Icon d={ICONS.menu} /><span>More</span></button>
        </nav>
      </div>
    </div>
  );
}
