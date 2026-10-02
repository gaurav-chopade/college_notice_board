import { useState } from 'react';
import ConfirmDialog from '../../components/ConfirmDialog';
import { ErrorState, Loading } from '../../components/States';
import { useToast } from '../../context/ToastContext';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../services/api';

/** Small table + inline form used by the department and category pages. */
export default function SimpleCrud({ title, api, fields, columns, empty }) {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.list(), []);
  const [form, setForm] = useState(empty);
  const [editingId, setEditingId] = useState(null);
  const [toDelete, setToDelete] = useState(null);

  const save = async (e) => {
    e.preventDefault();
    try {
      if (editingId) await api.update(editingId, form); else await api.create(form);
      toast(editingId ? 'Saved' : 'Added');
      setForm(empty); setEditingId(null); reload();
    } catch (err) { toast(errorMessage(err), 'danger'); }
  };

  return (
    <>
      <h1 className="mb-3">{title}</h1>
      <form className="panel mb-3 filters" onSubmit={save}>
        {fields.map((f) => f.type === 'select' ? (
          <select key={f.key} className="form-select" aria-label={f.label} required={f.required} value={form[f.key] || ''}
            onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}>
            {f.options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        ) : f.type === 'number' ? (
          <input key={f.key} type="number" min={f.min} max={f.max} className="form-control" placeholder={f.label} aria-label={f.label}
            value={form[f.key] ?? ''} onChange={(e) => setForm({ ...form, [f.key]: e.target.value === '' ? null : Number(e.target.value) })} />
        ) : f.type === 'checkbox' ? (
          <div key={f.key} className="form-check align-self-center" style={{ flex: '0 0 auto' }}>
            <input id={f.key} type="checkbox" className="form-check-input" checked={!!form[f.key]} onChange={(e) => setForm({ ...form, [f.key]: e.target.checked })} />
            <label htmlFor={f.key} className="form-check-label">{f.label}</label>
          </div>
        ) : (
          <input key={f.key} className="form-control" placeholder={f.label} aria-label={f.label} required={f.required}
            value={form[f.key] || ''} onChange={(e) => setForm({ ...form, [f.key]: e.target.value })} />
        ))}
        <button className="btn btn-primary" style={{ flex: '0 0 auto' }}>{editingId ? 'Save' : 'Add'}</button>
        {editingId && <button type="button" className="btn btn-light" style={{ flex: '0 0 auto' }} onClick={() => { setForm(empty); setEditingId(null); }}>Cancel</button>}
      </form>
      {loading && <Loading />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && (
        <div className="panel p-0 table-responsive">
          <table className="table mb-0 align-middle">
            <thead><tr>{columns.map((c) => <th key={c.key}>{c.label}</th>)}<th /></tr></thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.id}>
                  {columns.map((c) => <td key={c.key}>{c.render ? c.render(row) : row[c.key] || '—'}</td>)}
                  <td className="text-end text-nowrap">
                    <button className="btn btn-sm btn-light" onClick={() => { setEditingId(row.id); setForm(row); }}>Edit</button>{' '}
                    <button className="btn btn-sm btn-outline-danger" onClick={() => setToDelete(row)}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ConfirmDialog open={!!toDelete} title={`Delete ${toDelete?.name}?`} confirmLabel="Delete" variant="danger"
        onClose={() => setToDelete(null)}
        onConfirm={async () => { try { await api.remove(toDelete.id); toast('Deleted'); reload(); } catch (e) { toast(errorMessage(e), 'danger'); } setToDelete(null); }} />
    </>
  );
}
