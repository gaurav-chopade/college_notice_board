import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Pagination from '../../components/Pagination';
import PublicNoticeCard from '../../components/PublicNoticeCard';
import { Empty, ErrorState, SkeletonCards } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import { usePublicCategories } from '../../hooks/usePublicData';
import { PublicApi } from '../../services/api';

const WINGS = [['', 'All'], ['ENGINEERING', 'Engineering'], ['POLYTECHNIC', 'Polytechnic']];
const KEYS = ['q', 'institution', 'categoryId', 'priority', 'fromDate', 'toDate'];

/** Public notices with search and filters. Filters live in the URL so a filtered view can be shared. */
export default function PublicNotices({ fixedCategoryNames, title = 'Public notices', intro }) {
  const [params, setParams] = useSearchParams();
  const categories = usePublicCategories();
  const [q, setQ] = useState(params.get('q') || '');
  const page = Number(params.get('page') || 0);
  const query = Object.fromEntries(KEYS.map((k) => [k, params.get(k) || '']));

  // Admissions page: limit to its categories unless the visitor picked one.
  const fixed = (fixedCategoryNames || []).map((n) => categories?.find((c) => c.name.toLowerCase() === n.toLowerCase())).filter(Boolean);
  const effectiveCategory = query.categoryId || (fixed.length === 1 ? String(fixed[0].id) : '');
  const waitForCategories = fixedCategoryNames && !categories;

  const { data, loading, error, reload } = useAsync(() => {
    if (waitForCategories) return new Promise(() => {});
    const p = { page, size: 12 };
    KEYS.forEach((k) => { if (query[k]) p[k] = query[k]; });
    if (effectiveCategory) p.categoryId = effectiveCategory;
    else if (fixedCategoryNames && fixed.length === 0) p.q = query.q || fixedCategoryNames[0];
    return PublicApi.notices(p);
  }, [params.toString(), effectiveCategory, waitForCategories]);

  useEffect(() => { setQ(params.get('q') || ''); }, [params]);

  const set = (k, v) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v); else next.delete(k);
    next.delete('page');
    setParams(next, { replace: true });
  };
  const clear = () => setParams(new URLSearchParams(), { replace: true });
  const filtered = KEYS.some((k) => query[k]);

  return (
    <div className="pub-wrap pub-page">
      <h1 className="pub-page-title">{title}</h1>
      {intro && <p className="pub-sub">{intro}</p>}

      <form className="pub-search" role="search" onSubmit={(e) => { e.preventDefault(); set('q', q.trim()); }}>
        <label className="visually-hidden" htmlFor="pq">Search notices</label>
        <input id="pq" type="search" className="form-control form-control-lg" placeholder="Search admission, scholarship, schedule…" value={q} onChange={(e) => setQ(e.target.value)} />
        <button className="btn btn-primary btn-lg">Search</button>
      </form>

      <div className="pub-filters">
        <div className="seg" role="group" aria-label="Institution">
          {WINGS.map(([v, label]) => (
            <button key={label} type="button" aria-pressed={query.institution === v} onClick={() => set('institution', v)}>{label}</button>
          ))}
        </div>
        <select className="form-select" aria-label="Category" value={effectiveCategory} onChange={(e) => set('categoryId', e.target.value)}>
          <option value="">{fixedCategoryNames ? 'All admission categories' : 'Any category'}</option>
          {(categories || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select className="form-select" aria-label="Priority" value={query.priority} onChange={(e) => set('priority', e.target.value)}>
          <option value="">Any priority</option><option value="URGENT">Urgent</option><option value="IMPORTANT">Important</option><option value="NORMAL">Normal</option>
        </select>
        <label className="pub-date"><span>From</span><input type="date" className="form-control" value={query.fromDate} onChange={(e) => set('fromDate', e.target.value)} /></label>
        <label className="pub-date"><span>To</span><input type="date" className="form-control" value={query.toDate} onChange={(e) => set('toDate', e.target.value)} /></label>
      </div>

      {error && <ErrorState message={error} onRetry={reload} />}
      {(loading || waitForCategories) && <SkeletonCards />}
      {!loading && data && data.content.length === 0 && (
        <Empty title={filtered ? 'No notices match these filters' : 'No public notices right now'}
          action={filtered && <button className="btn btn-sm btn-outline-primary" onClick={clear}>Clear filters</button>}>
          {filtered ? 'Try a different search or fewer filters.' : 'New announcements appear here as soon as they are published.'}
        </Empty>
      )}
      {!loading && data && data.content.length > 0 && (
        <>
          <p className="small text-secondary" aria-live="polite">{data.totalElements} notice{data.totalElements === 1 ? '' : 's'}</p>
          <div className="pub-grid">{data.content.map((n) => <PublicNoticeCard key={n.id} notice={n} />)}</div>
        </>
      )}
      <Pagination page={data} onChange={(p) => { const next = new URLSearchParams(params); next.set('page', p); setParams(next); }} />
    </div>
  );
}
