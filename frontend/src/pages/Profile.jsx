import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { ThemeChoice } from '../components/ThemeControl';
import { UserApi, errorMessage } from '../services/api';
import { ROLE_LABEL } from '../utils/format';

export default function Profile() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const [profile, setProfile] = useState({ fullName: user.fullName, phone: user.phone || '' });
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '' });

  const saveProfile = async (e) => {
    e.preventDefault();
    try { setUser(await UserApi.updateMe(profile)); toast('Profile saved'); } catch (err) { toast(errorMessage(err), 'danger'); }
  };
  const savePw = async (e) => {
    e.preventDefault();
    try { await UserApi.changePassword(pw); setPw({ currentPassword: '', newPassword: '' }); toast('Password changed'); }
    catch (err) { toast(errorMessage(err), 'danger'); }
  };

  return (
    <>
      <h1 className="mb-3">Profile</h1>
      <div className="row g-3">
        <div className="col-lg-6">
          <form className="panel" onSubmit={saveProfile}>
            <p className="text-secondary small">{user.email} · {ROLE_LABEL[user.role]}
              {user.departmentName && ` · ${user.departmentName}`}{user.semester && ` · semester ${user.semester}, class ${user.className}`}</p>
            <label className="form-label">Full name</label>
            <input className="form-control mb-2" required value={profile.fullName} onChange={(e) => setProfile({ ...profile, fullName: e.target.value })} />
            <label className="form-label">Phone</label>
            <input className="form-control mb-3" value={profile.phone} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} />
            <button className="btn btn-primary">Save profile</button>
          </form>
        </div>
        <div className="col-lg-6">
          <form className="panel" onSubmit={savePw}>
            <h2 className="h6">Change password</h2>
            <label className="form-label">Current password</label>
            <input type="password" className="form-control mb-2" required value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} />
            <label className="form-label">New password</label>
            <input type="password" className="form-control mb-3" required value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} />
            <button className="btn btn-primary">Change password</button>
          </form>
        </div>
      </div>
      <section className="panel mt-3" aria-labelledby="appearance-h">
        <h2 id="appearance-h" className="h6">Appearance</h2>
        <p className="small text-secondary">Saved on this device.</p>
        <ThemeChoice />
      </section>
    </>
  );
}
