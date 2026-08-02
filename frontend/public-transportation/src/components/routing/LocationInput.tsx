import { useEffect, useCallback, useRef, forwardRef, useImperativeHandle } from 'react'
import type { GeocodeSuggestion } from '../../types'
import { geocodeSearch } from '../../services/routing-api'
import { useAutocomplete } from '../../hooks/useAutocomplete'
import { useI18n } from '../../i18n'
import styles from './LocationInput.module.css'

interface LocationInputProps {
  label: string
  /** Stable English identifier for data-id attributes; label is localized. */
  field: 'from' | 'to' | 'via'
  value: GeocodeSuggestion | null
  onChange: (place: GeocodeSuggestion | null) => void
  placeholder?: string
  onGpsClick?: () => void
  gpsLoading?: boolean
  isSaved?: boolean
  onToggleSave?: () => void
}

export interface LocationInputHandle {
  /** Commit typed-but-unpicked text to the top geocode hit, so a Search tap
   *  works like pressing Enter would. Returns the place now in effect:
   *  the already-picked value, the freshly resolved top hit, or null when
   *  the field is empty / nothing matches. */
  resolvePending: () => Promise<GeocodeSuggestion | null>
}

const searchFn = (query: string) => geocodeSearch(query)

const LocationInput = forwardRef<LocationInputHandle, LocationInputProps>(function LocationInput(
  { label, field, value, onChange, placeholder, onGpsClick, gpsLoading, isSaved, onToggleSave }, handleRef
) {
  const { t } = useI18n()
  const ac = useAutocomplete<GeocodeSuggestion>({ searchFn, maxResults: 5 })
  // Editing a selected place invalidates it (onChange(null) below), which makes
  // the value→text sync effect fire with null and wipe the text the user just
  // typed. Set only on a non-null→null transition caused by typing, so the
  // effect is guaranteed to run once and consume the flag.
  const skipNextSyncRef = useRef(false)

  useEffect(() => {
    if (skipNextSyncRef.current) {
      skipNextSyncRef.current = false
      return
    }
    ac.setText(value?.name || '')
  }, [value])

  const onInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (value) skipNextSyncRef.current = true
    onChange(null)
    ac.handleInput(e)
  }

  const selectItem = useCallback((s: GeocodeSuggestion) => {
    onChange(s)
    ac.setText(s.name)
    ac.handleSelect(s)
  }, [onChange, ac])

  useImperativeHandle(handleRef, () => ({
    resolvePending: async () => {
      if (value) return value
      const query = ac.text.trim()
      if (!query) return null
      const top = ac.suggestions[0] ?? (await ac.forceSearch(query))[0] ?? null
      if (top) selectItem(top)
      return top
    },
  }), [value, ac, selectItem])

  const onClear = () => {
    onChange(null)
    ac.handleClear()
  }

  const onKeyDown = async (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && ac.suggestions.length === 0) {
      e.preventDefault()
      const query = ac.text.trim()
      if (!query) return
      const results = await ac.forceSearch(query)
      if (results.length > 0) selectItem(results[0])
      return
    }
    ac.handleKeyDown(e, selectItem)
  }

  return (
    <div className={styles.wrapper}>
      <label className={styles.label}>{label}</label>
      <div className={styles.inputRow}>
        <input
          ref={ac.inputRef}
          className={styles.input}
          data-id={`location-input-${field}`}
          dir="auto"
          value={ac.text}
          onChange={onInput}
          onKeyDown={onKeyDown}
          onFocus={ac.handleFocus}
          onBlur={ac.handleBlur}
          placeholder={placeholder || label}
        />
        {onGpsClick && (
          <button
            className={styles.gpsBtn}
            onClick={onGpsClick}
            disabled={gpsLoading}
            type="button"
            title={t('gps.title')}
            data-id="gps-button"
          >
            {gpsLoading ? (
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" strokeDasharray="31.4" strokeDashoffset="10" />
              </svg>
            ) : (
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <circle cx="12" cy="12" r="8" />
                <circle cx="12" cy="12" r="3" fill="currentColor" />
                <line x1="12" y1="1" x2="12" y2="4" />
                <line x1="12" y1="20" x2="12" y2="23" />
                <line x1="1" y1="12" x2="4" y2="12" />
                <line x1="20" y1="12" x2="23" y2="12" />
              </svg>
            )}
          </button>
        )}
        {onToggleSave && value && (
          <button
            className={`${styles.saveBtn} ${isSaved ? styles.saveBtnActive : ''}`}
            onClick={onToggleSave}
            type="button"
            title={isSaved ? t('input.removePlace') : t('input.savePlace')}
            aria-label={isSaved ? t('input.removePlaceAria', { label }) : t('input.savePlaceAria', { label })}
            aria-pressed={isSaved}
            data-id={`save-place-${field}`}
          >
            {isSaved ? '★' : '☆'}
          </button>
        )}
        {ac.text && (
          <button className={styles.clear} onClick={onClear} type="button" data-id={`clear-location-${field}`}>&times;</button>
        )}
      </div>
      {ac.showDropdown && ac.suggestions.length > 0 && (
        <ul className={styles.dropdown}>
          {ac.suggestions.map((s, i) => (
            <li
              key={i}
              className={`${styles.suggestion} ${i === ac.highlightIndex ? styles.highlighted : ''}`}
              data-id="select-geocode-suggestion"
              dir="auto"
              onPointerDown={(e) => { e.preventDefault(); selectItem(s) }}
            >
              {s.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
})

export default LocationInput
