'use client'

import { useState } from 'react'
import { looksLikeEmail, looksLikePhone } from '../../hooks/useRegistration'
import { useI18n } from '../../i18n'
import styles from './RegistrationScreen.module.css'

interface RegistrationScreenProps {
  onSubmit: (email: string, phone: string) => Promise<{ ok: true } | { ok: false; error: string }>
}

/**
 * Registration gate.
 *
 * Collects the minimum needed to keep the pricing promise: a way to reach the
 * user before the app becomes paid, and an identity that carries their
 * early-user standing to a new device. Nothing else is asked for.
 *
 * Client-side checks only catch typos before a round trip — the daemon
 * re-validates and normalises, and is the authority.
 */
export default function RegistrationScreen({ onSubmit }: RegistrationScreenProps) {
  const { t } = useI18n()
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!looksLikeEmail(email.trim())) {
      setError(t('register.invalidEmail'))
      return
    }
    if (!looksLikePhone(phone)) {
      setError(t('register.invalidPhone'))
      return
    }

    setSubmitting(true)
    setError(null)
    const result = await onSubmit(email.trim(), phone.trim())
    if (!result.ok) {
      // Re-enable on failure, otherwise a dropped connection leaves the user
      // staring at a dead button. Prefer the server's reason when it gave one.
      setSubmitting(false)
      setError(result.error || t('register.failed'))
    }
  }

  return (
    <div className={styles.screen} data-id="registration-screen">
      <form className={styles.card} onSubmit={handleSubmit}>
        <h1 className={styles.title}>{t('register.title')}</h1>
        <p className={styles.subtitle}>{t('register.subtitle')}</p>

        <label className={styles.label} htmlFor="register-email">{t('register.email')}</label>
        <input
          id="register-email"
          className={styles.input}
          type="email"
          inputMode="email"
          autoComplete="email"
          value={email}
          onChange={e => { setEmail(e.target.value); setError(null) }}
          placeholder={t('register.emailPlaceholder')}
          disabled={submitting}
          dir="ltr"
          data-id="registration-email"
        />

        <label className={styles.label} htmlFor="register-phone">{t('register.phone')}</label>
        <input
          id="register-phone"
          className={styles.input}
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          value={phone}
          onChange={e => { setPhone(e.target.value); setError(null) }}
          placeholder={t('register.phonePlaceholder')}
          disabled={submitting}
          dir="ltr"
          data-id="registration-phone"
        />

        {error && <div className={styles.error} role="alert" data-id="registration-error">{error}</div>}

        <button
          type="submit"
          className={styles.submitBtn}
          disabled={submitting}
          data-id="submit-registration"
        >
          {submitting ? t('register.submitting') : t('register.submit')}
        </button>

        <p className={styles.privacy}>
          {t('register.privacy')}{' '}
          <a href="/privacy" className={styles.privacyLink} data-id="registration-privacy-link">
            {t('register.privacyLink')}
          </a>
        </p>
      </form>
    </div>
  )
}
