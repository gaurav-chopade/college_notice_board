import { useEffect, useState } from 'react';
import { NoticeApi, errorMessage } from '../services/api';
import { ROLE_LABEL, formatDate } from '../utils/format';

const STEP = {
  NOTICE_CREATED: 'Created', AI_DRAFT_CREATED: 'Created', NOTICE_EDITED: 'Edited',
  NOTICE_SUBMITTED: 'Submitted for approval', NOTICE_APPROVED: 'Approved', NOTICE_REJECTED: 'Rejected',
  NOTICE_SCHEDULED: 'Scheduled', NOTICE_PUBLISHED: 'Published', NOTICE_EXPIRED: 'Expired', NOTICE_ARCHIVED: 'Archived',
};
const TONE = { NOTICE_APPROVED: 'good', NOTICE_PUBLISHED: 'good', NOTICE_REJECTED: 'bad' };

/** Lifecycle history built from the audit log. `version` changes after every action so it refreshes. */
export default function NoticeTimeline({ notice, version }) {
  const [events, setEvents] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    NoticeApi.timeline(notice.id).then(setEvents).catch((e) => setError(errorMessage(e)));
  }, [notice.id, version]);

  if (error) return <p className="small text-danger">{error}</p>;
  if (!events) return null;

  const next = notice.status === 'PENDING_APPROVAL' ? 'Waiting for HOD approval'
    : notice.status === 'SCHEDULED' && notice.publishDate ? `Publishes ${formatDate(notice.publishDate)}`
      : notice.status === 'DRAFT' ? 'Not published yet'
        : null;

  return (
    <section className="panel" aria-labelledby="timeline-h">
      <h2 id="timeline-h" className="h6 mb-3">History</h2>
      <ol className="timeline">
        {events.map((e, i) => (
          <li key={i} className={`tl-step ${TONE[e.action] || ''}`}>
            <div className="tl-what">{STEP[e.action] || e.action}{e.times > 1 ? `, ${e.times} times` : ''}</div>
            <div className="tl-who">
              {e.actorName}{e.actorRole ? ` (${ROLE_LABEL[e.actorRole]})` : ''}, {formatDate(e.at)}
            </div>
            {e.note && (e.action === 'NOTICE_REJECTED'
              ? <blockquote className="tl-reason">{e.note}</blockquote>
              : <div className="tl-note">{e.note}</div>)}
          </li>
        ))}
        {next && <li className="tl-step pending"><div className="tl-what">{next}</div></li>}
      </ol>
    </section>
  );
}
