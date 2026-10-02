export const ROLE_LABEL = { SUPER_ADMIN: 'Super admin', PRINCIPAL: 'Principal', HOD: 'HOD', FACULTY: 'Teacher', STUDENT: 'Student' };
export const ACCOUNT_STATUS_LABEL = {
  PENDING: 'Waiting for approval', APPROVED: 'Active', REJECTED: 'Rejected', SUSPENDED: 'Suspended', DEACTIVATED: 'Closed',
};
export const STATUS_LABEL = {
  DRAFT: 'Draft', PENDING_APPROVAL: 'Pending approval', APPROVED: 'Approved', REJECTED: 'Rejected',
  SCHEDULED: 'Scheduled', PUBLISHED: 'Published', EXPIRED: 'Expired', ARCHIVED: 'Archived',
};
export const TARGET_LABEL = {
  COLLEGE: 'Entire college', ROLE: 'Specific role', DEPARTMENT: 'Whole department',
  ROLE_DEPARTMENT: 'Role in a department', SEMESTER: 'Semester', CLASS: 'Class', SPECIFIC_USER: 'Specific users',
};

export function formatDate(v, withTime = true) {
  if (!v) return '—';
  const d = new Date(v);
  return d.toLocaleString('en-IN', withTime
    ? { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' }
    : { day: 'numeric', month: 'short', year: 'numeric' });
}

/** LocalDateTime string (no zone) for <input type="datetime-local"> and back. */
export const toInputDate = (v) => (v ? String(v).slice(0, 16) : '');
export const fromInputDate = (v) => (v ? `${v}:00` : null);

export function fileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export const dashboardLabels = {
  totalUsers: 'Users', students: 'Students', faculty: 'Faculty', hods: 'HODs', departments: 'Departments',
  totalNotices: 'Notices', activeNotices: 'Active', expiredNotices: 'Expired', scheduledNotices: 'Scheduled',
  pendingApproval: 'Pending approval', published: 'Published', scheduled: 'Scheduled', drafts: 'Drafts',
  important: 'Important', urgent: 'Urgent', collegeNoticesActive: 'Active across college',
  departmentNotices: 'Department notices', pendingApprovals: 'Waiting for you', rejected: 'Rejected',
  myNotices: 'My notices', forMe: 'Addressed to me', approved: 'Approved', latest: 'Current notices',
  unread: 'Unread', examination: 'Examination', placement: 'Placement', events: 'Events',
};

/** Plural role names for counts: "170 students, 12 faculty". */
export const ROLE_PLURAL = {
  SUPER_ADMIN: ['administrator', 'administrators'], PRINCIPAL: ['principal', 'principals'],
  HOD: ['HOD', 'HODs'], FACULTY: ['teacher', 'teachers'], STUDENT: ['student', 'students'],
};
export const countRole = (role, n) => `${n} ${ROLE_PLURAL[role]?.[n === 1 ? 0 : 1] ?? role}`;

/** Strips UI-only fields from target rows before sending them to the API. */
export const toTargetRequests = (targets) => targets.map(({ targetType, targetRole, departmentId, semester, className, userId }) =>
  ({ targetType, targetRole, departmentId, semester, className: className || null, userId }));

export const formatTime = (v) => new Date(v).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });

export const INSTITUTION_LABEL = { ENGINEERING: 'Engineering', POLYTECHNIC: 'Polytechnic', BOTH: 'Engineering & Polytechnic' };

/** Year of study from semester: semesters 5 and 6 are year 3. */
export const yearOf = (semester) => Math.ceil(Number(semester) / 2);
const ORD = ['', '1st', '2nd', '3rd', '4th', '5th', '6th'];
export const yearLabel = (semester) => `${ORD[yearOf(semester)] || `${yearOf(semester)}th`} year`;
