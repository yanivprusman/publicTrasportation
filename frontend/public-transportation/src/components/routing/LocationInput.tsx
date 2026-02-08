import { useState, useRef, useCallback, useEffect } from 'react'
import type { GeocodeSuggestion } from '../../types'
import { geocodeSearch } from '../../services/routing-api'
import styles from './LocationInput.module.css'

interface LocationInputProps {
  label: string
  value: GeocodeSuggestion | null
  onChange: (place: GeocodeSuggestion | null) => void
  placeholder?: string
}

export default function LocationInput({ label, value, onChange, placeholder }: LocationInputProps) {
  const [text, setText] = useState(value?.name || '')
  const [suggestions, setSuggestions] = useState<GeocodeSuggestion[]>([])
  const [showDropdown, setShowDropdown] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout>>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setText(value?.name || '')
  }, [value])

  const doSearch = useCallback((query: string) => {
    if (timerRef.current) clearTimeout(timerRef.current)
    if (!query.trim()) {
      setSuggestions([])
      return
    }
    timerRef.current = setTimeout(async () => {
      const results = await geocodeSearch(query)
      setSuggestions(results.slice(0, 5))
      setShowDropdown(results.length > 0)
    }, 300)
  }, [])

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    setText(val)
    onChange(null)
    doSearch(val)
  }

  const handleSelect = (s: GeocodeSuggestion) => {
    onChange(s)
    setText(s.name)
    setSuggestions([])
    setShowDropdown(false)
  }

  const handleClear = () => {
    setText('')
    onChange(null)
    setSuggestions([])
    inputRef.current?.focus()
  }

  const handleBlur = () => {
    setTimeout(() => setShowDropdown(false), 200)
  }

  return (
    <div className={styles.wrapper}>
      <label className={styles.label}>{label}</label>
      <div className={styles.inputRow}>
        <input
          ref={inputRef}
          className={styles.input}
          value={text}
          onChange={handleInput}
          onFocus={() => suggestions.length > 0 && setShowDropdown(true)}
          onBlur={handleBlur}
          placeholder={placeholder || `Search ${label.toLowerCase()}...`}
        />
        {text && (
          <button className={styles.clear} onClick={handleClear} type="button">&times;</button>
        )}
      </div>
      {showDropdown && suggestions.length > 0 && (
        <ul className={styles.dropdown}>
          {suggestions.map((s, i) => (
            <li key={i} className={styles.suggestion} onMouseDown={() => handleSelect(s)}>
              {s.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
