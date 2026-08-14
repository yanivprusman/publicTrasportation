import type { Itinerary } from '../../types'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle, getModeLabel } from '../../utils/mode-colors'
import { estimateFare } from '../../utils/fare'
import { useI18n } from '../../i18n'
import styles from './ItineraryCard.module.css'

interface ItineraryCardProps {
  itinerary: Itinerary
  selected: boolean
  onClick: () => void
  /**
   * The wall clock in ms, ticked by the list. Passed in rather than read here so
   * every card counts down off one timer — and so a card that is never given a
   * clock cannot silently show a countdown frozen at render time.
   */
  now: number
}

/** Under five minutes you are running for it — the countdown says so in colour. */
const URGENT_MS = 5 * 60 * 1000
/** Past a few hours the countdown says less than the clock time already does. */
const COUNTDOWN_HORIZON_MS = 3 * 60 * 60 * 1000

export default function ItineraryCard({ itinerary, selected, onClick, now }: ItineraryCardProps) {
  const { t } = useI18n()
  const totalLegSeconds = itinerary.legs.reduce((sum, leg) => sum + (leg.duration || 0), 0)
  // Walk-only itineraries cost nothing — no badge rather than a "~₪0".
  const fare = estimateFare(itinerary)
  const barDescription = itinerary.legs
    .map(leg => {
      const what = `${getModeLabel(leg.mode)}${leg.routeShortName ? ` ${leg.routeShortName}` : ''}`
      return `${what} ${formatDuration(leg.duration)}`
    })
    .join(', ')

  // The card's time range starts when you leave the house, not when the bus pulls
  // out — and the bus is the part you can miss. A walk-only itinerary has no ride
  // to board, and an unparsable timestamp gets no line at all: a boarding time the
  // app is not sure of is worse than none.
  const boardingLeg = itinerary.legs.find(leg => leg.mode !== 'WALK')
  const boardingMs = boardingLeg ? new Date(boardingLeg.startTime).getTime() : NaN
  const remainingMs = boardingMs - now
  const countdown = !Number.isFinite(boardingMs)
    ? null
    : remainingMs < -60_000
      ? t('card.departureGone')
      : remainingMs < 60_000
        ? t('card.departsNow')
        : remainingMs <= COUNTDOWN_HORIZON_MS
          ? t('card.departsIn', { d: formatDuration(remainingMs / 1000) })
          : null

  return (
    <div
      className={`${styles.card} ${selected ? styles.selected : ''}`}
      data-id="select-itinerary"
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      onClick={onClick}
      onKeyDown={(e) => {
        // A bare clickable div is unreachable by keyboard/AT. Activate on
        // Enter/Space like a real button; preventDefault stops Space from
        // scrolling the results list instead of selecting the itinerary.
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick()
        }
      }}
    >
      <div className={styles.header}>
        <span className={styles.duration}>{formatDuration(itinerary.duration)}</span>
        {fare > 0 && (
          <span className={styles.fare} title={t('fare.title')} data-id="itinerary-fare">
            {t('fare.estimate', { n: Math.round(fare) })}
          </span>
        )}
        <span className={styles.transfers}>
          {itinerary.transfers === 0
            ? t('card.direct')
            : itinerary.transfers === 1
              ? t('card.transfersOne')
              : t('card.transfersMany', { n: itinerary.transfers })}
        </span>
        <span className={styles.times}>
          {formatTime(itinerary.startTime)} - {formatTime(itinerary.endTime)}
        </span>
      </div>
      {/* Leg widths are proportional to each leg's share of travel time, so the
          walk/ride balance of a route is readable at a glance */}
      <div className={styles.durationBar} role="img" aria-label={barDescription}>
        {itinerary.legs.map((leg, i) => {
          const style = getModeStyle(leg.mode, leg.routeColor)
          const isWalk = leg.mode === 'WALK'
          const share = totalLegSeconds > 0
            ? (leg.duration || 0) / totalLegSeconds
            : 1 / itinerary.legs.length
          // Israel Railways "short" names are full route descriptions
          // ("הרצליה<->ירושלים/יצחק נבון..."); a name that long can't work as a
          // pill label, so fall back to the mode label and keep the full name
          // in the tooltip/aria text.
          const pillName = leg.routeShortName && leg.routeShortName.length <= 8 ? leg.routeShortName : null
          const label = isWalk ? '\u{1F6B6}' : (pillName || getModeLabel(leg.mode))
          const what = `${getModeLabel(leg.mode)}${leg.routeShortName ? ` ${leg.routeShortName}` : ''}`
          return (
            <div
              key={i}
              className={`${styles.barSeg} ${isWalk ? styles.barSegWalk : ''}`}
              style={{
                // Floor tiny legs so every segment stays visible and labelable
                flexGrow: Math.max(share, 0.06),
                ...(isWalk ? {} : { background: style.color }),
              }}
              title={`${what} · ${formatDuration(leg.duration)}`}
            >
              <span className={styles.barLabel}>{label}</span>
            </div>
          )
        })}
      </div>
      {boardingLeg && Number.isFinite(boardingMs) && (
        <div
          className={styles.boarding}
          data-id="itinerary-boarding"
          aria-label={t('card.boardingAria', {
            line: boardingLeg.routeShortName || getModeLabel(boardingLeg.mode),
            t: formatTime(boardingLeg.startTime),
          })}
        >
          <span
            className={styles.boardingLine}
            style={{ background: getModeStyle(boardingLeg.mode, boardingLeg.routeColor).color }}
          >
            {boardingLeg.routeShortName && boardingLeg.routeShortName.length <= 8
              ? boardingLeg.routeShortName
              : getModeLabel(boardingLeg.mode)}
          </span>
          <span className={styles.boardingTime}>
            {t('card.departsAt', { t: formatTime(boardingLeg.startTime) })}
          </span>
          {countdown && (
            <>
              <span className={styles.boardingDot}>·</span>
              <span
                className={`${styles.boardingCountdown} ${
                  remainingMs < -60_000
                    ? styles.boardingGone
                    : remainingMs <= URGENT_MS
                      ? styles.boardingUrgent
                      : ''
                }`}
              >
                {countdown}
              </span>
            </>
          )}
        </div>
      )}
    </div>
  )
}
