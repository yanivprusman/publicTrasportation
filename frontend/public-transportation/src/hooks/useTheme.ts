import { useCallback, useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'pt-theme'

// The actual theme is decided before hydration by the inline boot script in
// app/layout.tsx (localStorage choice, else OS prefers-color-scheme) and
// stamped on <html data-theme>. This hook mirrors that attribute into React
// state and flips it on toggle.
function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

// More than one component reads the theme (the toggle button in the planner,
// the map-style default in App). Without a shared subscription each useTheme
// would keep its own copy and only the one that toggled would re-render, so a
// theme flip would not reach the others until a reload.
const listeners = new Set<(theme: Theme) => void>()

function broadcast(theme: Theme): void {
  listeners.forEach(listener => listener(theme))
}

export function useTheme(): { theme: Theme; toggleTheme: () => void } {
  const [theme, setTheme] = useState<Theme>('light')

  // Read the boot-script decision after mount — reading document during
  // render would mismatch the server-rendered HTML.
  useEffect(() => {
    setTheme(currentTheme())
    listeners.add(setTheme)
    return () => { listeners.delete(setTheme) }
  }, [])

  const toggleTheme = useCallback(() => {
    const next: Theme = currentTheme() === 'dark' ? 'light' : 'dark'
    document.documentElement.dataset.theme = next
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next)
    } catch {}
    broadcast(next)
  }, [])

  return { theme, toggleTheme }
}
