import { useCallback, useEffect, useState } from 'react'
import type { Itinerary, RouteLeg, TransitMode } from '../../types'
import { formatDuration, formatTime } from '../../utils/time-format'
import { getModeStyle, getModeLabel } from '../../utils/mode-colors'
import styles from './JourneyNavigator.module.css'

interface JourneyNavigatorProps {
  itinerary: Itinerary
  onClose: () => void
}

const MODE_ICONS: Record<TransitMode, string> = {
  WALK: '\u{1F6B6}',
  BUS: '\u{1F68C}',
  RAIL: '\u{1F686}',
  TRAM: '\u{1F68B}',
  SUBWAY: '\u{1F687}',
}

/** mm:ss under an hour, "Hh MMm" above — the live gap to a step's start time. */
function formatGap(ms: number): string {
  const totalSec = Math.max(0, Math.round(ms / 1000))
  const hours = Math.floor(totalSec / 3600)
  if (hours > 0) {
    const mins = Math.floor((totalSec % 3600) / 60)
    return `${hours}h ${String(mins).padStart(2, '0')}m`
  }
  const mins = Math.floor(totalSec / 60)
  const secs = totalSec % 60
  return `${mins}:${String(secs).padStart(2, '0')}`
}

interface StepInstruction {
  headline: string
  detail: string | null
  getOff: string | null
}

/** Turn one leg into the human instruction shown big on the step card. */
function instructionFor(leg: RouteLeg, isLast: boolean): StepInstruction {
  if (leg.mode === 'WALK') {
    return {
      headline: isLast ? 'Walk to your destination' : `Walk to ${leg.to.name}`,
      detail: `${formatDuration(leg.duration)} on foot`,
      getOff: null,
    }
  }
  const route = leg.routeShortName ? ` ${leg.routeShortName}` : ''
  const stopCount = leg.intermediateStops?.length ?? 0
  const rideDetail = stopCount > 0
    ? `${stopCount} ${stopCount === 1 ? 'stop' : 'stops'} · ${formatDuration(leg.duration)}`
    : formatDuration(leg.duration)
  return {
    headline: `Take ${getModeLabel(leg.mode)}${route}`,
    detail: rideDetail,
    getOff: leg.to.name,
  }
}

export default function JourneyNavigator({ itinerary, onClose }: JourneyNavigatorProps) {
  const legs = itinerary.legs
  const [stepIndex, setStepIndex] = useState(0)
  // "Arrived" is a virtual final step after the last leg.
  const arrivedIndex = legs.length
  const onArrived = stepIndex >= arrivedIndex

  // Live clock so the per-step departure countdown ticks.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  const goPrev = useCallback(() => setStepIndex((i) => Math.max(0, i - 1)), [])
  const goNext = useCallback(() => setStepIndex((i) => Math.min(arrivedIndex, i + 1)), [arrivedIndex])

  // Keyboard: Escape exits, arrows step through the journey.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      else if (e.key === 'ArrowRight') goNext()
      else if (e.key === 'ArrowLeft') goPrev()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, goNext, goPrev])

  const totalSteps = legs.length
  const currentLeg = onArrived ? null : legs[stepIndex]
  const nextLeg = onArrived ? null : legs[stepIndex + 1] ?? null

  return (
    <div className={styles.overlay} role="dialog" aria-modal="true" aria-label="Journey navigation" data-id="journey-navigator">
      <header className={styles.header}>
        <div className={styles.headerText}>
          <span className={styles.headerLabel}>Journey</span>
          <span className={styles.headerRange}>
            {formatTime(itinerary.startTime)} → {formatTime(itinerary.endTime)} · {formatDuration(itinerary.duration)}
          </span>
        </div>
        <button className={styles.exitBtn} onClick={onClose} type="button" aria-label="Exit journey" data-id="exit-journey">
          ✕
        </button>
      </header>

      <div className={styles.progress} aria-hidden="true">
        {legs.map((leg, i) => {
          const style = getModeStyle(leg.mode, leg.routeColor)
          const done = i < stepIndex
          const active = i === stepIndex
          return (
            <span
              key={i}
              className={`${styles.pip} ${done ? styles.pipDone : ''} ${active ? styles.pipActive : ''}`}
              style={{ '--pip-color': style.color } as React.CSSProperties}
            />
          )
        })}
        <span className={`${styles.pip} ${styles.pipFlag} ${onArrived ? styles.pipActive : ''}`} />
      </div>

      <main className={styles.body}>
        {onArrived ? (
          <ArrivedCard itinerary={itinerary} />
        ) : (
          <StepCard leg={currentLeg!} isLast={stepIndex === legs.length - 1} nowMs={now}
            stepNumber={stepIndex + 1} totalSteps={totalSteps} />
        )}

        {nextLeg && (
          <div className={styles.upNext} data-id="journey-up-next">
            <span className={styles.upNextLabel}>Up next</span>
            <span className={styles.upNextIcon} aria-hidden="true">{MODE_ICONS[nextLeg.mode]}</span>
            <span className={styles.upNextText}>
              {nextLeg.mode === 'WALK'
                ? `Walk to ${nextLeg.to.name}`
                : `${getModeLabel(nextLeg.mode)}${nextLeg.routeShortName ? ` ${nextLeg.routeShortName}` : ''} → ${nextLeg.to.name}`}
            </span>
          </div>
        )}
        {!onArrived && !nextLeg && (
          <div className={styles.upNext} data-id="journey-up-next">
            <span className={styles.upNextLabel}>Up next</span>
            <span className={styles.upNextIcon} aria-hidden="true">🏁</span>
            <span className={styles.upNextText}>Arrive at your destination</span>
          </div>
        )}
      </main>

      <footer className={styles.controls}>
        <button
          className={styles.navBtn}
          onClick={goPrev}
          disabled={stepIndex === 0}
          type="button"
          data-id="journey-prev-step"
        >
          ‹ Back
        </button>
        <span className={styles.stepCounter}>
          {onArrived ? 'Arrived' : `Step ${stepIndex + 1} of ${totalSteps}`}
        </span>
        {onArrived ? (
          <button className={`${styles.navBtn} ${styles.navBtnPrimary}`} onClick={onClose} type="button" data-id="journey-finish">
            Done
          </button>
        ) : (
          <button className={`${styles.navBtn} ${styles.navBtnPrimary}`} onClick={goNext} type="button" data-id="journey-next-step">
            {stepIndex === legs.length - 1 ? 'Arrive ›' : 'Next ›'}
          </button>
        )}
      </footer>
    </div>
  )
}

