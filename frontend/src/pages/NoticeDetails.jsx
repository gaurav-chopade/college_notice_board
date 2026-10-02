import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import ConfirmDialog from '../components/ConfirmDialog';
import NoticeTimeline from '../components/NoticeTimeline';
import SaveButton from '../components/SaveButton';
import { ErrorState, SkeletonNotice } from '../components/States';
import { useToast } from '../context/ToastContext';
import { useAsync } from '../hooks/useAsync';
import { AttachmentApi, NoticeApi, errorMessage } from '../services/api';
import { INSTITUTION_LABEL, ROLE_LABEL, STATUS_LABEL, fileSize, formatDate } from '../utils/format';

export default function NoticeDetails() {
  const { id } = useParams();
  const toast = useToast();
  const navigate = useNavigate();
  const { data: n, loading, error, reload } = useAsync(() => NoticeApi.get(id), [id]);
  const [stats, setStats] = useState(null);
  const [dialog, setDialog] = useState(null);

  if (loading && !n) return <SkeletonNotice />;
  if (error) return <ErrorState message={error} />;
  const p = n.permissions;

  const act = async (fn, done) => {
    try { await fn(); toast(done); setDialog(null); reload(); } catch (e) { toast(errorMessage(e), 'danger'); }
  };
  const download = async (a) => {
    try { const { url } = await AttachmentApi.download(a.id); window.open(url, '_blank', 'noopener'); }
    catch (e) { toast(errorMessage(e), 'danger'); }
  };
  const loadStats = async () => {
    try { setStats(await NoticeApi.stats(id)); } catch (e) { toast(errorMessage(e), 'danger'); }
  };

  return (
    <>
      <p className="small mb-2"><Link to="/notices">Notices</Link></p>
      <article className={`notice-card prio-${n.priority} mb-3`} style={{ padding: '1.5rem' }}>
        <div className="d-flex flex-wrap gap-2 align-items-center">
          <span className={`prio-label ${n.priority}`}>{n.priority === 'NORMAL' ? 'Normal' : n.priority === 'URGENT' ? 'Urgent' : 'Important'}</span>
          <span className="text-secondary small">{n.categoryName}</span>
          <span className={`status-chip ${n.status}`}>{STATUS_LABEL[n.status]}</span>
          {n.aiGenerated && <span className="status-chip">Drafted with AI</span>}
          {n.visibility === 'PUBLIC' && (
            <Link to={`/public-notices/${n.id}`} className={`scope-chip ${n.institutionScope}`} title="Open the public page">
              Public · {INSTITUTION_LABEL[n.institutionScope]}
            </Link>
          )}
          {p.canSave && <span className="ms-auto"><SaveButton noticeId={n.id} saved={n.saved} /></span>}
        </div>
        <h1 className="mt-2 mb-1">{n.title}</h1>
        <p className="text-secondary small mb-3">
          {n.createdByName} ({ROLE_LABEL[n.createdByRole]}{n.departmentName ? `, ${n.departmentName}` : ''}) ·
          {n.publishedAt ? ` published ${formatDate(n.publishedAt)}` : n.publishDate ? ` publishes ${formatDate(n.publishDate)}` : ` created ${formatDate(n.createdAt)}`}
          {n.expiryDate && ` · expires ${formatDate(n.expiryDate)}`}
        </p>

        {n.status === 'REJECTED' && n.rejectionReason && (
          <div className="alert alert-danger py-2"><strong>Returned by {n.reviewedByName}:</strong> {n.rejectionReason}</div>
        )}

        <div className="notice-body mb-3">{n.description}</div>

        {n.attachments.length > 0 && (
          <div className="mb-3">
            <h2 className="h6">Attachments</h2>
            <ul className="list-unstyled mb-0">
              {n.attachments.map((a) => (
                <li key={a.id} className="d-flex align-items-center gap-2 py-1 border-bottom">
                  <span className="flex-grow-1">{a.originalFileName} <span className="text-secondary small">{a.fileFormat.toUpperCase()}, {fileSize(a.fileSize)}</span></span>
                  <button className="btn btn-sm btn-outline-primary" onClick={() => download(a)}>Download</button>
                  {p.canManageAttachments && (
                    <button className="btn btn-sm btn-link text-danger" onClick={() => act(() => AttachmentApi.remove(a.id), 'Attachment removed')}>Remove</button>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}

        <p className="small text-secondary mb-0">For: {n.targets.map((t) => t.label).join(', ')}</p>

        {p.canAcknowledge && (
          <div className="ack-box mt-3">
            {n.acknowledged
              ? <span className="fw-semibold text-success">You acknowledged this notice.</span>
              : <button className="btn btn-primary" onClick={() => act(() => NoticeApi.acknowledge(id), 'Acknowledged')}>I have read this notice</button>}
          </div>
        )}
      </article>

      <div className="d-flex flex-wrap gap-2 mb-3">
        {p.canEdit && <Link className="btn btn-outline-primary" to={`/notices/${id}/edit`}>Edit</Link>}
        {p.canPublish && (
          <button className="btn btn-primary" onClick={() => act(() => NoticeApi.publish(id), n.publishDate && new Date(n.publishDate) > new Date() ? 'Scheduled' : 'Published')}>
            {n.publishDate && new Date(n.publishDate) > new Date() ? 'Schedule' : 'Publish now'}
          </button>
        )}
        {p.canSubmit && <button className="btn btn-primary" onClick={() => act(() => NoticeApi.submit(id), 'Sent to your HOD for approval')}>Submit for approval</button>}
        {p.canReview && (
          <>
            <button className="btn btn-primary" onClick={() => act(() => NoticeApi.approve(id), 'Approved')}>Approve</button>
            <button className="btn btn-outline-danger" onClick={() => setDialog('reject')}>Reject</button>
          </>
        )}
        {p.canViewStats && ['PUBLISHED', 'EXPIRED', 'ARCHIVED'].includes(n.status) && (
          <button className="btn btn-light" onClick={loadStats}>Show read statistics</button>
        )}
        {p.canArchive && <button className="btn btn-light" onClick={() => act(() => NoticeApi.archive(id), 'Archived')}>Archive</button>}
        {p.canDelete && <button className="btn btn-outline-danger ms-auto" onClick={() => setDialog('delete')}>Delete</button>}
      </div>

      {stats && (
        <div className="stat-row mb-3">
          <div className="stat"><div className="num">{stats.totalRecipients}</div><div className="lbl">Recipients</div></div>
          <div className="stat"><div className="num">{stats.read}</div><div className="lbl">Read</div></div>
          <div className="stat"><div className="num">{stats.notRead}</div><div className="lbl">Not read</div></div>
          <div className="stat"><div className="num">{stats.acknowledged}</div><div className="lbl">Acknowledged</div></div>
        </div>
      )}

      {p.canViewStats && <NoticeTimeline notice={n} version={`${n.status}|${n.updatedAt}`} />}

      <ConfirmDialog open={dialog === 'reject'} title="Reject this notice" inputLabel="Reason the faculty member will see"
        confirmLabel="Reject" variant="danger" onClose={() => setDialog(null)}
        onConfirm={(reason) => act(() => NoticeApi.reject(id, reason), 'Rejected')} />
      <ConfirmDialog open={dialog === 'delete'} title="Delete this notice?" body="The notice and its attachments are removed permanently."
        confirmLabel="Delete" variant="danger" onClose={() => setDialog(null)}
        onConfirm={async () => { try { await NoticeApi.remove(id); toast('Deleted'); navigate('/my-notices'); } catch (e) { toast(errorMessage(e), 'danger'); setDialog(null); } }} />
    </>
  );
}
