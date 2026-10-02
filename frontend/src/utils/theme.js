/**
 * Light / dark / follow-the-system theme. Stored per device; applied before React renders
 * (see index.html) so the page never flashes the wrong colours.
 */
const KEY = 'cnb_theme';
const media = () => window.matchMedia('(prefers-color-scheme: dark)');

export function getThemePref() {
  try { return localStorage.getItem(KEY) || 'system'; } catch { return 'system'; }
}

export function resolveTheme(pref = getThemePref()) {
  return pref === 'system' ? (media().matches ? 'dark' : 'light') : pref;
}

export function applyTheme(pref = getThemePref()) {
  document.documentElement.setAttribute('data-bs-theme', resolveTheme(pref));
}

export function setThemePref(pref) {
  try { if (pref === 'system') localStorage.removeItem(KEY); else localStorage.setItem(KEY, pref); } catch { /* storage unavailable */ }
  applyTheme(pref);
}

/** Keeps "system" in sync when the OS switches between light and dark. Returns an unsubscribe function. */
export function watchSystemTheme() {
  const m = media();
  const onChange = () => { if (getThemePref() === 'system') applyTheme('system'); };
  m.addEventListener('change', onChange);
  return () => m.removeEventListener('change', onChange);
}
