import { Link } from 'react-router-dom';
import NoticeCard from '../components/NoticeCard';
import UrgentBanner from '../components/UrgentBanner';
import { Empty, ErrorState, SkeletonDashboard } from '../components/States';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { DashboardApi } from '../services/api';
import { dashboardLabels } from '../utils/format';

const RECENT_TITLE = {
  SUPER_ADMIN: 'Latest notices', PRINCIPAL: 'Your recent notices', HOD: 'Waiting for your approval',
  FACULTY: 'Your recent notices', STUDENT: 'Latest notices for you',
};
const ATTENTION = ['pendingApprovals', 'unread', 'pendingApproval', 'rejected'];

export default function Dashboard() {
  const { user } = useAuth();
  const { data, loading, error, reload } = useAsync(() => DashboardApi.get(user.role), [user.role]);
  if (loading) return <SkeletonDashboard />;
  if (error) return <ErrorState message={error} onRetry={reload} />;
  const canCreate = user.role !== 'STUDENT';

  return (
    <>
      <UrgentBanner />
      <div className="d-flex align-items-center mb-3 gap-2 flex-wrap">
        <h1 className="mb-0">Good to see you, {user.fullName.split(' ')[0]}</h1>
        {canCreate && <Link to="/notices/new" className="btn btn-primary ms-auto">Create notice</Link>}
      </div>

      <section className="stat-row mb-4" aria-label="Summary">
        {Object.entries(data.counts).map(([k, v]) => (
          <div key={k} className={`stat ${ATTENTION.includes(k) && v > 0 ? 'attention' : ''}`}>
            <div className="num">{v}</div><div className="lbl">{dashboardLabels[k] || k}</div>
          </div>
        ))}
      </section>

      <div className="row g-4">
        <div className={data.readStatistics.length || Object.keys(data.breakdown).length ? 'col-lg-8' : 'col-12'}>
          <h2 className="h5 mb-3">{RECENT_TITLE[user.role]}</h2>
          {data.recentNotices.length === 0
            ? <Empty title="Nothing here yet">{user.role === 'HOD' ? 'No faculty notices are waiting for approval.' : 'New notices will appear here.'}</Empty>
            : <div className="notice-grid">{data.recentNotices.map((n) => <NoticeCard key={n.id} notice={n} showStatus={user.role !== 'STUDENT'} />)}</div>}
        </div>
        {(data.readStatistics.length > 0 || Object.keys(data.breakdown).length > 0) && (
          <div className="col-lg-4 d-flex flex-column gap-3">
            {data.readStatistics.length > 0 && (
              <div className="panel">
                <h2 className="h6">Who has read your notices</h2>
                {data.readStatistics.map((s) => (
                  <div key={s.noticeId} className="mb-2">
                    <Link to={`/notices/${s.noticeId}`} className="small fw-semibold">{s.title}</Link>
                    <div className="progress my-1" style={{ height: 6 }} role="progressbar" aria-valuenow={s.read} aria-valuemax={s.totalRecipients}>
                      <div className="progress-bar bg-success" style={{ width: `${s.totalRecipients ? (100 * s.read) / s.totalRecipients : 0}%` }} />
                    </div>
                    <div className="small text-secondary">{s.read} of {s.totalRecipients} read · {s.acknowledged} acknowledged</div>
                  </div>
                ))}
              </div>
            )}
            {Object.keys(data.breakdown).length > 0 && (
              <div className="panel">
                <h2 className="h6">Notices by department</h2>
                <table className="table table-sm mb-0"><tbody>
                  {Object.entries(data.breakdown).map(([k, v]) => <tr key={k}><td>{k}</td><td className="text-end">{v}</td></tr>)}
                </tbody></table>
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
}
