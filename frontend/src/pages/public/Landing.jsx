import { Link } from 'react-router-dom';
import InstitutionIcon from '../../components/InstitutionIcon';
import PublicNoticeCard from '../../components/PublicNoticeCard';
import { SkeletonCards } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import { useCollege, useInstitutions, usePublicCategories } from '../../hooks/usePublicData';
import { PublicApi } from '../../services/api';
import { formatDate } from '../../utils/format';

export const findCategory = (categories, name) => categories?.find((c) => c.name.toLowerCase() === name.toLowerCase());

export function InstitutionCards({ institutions }) {
  return (
    <div className="inst-grid">
      {['ENGINEERING', 'POLYTECHNIC'].map((type) => {
        const info = institutions?.find((i) => i.type === type);
        const label = type === 'ENGINEERING' ? 'Engineering' : 'Polytechnic';
        return (
          <Link key={type} to={`/${type.toLowerCase()}`} className={`inst-card ${type}`}>
            <span className="inst-icon"><InstitutionIcon type={type} size={34} /></span>
            <span className="inst-kicker">{type === 'ENGINEERING' ? 'Degree programmes' : 'Diploma programmes'}</span>
            <span className="inst-title">{label}</span>
            <span className="inst-meta">
              {info ? `${info.programs.join(', ') || (type === 'ENGINEERING' ? 'B.Tech' : 'Diploma')} · ${info.departments.length} department${info.departments.length === 1 ? '' : 's'}` : ' '}
            </span>
            {info?.about && <span className="inst-about">{info.about}</span>}
            <span className="inst-cta">Explore {label} →</span>
          </Link>
        );
      })}
    </div>
  );
}

/** Public home page: what the college is, the two wings, and what is on the notice board today. */
export default function Landing() {
  const college = useCollege();
  const institutions = useInstitutions();
  const categories = usePublicCategories();
  const latest = useAsync(() => PublicApi.notices({ size: 6 }), []);
  const admissionCat = findCategory(categories, 'Admission');
  const admissions = useAsync(() => (admissionCat ? PublicApi.notices({ categoryId: admissionCat.id, size: 4 }) : Promise.resolve(null)), [admissionCat?.id]);
  const pinned = latest.data?.content?.slice(0, 3) || [];

  return (
    <>
      <section className="pub-hero">
        <div className="pub-wrap pub-hero-grid">
          <div>
            <p className="pub-eyebrow">Engineering · Polytechnic</p>
            <h1 className="pub-hero-title">Welcome to {college?.name || 'the college'}</h1>
            <p className="pub-hero-lead">{college?.tagline}</p>
            <div className="d-flex flex-wrap gap-2">
              <Link to="/public-notices" className="btn btn-primary btn-lg">View public notices</Link>
              <Link to="/login" className="btn btn-outline-primary btn-lg">Login to college portal</Link>
            </div>
          </div>
          <div className="hero-board" aria-label="On the notice board today">
            <p className="hero-board-label">On the board today</p>
            {latest.loading && <div className="hero-sheet sk-card" />}
            {!latest.loading && pinned.length === 0 && <div className="hero-sheet"><p className="mb-0 text-secondary">New announcements will be pinned here.</p></div>}
            {pinned.map((n, i) => (
              <Link key={n.id} to={`/public-notices/${n.id}`} className={`hero-sheet tilt-${i} prio-${n.priority}`}>
                <span className="hero-sheet-meta">{n.categoryName} · {formatDate(n.publishDate, false)}</span>
                <span className="hero-sheet-title">{n.title}</span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      <section className="pub-section" aria-labelledby="choose-h">
        <div className="pub-wrap">
          <h2 id="choose-h" className="pub-h2">Choose your institution</h2>
          <p className="pub-sub">One college, two wings. Each has its own departments, programmes and notices.</p>
          <InstitutionCards institutions={institutions} />
        </div>
      </section>

      <section className="pub-section alt" aria-labelledby="latest-h">
        <div className="pub-wrap">
          <div className="pub-section-head">
            <h2 id="latest-h" className="pub-h2 mb-0">Latest public notices</h2>
            <Link to="/public-notices" className="ms-auto">View all notices →</Link>
          </div>
          {latest.loading && <SkeletonCards count={3} />}
          {latest.data && latest.data.content.length === 0 && <p className="text-secondary">No public notices at the moment. Please check again soon.</p>}
          {latest.data && latest.data.content.length > 0 && (
            <div className="pub-grid">{latest.data.content.map((n) => <PublicNoticeCard key={n.id} notice={n} />)}</div>
          )}
        </div>
      </section>

      <section className="pub-section" aria-labelledby="adm-h">
        <div className="pub-wrap admissions-band">
          <div>
            <h2 id="adm-h" className="pub-h2">Admissions</h2>
            <p className="pub-sub">Schedules, documents and dates for new applicants. No account needed.</p>
            <div className="d-flex flex-wrap gap-2">
              <Link className="btn btn-outline-primary" to="/admissions?institution=ENGINEERING">Engineering admissions</Link>
              <Link className="btn btn-outline-primary" to="/admissions?institution=POLYTECHNIC">Polytechnic admissions</Link>
            </div>
          </div>
          <ul className="adm-list">
            {(admissions.data?.content || []).map((n) => (
              <li key={n.id}>
                <Link to={`/public-notices/${n.id}`}>{n.title}</Link>
                <span>{formatDate(n.publishDate, false)}</span>
              </li>
            ))}
            {admissions.data && admissions.data.content.length === 0 && <li className="text-secondary">Admission notices will appear here when they are announced.</li>}
          </ul>
        </div>
      </section>

      {(college?.about || college?.address) && (
        <section className="pub-section alt" aria-labelledby="about-h">
          <div className="pub-wrap about-band">
            <div>
              <h2 id="about-h" className="pub-h2">About the college</h2>
              <p className="pub-about">{college.about}</p>
              <Link to="/about">More about us →</Link>
            </div>
            <div className="contact-card">
              <p className="pub-footer-h">Need help?</p>
              {college.admissionPhone && <p>Admission helpline<br /><a href={`tel:${college.admissionPhone.replace(/\s/g, '')}`} className="contact-big">{college.admissionPhone}</a></p>}
              {college.email && <p className="mb-0">Email <a href={`mailto:${college.email}`}>{college.email}</a></p>}
            </div>
          </div>
        </section>
      )}
    </>
  );
}
