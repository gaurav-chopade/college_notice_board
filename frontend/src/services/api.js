import axios from 'axios';

const TOKEN_KEY = 'cnb_token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api' });

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401 && tokenStore.get()) {
      tokenStore.clear();
      if (!window.location.pathname.startsWith('/login')) window.location.assign('/login?expired=1');
    }
    return Promise.reject(err);
  }
);

/** Field-level validation errors from the API ({ field: message }), if any. */
export const fieldErrors = (err) => err?.response?.data?.errors || {};

/** Turns an API error into a human-readable sentence. */
export function errorMessage(err) {
  const data = err?.response?.data;
  if (data?.errors) return Object.values(data.errors).join('. ');
  if (data?.message) return data.message;
  if (err?.request && !err?.response) return 'Cannot reach the server. Check that the backend is running.';
  return 'Something went wrong. Try again.';
}

const d = (p) => p.then((r) => r.data);

export const AuthApi = {
  login: (body) => d(api.post('/auth/login', body)),
  register: (body) => d(api.post('/auth/register', body)),
  forgot: (email) => d(api.post('/auth/forgot-password', { email })),
  reset: (token, newPassword) => d(api.post('/auth/reset-password', { token, newPassword })),
  logout: () => d(api.post('/auth/logout')),
};

export const UserApi = {
  me: () => d(api.get('/users/me')),
  updateMe: (body) => d(api.put('/users/me', body)),
  changePassword: (body) => d(api.put('/users/me/password', body)),
  list: (params) => d(api.get('/users', { params })),
  create: (body) => d(api.post('/users', body)),
  update: (id, body) => d(api.put(`/users/${id}`, body)),
  activate: (id) => d(api.put(`/users/${id}/activate`)),
  deactivate: (id) => d(api.put(`/users/${id}/deactivate`)),
  suspend: (id, reason) => d(api.put(`/users/${id}/suspend`, { reason })),
  lookup: (params) => d(api.get('/users/lookup', { params })),
};

export const DepartmentApi = {
  list: () => d(api.get('/departments')),
  create: (b) => d(api.post('/departments', b)),
  update: (id, b) => d(api.put(`/departments/${id}`, b)),
  remove: (id) => d(api.delete(`/departments/${id}`)),
};

export const CategoryApi = {
  list: (includeInactive = false) => d(api.get('/categories', { params: { includeInactive } })),
  create: (b) => d(api.post('/categories', b)),
  update: (id, b) => d(api.put(`/categories/${id}`, b)),
  remove: (id) => d(api.delete(`/categories/${id}`)),
};

export const NoticeApi = {
  list: (params) => d(api.get('/notices', { params })),
  get: (id) => d(api.get(`/notices/${id}`)),
  create: (b) => d(api.post('/notices', b)),
  update: (id, b) => d(api.put(`/notices/${id}`, b)),
  remove: (id) => d(api.delete(`/notices/${id}`)),
  publish: (id) => d(api.post(`/notices/${id}/publish`)),
  submit: (id) => d(api.post(`/notices/${id}/submit`)),
  approve: (id) => d(api.post(`/notices/${id}/approve`)),
  reject: (id, reason) => d(api.post(`/notices/${id}/reject`, { reason })),
  archive: (id) => d(api.post(`/notices/${id}/archive`)),
  acknowledge: (id) => d(api.post(`/notices/${id}/acknowledge`)),
  save: (id) => d(api.put(`/notices/${id}/save`)),
  unsave: (id) => d(api.delete(`/notices/${id}/save`)),
  stats: (id) => d(api.get(`/notices/${id}/stats`)),
  targetOptions: () => d(api.get('/notices/target-options')),
  recipientPreview: (targets) => d(api.post('/notices/recipient-preview', { targets })),
  timeline: (id) => d(api.get(`/notices/${id}/timeline`)),
  aiDraft: (text) => d(api.post('/ai/notice-draft', { text })),
};

export const AttachmentApi = {
  upload: (noticeId, files) => {
    const form = new FormData();
    files.forEach((f) => form.append('files', f));
    return d(api.post(`/notices/${noticeId}/attachments`, form));
  },
  /** One file per request so each file gets its own progress and its own error. */
  uploadOne: (noticeId, file, onProgress) => {
    const form = new FormData();
    form.append('files', file);
    return d(api.post(`/notices/${noticeId}/attachments`, form, {
      onUploadProgress: (e) => { if (e.total) onProgress?.(Math.round((100 * e.loaded) / e.total)); },
    }));
  },
  download: (id) => d(api.get(`/attachments/${id}/download`)),
  remove: (id) => d(api.delete(`/attachments/${id}`)),
};

export const NotificationApi = {
  list: (params) => d(api.get('/notifications', { params })),
  unread: () => d(api.get('/notifications/unread-count')),
  read: (id) => d(api.put(`/notifications/${id}/read`)),
  readAll: () => d(api.put('/notifications/read-all')),
  remove: (id) => d(api.delete(`/notifications/${id}`)),
};

export const DashboardApi = {
  get: (role) => d(api.get(`/dashboard/${{ SUPER_ADMIN: 'admin', PRINCIPAL: 'principal', HOD: 'hod', FACULTY: 'faculty', STUDENT: 'student' }[role]}`)),
};

/** Public site: works without signing in; the server returns only public notices that are live. */
export const PublicApi = {
  college: () => d(api.get('/public/college')),
  institutions: () => d(api.get('/public/institutions')),
  categories: () => d(api.get('/public/categories')),
  notices: (params) => d(api.get('/public/notices', { params })),
  notice: (id) => d(api.get(`/public/notices/${id}`)),
  attachmentLink: (noticeId, attachmentId) => d(api.get(`/public/notices/${noticeId}/attachments/${attachmentId}/link`)),
};

export const RegistrationApi = {
  pending: () => d(api.get('/registrations/pending')),
  count: () => d(api.get('/registrations/pending/count')),
  approve: (id) => d(api.post(`/registrations/${id}/approve`)),
  reject: (id, reason) => d(api.post(`/registrations/${id}/reject`, { reason })),
};

export const ClassTeacherApi = {
  list: (departmentId) => d(api.get('/class-teachers', { params: { departmentId } })),
  options: (departmentId) => d(api.get('/class-teachers/options', { params: { departmentId } })),
  assign: (body) => d(api.put('/class-teachers', body)),
  remove: (id) => d(api.delete(`/class-teachers/${id}`)),
};

export const AuditApi = { list: (params) => d(api.get('/audit-logs', { params })) };

export default api;
