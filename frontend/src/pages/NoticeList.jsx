import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import NoticeCard from '../components/NoticeCard';
import Pagination from '../components/Pagination';
import { Empty, ErrorState, SkeletonCards } from '../components/States';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { CategoryApi, DepartmentApi, NoticeApi } from '../services/api';
import { STATUS_LABEL } from '../utils/format';

const EMPTY_TEXT = {
  feed: 'No current notices are addressed to you.',
  history: 'No past notices yet.',
  mine: 'You have not created any notices yet.',
  approvals: 'No faculty notices match this filter.',
  all: 'No notices match these filters.',
  saved: 'Use Save on any notice to keep it here, for example an exam timetable or a placement deadline.',
};
const BLANK = { q: '', categoryId: '', priority: '', departmentId: '', semester: '', status: '', unreadOnly: false };

export default function NoticeList({ scope, title, showStatus }) {
  const { user } = useAuth();
  const [params] = useSearchParams();
  // Links such as /notices?priority=URGENT open the list pre-filtered.
  const [filters, setFilters] = useState({ q: '', categoryId: params.get('categoryId') || '', priority: params.get('priority') || '', departmentId: '', semester: '', status: '', unreadOnly: false });
  const [query, setQuery] = useState(filters);
  const [page, setPage] = useState(0);
  const [categories, setCategories] = useState([]);
  const [departments, setDepartments] = useState([]);

  useEffect(() => {
    CategoryApi.list().then(setCategories).catch(() => {});
    DepartmentApi.list().then(setDepartments).catch(() => {});
  }, []);
  useEffect(() => { setPage(0); }, [scope]);

  const { data, loading, error, reload } = useAsync(() => {
    const params = { scope, page, size: 12 };
    Object.entries(query).forEach(([k, v]) => { if (v !== '' && v !== false) params[k] = v; });
    return NoticeApi.list(params);
  }, [scope, page, query]);

  const apply = (e) => { e.preventDefault(); setPage(0); setQuery(filters); };
  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const statusFilter = ['mine', 'approvals', 'all'].includes(scope);
  const filtered = Object.entries(query).some(([k, v]) => v !== BLANK[k]);
  const clear = () => { setFilters(BLANK); setQuery(BLANK); setPage(0); };

  return (
    <>
      <h1 className="mb-3">{title}</h1>
      <form className="panel mb-3" onSubmit={apply} role="search">
        <div className="filters">
          <input className="form-control" placeholder="Search title or text" value={filters.q} onChange={set('q')} aria-label="Search" style={{ flexBasis: 240 }} />
          <select className="form-select" value={filters.categoryId} onChange={set('categoryId')} aria-label="Category">
            <option value="">All categories</option>{categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <select className="form-select" value={filters.priority} onChange={set('priority')} aria-label="Priority">
            <option value="">Any priority</option><option value="URGENT">Urgent</option><option value="IMPORTANT">Important</option><option value="NORMAL">Normal</option>
          </select>
          {user.role !== 'STUDENT' && (
            <select className="form-select" value={filters.departmentId} onChange={set('departmentId')} aria-label="Department">
              <option value="">All departments</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          )}
          <select className="form-select" value={filters.semester} onChange={set('semester')} aria-label="Semester">
            <option value="">Any semester</option>{[1, 2, 3, 4, 5, 6, 7, 8].map((s) => <option key={s} value={s}>Semester {s}</option>)}
          </select>
          {statusFilter && (
            <select className="form-select" value={filters.status} onChange={set('status')} aria-label="Status">
              <option value="">{scope === 'approvals' ? 'Pending approval' : 'Any status'}</option>
              {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          )}
        </div>
        <div className="d-flex align-items-center mt-2 gap-3">
          {(scope === 'feed' || scope === 'history') && (
            <div className="form-check mb-0">
              <input id="unread" className="form-check-input" type="checkbox" checked={filters.unreadOnly} onChange={set('unreadOnly')} />
              <label htmlFor="unread" className="form-check-label">Unread only</label>
            </div>
          )}
          <button className="btn btn-primary btn-sm ms-auto">Apply filters</button>
        </div>
      </form>

      {loading && <SkeletonCards />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {!loading && data && data.content.length === 0 && (filtered
        ? <Empty title="No notices match these filters" action={<button type="button" className="btn btn-sm btn-outline-primary" onClick={clear}>Clear filters</button>}>Try fewer filters or a different search.</Empty>
        : <Empty title={scope === 'saved' ? 'Nothing saved yet' : 'No notices'}
            action={scope === 'saved' ? <Link className="btn btn-sm btn-outline-primary" to="/notices">Browse notices</Link>
              : scope === 'mine' ? <Link className="btn btn-sm btn-primary" to="/notices/new">Create a notice</Link> : null}>
            {EMPTY_TEXT[scope]}
          </Empty>)}
      {!loading && data && data.content.length > 0 && (
        <div className="notice-grid">
          {data.content.map((n) => (
            <NoticeCard key={n.id} notice={n} showStatus={showStatus}
              onSavedChange={scope === 'saved' ? (isSaved) => { if (!isSaved) reload(); } : undefined} />
          ))}
        </div>
      )}
      <Pagination page={data} onChange={setPage} />
    </>
  );
}
