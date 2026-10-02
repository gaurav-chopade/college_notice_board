import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useCollege } from '../hooks/usePublicData';
import { ThemeToggle } from './ThemeControl';

const NAV = [
  ['/', 'Home', true], ['/public-notices', 'Public notices'], ['/admissions', 'Admissions'],
  ['/engineering', 'Engineering'], ['/polytechnic', 'Polytechnic'], ['/about', 'About'], ['/help', 'Help'],
];

/** Frame for visitors who are not signed in: header with navigation, page, footer with contact details. */
export default function PublicLayout() {
  const college = useCollege();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => { setOpen(false); window.scrollTo({ top: 0 }); }, [location.pathname]);
  useEffect(() => { if (college?.name) document.title = college.name; }, [college]);

  return (
    <div className="public-site">
      <a href="#main" className="skip-link">Skip to content</a>
      <header className="pub-header">
        <div className="pub-wrap pub-header-row">
          <Link to="/" className="pub-brand">
            <span className="pub-crest" aria-hidden="true">{(college?.name || 'C').slice(0, 1)}</span>
            <span>{college?.name || 'College'}<small>Notice board</small></span>
          </Link>
          <button type="button" className="pub-menu-btn" aria-expanded={open} aria-controls="pub-nav" onClick={() => setOpen((o) => !o)}>
            {open ? 'Close' : 'Menu'}
          </button>
          <nav id="pub-nav" className={`pub-nav ${open ? 'is-open' : ''}`} aria-label="Main">
            {NAV.map(([to, label, end]) => <NavLink key={to} to={to} end={end}>{label}</NavLink>)}
            <span className="pub-nav-actions">
              <ThemeToggle />
              <Link to="/login" className="btn btn-primary btn-sm">Login</Link>
            </span>
          </nav>
        </div>
      </header>

      <main id="main"><Outlet /></main>

      <footer className="pub-footer">
        <div className="pub-wrap pub-footer-grid">
          <div>
            <p className="pub-footer-name">{college?.name}</p>
            {college?.address && <p>{college.address}</p>}
            {college?.officeHours && <p>Office: {college.officeHours}</p>}
          </div>
          <div>
            <p className="pub-footer-h">Contact</p>
            {college?.phone && <p>Phone: <a href={`tel:${college.phone.replace(/\s/g, '')}`}>{college.phone}</a></p>}
            {college?.admissionPhone && <p>Admissions: <a href={`tel:${college.admissionPhone.replace(/\s/g, '')}`}>{college.admissionPhone}</a></p>}
            {college?.email && <p>Email: <a href={`mailto:${college.email}`}>{college.email}</a></p>}
          </div>
          <div>
            <p className="pub-footer-h">Explore</p>
            <p><Link to="/engineering">Engineering</Link> · <Link to="/polytechnic">Polytechnic</Link></p>
            <p><Link to="/admissions">Admissions</Link> · <Link to="/public-notices">Public notices</Link></p>
            <p><Link to="/login">Login to college portal</Link></p>
          </div>
        </div>
        <div className="pub-wrap pub-copy">© {new Date().getFullYear()} {college?.name}</div>
      </footer>
    </div>
  );
}
