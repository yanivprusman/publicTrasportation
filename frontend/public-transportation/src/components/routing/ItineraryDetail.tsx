import { Fragment, useState } from 'react'
import type { Coordinates, Itinerary, RouteLeg, TransitMode } from '../../types'
import type { LiveBusState } from '../../hooks/useLiveBus'
import { useDepartureReminder } from '../../hooks/useDepartureReminder'
import { departureDayLabel, formatDuration, formatTime, nextDayOffset } from '../../utils/time-format'
import { getModeStyle, getModeLabel } from '../../utils/mode-colors'
import { buildTripLink, type SharedTrip } from '../../utils/trip-link'
import DepartureCountdown from './DepartureCountdown'
import LiveBusStatus from './LiveBusStatus'
import JourneyNavigator from './JourneyNavigator'
import ShareTripDialog from './ShareTripDialog'
import { useI18n } from '../../i18n'
import type { TranslationKey } from '../../i18n/translations'
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
  BIKE: '\u{1F6B4}',
  CAR: '\u{1F697}',
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
  const dayLabel = departureDayLabel(itinerary.startTime, Date.now())
  const overnightDays = nextDayOffset(itinerary.startTime, itinerary.endTime)
  return (
    <div className={styles.wrapper}>
      <DepartureCountdown itinerary={itinerary} />
      <LiveBusStatus state={liveBus} onShowOnMap={onShowLiveBusOnMap} />
      {/* The same day marker the card carries: opening a result must not lose the
          one fact "HH:mm" cannot express — which day this is. */}
      {dayLabel && <div className={styles.dayBadge} data-id="detail-day">{dayLabel}</div>}
      <div className={styles.summaryRow}>
        <div className={styles.summary}>
          {formatTime(itinerary.startTime)} - {formatTime(itinerary.endTime)}
          {overnightDays > 0 && (
            <sup className={styles.nextDay} data-id="detail-next-day">+{overnightDays}</sup>
          )}
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
      {legs.length > 0 && <DepartureReminderButton itinerary={itinerary} />}
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

/**
 * Opens the share dialog (QR + copy/share). The link is built when the dialog
 * opens rather than on every render, so a trip edited mid-view still shares the
 * trip the user is looking at.
 */
function ShareTripButton({ trip }: { trip: SharedTrip }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const url = open ? buildTripLink(trip) : ''

  return (
    <>
      <button
        className={styles.shareBtn}
        onClick={() => setOpen(true)}
        type="button"
        title={t('detail.shareTitle')}
        data-id="share-trip"
      >
        {t('detail.share')}
      </button>
      {open && (
        <ShareTripDialog
          url={url}
          title={`${trip.origin.name} → ${trip.destination.name}`}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  )
}

/**
 * Sets a browser notification ahead of the itinerary's departure. Only offered
 * for trips that actually depart later — a reminder for a bus leaving now is
 * noise, and the hook refuses it anyway.
 */
function DepartureReminderButton({ itinerary }: { itinerary: Itinerary }) {
  const { t } = useI18n()
  const reminder = useDepartureReminder()

  const firstTransit = itinerary.legs.find(leg => leg.mode !== 'WALK')
  const departureIso = firstTransit?.startTime ?? itinerary.startTime
  const scheduled = reminder.isScheduledFor(departureIso)

  const line = firstTransit
    ? `${getModeLabel(firstTransit.mode)}${firstTransit.routeShortName ? ` ${firstTransit.routeShortName}` : ''}`
    : getModeLabel('WALK')
  const stop = firstTransit?.from.name ?? itinerary.legs[0]?.from.name ?? ''

  const handleClick = () => {
    if (scheduled) {
      reminder.cancel()
      return
    }
    reminder.schedule({ departureIso, line, stop })
  }

  return (
    <div className={styles.reminderRow}>
      <button
        className={`${styles.reminderBtn} ${scheduled ? styles.reminderBtnActive : ''}`}
        onClick={handleClick}
        type="button"
        aria-pressed={scheduled}
        data-id="toggle-departure-reminder"
      >
        <span aria-hidden="true">{scheduled ? '🔕' : '⏰'}</span>
        {scheduled ? t('reminder.cancel') : t('reminder.set')}
      </button>
      {reminder.status.kind === 'scheduled' && (
        <span className={styles.reminderNote} role="status">
          {t('reminder.scheduled', {
            time: new Date(reminder.status.fireAt).toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit',
            }),
          })}
        </span>
      )}
      {reminder.status.kind === 'error' && (
        <span className={styles.reminderError} role="alert">
          {t(reminder.status.messageKey as TranslationKey)}
        </span>
      )}
    </div>
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
  // The trip's two ends are the rider's own pins and arrive nameless. Say whose they
  // are rather than leaving the line where a stop name would be blank.
  const label = name || (
    kind === 'origin' ? t('place.yourLocation') :
    kind === 'destination' ? t('place.yourDestination') :
    ''
  )
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
        <span className={styles.nodeName}>{label}</span>
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
                {leg.routeShortName && leg.routeShortName.length <= 8 && (
                  <span className={styles.routeNumber}>{leg.routeShortName}</span>
                )}
              </span>
              <span className={styles.rideDuration}>{formatDuration(leg.duration)}</span>
            </div>
            {/* Israel Railways route names are whole descriptions — too long
                for the chip, still worth reading. Wrapping muted line instead. */}
            {leg.routeShortName && leg.routeShortName.length > 8 && (
              <div className={styles.routeLongName} dir="auto">{leg.routeShortName}</div>
            )}
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
