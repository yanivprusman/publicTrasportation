import { useState } from 'react'
import type { RouteResult } from '../../types'
import ItineraryCard from './ItineraryCard'
import styles from './RouteResults.module.css'

interface RouteResultsProps {
  results: RouteResult | null
  selectedIndex: number
  onSelect: (i: number) => void
  loading: boolean
  error: string | null
  onRetry?: () => void
}

export default function RouteResults({ results, selectedIndex, onSelect, loading, error, onRetry }: RouteResultsProps) {
  const [cardOpacity, setCardOpacity] = useState(0.6)
  if (loading) {
    return <div className={styles.status}><div className={styles.spinner} />Searching routes...</div>
  }

  if (error) {
    const isNoResults = error === 'No routes found'
    return (
      <div className={isNoResults ? styles.noResults : styles.error}>
        <span>{isNoResults ? 'No routes found between these locations' : error}</span>
        {onRetry && (
          <button className={styles.retryBtn} onClick={onRetry} type="button">
            Try Again
          </button>
        )}
      </div>
    )
  }

  if (!results || results.itineraries.length === 0) {
    return null
  }

  return (
    <div className={styles.list}>
      <div className={styles.opacityControl}>
        <label className={styles.opacityLabel}>Card transparency</label>
        <input
          type="range"
          min={0.2}
          max={1}
          step={0.05}
          value={cardOpacity}
          onChange={e => setCardOpacity(Number(e.target.value))}
          className={styles.opacitySlider}
        />
      </div>
      {results.itineraries.map((itin, i) => (
        <ItineraryCard
          key={i}
          itinerary={itin}
          selected={i === selectedIndex}
          onClick={() => onSelect(i)}
          cardOpacity={cardOpacity}
        />
      ))}
    </div>
  )
}
