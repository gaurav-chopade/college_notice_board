export default function Pagination({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  const { pageNumber, totalPages, totalElements } = page;
  return (
    <nav className="d-flex align-items-center justify-content-between mt-3" aria-label="Pages">
      <small className="text-secondary">{totalElements} total · page {pageNumber + 1} of {totalPages}</small>
      <div className="btn-group">
        <button className="btn btn-sm btn-outline-secondary" disabled={pageNumber === 0} onClick={() => onChange(pageNumber - 1)}>Previous</button>
        <button className="btn btn-sm btn-outline-secondary" disabled={page.last} onClick={() => onChange(pageNumber + 1)}>Next</button>
      </div>
    </nav>
  );
}
