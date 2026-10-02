import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import InstitutionIcon from '../../components/InstitutionIcon';
import { Empty, ErrorState, SkeletonNotice } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import { PublicApi, errorMessage } from '../../services/api';
import { INSTITUTION_LABEL, fileSize, formatDate } from '../../utils/format';

const PRIORITY = { NORMAL: 'Normal', IMPORTANT: 'Important', URGENT: 'Urgent' };

/** One public notice. Attachments open through a short-lived link the server issues per click. */
export default function PublicNoticeDetail() {
  const { id } = useParams();
  const { data: n, loading, error, reload } = useAsync(() => PublicApi.notice(id), [id]);
  const [opening, setOpening] = useState(null);
  const [fileError, setFileError] = useState('');

  const open = async (a) => {
    setOpening(a.id); setFileError('');
    // Open the tab first (in the click) so pop-up blockers allow it, then point it at the signed link.
    const tab = window.open('', '_blank');
    try {
      const link = await PublicApi.attachmentLink(n.id, a.id);
      if (tab) { tab.opener = null; tab.location.href = link.url; } else window.location.href = link.url;
    } catch (e) { tab?.close(); setFileError(errorMessage(e)); } finally { setOpening(null); }
  };

  const notFound = error && /not found/i.test(error);

  return (
    <div className="pub-wrap pub-page pub-narrow">
      <p className="mb-3"><Link to="/public-notices">← All public notices</Link></p>
      {loading && <SkeletonNotice />}
      {notFound && (
        <Empty title="This notice is not available" action={<Link className="btn btn-sm btn-outline-primary" to="/public-notices">See current notices</Link>}>
          It may have expired, been withdrawn, or the link may be wrong.
        </Empty>
      )}
      {error && !notFound && <ErrorState message={error} onRetry={reload} />}
      {n && (
        <article className={`notice-card prio-${n.priority} pub-article`}>
          <div className="pub-card-top">
            <span className={`scope-chip ${n.institutionScope}`}><InstitutionIcon type={n.institutionScope} size={15} />{INSTITUTION_LABEL[n.institutionScope]}</span>
            <span className="text-secondary small">{n.categoryName}</span>
            <span className={`prio-label ${n.priority} ms-auto`}>{PRIORITY[n.priority]}</span>
          </div>
          <h1 className="pub-article-title">{n.title}</h1>
          <dl className="pub-facts">
            <div><dt>Published</dt><dd>{formatDate(n.publishDate)}</dd></div>
            {n.expiryDate && <div><dt>Valid until</dt><dd>{formatDate(n.expiryDate)}</dd></div>}
            <div><dt>Issued by</dt><dd>{n.issuedBy}</dd></div>
          </dl>
          <div className="notice-body mb-4">{n.description}</div>

          {n.attachments.length > 0 && (
            <section className="mb-4" aria-labelledby="att-h">
              <h2 id="att-h" className="h6">Attachments</h2>
              {fileError && <div className="alert alert-danger py-2">{fileError}</div>}
              <ul className="pub-files">
                {n.attachments.map((a) => (
                  <li key={a.id}>
                    <span className="pub-file-type">{a.fileFormat?.toUpperCase()}</span>
                    <span className="pub-file-name">{a.fileName}<small>{fileSize(a.fileSize)}</small></span>
                    <button className="btn btn-sm btn-outline-primary" onClick={() => open(a)} disabled={opening === a.id}>
                      {opening === a.id ? 'Opening…' : 'View / download'}
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {(n.contactPhone || n.contactEmail) && (
            <aside className="contact-card">
              <p className="pub-footer-h">Questions about this notice?</p>
              {n.contactPhone && <p className="mb-1">Call <a href={`tel:${n.contactPhone.replace(/\s/g, '')}`}>{n.contactPhone}</a></p>}
              {n.contactEmail && <p className="mb-0">Email <a href={`mailto:${n.contactEmail}`}>{n.contactEmail}</a></p>}
            </aside>
          )}
        </article>
      )}
    </div>
  );
}
