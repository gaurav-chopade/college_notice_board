import { useEffect, useMemo, useState } from 'react';
import { Empty, ErrorState, SkeletonRows } from '../components/States';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { ClassTeacherApi, DepartmentApi, errorMessage } from '../services/api';

/**
 * Who is class teacher of each class. Student account requests go to that teacher;
 * classes without one send requests to the HOD.
 */
export default function ClassTeachers() {
  const { user } = useAuth();
  const toast = useToast();
  const isHod = user.role === 'HOD';
  const [departments, setDepartments] = useState([]);
  const [deptId, setDeptId] = useState(isHod ? user.departmentId : '');
  const [state, setState] = useState({ loading: false, error: '', assignments: [], options: null });
  const [adding, setAdding] = useState({ semester: '', className: '', teacherId: '' });
  const [saving, setSaving] = useState('');

  useEffect(() => { if (!isHod) DepartmentApi.list().then(setDepartments).catch(() => {}); }, [isHod]);

  const load = () => {
    if (!deptId) return;
    setState((s) => ({ ...s, loading: true, error: '' }));
    Promise.all([ClassTeacherApi.list(deptId), ClassTeacherApi.options(deptId)])
      .then(([assignments, options]) => setState({ loading: false, error: '', assignments, options }))
      .catch((e) => setState((s) => ({ ...s, loading: false, error: errorMessage(e) })));
  };
  useEffect(load, [deptId]);

  // One row per class: classes students are in, plus classes that only have an assignment.
  const rows = useMemo(() => {
    const map = new Map();
    (state.options?.classes || []).forEach((c) => map.set(`${c.semester}|${c.className}`, { semester: c.semester, className: c.className, students: c.students, assignment: null }));
    state.assignments.forEach((a) => {
      const key = `${a.semester}|${a.className}`;
      map.set(key, { ...(map.get(key) || { semester: a.semester, className: a.className, students: a.students }), assignment: a });
    });
    return [...map.values()].sort((x, y) => x.semester - y.semester || x.className.localeCompare(y.className));
  }, [state]);

  const assign = async (semester, className, teacherId) => {
    const key = `${semester}|${className}`;
    setSaving(key);
    try {
      const res = await ClassTeacherApi.assign({ departmentId: Number(deptId), semester: Number(semester), className, teacherId: Number(teacherId) });
      toast(`${res.teacherName} is now class teacher of semester ${res.semester} class ${res.className}`);
      load();
      return true;
    } catch (e) { toast(errorMessage(e), 'danger'); return false; } finally { setSaving(''); }
  };
  const remove = async (row) => {
    setSaving(`${row.semester}|${row.className}`);
    try { await ClassTeacherApi.remove(row.assignment.id); toast('Class teacher removed. New requests for this class go to the HOD.'); load(); }
    catch (e) { toast(errorMessage(e), 'danger'); } finally { setSaving(''); }
  };
  const addClass = async (e) => {
    e.preventDefault();
    if (await assign(adding.semester, adding.className.trim().toUpperCase(), adding.teacherId)) setAdding({ semester: '', className: '', teacherId: '' });
  };

  const teachers = state.options?.teachers || [];
  const unassigned = rows.filter((r) => !r.assignment).length;

  return (
    <>
      <h1 className="mb-1">Class teachers</h1>
      <p className="text-secondary">New student requests go to their class teacher. Classes without one send requests to the HOD.</p>

      {!isHod && (
        <div className="panel mb-3" style={{ maxWidth: 420 }}>
          <label className="form-label" htmlFor="dept">Department</label>
          <select id="dept" className="form-select" value={deptId} onChange={(e) => setDeptId(e.target.value)}>
            <option value="">Select…</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </div>
      )}

      {!deptId && !isHod && <Empty title="Choose a department">Its classes and class teachers will appear here.</Empty>}
      {state.error && <ErrorState message={state.error} onRetry={load} />}
      {state.loading && !state.options && <SkeletonRows count={4} />}

      {state.options && (
        <>
          {teachers.length === 0 && <div className="alert alert-warning py-2">This department has no active teachers yet. Approve teacher requests first.</div>}
          {unassigned > 0 && <p className="small"><strong>{unassigned}</strong> {unassigned === 1 ? 'class has' : 'classes have'} no class teacher.</p>}
          {rows.length === 0
            ? <Empty title="No classes yet">Classes appear once students register. You can also add one below.</Empty>
            : (
              <div className="panel p-0 table-responsive mb-3">
                <table className="table mb-0 align-middle">
                  <thead><tr><th>Semester</th><th>Class</th><th>Students</th><th>Class teacher</th><th /></tr></thead>
                  <tbody>
                    {rows.map((r) => {
                      const key = `${r.semester}|${r.className}`;
                      return (
                        <tr key={key} className={r.assignment ? '' : 'row-attention'}>
                          <td>{r.semester}</td>
                          <td>{r.className}</td>
                          <td>{r.students}</td>
                          <td style={{ minWidth: 220 }}>
                            <select className="form-select form-select-sm" aria-label={`Class teacher of semester ${r.semester} class ${r.className}`}
                              value={r.assignment?.teacherId || ''} disabled={saving === key || teachers.length === 0}
                              onChange={(e) => e.target.value && assign(r.semester, r.className, e.target.value)}>
                              <option value="">{r.assignment ? '' : 'None: requests go to the HOD'}</option>
                              {teachers.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
                              {r.assignment && !r.assignment.teacherActive && <option value={r.assignment.teacherId}>{r.assignment.teacherName} (inactive)</option>}
                            </select>
                          </td>
                          <td className="text-end">
                            {r.assignment && <button className="btn btn-sm btn-link text-danger" onClick={() => remove(r)} disabled={saving === key}>Remove</button>}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

          <form className="panel" onSubmit={addClass}>
            <h2 className="h6">Assign a class that is not listed</h2>
            <div className="row g-2 align-items-end">
              <div className="col-4 col-md-2"><label className="form-label small" htmlFor="add-sem">Semester</label>
                <select id="add-sem" className="form-select form-select-sm" required value={adding.semester} onChange={(e) => setAdding({ ...adding, semester: e.target.value })}>
                  <option value="">–</option>{[1, 2, 3, 4, 5, 6, 7, 8].map((s) => <option key={s} value={s}>{s}</option>)}
                </select></div>
              <div className="col-4 col-md-2"><label className="form-label small" htmlFor="add-cls">Class</label>
                <input id="add-cls" className="form-control form-control-sm" required maxLength={20} placeholder="A" value={adding.className} onChange={(e) => setAdding({ ...adding, className: e.target.value })} /></div>
              <div className="col-12 col-md-5"><label className="form-label small" htmlFor="add-t">Class teacher</label>
                <select id="add-t" className="form-select form-select-sm" required value={adding.teacherId} onChange={(e) => setAdding({ ...adding, teacherId: e.target.value })}>
                  <option value="">Select…</option>{teachers.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
                </select></div>
              <div className="col-auto"><button className="btn btn-sm btn-primary" disabled={!!saving || teachers.length === 0}>Assign</button></div>
            </div>
          </form>
        </>
      )}
    </>
  );
}
