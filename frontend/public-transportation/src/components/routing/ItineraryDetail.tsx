import { useState } from 'react'
import type { Itinerary } from '../../types'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle, getModeLabel } from '../../utils/mode-colors'
import styles from './ItineraryDetail.module.css'

interface ItineraryDetailProps {
  itinerary: Itinerary
}

export default function ItineraryDetail({ itinerary }: ItineraryDetailProps) {
  return (
    <div className={styles.wrapper}>
      <div className={styles.summary}>
        {formatTime(itinerary.startTime)} - {formatTime(itinerary.endTime)}
        {' \u00B7 '}
        {formatDuration(itinerary.duration)}
        {' \u00B7 '}
        {itinerary.transfers} transfer{itinerary.transfers !== 1 ? 's' : ''}
      </div>
      {itinerary.legs.map((leg, i) => (
        <LegCard key={i} leg={leg} />
      ))}
    </div>
  )
}

function LegCard({ leg }: { leg: Itinerary['legs'][number] }) {
  const style = getModeStyle(leg.mode, leg.routeColor)
  const [stopsOpen, setStopsOpen] = useState(false)
  const hasStops = leg.intermediateStops && leg.intermediateStops.length > 0

  const description = leg.mode === 'WALK'
    ? `Walk ${formatDuration(leg.duration)} to ${leg.to.name}`
    : `${getModeLabel(leg.mode)}${leg.routeShortName ? ` ${leg.routeShortName}` : ''} toward ${leg.to.name} - ${formatDuration(leg.duration)}${hasStops ? ` (${leg.intermediateStops!.length} stops)` : ''}`

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
              {stopsOpen ? 'Hide' : 'Show'} {leg.intermediateStops!.length} stops
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
