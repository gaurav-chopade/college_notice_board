import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import AccountStatusCard from '../components/AccountStatusCard';
import { PasswordField, PasswordStrength, passwordOk } from '../components/PasswordField';
import { useAuth } from '../context/AuthContext';
import { AuthApi, DepartmentApi, errorMessage, fieldErrors } from '../services/api';
import { yearLabel } from '../utils/format';

function AuthFrame({ title, subtitle, children, wide }) {
  return (
    <div className="auth-wrap">
      <div className={`auth-card panel p-4 ${wide ? 'wide' : ''}`}>
        <p className="auth-back"><Link to="/">← Back to public notices</Link></p>
        <p className="auth-brand">College notice board</p>
        <h1 className="auth-title">{title}</h1>
        {subtitle && <p className="text-secondary mb-4">{subtitle}</p>}
        {children}
      </div>
    </div>
  );
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function Login() {
  const { user, login } = useAuth();
  const [params] = useSearchParams();
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(params.get('expired') ? 'Your session ended. Sign in again.' : '');
  const [account, setAccount] = useState(null);
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  if (user) return <Navigate to="/" replace />;

  const signIn = async () => {
    setBusy(true); setError('');
    try {
      await login(form.email.trim(), form.password);
      navigate(location.state?.from?.pathname || '/', { replace: true });
    } catch (err) {
      const acc = err?.response?.data?.account;
      if (acc) setAccount(acc);
      else { setAccount(null); setError(errorMessage(err)); }
    } finally { setBusy(false); }
  };

  const submit = (e) => {
    e.preventDefault();
    const errs = {};
    if (!EMAIL_RE.test(form.email.trim())) errs.email = 'Enter your college email address';
    if (!form.password) errs.password = 'Enter your password';
    setErrors(errs);
    if (Object.keys(errs).length === 0) signIn();
  };

  if (account) {
    return (
      <AuthFrame title="Account status">
        <AccountStatusCard account={account} email={form.email.trim()} onRetry={signIn} retrying={busy} />
        <p className="small mt-3 mb-0"><button type="button" className="btn btn-link p-0" onClick={() => setAccount(null)}>Sign in with a different account</button></p>
      </AuthFrame>
    );
  }

  return (
    <AuthFrame title="Sign in" subtitle="See the notices addressed to you.">
      <form onSubmit={submit} noValidate>
        {error && <div className="alert alert-danger py-2" role="alert">{error}</div>}
        <div className="mb-3">
          <label className="form-label" htmlFor="email">College email</label>
          <input id="email" type="email" className={`form-control ${errors.email ? 'is-invalid' : ''}`} autoComplete="username"
            value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}
            aria-invalid={!!errors.email} aria-describedby={errors.email ? 'email-err' : undefined} />
          {errors.email && <div id="email-err" className="invalid-feedback">{errors.email}</div>}
        </div>
        <PasswordField id="password" label="Password" value={form.password} error={errors.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })} />
        <button className="btn btn-primary w-100" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <div className="d-flex flex-wrap justify-content-between gap-2 mt-3 small">
        <Link to="/forgot-password">Forgot password?</Link>
        <Link to="/register">New here? Request an account</Link>
      </div>
    </AuthFrame>
  );
}

const ROLES = [
  { value: 'STUDENT', label: 'Student', hint: 'Your class teacher approves your account.' },
  { value: 'FACULTY', label: 'Teacher', hint: 'Your department HOD approves your account.' },
  { value: 'HOD', label: 'HOD', hint: 'The Principal approves HOD accounts. Each department has one HOD.' },
];

