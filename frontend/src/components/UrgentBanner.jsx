import { Link } from 'react-router-dom';
import { useAsync } from '../hooks/useAsync';
import { NoticeApi } from '../services/api';
import { formatDate } from '../utils/format';

/** Active urgent notices addressed to the signed-in user. Disappears by itself when they expire. */
export default function UrgentBanner() {
  const { data } = useAsync(() => NoticeApi.list({ scope: 'feed', priority: 'URGENT', size: 3 }), []);
  const notices = data?.content || [];
  if (notices.length === 0) return null;

  return (
    <section className="urgent-banner mb-4" aria-label="Urgent notices">
      {notices.map((n) => (
        <div key={n.id} className={`urgent-item ${n.read ? 'is-read' : ''}`}>
          <div className="urgent-text">
            <span className="urgent-tag">Urgent</span>
            <Link to={`/notices/${n.id}`} className="urgent-title">{n.title}</Link>
            {!n.read && <p className="urgent-excerpt">{n.excerpt}</p>}
            <span className="urgent-meta">{n.createdByName}, {formatDate(n.publishDate)}{n.read ? ', read' : ''}</span>
          </div>
          {!n.read && <Link to={`/notices/${n.id}`} className="btn btn-sm btn-danger">Read notice</Link>}
        </div>
      ))}
      {data.totalElements > notices.length && (
        <Link to="/notices?priority=URGENT" className="small">See all {data.totalElements} urgent notices</Link>
      )}
    </section>
  );
}
