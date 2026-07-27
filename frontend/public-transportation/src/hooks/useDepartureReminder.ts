import { useState, useCallback, useEffect, useRef } from 'react'

/** How long before departure the reminder fires. Matches the Android default. */
export const REMINDER_LEAD_MINUTES = 10

/**
 * setTimeout stores its delay in a 32-bit int; anything longer overflows and
 * fires immediately. Reminders further out than this are refused rather than
 * silently firing right away.
 */
const MAX_TIMEOUT_MS = 2_147_483_647

export type ReminderStatus =
  | { kind: 'idle' }
  | { kind: 'scheduled'; fireAt: number; departureIso: string }
  | { kind: 'error'; messageKey: string }

export interface UseDepartureReminderReturn {
  status: ReminderStatus
  /** True while a reminder is pending for this exact departure. */
  isScheduledFor: (departureIso: string) => boolean
  schedule: (params: { departureIso: string; line: string; stop: string }) => Promise<void>
  cancel: () => void
}

/**
 * A departure reminder backed by the Notification API.
 *
 * This is the web counterpart of Android's ReminderScheduler. The important
 * difference is honesty about the platform: a browser timer only survives while
 * the tab is alive, so we deliberately do NOT promise background delivery. The
 * notification fires from a live timer; closing the tab cancels it.
 */
export function useDepartureReminder(): UseDepartureReminderReturn {
  const [status, setStatus] = useState<ReminderStatus>({ kind: 'idle' })
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  useEffect(() => clearTimer, [clearTimer])

  const cancel = useCallback(() => {
    clearTimer()
    setStatus({ kind: 'idle' })
  }, [clearTimer])

  const schedule = useCallback(async ({ departureIso, line, stop }: {
    departureIso: string
    line: string
    stop: string
  }) => {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      setStatus({ kind: 'error', messageKey: 'reminder.unsupported' })
      return
    }

    const departureMs = new Date(departureIso).getTime()
    if (!Number.isFinite(departureMs)) {
      setStatus({ kind: 'error', messageKey: 'reminder.tooSoon' })
      return
    }

    const fireAt = departureMs - REMINDER_LEAD_MINUTES * 60_000
    const delay = fireAt - Date.now()
    // Nothing useful to schedule if the lead time has already elapsed — the
    // rider needs to leave now, not be told about it later.
    if (delay <= 0 || delay > MAX_TIMEOUT_MS) {
      setStatus({ kind: 'error', messageKey: 'reminder.tooSoon' })
      return
    }

    let permission = Notification.permission
    if (permission === 'default') {
      permission = await Notification.requestPermission()
    }
    if (permission !== 'granted') {
      setStatus({ kind: 'error', messageKey: 'reminder.denied' })
      return
    }

    clearTimer()
    timerRef.current = setTimeout(() => {
      timerRef.current = null
      const time = new Date(departureMs).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      })
      try {
        new Notification(`\u{1F68C} ${line}`, {
          body: `${time} · ${stop}`,
          tag: `pt-departure-${departureIso}`,
        })
      } catch {
        // Some browsers refuse constructor notifications outside a service
        // worker. The reminder is lost either way; clearing state keeps the
        // button from claiming a reminder is still pending.
      }
      setStatus({ kind: 'idle' })
    }, delay)

    setStatus({ kind: 'scheduled', fireAt, departureIso })
  }, [clearTimer])

  const isScheduledFor = useCallback(
    (departureIso: string) => status.kind === 'scheduled' && status.departureIso === departureIso,
    [status]
  )

  return { status, isScheduledFor, schedule, cancel }
}
