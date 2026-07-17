import type { RouteResult } from '../../types'
import ItineraryCard from './ItineraryCard'
import { useI18n } from '../../i18n'
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
  const { t, tm } = useI18n()

  if (loading) {
    return <div className={styles.status}><div className={styles.spinner} />{t('results.searching')}</div>
  }

  if (error) {
    // The hook reports known conditions as translation keys; anything else is
    // a raw (e.g. server) message shown as-is.
    const isNoResults = error === 'errors.noRoutes'
    return (
      <div className={isNoResults ? styles.noResults : styles.error}>
        <span>{isNoResults ? t('results.noRoutes') : tm(error)}</span>
        {onRetry && (
          <button className={styles.retryBtn} onClick={onRetry} type="button" data-id="retry-route-search">
            {t('results.tryAgain')}
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
          {t('results.earlier')}
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
          {t('results.later')}
        </button>
      )}
      {pagingNotice && (
        <div className={styles.pagingNotice} role="alert" data-id="paging-notice">
          {tm(pagingNotice)}
        </div>
      )}
    </div>
  )
}
