import { Link } from 'react-router-dom';
import InstitutionIcon from '../../components/InstitutionIcon';
import PublicNoticeCard from '../../components/PublicNoticeCard';
import { SkeletonCards } from '../../components/States';
import { useAsync } from '../../hooks/useAsync';
import { useCollege, useInstitutions } from '../../hooks/usePublicData';
import { PublicApi } from '../../services/api';

/** Engineering or Polytechnic: programmes and departments from the database, plus that wing's public notices. */
export default function InstitutionPage({ type }) {
  const label = type === 'ENGINEERING' ? 'Engineering' : 'Polytechnic';
  const other = type === 'ENGINEERING' ? 'POLYTECHNIC' : 'ENGINEERING';
  const institutions = useInstitutions();
  const college = useCollege();
  const info = institutions?.find((i) => i.type === type);
  const notices = useAsync(() => PublicApi.notices({ institution: type, size: 6 }), [type]);

  return (
    <>
      <section className={`inst-hero ${type}`}>
        <div className="pub-wrap">
          <p className="pub-eyebrow"><InstitutionIcon type={type} size={18} /> {college?.name}</p>
          <h1 className="pub-hero-title">{label}</h1>
          <p className="pub-hero-lead">{info?.about || (type === 'ENGINEERING' ? 'Degree engineering programmes.' : 'Diploma programmes.')}</p>
          <div className="d-flex flex-wrap gap-2">
            <Link className="btn btn-primary" to={`/admissions?institution=${type}`}>{label} admissions</Link>
            <Link className="btn btn-outline-primary" to={`/public-notices?institution=${type}`}>All {label} notices</Link>
          </div>
        </div>
      </section>

      <section className="pub-section" aria-labelledby="dept-h">
        <div className="pub-wrap">
          <h2 id="dept-h" className="pub-h2">Programmes and departments</h2>
          {!info && <SkeletonCards count={3} />}
          {info && info.departments.length === 0 && <p className="text-secondary">Department details will be published soon.</p>}
          {info && info.departments.length > 0 && (
            <ul className="dept-grid">
              {info.departments.map((d) => (
                <li key={d.code} className="dept-card">
                  <span className="dept-program">{d.programName}</span>
                  <span className="dept-name">{d.name}</span>
                  <span className="dept-len">{d.semesterCount / 2} years · {d.semesterCount} semesters</span>
                  {d.description && <span className="dept-desc">{d.description}</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <section className="pub-section alt" aria-labelledby="inst-notices-h">
        <div className="pub-wrap">
          <div className="pub-section-head">
            <h2 id="inst-notices-h" className="pub-h2 mb-0">Latest {label} notices</h2>
            <Link className="ms-auto" to={`/public-notices?institution=${type}`}>View all →</Link>
          </div>
          {notices.loading && <SkeletonCards count={3} />}
          {notices.data?.content.length === 0 && <p className="text-secondary">No {label} notices at the moment.</p>}
          {notices.data?.content.length > 0 && <div className="pub-grid">{notices.data.content.map((n) => <PublicNoticeCard key={n.id} notice={n} />)}</div>}
        </div>
      </section>

      <section className="pub-section" aria-labelledby="inst-contact-h">
        <div className="pub-wrap about-band">
          <div>
            <h2 id="inst-contact-h" className="pub-h2">Contact the admission office</h2>
            <p className="pub-about">{college?.officeHours && `Open ${college.officeHours}. `}{college?.address}</p>
            <Link to={`/${other.toLowerCase()}`}>Looking for {other === 'ENGINEERING' ? 'Engineering' : 'Polytechnic'} instead? →</Link>
          </div>
          <div className="contact-card">
            {college?.admissionPhone && <p>Admission helpline<br /><a className="contact-big" href={`tel:${college.admissionPhone.replace(/\s/g, '')}`}>{college.admissionPhone}</a></p>}
            {college?.email && <p className="mb-0">Email <a href={`mailto:${college.email}`}>{college.email}</a></p>}
          </div>
        </div>
      </section>
    </>
  );
}
