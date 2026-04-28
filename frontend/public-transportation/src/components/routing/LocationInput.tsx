import { useEffect, useCallback } from 'react'
import type { GeocodeSuggestion } from '../../types'
import { geocodeSearch } from '../../services/routing-api'
import { useAutocomplete } from '../../hooks/useAutocomplete'
import styles from './LocationInput.module.css'

interface LocationInputProps {
  label: string
  value: GeocodeSuggestion | null
  onChange: (place: GeocodeSuggestion | null) => void
  placeholder?: string
  onGpsClick?: () => void
  gpsLoading?: boolean
}

const searchFn = (query: string) => geocodeSearch(query)

export default function LocationInput({ label, value, onChange, placeholder, onGpsClick, gpsLoading }: LocationInputProps) {
  const ac = useAutocomplete<GeocodeSuggestion>({ searchFn, maxResults: 5 })

  useEffect(() => {
    ac.setText(value?.name || '')
  }, [value])

  const onInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(null)
    ac.handleInput(e)
  }

  const selectItem = useCallback((s: GeocodeSuggestion) => {
    onChange(s)
    ac.setText(s.name)
    ac.handleSelect(s)
  }, [onChange, ac])

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
          value={ac.text}
          onChange={onInput}
          onKeyDown={onKeyDown}
          onFocus={ac.handleFocus}
          onBlur={ac.handleBlur}
          placeholder={placeholder || `Search ${label.toLowerCase()}...`}
        />
        {onGpsClick && !ac.text && (
          <button
            className={styles.gpsBtn}
            onClick={onGpsClick}
            disabled={gpsLoading}
            type="button"
            title="Use my location"
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
        {ac.text && (
          <button className={styles.clear} onClick={onClear} type="button">&times;</button>
        )}
      </div>
      {ac.showDropdown && ac.suggestions.length > 0 && (
        <ul className={styles.dropdown}>
          {ac.suggestions.map((s, i) => (
            <li
              key={i}
              className={`${styles.suggestion} ${i === ac.highlightIndex ? styles.highlighted : ''}`}
              onMouseDown={() => selectItem(s)}
            >
              {s.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
