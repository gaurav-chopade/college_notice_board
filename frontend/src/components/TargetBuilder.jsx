import { useEffect, useState } from 'react';
import { UserApi } from '../services/api';
import { ROLE_LABEL, TARGET_LABEL } from '../utils/format';

/**
 * Builds the target audience list. Only the target types and roles the backend allows for the
 * signed-in role are offered (from /notices/target-options); the backend re-validates on save.
 */
export default function TargetBuilder({ options, departments, value, onChange }) {
  const fixedDept = options.fixedDepartmentId;
  const blank = () => ({ targetType: options.allowedTypes[0], targetRole: null, departmentId: fixedDept, semester: null, className: '', userId: null, userName: '' });

  const update = (i, patch) => onChange(value.map((t, idx) => (idx === i ? { ...t, ...patch } : t)));
  const remove = (i) => onChange(value.filter((_, idx) => idx !== i));

  return (
    <div className="d-flex flex-column gap-2">
      {value.map((t, i) => (
        <TargetRow key={i} t={t} options={options} departments={departments} fixedDept={fixedDept}
          onChange={(p) => update(i, p)} onRemove={value.length > 1 ? () => remove(i) : null} />
      ))}
      <div><button type="button" className="btn btn-sm btn-outline-primary" onClick={() => onChange([...value, blank()])}>Add another audience</button></div>
    </div>
  );
}

function TargetRow({ t, options, departments, fixedDept, onChange, onRemove }) {
  const needsRole = ['ROLE', 'ROLE_DEPARTMENT'].includes(t.targetType);
  const needsDept = ['DEPARTMENT', 'ROLE_DEPARTMENT', 'SEMESTER', 'CLASS'].includes(t.targetType);
  const needsSem = ['SEMESTER', 'CLASS'].includes(t.targetType);
  const needsClass = t.targetType === 'CLASS';
  const isUser = t.targetType === 'SPECIFIC_USER';
  const roles = options.allowedRoles;

  const setType = (targetType) => onChange({
    targetType, targetRole: ['ROLE', 'ROLE_DEPARTMENT'].includes(targetType) ? roles[0] : null,
    departmentId: fixedDept ?? null, semester: null, className: '', userId: null, userName: '',
  });

  return (
    <div className="target-row">
      <div className="row g-2 align-items-end">
        <div className="col-sm-4">
          <label className="form-label small mb-1">Audience</label>
          <select className="form-select form-select-sm" value={t.targetType} onChange={(e) => setType(e.target.value)}>
            {options.allowedTypes.map((tt) => <option key={tt} value={tt}>{TARGET_LABEL[tt]}</option>)}
          </select>
        </div>
        {needsRole && (
          <div className="col-sm-3">
            <label className="form-label small mb-1">Role</label>
            <select className="form-select form-select-sm" value={t.targetRole || ''} onChange={(e) => onChange({ targetRole: e.target.value })}>
              {roles.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
            </select>
          </div>
        )}
        {needsDept && (
          <div className="col-sm-3">
            <label className="form-label small mb-1">Department</label>
            <select className="form-select form-select-sm" value={t.departmentId || ''} disabled={!!fixedDept}
              onChange={(e) => onChange({ departmentId: Number(e.target.value) || null })}>
              <option value="">Select…</option>
              {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </div>
        )}
        {needsSem && (
          <div className="col-sm-2">
            <label className="form-label small mb-1">Semester</label>
            <select className="form-select form-select-sm" value={t.semester || ''} onChange={(e) => onChange({ semester: Number(e.target.value) || null })}>
              <option value="">–</option>
              {Array.from({ length: 8 }, (_, k) => k + 1).map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
        )}
        {needsClass && (
          <div className="col-sm-2">
            <label className="form-label small mb-1">Class</label>
            <input className="form-control form-control-sm" maxLength={20} value={t.className || ''} onChange={(e) => onChange({ className: e.target.value })} placeholder="A" />
          </div>
        )}
        {isUser && <UserPicker t={t} onChange={onChange} />}
        {onRemove && (
          <div className="col-auto ms-auto">
            <button type="button" className="btn btn-sm btn-link text-danger" onClick={onRemove}>Remove</button>
          </div>
        )}
      </div>
    </div>
  );
}

function UserPicker({ t, onChange }) {
  const [q, setQ] = useState('');
  const [results, setResults] = useState([]);
  useEffect(() => {
    if (q.trim().length < 2) { setResults([]); return; }
    const h = setTimeout(() => UserApi.lookup({ q }).then(setResults).catch(() => setResults([])), 300);
    return () => clearTimeout(h);
  }, [q]);

  if (t.userId) {
    return (
      <div className="col-sm-6 small">
        <span className="fw-semibold">{t.userName}</span>
        <button type="button" className="btn btn-sm btn-link" onClick={() => onChange({ userId: null, userName: '' })}>Change</button>
      </div>
    );
  }
  return (
    <div className="col-sm-6 position-relative">
      <label className="form-label small mb-1">Find a person</label>
      <input className="form-control form-control-sm" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Name or email" />
      {results.length > 0 && (
        <ul className="list-group position-absolute w-100 shadow-sm" style={{ zIndex: 5 }}>
          {results.map((u) => (
            <li key={u.id}>
              <button type="button" className="list-group-item list-group-item-action small"
                onClick={() => { onChange({ userId: u.id, userName: `${u.fullName} (${ROLE_LABEL[u.role]})` }); setQ(''); }}>
                {u.fullName} <span className="text-secondary">· {ROLE_LABEL[u.role]}{u.departmentName ? `, ${u.departmentName}` : ''}{u.semester ? ` sem ${u.semester}${u.className ? u.className : ''}` : ''}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