interface StepCardProps {
  leg: RouteLeg
  isLast: boolean
  nowMs: number
  stepNumber: number
  totalSteps: number
}

function StepCard({ leg, isLast, nowMs, stepNumber, totalSteps }: StepCardProps) {
  const style = getModeStyle(leg.mode, leg.routeColor)
  const { headline, detail, getOff } = instructionFor(leg, isLast)
  const startMs = new Date(leg.startTime).getTime()
  const hasTime = !Number.isNaN(startMs)
  const remaining = hasTime ? startMs - nowMs : NaN
  const verb = leg.mode === 'WALK' ? 'Leave' : 'Departs'

  return (
    <section className={styles.stepCard} style={{ '--leg-color': style.color } as React.CSSProperties}>
      <div className={styles.stepIconWrap}>
        <span className={styles.stepIcon} aria-hidden="true">{MODE_ICONS[leg.mode]}</span>
      </div>
      <div className={styles.stepHeadline} data-id="journey-step-headline">{headline}</div>
      {leg.mode !== 'WALK' && (
        <div className={styles.boardAt}>Board at <strong>{leg.from.name}</strong></div>
      )}
      {detail && <div className={styles.stepDetail}>{detail}</div>}
      {getOff && (
        <div className={styles.getOff}>
          <span className={styles.getOffLabel}>Get off at</span>
          <span className={styles.getOffName}>{getOff}</span>
        </div>
      )}
      <div className={styles.timing} data-id="journey-step-timing">
        {hasTime && remaining > 0 ? (
          <>
            <span className={styles.timingBig}>{formatGap(remaining)}</span>
            <span className={styles.timingLabel}>{verb} · scheduled {formatTime(leg.startTime)}</span>
          </>
        ) : hasTime ? (
          <>
            <span className={`${styles.timingBig} ${styles.timingNow}`}>Now</span>
            <span className={styles.timingLabel}>Scheduled {formatTime(leg.startTime)}</span>
          </>
        ) : (
          <span className={styles.timingLabel}>Step {stepNumber} of {totalSteps}</span>
        )}
      </div>
    </section>
  )
}

function ArrivedCard({ itinerary }: { itinerary: Itinerary }) {
  const dest = itinerary.legs[itinerary.legs.length - 1]?.to.name
  return (
    <section className={`${styles.stepCard} ${styles.arrivedCard}`}>
      <div className={styles.arrivedIcon} aria-hidden="true">🏁</div>
      <div className={styles.stepHeadline}>You&apos;ve arrived</div>
      {dest && <div className={styles.getOffName}>{dest}</div>}
      <div className={styles.stepDetail}>
        Arrived {formatTime(itinerary.endTime)} · {formatDuration(itinerary.duration)} total
      </div>
    </section>
  )
}
