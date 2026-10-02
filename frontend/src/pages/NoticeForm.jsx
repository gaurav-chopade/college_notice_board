import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import AudienceSummary from '../components/AudienceSummary';
import FileDrop from '../components/FileDrop';
import TargetBuilder from '../components/TargetBuilder';
import { ErrorState, SkeletonNotice } from '../components/States';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { AttachmentApi, CategoryApi, DepartmentApi, NoticeApi, errorMessage } from '../services/api';
import { INSTITUTION_LABEL, ROLE_LABEL, formatDate, formatTime, fromInputDate, toInputDate, toTargetRequests } from '../utils/format';

const PRIORITY = { NORMAL: 'Normal', IMPORTANT: 'Important', URGENT: 'Urgent' };
const AUTOSAVE_MS = 1500;

/**
 * Unsaved work is kept in this browser (per user, per notice) so a closed tab or expired session
 * does not lose it. Nothing reaches the server until the author saves, so half-written notices
 * never appear in "My notices" or the audit log.
 */
const draftStore = {
  key: (userId, id) => `cnb_draft:${userId}:${id || 'new'}`,
  read(key) { try { return JSON.parse(localStorage.getItem(key)); } catch { return null; } },
  write(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); return true; } catch { return false; } },
  clear(key) { try { localStorage.removeItem(key); } catch { /* storage unavailable */ } },
};

