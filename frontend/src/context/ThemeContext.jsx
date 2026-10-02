import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { applyTheme, getThemePref, resolveTheme, setThemePref, watchSystemTheme } from '../utils/theme';

const ThemeContext = createContext(null);

export function ThemeProvider({ children }) {
  const [pref, setPref] = useState(getThemePref);
  const [resolved, setResolved] = useState(() => resolveTheme());

  useEffect(() => {
    applyTheme(pref);
    setResolved(resolveTheme(pref));
    return watchSystemTheme();
  }, [pref]);

  useEffect(() => {
    if (pref !== 'system') return undefined;
    const m = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => setResolved(m.matches ? 'dark' : 'light');
    m.addEventListener('change', onChange);
    return () => m.removeEventListener('change', onChange);
  }, [pref]);

  const choose = useCallback((p) => { setThemePref(p); setPref(p); }, []);
  return <ThemeContext.Provider value={{ pref, resolved, choose }}>{children}</ThemeContext.Provider>;
}

export const useTheme = () => useContext(ThemeContext);
