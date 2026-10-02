import { useState } from 'react';

/** Mirrors the backend rule (AuthDtos.PASSWORD_REGEX) so the checklist and the server always agree. */
export const PASSWORD_RULES = [
  { id: 'len', label: '8 to 64 characters', test: (p) => p.length >= 8 && p.length <= 64 },
  { id: 'upper', label: 'An uppercase letter', test: (p) => /[A-Z]/.test(p) },
  { id: 'lower', label: 'A lowercase letter', test: (p) => /[a-z]/.test(p) },
  { id: 'digit', label: 'A number', test: (p) => /\d/.test(p) },
  { id: 'symbol', label: 'A symbol such as ! @ #', test: (p) => /[^A-Za-z0-9]/.test(p) },
];

export const passwordOk = (p) => PASSWORD_RULES.every((r) => r.test(p));

const STRENGTH = ['Too weak', 'Weak', 'Fair', 'Good', 'Strong'];

/** 0–4: the required rules, plus credit for length beyond the minimum. */
export function passwordScore(p) {
  if (!p) return 0;
  const met = PASSWORD_RULES.filter((r) => r.test(p)).length;
  // Below the required rules the password cannot be used, so never call it more than weak.
  if (met < PASSWORD_RULES.length) return met >= 3 ? 1 : 0;
  return p.length >= 14 ? 4 : p.length >= 11 ? 3 : 2;
}

/** Password input with a show/hide toggle and an optional inline error. */
export function PasswordField({ id, label, value, onChange, autoComplete = 'current-password', error, describedBy, required = true }) {
  const [shown, setShown] = useState(false);
  const errId = error ? `${id}-err` : undefined;
  return (
    <div className="mb-3">
      <label className="form-label" htmlFor={id}>{label}</label>
      <div className="input-group has-validation">
        <input id={id} type={shown ? 'text' : 'password'} className={`form-control ${error ? 'is-invalid' : ''}`}
          autoComplete={autoComplete} required={required} value={value} onChange={onChange}
          aria-invalid={!!error} aria-describedby={[describedBy, errId].filter(Boolean).join(' ') || undefined} />
        <button type="button" className="btn btn-outline-secondary pw-toggle" onClick={() => setShown((s) => !s)}
          aria-pressed={shown} aria-controls={id}>{shown ? 'Hide' : 'Show'}</button>
        {error && <div id={errId} className="invalid-feedback">{error}</div>}
      </div>
    </div>
  );
}

/** Strength bar plus the checklist of rules, updated as the person types. */
export function PasswordStrength({ id, password }) {
  const score = passwordScore(password);
  return (
    <div id={id} className="pw-strength mb-3" aria-live="polite">
      <div className={`pw-bar s${score}`} aria-hidden="true">{[0, 1, 2, 3].map((i) => <span key={i} />)}</div>
      {password && <div className="pw-label">Strength: {STRENGTH[score]}</div>}
      <ul className="pw-rules">
        {PASSWORD_RULES.map((r) => {
          const ok = r.test(password);
          return <li key={r.id} className={ok ? 'ok' : ''}><span className="visually-hidden">{ok ? 'Done: ' : 'Needed: '}</span>{r.label}</li>;
        })}
      </ul>
    </div>
  );
}
