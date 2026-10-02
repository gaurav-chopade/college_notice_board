import { Link } from 'react-router-dom';
import SaveButton from './SaveButton';
import { INSTITUTION_LABEL, STATUS_LABEL, formatDate } from '../utils/format';

const PRIORITY = { URGENT: 'Urgent', IMPORTANT: 'Important', NORMAL: 'Normal' };

const SAVEABLE = ['PUBLISHED', 'EXPIRED'];

export default function NoticeCard({ notice, showStatus, onSavedChange }) {
  const n = notice;
  return (
    <article className={`notice-card prio-${n.priority} ${n.read ? '' : 'unread'}`} aria-label={n.read ? n.title : `${n.title} (unread)`}>
      <div className="d-flex gap-2 align-items-center">
        <span className={`prio-label ${n.priority}`}>{PRIORITY[n.priority]}</span>
        <span className="text-secondary small">{n.categoryName}</span>
        {n.visibility === 'PUBLIC' && <span className={`scope-chip ${n.institutionScope}`}>Public · {INSTITUTION_LABEL[n.institutionScope]}</span>}
        {showStatus && <span className={`status-chip ${n.status} ms-auto`}>{STATUS_LABEL[n.status]}</span>}
      </div>
      <h3><Link to={`/notices/${n.id}`}>{n.title}</Link></h3>
      <p className="excerpt">{n.excerpt}</p>
      <div className="notice-meta">
        <span>For: {n.targetSummary || 'Public website'}{n.visibility === 'PUBLIC' && n.targetSummary ? ' + public website' : ''}</span>
        <span>{formatDate(n.publishDate || n.createdAt, false)}</span>
        <span>{n.createdByName}</span>
        {n.attachmentCount > 0 && <span>{n.attachmentCount} attachment{n.attachmentCount > 1 ? 's' : ''}</span>}
        {n.acknowledgementRequired && <span>Needs acknowledgement</span>}
      </div>
      {SAVEABLE.includes(n.status) && (
        <div className="card-actions"><SaveButton noticeId={n.id} saved={n.saved} onChange={onSavedChange} /></div>
      )}
    </article>
  );
}
