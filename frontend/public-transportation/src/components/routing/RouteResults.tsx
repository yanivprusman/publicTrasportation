import { useEffect, useMemo, useState } from 'react'
import type { RouteResult } from '../../types'
import ItineraryCard from './ItineraryCard'
import { ROUTE_SORT_MODES, sortItineraries, type RouteSortMode } from '../../utils/route-sort'
import { laterDeparturesOf } from '../../utils/departures'
import { useI18n } from '../../i18n'
import type { TranslationKey } from '../../i18n/translations'
import styles from './RouteResults.module.css'

const SORT_LABELS: Record<RouteSortMode, TranslationKey> = {
  fastest: 'sort.fastest',
  fewerTransfers: 'sort.fewerTransfers',
  lessWalking: 'sort.lessWalking',
}

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
  const [sortMode, setSortMode] = useState<RouteSortMode>('fastest')

  // One clock for the whole list: every card's "departs in …" counts down off it,
  // so a results list left open never shows a boarding time that has quietly passed.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 10_000)
    return () => clearInterval(id)
  }, [])

  // Sorted entries keep their original index, so selecting a card still refers
  // to the same itinerary the map and detail panel are working from.
  const ordered = useMemo(
    () => (results ? sortItineraries(results.itineraries, sortMode) : []),
    [results, sortMode]
  )

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
      {/* Sorting only reorders the trips already found — it never re-queries,
          so switching is instant and cannot lose the current selection. */}
      {results.itineraries.length > 1 && (
        <div className={styles.sortRow} role="group" aria-label={t('sort.label')}>
          {ROUTE_SORT_MODES.map(mode => (
            <button
              key={mode}
              type="button"
              className={`${styles.sortChip} ${sortMode === mode ? styles.sortChipActive : ''}`}
              onClick={() => setSortMode(mode)}
              aria-pressed={sortMode === mode}
              data-id={`sort-routes-${mode}`}
            >
              {t(SORT_LABELS[mode])}
            </button>
          ))}
        </div>
      )}
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
      {ordered.map(({ itinerary, index }) => (
        <ItineraryCard
          key={index}
          itinerary={itinerary}
          selected={index === selectedIndex}
          onClick={() => onSelect(index)}
          now={now}
          laterDepartures={laterDeparturesOf(itinerary, ordered.map(o => o.itinerary))}
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
