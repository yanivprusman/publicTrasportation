import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { translations, isTranslationKey, type Language, type TranslationKey } from './translations'

const LANG_STORAGE_KEY = 'pt-lang'

export type TranslateParams = Record<string, string | number>

// Module-level mirror of the active language so plain utility functions
// (formatDuration, getModeLabel, …) can localize without threading a
// parameter through every call site. Kept in sync during the provider's
// render, before any consumer renders. Every component that displays
// translated text consumes the context, so a language switch re-renders
// everything that reads this.
let activeLanguage: Language = 'en'

export function getLanguage(): Language {
  return activeLanguage
}

export function translate(lang: Language, key: TranslationKey, params?: TranslateParams): string {
  let text = translations[lang][key]
  if (params) {
    for (const [name, value] of Object.entries(params)) {
      text = text.split(`{${name}}`).join(String(value))
    }
  }
  return text
}

/**
 * Localize a message that is either a translation key (set by hooks for known
 * conditions) or a raw string (e.g. a server error) shown as-is.
 */
export function translateMaybe(lang: Language, message: string, params?: TranslateParams): string {
  return isTranslationKey(message) ? translate(lang, message, params) : message
}

interface I18nValue {
  lang: Language
  t: (key: TranslationKey, params?: TranslateParams) => string
  /** Localize a hook-supplied message that may be a key or a raw string. */
  tm: (message: string, params?: TranslateParams) => string
  toggleLanguage: () => void
}

const I18nContext = createContext<I18nValue | null>(null)

function initialLanguage(): Language {
  try {
    const saved = localStorage.getItem(LANG_STORAGE_KEY)
    if (saved === 'en' || saved === 'he') return saved
  } catch {}
  return navigator.language.toLowerCase().startsWith('he') ? 'he' : 'en'
}

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [lang, setLang] = useState<Language>(initialLanguage)

  // Sync before children render so utils called during render see the new
  // language. Idempotent assignment, safe to run on every render.
  activeLanguage = lang

  useEffect(() => {
    document.documentElement.lang = lang
    document.documentElement.dir = lang === 'he' ? 'rtl' : 'ltr'
    try {
      localStorage.setItem(LANG_STORAGE_KEY, lang)
    } catch {}
  }, [lang])

  const t = useCallback(
    (key: TranslationKey, params?: TranslateParams) => translate(lang, key, params),
    [lang]
  )
  const tm = useCallback(
    (message: string, params?: TranslateParams) => translateMaybe(lang, message, params),
    [lang]
  )
  const toggleLanguage = useCallback(() => {
    setLang(prev => (prev === 'he' ? 'en' : 'he'))
  }, [])

  return (
    <I18nContext.Provider value={{ lang, t, tm, toggleLanguage }}>
      {children}
    </I18nContext.Provider>
  )
}

export function useI18n(): I18nValue {
  const value = useContext(I18nContext)
  if (!value) throw new Error('useI18n must be used within a LanguageProvider')
  return value
}
