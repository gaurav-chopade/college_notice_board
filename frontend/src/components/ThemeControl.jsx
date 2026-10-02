import { useTheme } from '../context/ThemeContext';

/** Quick switch for the top bar: flips between light and dark. */
export function ThemeToggle() {
  const { resolved, choose } = useTheme();
  const next = resolved === 'dark' ? 'light' : 'dark';
  return (
    <button type="button" className="btn btn-sm btn-light theme-toggle" onClick={() => choose(next)}
      aria-label={`Switch to ${next} mode`} title={`Switch to ${next} mode`}>
      {resolved === 'dark'
        ? <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4.5" fill="none" stroke="currentColor" strokeWidth="1.8" /><path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
        : <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /></svg>}
    </button>
  );
}

/** Full choice for the profile page, including "match my device". */
export function ThemeChoice() {
  const { pref, choose } = useTheme();
  const options = [['system', 'Match my device'], ['light', 'Light'], ['dark', 'Dark']];
  return (
    <div className="seg" role="group" aria-label="Appearance">
      {options.map(([value, label]) => (
        <button key={value} type="button" aria-pressed={pref === value} onClick={() => choose(value)}>{label}</button>
      ))}
    </div>
  );
}
