import { useCallback, useEffect, useState } from 'react'

const INSTALL_ID_KEY = 'pt-install-id'
const REGISTERED_EMAIL_KEY = 'pt-registered-email'
const NOTICE_ACK_KEY = 'pt-pricing-notice-ack'

/**
 * Anonymous install id, generated once per browser and reused. It links a
 * registration to the same install the way the Android app's install UUID does,
 * and identifies nothing about the person.
 */
export function getInstallId(): string {
  try {
    const existing = localStorage.getItem(INSTALL_ID_KEY)
    if (existing) return existing
    const created = crypto.randomUUID()
    localStorage.setItem(INSTALL_ID_KEY, created)
    return created
  } catch {
    // Private-mode storage refusal: a per-session id still lets registration
    // succeed, it just will not be remembered.
    return crypto.randomUUID()
  }
}

/** Catches typos before a round trip; the daemon re-validates and is the authority. */
export function looksLikeEmail(value: string): boolean {
  const at = value.indexOf('@')
  return at > 0 && value.indexOf('.', at) > at + 1 && !value.includes(' ')
}

export function looksLikePhone(value: string): boolean {
  const digits = (value.match(/\d/g) || []).length
  return digits >= 9 && digits <= 13
}

export type RegistrationState = 'loading' | 'needsRegistration' | 'registered'

export interface UseRegistrationReturn {
  state: RegistrationState
  noticeAcknowledged: boolean
  acknowledgeNotice: () => void
  register: (email: string, phone: string) => Promise<{ ok: true } | { ok: false; error: string }>
}

/**
 * Registration gate state, mirroring the Android launch flow: the pricing
 * notice is shown first, then registration is asked for. Both decisions are
 * remembered in localStorage.
 */
export function useRegistration(): UseRegistrationReturn {
  const [state, setState] = useState<RegistrationState>('loading')
  const [noticeAcknowledged, setNoticeAcknowledged] = useState(true)

  // Resolved after mount: reading localStorage during render would not match
  // the server-rendered HTML.
  useEffect(() => {
    let email: string | null = null
    let ack = false
    try {
      email = localStorage.getItem(REGISTERED_EMAIL_KEY)
      ack = localStorage.getItem(NOTICE_ACK_KEY) === '1'
    } catch {}
    setNoticeAcknowledged(ack)
    setState(email ? 'registered' : 'needsRegistration')
  }, [])

  const acknowledgeNotice = useCallback(() => {
    setNoticeAcknowledged(true)
    try {
      localStorage.setItem(NOTICE_ACK_KEY, '1')
    } catch {}
  }, [])

  const register = useCallback(async (email: string, phone: string) => {
    try {
      const res = await fetch('/api/app/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ installId: getInstallId(), email, phone }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok || data?.ok !== true) {
        return { ok: false as const, error: typeof data?.error === 'string' ? data.error : '' }
      }
      try {
        localStorage.setItem(REGISTERED_EMAIL_KEY, email)
      } catch {}
      setState('registered')
      return { ok: true as const }
    } catch {
      return { ok: false as const, error: '' }
    }
  }, [])

  return { state, noticeAcknowledged, acknowledgeNotice, register }
}
