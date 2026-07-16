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
  onLoadEarlier?: () => void
  onLoadLater?: () => void
  loadingEarlier?: boolean
  loadingLater?: boolean
  pagingNotice?: string | null
}

export default function RouteResults({
  results, selectedIndex, onSelect, loading, error, onRetry,
  onLoadEarlier, onLoadLater, loadingEarlier, loadingLater, pagingNotice,
}: RouteResultsProps) {
  if (loading) {
    return <div className={styles.status}><div className={styles.spinner} />Searching routes...</div>
  }

  if (error) {
    const isNoResults = error === 'No routes found'
    return (
      <div className={isNoResults ? styles.noResults : styles.error}>
        <span>{isNoResults ? 'No routes found between these locations' : error}</span>
        {onRetry && (
          <button className={styles.retryBtn} onClick={onRetry} type="button" data-id="retry-route-search">
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
      {results.previousPageCursor && onLoadEarlier && (
        <button
          className={styles.pageBtn}
          onClick={onLoadEarlier}
          disabled={loadingEarlier || loadingLater}
          type="button"
          data-id="load-earlier-trips"
        >
          {loadingEarlier ? <span className={styles.pageSpinner} /> : <span className={styles.pageArrow}>↑</span>}
          Earlier departures
        </button>
      )}
      {results.itineraries.map((itin, i) => (
        <ItineraryCard
          key={i}
          itinerary={itin}
          selected={i === selectedIndex}
          onClick={() => onSelect(i)}
        />
      ))}
      {results.nextPageCursor && onLoadLater && (
        <button
          className={styles.pageBtn}
          onClick={onLoadLater}
          disabled={loadingEarlier || loadingLater}
          type="button"
          data-id="load-later-trips"
        >
          {loadingLater ? <span className={styles.pageSpinner} /> : <span className={styles.pageArrow}>↓</span>}
          Later departures
        </button>
      )}
      {pagingNotice && (
        <div className={styles.pagingNotice} role="alert" data-id="paging-notice">
          {pagingNotice}
        </div>
      )}
    </div>
  )
}
