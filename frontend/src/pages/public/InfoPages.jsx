import { Link } from 'react-router-dom';
import { InstitutionCards } from './Landing';
import PublicNotices from './PublicNotices';
import { useCollege, useInstitutions } from '../../hooks/usePublicData';

export function Admissions() {
  return (
    <PublicNotices title="Admissions" fixedCategoryNames={['Admission']}
      intro="Admission notices for Engineering (B.Tech) and Polytechnic (Diploma). Choose a wing below, or search." />
  );
}

export function About() {
  const college = useCollege();
  const institutions = useInstitutions();
  return (
    <div className="pub-wrap pub-page">
      <h1 className="pub-page-title">About {college?.name || 'the college'}</h1>
      {college?.about && <p className="pub-about mb-4">{college.about}</p>}
      <InstitutionCards institutions={institutions} />
      <div className="about-band mt-5">
        <div>
          <h2 className="pub-h2">Visit us</h2>
          <p className="pub-about">{college?.address}</p>
          {college?.officeHours && <p className="text-secondary">Office hours: {college.officeHours}</p>}
        </div>
        <div className="contact-card">
          {college?.phone && <p>Phone <a href={`tel:${college.phone.replace(/\s/g, '')}`}>{college.phone}</a></p>}
          {college?.email && <p className="mb-0">Email <a href={`mailto:${college.email}`}>{college.email}</a></p>}
        </div>
      </div>
    </div>
  );
}

const FAQ = [
  ['Do I need an account to read admission notices?', 'No. Everything on the public pages, including admission notices and their attachments, can be read without signing in.'],
  ['Where do I find admission dates and documents?', <>Open <Link to="/admissions">Admissions</Link> and choose Engineering or Polytechnic. Each notice lists dates and any forms to download.</>],
  ['I am a student or staff member. How do I get an account?', <>Choose <Link to="/register">Request an account</Link> on the login page. Your class teacher, HOD or the Principal approves it, and you can sign in once it is approved.</>],
  ['I cannot open an attachment.', 'Download links are valid for a few minutes. Reload the notice and click the attachment again. If it still fails, call the office.'],
  ['A notice I saw earlier is gone.', 'Notices are removed from the public site when they expire or are withdrawn. Call the admission helpline if you need the information.'],
];

export function Help() {
  const college = useCollege();
  return (
    <div className="pub-wrap pub-page pub-narrow">
      <h1 className="pub-page-title">Help</h1>
      <div className="faq">
        {FAQ.map(([q, a]) => (
          <details key={q}>
            <summary>{q}</summary>
            <p>{a}</p>
          </details>
        ))}
      </div>
      <aside className="contact-card mt-4">
        <p className="pub-footer-h">Still need help?</p>
        {college?.admissionPhone && <p className="mb-1">Admission helpline <a href={`tel:${college.admissionPhone.replace(/\s/g, '')}`}>{college.admissionPhone}</a></p>}
        {college?.email && <p className="mb-0">Email <a href={`mailto:${college.email}`}>{college.email}</a></p>}
      </aside>
    </div>
  );
}
