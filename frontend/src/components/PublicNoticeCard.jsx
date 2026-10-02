import { Link } from 'react-router-dom';
import InstitutionIcon from './InstitutionIcon';
import { INSTITUTION_LABEL, formatDate } from '../utils/format';

const PRIORITY = { IMPORTANT: 'Important', URGENT: 'Urgent' };

/** A public notice as a pinned sheet: wing, category, title, excerpt, date. */
export default function PublicNoticeCard({ notice: n }) {
  return (
    <article className={`notice-card pub-card prio-${n.priority}`}>
      <div className="pub-card-top">
        <span className={`scope-chip ${n.institutionScope}`}><InstitutionIcon type={n.institutionScope} size={15} />{INSTITUTION_LABEL[n.institutionScope]}</span>
        <span className="text-secondary small">{n.categoryName}</span>
        {PRIORITY[n.priority] && <span className={`prio-label ${n.priority} ms-auto`}>{PRIORITY[n.priority]}</span>}
      </div>
      <h3 className="pub-card-title"><Link to={`/public-notices/${n.id}`} className="stretched-link">{n.title}</Link></h3>
      <p className="pub-card-excerpt">{n.excerpt}</p>
      <div className="pub-card-meta">
        <span>{formatDate(n.publishDate, false)}</span>
        {n.attachmentCount > 0 && <span>{n.attachmentCount} attachment{n.attachmentCount > 1 ? 's' : ''}</span>}
        <span className="pub-card-cta" aria-hidden="true">View notice →</span>
      </div>
    </article>
  );
}
