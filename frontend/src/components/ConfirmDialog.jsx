import { useState } from 'react';

/**
 * Modal confirmation. When `inputLabel` is set the user must type a value (e.g. a rejection reason),
 * which is passed to onConfirm.
 */
export default function ConfirmDialog({ open, title, body, confirmLabel, variant = 'primary', inputLabel, onConfirm, onClose }) {
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  if (!open) return null;
  const confirm = async () => {
    setBusy(true);
    try { await onConfirm(value); setValue(''); } finally { setBusy(false); }
  };
  return (
    <>
      <div className="modal d-block" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content">
            <div className="modal-header"><h2 id="confirm-title" className="modal-title fs-5">{title}</h2>
              <button className="btn-close" aria-label="Close" onClick={onClose} /></div>
            <div className="modal-body">
              {body && <p className="mb-2">{body}</p>}
              {inputLabel && (
                <>
                  <label className="form-label" htmlFor="confirm-input">{inputLabel}</label>
                  <textarea id="confirm-input" className="form-control" rows={3} value={value} onChange={(e) => setValue(e.target.value)} autoFocus />
                </>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-light" onClick={onClose}>Cancel</button>
              <button className={`btn btn-${variant}`} disabled={busy || (inputLabel && !value.trim())} onClick={confirm}>{confirmLabel}</button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  );
}
