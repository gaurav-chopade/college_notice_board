import { useState } from 'react';
import ConfirmDialog from '../components/ConfirmDialog';
import { Empty, ErrorState, SkeletonRows } from '../components/States';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useAsync } from '../hooks/useAsync';
import { RegistrationApi, errorMessage } from '../services/api';
import { ROLE_LABEL, formatDate } from '../utils/format';

const INTRO = {
  FACULTY: 'Students who registered for a class you are class teacher of.',
  HOD: 'Teachers and students of your department. Students of classes with a class teacher are also shown, in case you want to step in.',
  PRINCIPAL: 'HOD requests, and requests from departments or classes that have no one else to approve them.',
  SUPER_ADMIN: 'Every pending request in the college.',
};

/** Approver's queue: approve, or reject with a reason the applicant will see. */
export default function AccountRequests() {
  const { user } = useAuth();
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => RegistrationApi.pending(), []);
  const [handled, setHandled] = useState(new Set());
  const [rejecting, setRejecting] = useState(null);
  const [busy, setBusy] = useState(null);

  const done = (id) => setHandled((s) => new Set(s).add(id));
  const approve = async (r) => {
    setBusy(r.id);
    try { await RegistrationApi.approve(r.id); done(r.id); toast(`${r.fullName} approved. They can sign in now.`); }
    catch (e) { toast(errorMessage(e), 'danger'); reload(); }
    finally { setBusy(null); }
  };
  const reject = async (reason) => {
    const r = rejecting;
    try { await RegistrationApi.reject(r.id, reason); done(r.id); toast(`${r.fullName}'s request rejected`); setRejecting(null); }
    catch (e) { toast(errorMessage(e), 'danger'); }
  };

  const items = (data || []).filter((r) => !handled.has(r.id));
  const mine = items.filter((r) => r.primary);
  const others = items.filter((r) => !r.primary);

  const card = (r) => (
    <li key={r.id} className="request-card">
      <div className="request-main">
        <div className="request-name">{r.fullName}</div>
        <div className="small">{ROLE_LABEL[r.role]}{r.departmentName ? `, ${r.departmentName}` : ''}
          {r.semester ? `, semester ${r.semester} class ${r.className}` : ''}</div>
        <div className="small text-secondary">{r.email}{r.phone ? `, ${r.phone}` : ''}</div>
        <div className="small text-secondary">Requested {formatDate(r.appliedAt)}</div>
        {!r.primary && <div className="small mt-1">Waiting for {r.waitingFor}</div>}
      </div>
      <div className="request-actions">
        <button className="btn btn-primary btn-sm" onClick={() => approve(r)} disabled={busy === r.id}>{busy === r.id ? 'Approving…' : 'Approve'}</button>
        <button className="btn btn-outline-danger btn-sm" onClick={() => setRejecting(r)} disabled={busy === r.id}>Reject</button>
      </div>
    </li>
  );

  return (
    <>
      <h1 className="mb-1">Account requests</h1>
      <p className="text-secondary">{INTRO[user.role]}</p>
      {loading && !data && <SkeletonRows count={3} />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && items.length === 0 && <Empty title="No requests waiting">New requests appear here and in your notifications.</Empty>}
      {mine.length > 0 && (
        <section className="mb-4" aria-labelledby="mine-h">
          <h2 id="mine-h" className="h6">Waiting for you ({mine.length})</h2>
          <ul className="request-list">{mine.map(card)}</ul>
        </section>
      )}
      {others.length > 0 && (
        <section aria-labelledby="others-h">
          <h2 id="others-h" className="h6">Waiting for someone else ({others.length})</h2>
          <p className="small text-secondary">You can still approve or reject these if needed.</p>
          <ul className="request-list">{others.map(card)}</ul>
        </section>
      )}
      <ConfirmDialog open={!!rejecting} title={rejecting ? `Reject ${rejecting.fullName}'s request` : ''}
        body="They will see this reason when they try to sign in, and can correct their details and apply again."
        inputLabel="Reason" confirmLabel="Reject request" variant="danger" onClose={() => setRejecting(null)} onConfirm={reject} />
    </>
  );
}
