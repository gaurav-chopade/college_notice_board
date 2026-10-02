import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Empty, ErrorState, SkeletonRows } from '../components/States';
import { useToast } from '../context/ToastContext';
import { NotificationApi, errorMessage } from '../services/api';
import { formatDate, formatTime } from '../utils/format';

const PAGE_SIZE = 20;

function groupOf(date, now) {
  const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOfDay(now) - startOfDay(date)) / 86400000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  if (days < 7) return 'Earlier this week';
  return 'Older';
}

/** Notifications grouped by day, with an unread filter, per-item actions and "show older". */
export default function Notifications() {
  const navigate = useNavigate();
  const toast = useToast();
  const [filter, setFilter] = useState('all');
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback((pageNumber, replace) => {
    setLoading(true); setError('');
    return NotificationApi.list({ page: pageNumber, size: PAGE_SIZE, unreadOnly: filter === 'unread' })
      .then((res) => { setPage(res); setItems((prev) => (replace ? res.content : [...prev, ...res.content])); })
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false));
  }, [filter]);

  useEffect(() => { load(0, true); }, [load]);

  const patch = (id, p) => setItems((list) => list.map((n) => (n.id === id ? { ...n, ...p } : n)));

  const open = async (n) => {
    if (!n.read) await NotificationApi.read(n.id).catch(() => {});
    if (n.noticeId) navigate(`/notices/${n.noticeId}`);
    else if (n.link) navigate(n.link);
    else patch(n.id, { read: true });
  };
  const markRead = async (n) => {
    try {
      await NotificationApi.read(n.id);
      if (filter === 'unread') setItems((list) => list.filter((x) => x.id !== n.id)); else patch(n.id, { read: true });
    } catch (e) { toast(errorMessage(e), 'danger'); }
  };
  const remove = async (n) => {
    try { await NotificationApi.remove(n.id); setItems((list) => list.filter((x) => x.id !== n.id)); toast('Notification deleted'); }
    catch (e) { toast(errorMessage(e), 'danger'); }
  };
  const readAll = async () => {
    try {
      await NotificationApi.readAll();
      if (filter === 'unread') setItems([]); else setItems((list) => list.map((n) => ({ ...n, read: true })));
      toast('All notifications marked as read');
    } catch (e) { toast(errorMessage(e), 'danger'); }
  };

  const now = new Date();
  const groups = [];
  items.forEach((n) => {
    const g = groupOf(new Date(n.createdAt), now);
    if (!groups.length || groups[groups.length - 1].name !== g) groups.push({ name: g, items: [] });
    groups[groups.length - 1].items.push(n);
  });
  const hasUnread = items.some((n) => !n.read);

  return (
    <>
      <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
        <h1 className="mb-0 me-auto">Notifications</h1>
        <div className="seg" role="group" aria-label="Show">
          <button type="button" aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>All</button>
          <button type="button" aria-pressed={filter === 'unread'} onClick={() => setFilter('unread')}>Unread</button>
        </div>
        <button className="btn btn-sm btn-outline-secondary" onClick={readAll} disabled={!hasUnread}>Mark all as read</button>
      </div>

      {error && <ErrorState message={error} onRetry={() => load(0, true)} />}
      {loading && items.length === 0 && <SkeletonRows />}
      {!loading && !error && items.length === 0 && (filter === 'unread'
        ? <Empty title="You're all caught up" action={<button className="btn btn-sm btn-outline-primary" onClick={() => setFilter('all')}>Show all notifications</button>}>No unread notifications.</Empty>
        : <Empty title="No notifications yet" action={<Link className="btn btn-sm btn-outline-primary" to="/notices">Browse notices</Link>}>You are notified here when a notice is published for you.</Empty>)}

      {groups.map((g) => (
        <section key={g.name} className="mb-3" aria-labelledby={`g-${g.name}`}>
          <h2 id={`g-${g.name}`} className="notif-group">{g.name}</h2>
          <ul className="notif-list">
            {g.items.map((n) => (
              <li key={n.id} className={`notif-item prio-${n.noticePriority || 'NONE'} ${n.read ? '' : 'unread'}`}>
                <button type="button" className="notif-main" onClick={() => open(n)}>
                  <span className="notif-msg">{n.read ? '' : <span className="visually-hidden">Unread: </span>}{n.message}</span>
                  <span className="notif-time">{g.name === 'Today' || g.name === 'Yesterday' ? formatTime(n.createdAt) : formatDate(n.createdAt)}</span>
                </button>
                <span className="notif-actions">
                  {!n.read && <button type="button" className="btn btn-sm btn-link" onClick={() => markRead(n)}>Mark as read</button>}
                  <button type="button" className="btn btn-sm btn-link text-danger" onClick={() => remove(n)} aria-label={`Delete notification: ${n.message}`}>Delete</button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      ))}

      {page && !page.last && items.length > 0 && (
        <div className="text-center">
          <button className="btn btn-sm btn-outline-secondary" disabled={loading} onClick={() => load(page.pageNumber + 1, false)}>
            {loading ? 'Loading…' : 'Show older'}
          </button>
        </div>
      )}
    </>
  );
}
