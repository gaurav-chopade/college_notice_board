import { useEffect, useState } from 'react';
import { useToast } from '../context/ToastContext';
import { NoticeApi, errorMessage } from '../services/api';

/** Save / unsave a notice to the user's reading list. Updates immediately, rolls back if the server refuses. */
export default function SaveButton({ noticeId, saved: initial, onChange, size = 'sm' }) {
  const toast = useToast();
  const [saved, setSaved] = useState(initial);
  const [busy, setBusy] = useState(false);
  useEffect(() => { setSaved(initial); }, [initial]);

  const toggle = async () => {
    const next = !saved;
    setSaved(next); setBusy(true);
    try {
      if (next) await NoticeApi.save(noticeId); else await NoticeApi.unsave(noticeId);
      toast(next ? 'Saved. Find it under Saved notices.' : 'Removed from saved notices');
      onChange?.(next);
    } catch (e) {
      setSaved(!next);
      toast(errorMessage(e), 'danger');
    } finally { setBusy(false); }
  };

  return (
    <button type="button" className={`save-btn btn btn-${size} ${saved ? 'is-saved' : ''}`} onClick={toggle} disabled={busy}
      aria-pressed={saved} title={saved ? 'Remove from saved notices' : 'Save to read later'}>
      <svg width="12" height="14" viewBox="0 0 12 14" aria-hidden="true" focusable="false">
        <path d="M1 1h10v12L6 9.5 1 13z" fill={saved ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      </svg>
      <span>{saved ? 'Saved' : 'Save'}</span>
    </button>
  );
}
