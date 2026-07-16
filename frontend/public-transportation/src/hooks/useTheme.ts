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

export function useTheme(): { theme: Theme; toggleTheme: () => void } {
  const [theme, setTheme] = useState<Theme>('light')

  // Read the boot-script decision after mount — reading document during
  // render would mismatch the server-rendered HTML.
  useEffect(() => {
    setTheme(currentTheme())
  }, [])

  const toggleTheme = useCallback(() => {
    setTheme(prev => {
      const next: Theme = prev === 'dark' ? 'light' : 'dark'
      document.documentElement.dataset.theme = next
      try {
        localStorage.setItem(THEME_STORAGE_KEY, next)
      } catch {}
      return next
    })
  }, [])

  return { theme, toggleTheme }
}
