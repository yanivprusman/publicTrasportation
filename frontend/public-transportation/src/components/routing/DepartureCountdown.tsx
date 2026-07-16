import { useEffect, useState } from 'react'
import type { Itinerary } from '../../types'
import { formatTime } from '../../utils/time-format'
import { getModeLabel } from '../../utils/mode-colors'
import styles from './DepartureCountdown.module.css'

interface DepartureCountdownProps {
  itinerary: Itinerary
}

// Time under which the banner turns urgent (red + pulse): the user has to move now.
const URGENT_MS = 2 * 60 * 1000

function formatClock(ms: number): { text: string; big: boolean } {
  const totalSec = Math.max(0, Math.round(ms / 1000))
  const hours = Math.floor(totalSec / 3600)
  if (hours > 0) {
    const mins = Math.floor((totalSec % 3600) / 60)
    return { text: `${hours}h ${String(mins).padStart(2, '0')}m`, big: false }
  }
  const mins = Math.floor(totalSec / 60)
  const secs = totalSec % 60
  return { text: `${mins}:${String(secs).padStart(2, '0')}`, big: true }
}

export default function DepartureCountdown({ itinerary }: DepartureCountdownProps) {
  // Re-render every second so the countdown ticks live. A single interval keyed
  // to the itinerary's departure is enough — no per-leg timers.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  const departMs = new Date(itinerary.startTime).getTime()
  // A missing/unparseable start time (legs can default to '') has no deadline to
  // count down to — render nothing rather than a "NaN" banner.
  if (Number.isNaN(departMs)) return null

  const remaining = departMs - now

  // The ride the countdown is really about: the first non-walk leg. Walk-only
  // itineraries have no vehicle to catch, so the sub-line describes the walk.
  const firstRide = itinerary.legs.find((l) => l.mode !== 'WALK')

  if (remaining <= 0) {
    return (
      <div className={`${styles.banner} ${styles.passed}`} data-id="departure-countdown" role="status">
        <span className={`${styles.clock} ${styles.clockSmall}`}>—</span>
        <div className={styles.text}>
          <span className={styles.headline}>This departure has passed</span>
          <span className={styles.sub}>Search again for an up-to-date route.</span>
        </div>
      </div>
    )
  }

  const { text, big } = formatClock(remaining)
  const urgent = remaining <= URGENT_MS
  const totalMin = Math.floor(remaining / 60000)

  const headline = urgent
    ? 'Leave now to catch it!'
    : totalMin < 60
      ? `Leave in ${totalMin} min to catch it`
      : 'Leave on time to catch it'

  const sub = firstRide
    ? `Catch ${getModeLabel(firstRide.mode)}${firstRide.routeShortName ? ` ${firstRide.routeShortName}` : ''} at ${firstRide.from.name} · departs ${formatTime(firstRide.startTime)}`
    : `Start walking to arrive by ${formatTime(itinerary.endTime)}`

  return (
    <div
      className={`${styles.banner} ${urgent ? styles.urgent : ''}`}
      data-id="departure-countdown"
      // No aria-live here: the clock re-renders every second, and a live region
      // would make screen readers announce the countdown once per tick.
      aria-label={`${headline}. ${sub}`}
    >
      <span className={`${styles.clock} ${big ? styles.clockBig : styles.clockSmall}`}>{text}</span>
      <div className={styles.text}>
        <span className={styles.headline}>{headline}</span>
        <span className={styles.sub}>{sub}</span>
      </div>
    </div>
  )
}
