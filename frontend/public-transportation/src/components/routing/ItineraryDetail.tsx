import { useEffect, useRef, useState } from 'react'
import type { Itinerary } from '../../types'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle, getModeLabel } from '../../utils/mode-colors'
import { buildTripLink, type SharedTrip } from '../../utils/trip-link'
import styles from './ItineraryDetail.module.css'

interface ItineraryDetailProps {
  itinerary: Itinerary
  /** Trip to encode in the share link; null hides the share button (e.g. an input was cleared) */
  trip: SharedTrip | null
}

export default function ItineraryDetail({ itinerary, trip }: ItineraryDetailProps) {
  return (
    <div className={styles.wrapper}>
      <div className={styles.summaryRow}>
        <div className={styles.summary}>
          {formatTime(itinerary.startTime)} - {formatTime(itinerary.endTime)}
          {' · '}
          {formatDuration(itinerary.duration)}
          {' · '}
          {itinerary.transfers} transfer{itinerary.transfers !== 1 ? 's' : ''}
        </div>
        {trip && <ShareTripButton trip={trip} />}
      </div>
      {itinerary.legs.map((leg, i) => (
        <LegCard key={i} leg={leg} />
      ))}
    </div>
  )
}

function ShareTripButton({ trip }: { trip: SharedTrip }) {
  const [status, setStatus] = useState<'idle' | 'copied' | 'error'>('idle')
  const resetTimerRef = useRef<number | null>(null)

  useEffect(() => () => {
    if (resetTimerRef.current !== null) clearTimeout(resetTimerRef.current)
  }, [])

  const flash = (next: 'copied' | 'error') => {
    if (resetTimerRef.current !== null) clearTimeout(resetTimerRef.current)
    setStatus(next)
    resetTimerRef.current = window.setTimeout(() => setStatus('idle'), 2500)
  }

  const handleShare = async () => {
    const url = buildTripLink(trip)
    if (navigator.share) {
      try {
        await navigator.share({ title: `${trip.origin.name} → ${trip.destination.name}`, url })
      } catch (err) {
        // Closing the OS share sheet is a user choice, not a failure
        if (!(err instanceof Error) || err.name !== 'AbortError') flash('error')
      }
      return
    }
    try {
      await navigator.clipboard.writeText(url)
      flash('copied')
    } catch {
      flash('error')
    }
  }

  return (
    <button
      className={`${styles.shareBtn} ${status === 'copied' ? styles.shareBtnCopied : ''}`}
      onClick={handleShare}
      type="button"
      title="Share this trip as a link"
      data-id="share-trip"
    >
      {status === 'copied' ? '✓ Link copied' : status === 'error' ? 'Share failed' : '\u{1F517} Share trip'}
    </button>
  )
}

function LegCard({ leg }: { leg: Itinerary['legs'][number] }) {
  const style = getModeStyle(leg.mode, leg.routeColor)
  const [stopsOpen, setStopsOpen] = useState(false)
  const stopCount = leg.intermediateStops?.length ?? 0
  const hasStops = stopCount > 0
  // A single intermediate stop must read "1 stop", not "1 stops" — the file
  // already pluralizes "transfer(s)" this way; keep stop counts consistent.
  const stopsLabel = `${stopCount} ${stopCount === 1 ? 'stop' : 'stops'}`

  const description = leg.mode === 'WALK'
    ? `Walk ${formatDuration(leg.duration)} to ${leg.to.name}`
    : `${getModeLabel(leg.mode)}${leg.routeShortName ? ` ${leg.routeShortName}` : ''} toward ${leg.to.name} - ${formatDuration(leg.duration)}${hasStops ? ` (${stopsLabel})` : ''}`

  return (
    <div className={styles.leg}>
      <div className={styles.colorBar} style={{ background: style.color }} />
      <div className={styles.legContent}>
        <div className={styles.legTimes}>
          {formatTime(leg.startTime)} - {formatTime(leg.endTime)}
        </div>
        <div className={styles.legDesc}>{description}</div>
        {leg.agencyName && <div className={styles.agency}>{leg.agencyName}</div>}
        {hasStops && (
          <>
            <button
              className={styles.stopsToggle}
              onClick={() => setStopsOpen(!stopsOpen)}
              type="button"
              data-id="toggle-leg-stops"
            >
              {stopsOpen ? 'Hide' : 'Show'} {stopsLabel}
            </button>
            {stopsOpen && (
              <ul className={styles.stopsList}>
                {leg.intermediateStops!.map((s, j) => (
                  <li key={j} className={styles.stop}>{s.name}</li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>
    </div>
  )
}