export default function NoticeForm() {
  const { id } = useParams();
  const editing = !!id;
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();
  const storeKey = draftStore.key(user.id, id);

  const [meta, setMeta] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [status, setStatus] = useState(null);
  const [form, setForm] = useState({
    title: '', description: '', categoryId: '', priority: 'NORMAL', publishDate: '', expiryDate: '', acknowledgementRequired: false,
    visibility: 'PRIVATE', institutionScope: '', notifyMembers: false,
  });
  const [targets, setTargets] = useState([]);
  const [uploads, setUploads] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [aiText, setAiText] = useState('');
  const [aiBusy, setAiBusy] = useState(false);
  const [mode, setMode] = useState('edit');
  const [audience, setAudience] = useState(null);
  const [restorable, setRestorable] = useState(null);
  const [savedLocallyAt, setSavedLocallyAt] = useState(null);
  const touched = useRef(false);
  const formRef = useRef(null);

  useEffect(() => {
    Promise.all([NoticeApi.targetOptions(), CategoryApi.list(), DepartmentApi.list(), editing ? NoticeApi.get(id) : null])
      .then(([options, categories, departments, notice]) => {
        setMeta({ options, categories, departments });
        if (notice) {
          setStatus(notice.status);
          setForm({
            title: notice.title, description: notice.description, categoryId: notice.categoryId, priority: notice.priority,
            publishDate: toInputDate(notice.publishDate), expiryDate: toInputDate(notice.expiryDate),
            acknowledgementRequired: notice.acknowledgementRequired && notice.priority === 'NORMAL',
            visibility: notice.visibility || 'PRIVATE', institutionScope: notice.institutionScope || '',
            notifyMembers: notice.visibility === 'PUBLIC' && notice.targets.length > 0,
          });
          setTargets(notice.targets.map((t) => ({ ...t, userName: t.userName || '' })));
        } else {
          setTargets([{ targetType: options.allowedTypes[0], targetRole: ['ROLE', 'ROLE_DEPARTMENT'].includes(options.allowedTypes[0]) ? options.allowedRoles[0] : null, departmentId: options.fixedDepartmentId, semester: null, className: '', userId: null }]);
          if (categories[0]) setForm((f) => ({ ...f, categoryId: categories.find((c) => c.name === 'General')?.id || categories[0].id }));
        }
        const stored = draftStore.read(storeKey);
        // For an existing notice, only offer changes made after its last server save.
        if (stored && (!notice || new Date(stored.savedAt) > new Date(notice.updatedAt))) setRestorable(stored);
      })
      .catch((e) => setLoadError(errorMessage(e)));
  }, [id, editing, storeKey]);

  // Debounced local auto-save, only after the author has changed something.
  useEffect(() => {
    if (!touched.current) return undefined;
    const h = setTimeout(() => {
      const savedAt = new Date().toISOString();
      if (draftStore.write(storeKey, { form, targets, savedAt })) setSavedLocallyAt(savedAt);
    }, AUTOSAVE_MS);
    return () => clearTimeout(h);
  }, [form, targets, storeKey]);

  useEffect(() => { window.scrollTo({ top: 0 }); }, [mode]);

  if (loadError) return <ErrorState message={loadError} />;
  if (!meta) return <SkeletonNotice />;

  const markTouched = () => { touched.current = true; if (restorable) setRestorable(null); };
  const set = (k) => (e) => { markTouched(); setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }); };
  const changeTargets = (t) => { markTouched(); setTargets(t); };

  const restore = () => {
    setForm(restorable.form); setTargets(restorable.targets);
    setSavedLocallyAt(restorable.savedAt); setRestorable(null);
    touched.current = true;
  };
  const discard = () => { draftStore.clear(storeKey); setRestorable(null); };

  const isFuture = form.publishDate && new Date(form.publishDate) > new Date();
  const isPublic = form.visibility === 'PUBLIC';
  // Private notices always go to their targets; public ones only if the author also wants members notified.
  const usesTargets = !isPublic || form.notifyMembers;
  // HODs and teachers publish publicly for their own wing only (the server enforces this too).
  const scopeChoices = ['SUPER_ADMIN', 'PRINCIPAL'].includes(user.role)
    ? ['ENGINEERING', 'POLYTECHNIC', 'BOTH'] : user.institutionType ? [user.institutionType] : [];
  const choose = (patch) => { markTouched(); setForm((f) => ({ ...f, ...patch })); };
  const noAudience = usesTargets && audience?.total === 0;
  const lifecycleOpen = !editing || status === 'DRAFT' || status === 'REJECTED';
  const canPublishFromHere = !meta.options.requiresApproval && (!editing || status === 'DRAFT');
  const canSubmitFromHere = meta.options.requiresApproval && lifecycleOpen;

  /** Saves, then optionally publishes or submits. `then` is null | 'publish' | 'submit'. */
  const save = async (then) => {
    setBusy(then || 'save'); setError('');
    const { notifyMembers, ...fields } = form;
    const body = {
      ...fields, categoryId: Number(form.categoryId), publishDate: fromInputDate(form.publishDate), expiryDate: fromInputDate(form.expiryDate),
      institutionScope: isPublic ? form.institutionScope || null : null,
      targets: usesTargets ? toTargetRequests(targets) : [],
    };
    let saved;
    try {
      saved = editing ? await NoticeApi.update(id, body) : await NoticeApi.create(body);
    } catch (err) {
      setError(errorMessage(err)); setMode('edit'); setBusy(''); return;
    }
    draftStore.clear(storeKey);
    touched.current = false;
    const failed = await uploadAll(saved.id);
    setBusy(then || 'save');
    if (failed > 0) {
      toast(`${failed} file${failed > 1 ? 's were' : ' was'} not attached. Open Edit to add ${failed > 1 ? 'them' : 'it'} again.`, 'warning');
    }
    try {
      if (then === 'publish') {
        const res = await NoticeApi.publish(saved.id);
        toast(res.status === 'SCHEDULED' ? `Scheduled for ${formatDate(res.publishDate)}` : 'Published');
      } else if (then === 'submit') {
        await NoticeApi.submit(saved.id);
        toast('Sent to your HOD for approval');
      } else {
        toast(editing ? 'Changes saved' : 'Draft saved');
      }
    } catch (err) {
      toast(`Saved as a draft, but it was not ${then === 'publish' ? 'published' : 'submitted'}: ${errorMessage(err)}`, 'warning');
    }
    setBusy('');
    navigate(`/notices/${saved.id}`);
  };

  /** Uploads ready files one at a time so each shows its own progress. Returns how many failed. */
  const uploadAll = async (noticeId) => {
    const patch = (key, p) => setUploads((list) => list.map((e) => (e.key === key ? { ...e, ...p } : e)));
    let failed = 0;
    for (const entry of uploads.filter((e) => e.status === 'ready' || e.status === 'error')) {
      setBusy('upload');
      patch(entry.key, { status: 'uploading', progress: 0, error: '' });
      try {
        await AttachmentApi.uploadOne(noticeId, entry.file, (progress) => patch(entry.key, { progress }));
        patch(entry.key, { status: 'done', progress: 100 });
      } catch (err) {
        failed += 1;
        patch(entry.key, { status: 'error', error: errorMessage(err) });
      }
    }
    return failed;
  };

  const saveDraftFromForm = () => { if (formRef.current.reportValidity()) save(null); };

  const aiDraft = async () => {
    setAiBusy(true);
    try {
      const res = await NoticeApi.aiDraft(aiText);
      toast(res.source === 'AI' ? 'AI draft saved. Review it before publishing.' : 'Draft generated offline. Review it before publishing.');
      navigate(`/notices/${res.draft.id}/edit`);
    } catch (err) { toast(errorMessage(err), 'danger'); } finally { setAiBusy(false); }
  };

  const publishLabel = isFuture ? 'Save and schedule' : 'Save and publish';
  const saveLabel = editing ? 'Save changes' : 'Save draft';

  if (mode === 'preview') {
    const category = meta.categories.find((c) => String(c.id) === String(form.categoryId));
    const needsAck = form.priority !== 'NORMAL' || form.acknowledgementRequired;
    return (
      <>
        <h1 className="mb-1">Check your notice</h1>
        <p className="text-secondary">This is how recipients will see it.</p>
        {error && <div className="alert alert-danger py-2">{error}</div>}

        <article className={`notice-card prio-${form.priority} preview-sheet mb-3`}>
          <div className="d-flex flex-wrap gap-2 align-items-center">
            <span className={`prio-label ${form.priority}`}>{PRIORITY[form.priority]}</span>
            <span className="text-secondary small">{category?.name}</span>
            {isPublic && <span className={`scope-chip ${form.institutionScope}`}>Public · {INSTITUTION_LABEL[form.institutionScope]}</span>}
          </div>
          <h2 className="preview-title">{form.title}</h2>
          <p className="text-secondary small mb-3">
            {user.fullName} ({ROLE_LABEL[user.role]}{user.departmentName ? `, ${user.departmentName}` : ''})
            {isFuture ? `, publishes ${formatDate(fromInputDate(form.publishDate))}` : meta.options.requiresApproval ? ', publishes once your HOD approves it' : ', publishes as soon as you publish it'}
            {form.expiryDate && `, expires ${formatDate(fromInputDate(form.expiryDate))}`}
          </p>
          <div className="notice-body mb-3">{form.description}</div>
          {uploads.some((u) => u.status !== 'invalid') && (
            <div className="mb-3">
              <h3 className="h6">Attachments</h3>
              <FileDrop entries={uploads.filter((u) => u.status !== 'invalid')} readOnly />
            </div>
          )}
          {needsAck && <p className="small mb-0">Recipients will be asked to confirm they have read it{form.priority !== 'NORMAL' ? ', and it is also emailed to them' : ''}.</p>}
        </article>

        {isPublic && (
          <div className="audience-summary mb-3">
            <p className="mb-0"><strong>Anyone can read this on the public website</strong> once it is published, under {INSTITUTION_LABEL[form.institutionScope]}.
              {usesTargets ? ' The college members below are also notified.' : ' No one is notified.'}</p>
          </div>
        )}
        {usesTargets && <AudienceSummary targets={targets} onResult={setAudience} />}

        <div className="d-flex flex-wrap gap-2 mt-3">
          <button type="button" className="btn btn-light" onClick={() => setMode('edit')} disabled={!!busy}>Back to editing</button>
          <span className="ms-auto" />
          <button type="button" className={`btn ${canPublishFromHere || canSubmitFromHere ? 'btn-outline-primary' : 'btn-primary'}`} onClick={() => save(null)} disabled={!!busy}>
            {busy === 'save' ? 'Saving…' : busy === 'upload' ? 'Uploading files…' : saveLabel}
          </button>
          {canPublishFromHere && (
            <button type="button" className="btn btn-primary" onClick={() => save('publish')} disabled={!!busy || noAudience}>
              {busy === 'publish' ? 'Publishing…' : publishLabel}
            </button>
          )}
          {canSubmitFromHere && (
            <button type="button" className="btn btn-primary" onClick={() => save('submit')} disabled={!!busy || noAudience}>
              {busy === 'submit' ? 'Submitting…' : 'Save and submit for approval'}
            </button>
          )}
        </div>
        {noAudience && (canPublishFromHere || canSubmitFromHere) && (
          <p className="small text-secondary mt-2 mb-0">Publishing is off because no one would receive this notice. You can still save it as a draft.</p>
        )}
      </>
    );
  }

  return (
    <>
      <h1 className="mb-1">{editing ? 'Edit notice' : 'Create notice'}</h1>
      <p className="text-secondary">
        {meta.options.requiresApproval ? 'Your notice goes to your HOD for approval before students see it.' : 'Check the preview, then publish or keep it as a draft.'}
      </p>

      {restorable && (
        <div className="alert alert-warning d-flex flex-wrap align-items-center gap-2 py-2" role="status">
          <span>You have unsaved changes from {formatDate(restorable.savedAt)}. Attachments need to be chosen again.</span>
          <span className="ms-auto d-flex gap-2">
            <button type="button" className="btn btn-sm btn-primary" onClick={restore}>Restore changes</button>
            <button type="button" className="btn btn-sm btn-light" onClick={discard}>Discard</button>
          </span>
        </div>
      )}

      {!editing && (
        <div className="panel mb-3">
          <label className="form-label fw-semibold" htmlFor="ai">Draft with the assistant</label>
          <textarea id="ai" className="form-control mb-2" rows={2} maxLength={4000} value={aiText} onChange={(e) => setAiText(e.target.value)}
            placeholder="College will remain closed tomorrow because of a public holiday." />
          <button className="btn btn-outline-primary btn-sm" disabled={aiBusy || aiText.trim().length < 5} onClick={aiDraft}>
            {aiBusy ? 'Writing draft…' : 'Write a draft'}
          </button>
          <span className="small text-secondary ms-2">Saved as a draft for you to review; never published automatically.</span>
        </div>
      )}

      <form ref={formRef} className="panel" onSubmit={(e) => { e.preventDefault(); setError(''); setMode('preview'); }}>
        {error && <div className="alert alert-danger py-2">{error}</div>}
        <div className="mb-3">
          <label className="form-label" htmlFor="title">Title</label>
          <input id="title" className="form-control" required maxLength={200} value={form.title} onChange={set('title')} />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="desc">Notice text</label>
          <textarea id="desc" className="form-control" required rows={7} maxLength={20000} value={form.description} onChange={set('description')} />
        </div>
        <div className="row g-3 mb-3">
          <div className="col-md-3">
            <label className="form-label" htmlFor="category">Category</label>
            <select id="category" className="form-select" required value={form.categoryId} onChange={set('categoryId')}>
              {meta.categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div className="col-md-3">
            <label className="form-label" htmlFor="priority">Priority</label>
            <select id="priority" className="form-select" value={form.priority} onChange={set('priority')}>
              <option value="NORMAL">Normal</option><option value="IMPORTANT">Important</option><option value="URGENT">Urgent</option>
            </select>
          </div>
          <div className="col-md-3">
            <label className="form-label" htmlFor="publish">Publish on</label>
            <input id="publish" type="datetime-local" className="form-control" value={form.publishDate} onChange={set('publishDate')} disabled={status === 'PUBLISHED'} />
            <div className="form-text">Leave empty to publish immediately.</div>
          </div>
          <div className="col-md-3">
            <label className="form-label" htmlFor="expiry">Expires on</label>
            <input id="expiry" type="datetime-local" className="form-control" value={form.expiryDate} onChange={set('expiryDate')} />
          </div>
        </div>
        {form.priority === 'NORMAL'
          ? (
            <div className="form-check mb-3">
              <input id="ack" type="checkbox" className="form-check-input" checked={form.acknowledgementRequired} onChange={set('acknowledgementRequired')} />
              <label htmlFor="ack" className="form-check-label">Ask recipients to confirm they have read it</label>
            </div>
          )
          : <p className="small text-secondary">Important and urgent notices always ask recipients to confirm, and are also emailed.</p>}

        <fieldset className="mb-3">
          <legend className="form-label fw-semibold fs-6">Visibility</legend>
          <div className="role-choice two mb-2">
            <label className={`role-option ${!isPublic ? 'is-on' : ''}`}>
              <input type="radio" name="visibility" value="PRIVATE" checked={!isPublic} onChange={() => choose({ visibility: 'PRIVATE' })} />
              <span>Private<small>College members you choose</small></span>
            </label>
            <label className={`role-option ${isPublic ? 'is-on' : ''} ${scopeChoices.length === 0 ? 'is-disabled' : ''}`}>
              <input type="radio" name="visibility" value="PUBLIC" checked={isPublic} disabled={scopeChoices.length === 0}
                onChange={() => choose({ visibility: 'PUBLIC', institutionScope: form.institutionScope || (scopeChoices.length === 1 ? scopeChoices[0] : '') })} />
              <span>Public<small>Anyone, on the public website</small></span>
            </label>
          </div>
          {isPublic && (
            <div className="panel-inset">
              <p className="form-label mb-1" id="scope-l">Show it under</p>
              <div className="d-flex flex-wrap gap-3 mb-2" role="radiogroup" aria-labelledby="scope-l">
                {scopeChoices.map((s) => (
                  <div key={s} className="form-check">
                    <input id={`scope-${s}`} className="form-check-input" type="radio" name="scope" required checked={form.institutionScope === s}
                      onChange={() => choose({ institutionScope: s })} />
                    <label htmlFor={`scope-${s}`} className="form-check-label">{INSTITUTION_LABEL[s]}</label>
                  </div>
                ))}
              </div>
              <p className="small text-secondary mb-2">
                Visible without signing in from the moment it is published until it expires.
                {meta.options.requiresApproval && ' Your HOD approves it first, like any teacher notice.'}
              </p>
              <div className="form-check">
                <input id="notify" type="checkbox" className="form-check-input" checked={form.notifyMembers} onChange={(e) => choose({ notifyMembers: e.target.checked })} />
                <label htmlFor="notify" className="form-check-label">Also notify college members</label>
              </div>
            </div>
          )}
        </fieldset>

        {usesTargets && (
          <fieldset className="mb-3">
            <legend className="form-label fw-semibold fs-6">{isPublic ? 'Members to notify' : 'Who should see this notice'}</legend>
            <div className="row g-3">
              <div className="col-lg-8">
                <TargetBuilder options={meta.options} departments={meta.departments} value={targets} onChange={changeTargets} />
              </div>
              <div className="col-lg-4">
                <AudienceSummary targets={targets} onResult={setAudience} />
              </div>
            </div>
          </fieldset>
        )}

        <div className="mb-4">
          <p className="form-label mb-1">Attachments</p>
          <FileDrop entries={uploads} onChange={setUploads} disabled={!!busy} />
        </div>

        <div className="d-flex flex-wrap align-items-center gap-2">
          <button className="btn btn-primary" disabled={!!busy}>Preview</button>
          <button type="button" className="btn btn-outline-primary" disabled={!!busy} onClick={saveDraftFromForm}>{busy === 'save' ? 'Saving…' : busy === 'upload' ? 'Uploading files…' : saveLabel}</button>
          <button type="button" className="btn btn-light" onClick={() => navigate(-1)}>Cancel</button>
          {savedLocallyAt && (
            <span className="autosave-note ms-auto" role="status">Unsaved changes kept on this device at {formatTime(savedLocallyAt)}</span>
          )}
        </div>
      </form>
    </>
  );
}
