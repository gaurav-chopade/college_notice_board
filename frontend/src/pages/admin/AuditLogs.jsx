import { useState } from 'react';
import Pagination from '../../components/Pagination';
import { ErrorState, Loading } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import { AuditApi } from '../../services/api';
import { formatDate } from '../../utils/format';

const ACTIONS = ['USER_CREATED', 'USER_UPDATED', 'USER_ACTIVATED', 'USER_DEACTIVATED', 'USER_REGISTERED', 'NOTICE_CREATED', 'NOTICE_EDITED',
  'NOTICE_SUBMITTED', 'NOTICE_APPROVED', 'NOTICE_REJECTED', 'NOTICE_PUBLISHED', 'NOTICE_SCHEDULED', 'NOTICE_EXPIRED', 'NOTICE_ARCHIVED',
  'NOTICE_DELETED', 'NOTICE_ACKNOWLEDGED', 'ATTACHMENT_UPLOADED', 'ATTACHMENT_DELETED', 'DEPARTMENT_CHANGED', 'CATEGORY_CHANGED', 'AI_DRAFT_CREATED'];

const pretty = (a) => a.charAt(0) + a.slice(1).toLowerCase().replaceAll('_', ' ');

export default function AuditLogs() {
  const [page, setPage] = useState(0);
  const [action, setAction] = useState('');
  const { data, loading, error, reload } = useAsync(() => AuditApi.list({ page, size: 25, ...(action ? { action } : {}) }), [page, action]);
  return (
    <>
      <div className="d-flex align-items-center mb-3 gap-2">
        <h1 className="mb-0">Audit log</h1>
        <select className="form-select ms-auto" style={{ maxWidth: 260 }} value={action} onChange={(e) => { setPage(0); setAction(e.target.value); }} aria-label="Action">
          <option value="">All actions</option>{ACTIONS.map((a) => <option key={a} value={a}>{pretty(a)}</option>)}
        </select>
      </div>
      {loading && <Loading />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && (
        <div className="panel p-0 table-responsive">
          <table className="table table-sm mb-0">
            <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Item</th><th>Details</th></tr></thead>
            <tbody>
              {data.content.map((l) => (
                <tr key={l.id}>
                  <td className="text-nowrap">{formatDate(l.timestamp)}</td>
                  <td>{l.userEmail}</td>
                  <td>{pretty(l.action)}</td>
                  <td>{l.entityType} #{l.entityId}</td>
                  <td className="small">{l.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={data} onChange={setPage} />
    </>
  );
}
