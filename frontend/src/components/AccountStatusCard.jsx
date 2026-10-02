import { Link } from 'react-router-dom';
import { ROLE_LABEL, formatDate } from '../utils/format';

const TITLE = {
  PENDING: 'Your request is waiting for approval',
  REJECTED: 'Your registration was not approved',
  SUSPENDED: 'Your account is suspended',
  DEACTIVATED: 'Your account is closed',
};
const STEP = {
  USER_REGISTERED: 'Request sent', USER_APPROVED: 'Approved', USER_REJECTED: 'Rejected',
  USER_SUSPENDED: 'Suspended', USER_ACTIVATED: 'Reactivated', USER_DEACTIVATED: 'Closed',
};
const TONE = { USER_APPROVED: 'good', USER_ACTIVATED: 'good', USER_REJECTED: 'bad', USER_SUSPENDED: 'bad', USER_DEACTIVATED: 'bad' };

/** What a not-yet-approved (or blocked) person sees after entering the right password. */
export default function AccountStatusCard({ account, email, onRetry, retrying }) {
  const a = account;
  const where = [ROLE_LABEL[a.role], a.departmentName, a.semester && `semester ${a.semester}`, a.className && `class ${a.className}`]
    .filter(Boolean).join(', ');
  return (
    <section className={`account-status ${a.status}`} aria-live="polite">
      <h2 className="h5 mb-1">{TITLE[a.status]}</h2>
      <p className="small text-secondary mb-3">{where}</p>

      {a.status === 'PENDING' && <p>It will be reviewed by <strong>{a.waitingFor}</strong>. You can sign in once it is approved.</p>}
      {a.reason && (
        <div className="account-reason">
          <div className="small fw-semibold">{a.status === 'REJECTED' ? 'Reason given' : 'Reason'}{a.reviewedByName ? ` by ${a.reviewedByName}` : ''}</div>
          {a.reason}
        </div>
      )}
      {a.status === 'DEACTIVATED' && <p>Contact the college office if this is a mistake.</p>}

      {a.timeline?.length > 0 && (
        <ol className="timeline mt-3">
          {a.timeline.map((e, i) => (
            <li key={i} className={`tl-step ${TONE[e.action] || ''}`}>
              <div className="tl-what">{STEP[e.action] || e.action}</div>
              <div className="tl-who">{e.actorName}, {formatDate(e.at)}</div>
            </li>
          ))}
          {a.status === 'PENDING' && <li className="tl-step pending"><div className="tl-what">Waiting for review</div></li>}
        </ol>
      )}

      <div className="d-flex flex-wrap gap-2 mt-3">
        {a.status === 'PENDING' && (
          <button type="button" className="btn btn-outline-primary btn-sm" onClick={onRetry} disabled={retrying}>
            {retrying ? 'Checking…' : 'Check again'}
          </button>
        )}
        {a.status === 'REJECTED' && (
          <Link className="btn btn-primary btn-sm" to={`/register?email=${encodeURIComponent(email)}&role=${a.role}`}>Correct details and apply again</Link>
        )}
      </div>
    </section>
  );
}
