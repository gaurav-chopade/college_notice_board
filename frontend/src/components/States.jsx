export const Loading = ({ label = 'Loading…' }) => (
  <div className="empty"><div className="spinner-border spinner-border-sm me-2" role="status" />{label}</div>
);

/** Empty state that tells people what to do next. `action` is an optional button or link. */
export const Empty = ({ title, children, action }) => (
  <div className="empty panel">
    <p className="empty-title">{title}</p>
    {children && <p className="mb-0">{children}</p>}
    {action && <div className="mt-3">{action}</div>}
  </div>
);

export const ErrorState = ({ message, onRetry }) => (
  <div className="alert alert-danger d-flex justify-content-between align-items-center">
    <span>{message}</span>
    {onRetry && <button className="btn btn-sm btn-outline-danger" onClick={onRetry}>Try again</button>}
  </div>
);

const Bar = ({ w, h = '.8rem', className = '' }) => <span className={`sk ${className}`} style={{ width: w, height: h }} />;

/** Placeholder notice cards shaped like the real ones, shown while a list loads. */
export const SkeletonCards = ({ count = 6 }) => (
  <div className="notice-grid" aria-busy="true" aria-label="Loading notices">
    {Array.from({ length: count }, (_, i) => (
      <div key={i} className="notice-card sk-card" aria-hidden="true">
        <Bar w="35%" h=".7rem" />
        <Bar w={i % 2 ? '70%' : '85%'} h="1.1rem" />
        <Bar w="100%" /><Bar w="90%" />
        <Bar w="55%" h=".7rem" className="mt-2" />
      </div>
    ))}
  </div>
);

/** Placeholder rows for list-style pages such as notifications. */
export const SkeletonRows = ({ count = 5 }) => (
  <div className="panel p-0" aria-busy="true" aria-label="Loading">
    {Array.from({ length: count }, (_, i) => (
      <div key={i} className="sk-row" aria-hidden="true">
        <Bar w={i % 2 ? '60%' : '75%'} /><Bar w="25%" h=".7rem" />
      </div>
    ))}
  </div>
);

/** Placeholder for a single notice page. */
export const SkeletonNotice = () => (
  <div className="notice-card sk-card" style={{ padding: '1.5rem' }} aria-busy="true" aria-label="Loading notice">
    <Bar w="20%" h=".7rem" /><Bar w="60%" h="1.6rem" /><Bar w="40%" h=".7rem" />
    <div className="mt-3 d-flex flex-column gap-2"><Bar w="100%" /><Bar w="96%" /><Bar w="88%" /><Bar w="70%" /></div>
  </div>
);

/** Placeholder for the dashboard's summary numbers and recent notices. */
export const SkeletonDashboard = () => (
  <>
    <div className="sk mb-3" style={{ width: '40%', height: '1.8rem' }} />
    <div className="stat-row mb-4" aria-hidden="true">
      {Array.from({ length: 4 }, (_, i) => <div key={i} className="stat"><Bar w="40%" h="1.8rem" /><Bar w="70%" h=".7rem" className="mt-2" /></div>)}
    </div>
    <SkeletonCards count={4} />
  </>
);
