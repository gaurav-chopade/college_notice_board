/** Graduation cap for Engineering, building for Polytechnic, both for notices meant for both. */
export default function InstitutionIcon({ type, size = 28 }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', 'aria-hidden': true, focusable: false, fill: 'none', stroke: 'currentColor', strokeWidth: 1.6, strokeLinecap: 'round', strokeLinejoin: 'round' };
  if (type === 'POLYTECHNIC') {
    return <svg {...common}><path d="M3 21h18M5 21V9l7-5 7 5v12M9 21v-5h6v5M9 12h.01M15 12h.01" /></svg>;
  }
  if (type === 'BOTH') {
    return <svg {...common}><path d="M2 9l7-4 7 4-7 4zM5 10.5V14c0 1.2 1.8 2.5 4 2.5s4-1.3 4-2.5v-3.5M14 21h8M15 21v-7l3-2 3 2v7" /></svg>;
  }
  return <svg {...common}><path d="M2 9l10-5 10 5-10 5zM6 11v4.5c0 1.6 2.7 3 6 3s6-1.4 6-3V11M22 9v5" /></svg>;
}
