import type { RouteResult } from '../../types'
import ItineraryCard from './ItineraryCard'
import styles from './RouteResults.module.css'

interface RouteResultsProps {
  results: RouteResult | null
  selectedIndex: number
  onSelect: (i: number) => void
  loading: boolean
  error: string | null
}

export default function RouteResults({ results, selectedIndex, onSelect, loading, error }: RouteResultsProps) {
  if (loading) {
    return <div className={styles.status}><div className={styles.spinner} />Searching routes...</div>
  }

  if (error) {
    return <div className={styles.error}>{error}</div>
  }

  if (!results || results.itineraries.length === 0) {
    return null
  }

  return (
    <div className={styles.list}>
      {results.itineraries.map((itin, i) => (
        <ItineraryCard
          key={i}
          itinerary={itin}
          selected={i === selectedIndex}
          onClick={() => onSelect(i)}
        />
      ))}
    </div>
  )
}