export function Register() {
  const [params] = useSearchParams();
  const [departments, setDepartments] = useState([]);
  const [form, setForm] = useState({
    role: ROLES.some((r) => r.value === params.get('role')) ? params.get('role') : 'STUDENT',
    institutionType: ['ENGINEERING', 'POLYTECHNIC'].includes(params.get('institution')) ? params.get('institution') : '',
    fullName: '', email: params.get('email') || '', phone: '', departmentId: '', semester: '', className: '',
    password: '', confirm: '',
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [done, setDone] = useState(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { DepartmentApi.list().then(setDepartments).catch(() => setError('Could not load departments. Refresh the page to try again.')); }, []);

  const set = (k) => (e) => { setForm({ ...form, [k]: e.target.value }); if (errors[k]) setErrors({ ...errors, [k]: undefined }); };
  const isStudent = form.role === 'STUDENT';
  const role = ROLES.find((r) => r.value === form.role);
  // Departments, programme and semester count all come from the department table.
  const wingDepartments = departments.filter((d) => d.institutionType === form.institutionType);
  const dept = departments.find((d) => String(d.id) === String(form.departmentId));
  const semesters = Array.from({ length: dept?.semesterCount || 8 }, (_, i) => i + 1);

  const validate = () => {
    const e = {};
    if (!form.fullName.trim()) e.fullName = 'Enter your full name';
    if (!EMAIL_RE.test(form.email.trim())) e.email = 'Enter a valid email address';
    if (form.phone && !/^[0-9+ -]{7,20}$/.test(form.phone)) e.phone = 'Use 7 to 20 digits, spaces, + or -';
    if (!form.institutionType) e.institutionType = 'Choose Engineering or Polytechnic';
    if (!form.departmentId) e.departmentId = 'Select your department';
    if (isStudent && !form.semester) e.semester = 'Select your semester';
    if (isStudent && !form.className.trim()) e.className = 'Enter your class or division';
    if (!passwordOk(form.password)) e.password = 'The password does not meet all the rules below';
    if (form.confirm !== form.password) e.confirm = 'The passwords do not match';
    return e;
  };

  const submit = async (ev) => {
    ev.preventDefault();
    const e = validate();
    setErrors(e); setError('');
    if (Object.keys(e).length) {
      document.getElementById(Object.keys(e)[0])?.focus();
      return;
    }
    setBusy(true);
    try {
      const res = await AuthApi.register({
        role: form.role, institutionType: form.institutionType, fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone.trim() || null,
        password: form.password, departmentId: Number(form.departmentId),
        semester: isStudent ? Number(form.semester) : null, className: isStudent ? form.className.trim() : null,
      });
      setDone(res);
    } catch (err) {
      const fe = fieldErrors(err);
      if (Object.keys(fe).length) setErrors(fe); else setError(errorMessage(err));
    } finally { setBusy(false); }
  };

  if (done) {
    return (
      <AuthFrame title={done.status === 'APPROVED' ? 'You are registered' : 'Request sent'}>
        <div className="account-status PENDING">
          {done.status === 'APPROVED'
            ? <p className="mb-0">You can sign in now.</p>
            : <p className="mb-0">It will be reviewed by <strong>{done.waitingFor}</strong>. Sign in any time with the password you chose to see where it stands.</p>}
        </div>
        <Link className="btn btn-primary w-100 mt-3" to="/login">Go to sign in</Link>
      </AuthFrame>
    );
  }

  const field = (id, label, input, hint) => (
    <div className="mb-3">
      <label className="form-label" htmlFor={id}>{label}</label>
      {input}
      {hint && !errors[id] && <div className="form-text" id={`${id}-hint`}>{hint}</div>}
      {errors[id] && <div id={`${id}-err`} className="invalid-feedback d-block">{errors[id]}</div>}
    </div>
  );
  const cls = (id, base = 'form-control') => `${base} ${errors[id] ? 'is-invalid' : ''}`;
  const aria = (id) => ({ 'aria-invalid': !!errors[id], 'aria-describedby': errors[id] ? `${id}-err` : undefined });

  return (
    <AuthFrame title="Request an account" subtitle="Your account is checked by the right person before you can sign in." wide>
      {error && <div className="alert alert-danger py-2" role="alert">{error}</div>}
      <form onSubmit={submit} noValidate>
        <fieldset className="mb-3">
          <legend className="form-label fs-6 mb-1">I am a</legend>
          <div className="role-choice">
            {ROLES.map((r) => (
              <label key={r.value} className={`role-option ${form.role === r.value ? 'is-on' : ''}`}>
                <input type="radio" name="role" value={r.value} checked={form.role === r.value}
                  onChange={() => { setForm({ ...form, role: r.value }); setErrors({}); }} />
                <span>{r.label}</span>
              </label>
            ))}
          </div>
          <div className="form-text">{role.hint}</div>
        </fieldset>

        <fieldset className="mb-3">
          <legend className="form-label fs-6 mb-1">Institution</legend>
          <div className="role-choice two" id="institutionType" tabIndex={-1}>
            {[['ENGINEERING', 'Engineering', 'Degree (B.Tech)'], ['POLYTECHNIC', 'Polytechnic', 'Diploma']].map(([v, label, hint]) => (
              <label key={v} className={`role-option ${form.institutionType === v ? 'is-on' : ''}`}>
                <input type="radio" name="institutionType" value={v} checked={form.institutionType === v}
                  onChange={() => { setForm({ ...form, institutionType: v, departmentId: '', semester: '' }); setErrors({ ...errors, institutionType: undefined }); }} />
                <span>{label}<small>{hint}</small></span>
              </label>
            ))}
          </div>
          {errors.institutionType && <div className="invalid-feedback d-block">{errors.institutionType}</div>}
        </fieldset>

        <div className="row g-3">
          <div className="col-md-6">
            {field('fullName', 'Full name', <input id="fullName" className={cls('fullName')} autoComplete="name" value={form.fullName} onChange={set('fullName')} maxLength={120} {...aria('fullName')} />)}
          </div>
          <div className="col-md-6">
            {field('email', 'College email', <input id="email" type="email" className={cls('email')} autoComplete="email" value={form.email} onChange={set('email')} maxLength={160} {...aria('email')} />)}
          </div>
          <div className="col-md-6">
            {field('departmentId', 'Department', (
              <select id="departmentId" className={cls('departmentId', 'form-select')} value={form.departmentId} disabled={!form.institutionType}
                onChange={(e) => { setForm({ ...form, departmentId: e.target.value, semester: '' }); setErrors({ ...errors, departmentId: undefined }); }} {...aria('departmentId')}>
                <option value="">{form.institutionType ? 'Select…' : 'Choose the institution first'}</option>
                {wingDepartments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
            ), dept ? `Programme: ${dept.programName}` : null)}
          </div>
          <div className="col-md-6">
            {field('phone', 'Phone (optional)', <input id="phone" type="tel" className={cls('phone')} autoComplete="tel" value={form.phone} onChange={set('phone')} maxLength={20} {...aria('phone')} />)}
          </div>
          {isStudent && (
            <>
              <div className="col-6">
                {field('semester', 'Semester', (
                  <select id="semester" className={cls('semester', 'form-select')} value={form.semester} onChange={set('semester')} disabled={!dept} {...aria('semester')}>
                    <option value="">Select…</option>{semesters.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                ), form.semester ? `${dept?.programName}, ${yearLabel(form.semester)}` : null)}
              </div>
              <div className="col-6">
                {field('className', 'Class / division', <input id="className" className={cls('className')} value={form.className} onChange={set('className')} maxLength={20} placeholder="A" {...aria('className')} />)}
              </div>
            </>
          )}
        </div>

        <PasswordField id="password" label="Password" autoComplete="new-password" value={form.password} onChange={set('password')}
          error={errors.password} describedBy="pw-rules" />
        <PasswordStrength id="pw-rules" password={form.password} />
        <PasswordField id="confirm" label="Confirm password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} error={errors.confirm} />

        <button className="btn btn-primary w-100" disabled={busy}>{busy ? 'Sending request…' : 'Send request'}</button>
      </form>
      <p className="small mt-3 mb-0">Already have an account? <Link to="/login">Sign in</Link></p>
    </AuthFrame>
  );
}

export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try { setMsg((await AuthApi.forgot(email)).message); } catch (err) { setMsg(errorMessage(err)); } finally { setBusy(false); }
  };
  return (
    <AuthFrame title="Reset your password" subtitle="We will email a reset link valid for 30 minutes.">
      {msg && <div className="alert alert-info py-2" role="status">{msg}</div>}
      <form onSubmit={submit}>
        <label className="form-label" htmlFor="email">College email</label>
        <input id="email" type="email" className="form-control mb-3" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <button className="btn btn-primary w-100" disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</button>
      </form>
      <p className="small mt-3 mb-0"><Link to="/login">Back to sign in</Link></p>
    </AuthFrame>
  );
}

export function ResetPassword() {
  const [params] = useSearchParams();
  const [pw, setPw] = useState({ password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [msg, setMsg] = useState(null);
  const submit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!passwordOk(pw.password)) errs.password = 'The password does not meet all the rules below';
    if (pw.confirm !== pw.password) errs.confirm = 'The passwords do not match';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    try { setMsg({ ok: true, text: (await AuthApi.reset(params.get('token') || '', pw.password)).message }); }
    catch (err) { setMsg({ ok: false, text: errorMessage(err) }); }
  };
  return (
    <AuthFrame title="Choose a new password">
      {msg && <div className={`alert py-2 alert-${msg.ok ? 'success' : 'danger'}`} role="status">{msg.text}</div>}
      {!msg?.ok && (
        <form onSubmit={submit} noValidate>
          <PasswordField id="password" label="New password" autoComplete="new-password" value={pw.password}
            onChange={(e) => setPw({ ...pw, password: e.target.value })} error={errors.password} describedBy="pw-rules" />
          <PasswordStrength id="pw-rules" password={pw.password} />
          <PasswordField id="confirm" label="Confirm new password" autoComplete="new-password" value={pw.confirm}
            onChange={(e) => setPw({ ...pw, confirm: e.target.value })} error={errors.confirm} />
          <button className="btn btn-primary w-100">Save password</button>
        </form>
      )}
      <p className="small mt-3 mb-0"><Link to="/login">Back to sign in</Link></p>
    </AuthFrame>
  );
}
