import { useEffect, useState } from 'react';
import ConfirmDialog from '../../components/ConfirmDialog';
import Pagination from '../../components/Pagination';
import { ErrorState, Loading } from '../../components/States';
import { useToast } from '../../context/ToastContext';
import { useAsync } from '../../hooks/useAsync';
import { DepartmentApi, UserApi, errorMessage } from '../../services/api';
import { ACCOUNT_STATUS_LABEL, ROLE_LABEL } from '../../utils/format';

const EMPTY = { fullName: '', email: '', password: '', role: 'STUDENT', departmentId: '', semester: '', className: '', phone: '', active: true };

export default function Users() {
  const toast = useToast();
  const [filters, setFilters] = useState({ q: '', role: '', departmentId: '', status: '' });
  const [suspending, setSuspending] = useState(null);
  const [query, setQuery] = useState(filters);
  const [page, setPage] = useState(0);
  const [departments, setDepartments] = useState([]);
  const [editing, setEditing] = useState(null);
  useEffect(() => { DepartmentApi.list().then(setDepartments).catch(() => {}); }, []);

  const { data, loading, error, reload } = useAsync(() => {
    const params = { page, size: 15 };
    Object.entries(query).forEach(([k, v]) => { if (v !== '') params[k] = v; });
    return UserApi.list(params);
  }, [page, query]);

  const toggle = async (u) => {
    try { await (u.active ? UserApi.deactivate(u.id) : UserApi.activate(u.id)); toast(u.active ? 'Account closed' : 'Account activated'); reload(); }
    catch (e) { toast(errorMessage(e), 'danger'); }
  };
  const suspend = async (reason) => {
    try { await UserApi.suspend(suspending.id, reason); toast(`${suspending.fullName} suspended`); setSuspending(null); reload(); }
    catch (e) { toast(errorMessage(e), 'danger'); }
  };
  const ACTIVATE_LABEL = { PENDING: 'Approve', REJECTED: 'Approve', SUSPENDED: 'Lift suspension', DEACTIVATED: 'Reopen' };

  return (
    <>
      <div className="d-flex align-items-center mb-3">
        <h1 className="mb-0">Users</h1>
        <button className="btn btn-primary ms-auto" onClick={() => setEditing(EMPTY)}>Add user</button>
      </div>
      <form className="panel mb-3 filters" onSubmit={(e) => { e.preventDefault(); setPage(0); setQuery(filters); }}>
        <input className="form-control" placeholder="Name or email" value={filters.q} onChange={(e) => setFilters({ ...filters, q: e.target.value })} />
        <select className="form-select" value={filters.role} onChange={(e) => setFilters({ ...filters, role: e.target.value })}>
          <option value="">All roles</option>{Object.entries(ROLE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select className="form-select" value={filters.departmentId} onChange={(e) => setFilters({ ...filters, departmentId: e.target.value })}>
          <option value="">All departments</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
        </select>
        <select className="form-select" value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })} aria-label="Account status">
          <option value="">Any status</option>{Object.entries(ACCOUNT_STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <button className="btn btn-outline-primary" style={{ flex: '0 0 auto' }}>Search</button>
      </form>
      {loading && <Loading />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && (
        <div className="panel p-0 table-responsive">
          <table className="table table-hover mb-0 align-middle">
            <thead><tr><th>Name</th><th>Role</th><th>Department</th><th>Sem / class</th><th>Status</th><th /></tr></thead>
            <tbody>
              {data.content.map((u) => (
                <tr key={u.id}>
                  <td>{u.fullName}<div className="small text-secondary">{u.email}</div></td>
                  <td>{ROLE_LABEL[u.role]}</td>
                  <td>{u.departmentName || '—'}</td>
                  <td>{u.semester ? `${u.semester} / ${u.className}` : '—'}</td>
                  <td><span className={`acct-chip ${u.status}`}>{ACCOUNT_STATUS_LABEL[u.status]}</span>
                    {u.statusReason && <div className="small text-secondary">{u.statusReason}</div>}</td>
                  <td className="text-end text-nowrap">
                    <button className="btn btn-sm btn-light" onClick={() => setEditing({ ...u, password: '', departmentId: u.departmentId || '', semester: u.semester || '', className: u.className || '', phone: u.phone || '' })}>Edit</button>{' '}
                    {u.active && <><button className="btn btn-sm btn-outline-secondary" onClick={() => setSuspending(u)}>Suspend</button>{' '}</>}
                    <button className="btn btn-sm btn-outline-secondary" onClick={() => toggle(u)}>{u.active ? 'Close account' : ACTIVATE_LABEL[u.status] || 'Activate'}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={data} onChange={setPage} />
      <ConfirmDialog open={!!suspending} title={suspending ? `Suspend ${suspending.fullName}` : ''}
        body="They are signed out at once and see this reason when they try to sign in."
        inputLabel="Reason" confirmLabel="Suspend" variant="danger" onClose={() => setSuspending(null)} onConfirm={suspend} />
      {editing && <UserModal user={editing} departments={departments} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); }} />}
    </>
  );
}

function UserModal({ user, departments, onClose, onSaved }) {
  const toast = useToast();
  const [f, setF] = useState(user);
  const [error, setError] = useState('');
  const isNew = !user.id;
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const isStudent = f.role === 'STUDENT';
  const needsDept = ['HOD', 'FACULTY', 'STUDENT'].includes(f.role);

  const save = async (e) => {
    e.preventDefault();
    const body = {
      fullName: f.fullName, email: f.email, role: f.role, phone: f.phone || null, active: f.active,
      departmentId: Number(f.departmentId) || null, semester: isStudent ? Number(f.semester) || null : null,
      className: isStudent ? f.className : null,
    };
    try {
      if (isNew) await UserApi.create({ ...body, password: f.password });
      else await UserApi.update(user.id, { ...body, newPassword: f.password || null });
      toast(isNew ? 'User created' : 'User saved');
      onSaved();
    } catch (err) { setError(errorMessage(err)); }
  };

  return (
    <>
      <div className="modal d-block" role="dialog" aria-modal="true">
        <div className="modal-dialog modal-dialog-centered">
          <form className="modal-content" onSubmit={save}>
            <div className="modal-header"><h2 className="modal-title fs-5">{isNew ? 'Add user' : 'Edit user'}</h2><button type="button" className="btn-close" onClick={onClose} aria-label="Close" /></div>
            <div className="modal-body">
              {error && <div className="alert alert-danger py-2">{error}</div>}
              <label className="form-label">Full name</label><input className="form-control mb-2" required value={f.fullName} onChange={set('fullName')} />
              <label className="form-label">Email</label><input type="email" className="form-control mb-2" required value={f.email} onChange={set('email')} />
              <label className="form-label">{isNew ? 'Password' : 'New password (optional)'}</label>
              <input type="password" className="form-control mb-2" required={isNew} value={f.password} onChange={set('password')} />
              <div className="row g-2 mb-2">
                <div className="col"><label className="form-label">Role</label>
                  <select className="form-select" value={f.role} onChange={set('role')}>{Object.entries(ROLE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
                <div className="col"><label className="form-label">Department</label>
                  <select className="form-select" required={needsDept} value={f.departmentId} onChange={set('departmentId')}>
                    <option value="">None</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
              </div>
              {isStudent && (
                <div className="row g-2 mb-2">
                  <div className="col"><label className="form-label">Semester</label><input type="number" min={1} max={12} className="form-control" required value={f.semester} onChange={set('semester')} /></div>
                  <div className="col"><label className="form-label">Class</label><input className="form-control" required value={f.className} onChange={set('className')} /></div>
                </div>
              )}
              <label className="form-label">Phone</label><input className="form-control mb-2" value={f.phone} onChange={set('phone')} />
              <div className="form-check"><input id="act" type="checkbox" className="form-check-input" checked={f.active} onChange={set('active')} /><label htmlFor="act" className="form-check-label">Active</label></div>
            </div>
            <div className="modal-footer"><button type="button" className="btn btn-light" onClick={onClose}>Cancel</button><button className="btn btn-primary">Save</button></div>
          </form>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  );
}
