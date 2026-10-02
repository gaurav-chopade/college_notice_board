import { useEffect, useRef, useState } from 'react';
import { NoticeApi, errorMessage } from '../services/api';
import { countRole, toTargetRequests } from '../utils/format';

const ROLE_ORDER = ['STUDENT', 'FACULTY', 'HOD', 'PRINCIPAL', 'SUPER_ADMIN'];

/**
 * Live "who will receive this" panel. The backend resolves the unsaved audience with the same
 * rules it applies on save, so the number shown here is the number that gets notified.
 */
export default function AudienceSummary({ targets, onResult }) {
  const [state, setState] = useState({ loading: true, data: null, error: '' });
  const seq = useRef(0);
  const body = JSON.stringify(toTargetRequests(targets));

  useEffect(() => {
    const mine = ++seq.current;
    setState((s) => ({ ...s, loading: true }));
    const h = setTimeout(() => {
      NoticeApi.recipientPreview(JSON.parse(body))
        .then((data) => { if (mine === seq.current) { setState({ loading: false, data, error: '' }); onResult?.(data); } })
        .catch((e) => { if (mine === seq.current) { setState({ loading: false, data: null, error: errorMessage(e) }); onResult?.(null); } });
    }, 400);
    return () => clearTimeout(h);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [body]);

  const { loading, data, error } = state;

  if (error) {
    return (
      <div className="audience-summary incomplete" aria-live="polite">
        <p className="mb-0"><strong>Finish choosing the audience to see who it reaches.</strong> {error}</p>
      </div>
    );
  }

  const breakdown = data ? ROLE_ORDER.filter((r) => data.byRole[r]).map((r) => countRole(r, data.byRole[r])).join(', ') : '';
  return (
    <div className={`audience-summary ${data && data.total === 0 ? 'empty-audience' : ''} ${loading ? 'is-loading' : ''}`} aria-live="polite" aria-busy={loading}>
      <div className="audience-count">
        <span className="num">{data ? data.total.toLocaleString('en-IN') : '…'}</span>
        <span className="lbl">{data?.total === 1 ? 'person will receive this notice' : 'people will receive this notice'}</span>
      </div>
      {data && data.total > 0 && <p className="small mb-2">{breakdown}</p>}
      {data && data.total === 0 && (
        <p className="small mb-2">No active account matches this audience yet. Check the department, semester and class.</p>
      )}
      {data && (
        <ul className="audience-list">
          {data.audience.map((a) => <li key={a}>{a}</li>)}
        </ul>
      )}
      <p className="small text-secondary mb-0">Counts active accounts only, not including you. Recalculated when the notice is published.</p>
    </div>
  );
}
