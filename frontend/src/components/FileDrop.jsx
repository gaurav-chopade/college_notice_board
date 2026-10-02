import { useRef, useState } from 'react';
import { fileSize } from '../utils/format';

export const ALLOWED = ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png'];
export const MAX_BYTES = 10 * 1024 * 1024;

const ext = (name) => (name.includes('.') ? name.split('.').pop().toLowerCase() : '');

/** Wraps chosen files with an upload state. Files the server would reject are flagged up front. */
export function toEntries(fileList) {
  return Array.from(fileList).map((file) => {
    let error = '';
    if (!ALLOWED.includes(ext(file.name))) error = 'Only PDF, Word, JPG and PNG files can be attached';
    else if (file.size > MAX_BYTES) error = `Too large (${fileSize(file.size)}). The limit is 10 MB`;
    else if (file.size === 0) error = 'This file is empty';
    return { key: `${file.name}|${file.size}|${file.lastModified}`, file, status: error ? 'invalid' : 'ready', progress: 0, error };
  });
}

const STATUS = { ready: 'Ready to upload', uploading: 'Uploading', done: 'Uploaded', error: 'Not uploaded', invalid: 'Can’t attach' };

/**
 * Drop zone plus per-file list. With `readOnly` it only shows the list (used on the preview step
 * and while uploading), so progress stays visible whichever screen the author saved from.
 */
export default function FileDrop({ entries, onChange, disabled, readOnly }) {
  const input = useRef(null);
  const [over, setOver] = useState(false);
  const depth = useRef(0);

  const add = (fileList) => {
    const known = new Set(entries.map((e) => e.key));
    onChange([...entries, ...toEntries(fileList).filter((e) => !known.has(e.key))]);
  };
  const remove = (key) => onChange(entries.filter((e) => e.key !== key));

  const onDrop = (e) => {
    e.preventDefault(); depth.current = 0; setOver(false);
    if (!disabled && e.dataTransfer.files.length) add(e.dataTransfer.files);
  };

  return (
    <div>
      {!readOnly && (
        <div className={`drop-zone ${over ? 'is-over' : ''} ${disabled ? 'is-disabled' : ''}`}
          onDragEnter={(e) => { e.preventDefault(); depth.current += 1; setOver(true); }}
          onDragOver={(e) => e.preventDefault()}
          onDragLeave={() => { depth.current -= 1; if (depth.current <= 0) setOver(false); }}
          onDrop={onDrop}>
          <svg width="28" height="28" viewBox="0 0 24 24" aria-hidden="true" focusable="false" className="drop-icon">
            <path d="M12 16V4m0 0L7 9m5-5 5 5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <p className="mb-1"><strong>Drop files here</strong> or{' '}
            <button type="button" className="btn btn-link p-0 align-baseline" onClick={() => input.current.click()} disabled={disabled}>choose files</button>
          </p>
          <p className="small text-secondary mb-0">PDF, Word, JPG or PNG, up to 10 MB each</p>
          <input ref={input} id="files" type="file" multiple accept={ALLOWED.map((x) => `.${x}`).join(',')} className="visually-hidden"
            onChange={(e) => { add(e.target.files); e.target.value = ''; }} tabIndex={-1} />
        </div>
      )}

      {entries.length > 0 && (
        <ul className="upload-list" aria-live="polite">
          {entries.map((e) => (
            <li key={e.key} className={`upload-item ${e.status}`}>
              <div className="upload-row">
                <span className="upload-name">{e.file.name}</span>
                <span className="upload-size">{fileSize(e.file.size)}</span>
                <span className="upload-status">{STATUS[e.status]}{e.status === 'uploading' ? ` ${e.progress}%` : ''}</span>
                {!readOnly && e.status !== 'uploading' && e.status !== 'done' && (
                  <button type="button" className="btn btn-sm btn-link text-danger p-0" onClick={() => remove(e.key)} aria-label={`Remove ${e.file.name}`}>Remove</button>
                )}
              </div>
              {(e.status === 'uploading' || e.status === 'done') && (
                <div className="upload-bar" role="progressbar" aria-valuenow={e.progress} aria-valuemin={0} aria-valuemax={100} aria-label={`${e.file.name} upload`}>
                  <span style={{ width: `${e.status === 'done' ? 100 : e.progress}%` }} />
                </div>
              )}
              {e.error && <div className="upload-error">{e.error}</div>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
