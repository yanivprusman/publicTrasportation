import { Fragment, useEffect, useRef, useState } from 'react'
import type { Coordinates, Itinerary, RouteLeg, TransitMode } from '../../types'
import type { LiveBusState } from '../../hooks/useLiveBus'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle, getModeLabel } from '../../utils/mode-colors'
import { buildTripLink, type SharedTrip } from '../../utils/trip-link'
import DepartureCountdown from './DepartureCountdown'
import LiveBusStatus from './LiveBusStatus'
import JourneyNavigator from './JourneyNavigator'
import { useI18n } from '../../i18n'
import styles from './ItineraryDetail.module.css'

interface ItineraryDetailProps {
  itinerary: Itinerary
  /** Trip to encode in the share link; null hides the share button (e.g. an input was cleared) */
  trip: SharedTrip | null
  /** Live position/ETA of the itinerary's next bus, tracked by useLiveBus in App */
  liveBus: LiveBusState
  onShowLiveBusOnMap: (position: Coordinates) => void
}

const MODE_ICONS: Record<TransitMode, string> = {
  WALK: '\u{1F6B6}',
  BUS: '\u{1F68C}',
  RAIL: '\u{1F686}',
  TRAM: '\u{1F68B}',
  SUBWAY: '\u{1F687}',
}

/** Seconds spent waiting at the transfer point between two legs; 0 when negligible or unparseable */
function waitSecondsBetween(prev: RouteLeg, next: RouteLeg): number {
  const gap = (Date.parse(next.startTime) - Date.parse(prev.endTime)) / 1000
  if (!Number.isFinite(gap) || gap < 60) return 0
  return gap
}

export default function ItineraryDetail({ itinerary, trip, liveBus, onShowLiveBusOnMap }: ItineraryDetailProps) {
  const { t } = useI18n()
  const legs = itinerary.legs
  const [navigating, setNavigating] = useState(false)
  return (
    <div className={styles.wrapper}>
      <DepartureCountdown itinerary={itinerary} />
      <LiveBusStatus state={liveBus} onShowOnMap={onShowLiveBusOnMap} />
      <div className={styles.summaryRow}>
        <div className={styles.summary}>
          {formatTime(itinerary.startTime)} - {formatTime(itinerary.endTime)}
          {' · '}
          {formatDuration(itinerary.duration)}
          {' · '}
          {itinerary.transfers === 0
            ? t('card.direct')
            : itinerary.transfers === 1
              ? t('card.transfersOne')
              : t('card.transfersMany', { n: itinerary.transfers })}
        </div>
        {trip && <ShareTripButton trip={trip} />}
      </div>
      {legs.length > 0 && (
        <button
          className={styles.startJourneyBtn}
          onClick={() => setNavigating(true)}
          type="button"
          data-id="start-journey"
        >
          <span className={styles.startJourneyIcon} aria-hidden="true">{'\u{1F9ED}'}</span>
          {t('detail.startJourney')}
        </button>
      )}
      {navigating && (
        <JourneyNavigator itinerary={itinerary} onClose={() => setNavigating(false)} />
      )}
      {legs.length > 0 && (
        <div className={styles.timeline}>
          <TimelineNode kind="origin" time={legs[0].startTime} name={legs[0].from.name} />
          {legs.map((leg, i) => {
            const next = legs[i + 1]
            return (
              <Fragment key={i}>
                <LegSegment leg={leg} />
                {next ? (
                  <TimelineNode
                    kind="transfer"
                    time={leg.endTime}
                    name={leg.to.name}
                    waitSeconds={waitSecondsBetween(leg, next)}
                  />
                ) : (
                  <TimelineNode kind="destination" time={leg.endTime} name={leg.to.name} />
                )}
              </Fragment>
            )
          })}
        </div>
      )}
    </div>
  )
}

function ShareTripButton({ trip }: { trip: SharedTrip }) {
  const { t } = useI18n()
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
      title={t('detail.shareTitle')}
      data-id="share-trip"
    >
      {status === 'copied' ? t('detail.shareCopied') : status === 'error' ? t('detail.shareFailed') : t('detail.share')}
    </button>
  )
}

interface TimelineNodeProps {
  kind: 'origin' | 'transfer' | 'destination'
  time: string
  name: string
  waitSeconds?: number
}

function TimelineNode({ kind, time, name, waitSeconds = 0 }: TimelineNodeProps) {
  const { t } = useI18n()
  const dotClass =
    kind === 'origin' ? styles.dotOrigin :
    kind === 'destination' ? styles.dotDestination :
    styles.dotTransfer
  return (
    <div className={styles.nodeRow}>
      <div className={styles.nodeTime}>{formatTime(time)}</div>
      <div className={styles.spineCell}>
        <div className={`${styles.nodeDot} ${dotClass}`} />
      </div>
      <div className={styles.nodeContent}>
        <span className={styles.nodeName}>{name}</span>
        {waitSeconds > 0 && (
          <span className={styles.waitChip}>{t('detail.wait', { d: formatDuration(waitSeconds) })}</span>
        )}
      </div>
    </div>
  )
}

function LegSegment({ leg }: { leg: RouteLeg }) {
  const { t } = useI18n()
  const style = getModeStyle(leg.mode, leg.routeColor)
  const [stopsOpen, setStopsOpen] = useState(false)
  const stopCount = leg.intermediateStops?.length ?? 0
  const stopsLabel = stopCount === 1 ? t('detail.stopsOne') : t('detail.stopsMany', { n: stopCount })
  const isWalk = leg.mode === 'WALK'

  return (
    <div
      className={styles.segmentRow}
      style={{ '--leg-color': style.color } as React.CSSProperties}
    >
      <div className={styles.segmentTime} />
      <div className={styles.spineCell}>
        <div className={isWalk ? styles.spineWalk : styles.spineRide} />
      </div>
      <div className={styles.segmentContent}>
        {isWalk ? (
          <div className={styles.walkLine}>
            <span className={styles.modeIcon} aria-hidden="true">{MODE_ICONS.WALK}</span>
            <span>{t('detail.walk', { d: formatDuration(leg.duration) })}</span>
          </div>
        ) : (
          <>
            <div className={styles.rideLine}>
              <span className={styles.modeChip}>
                <span className={styles.modeIcon} aria-hidden="true">{MODE_ICONS[leg.mode]}</span>
                <span>{getModeLabel(leg.mode)}</span>
                {leg.routeShortName && (
                  <span className={styles.routeNumber}>{leg.routeShortName}</span>
                )}
              </span>
              <span className={styles.rideDuration}>{formatDuration(leg.duration)}</span>
            </div>
            {(stopCount > 0 || leg.agencyName) && (
              <div className={styles.rideMeta}>
                {stopCount > 0 && (
                  <button
                    className={styles.stopsToggle}
                    onClick={() => setStopsOpen(!stopsOpen)}
                    type="button"
                    aria-expanded={stopsOpen}
                    data-id="toggle-leg-stops"
                  >
                    <span className={styles.stopsCaret} aria-hidden="true">
                      {stopsOpen ? '▾' : '▸'}
                    </span>
                    {stopsLabel}
                  </button>
                )}
                {leg.agencyName && <span className={styles.agency}>{leg.agencyName}</span>}
              </div>
            )}
            {stopsOpen && stopCount > 0 && (
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
