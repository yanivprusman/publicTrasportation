'use client'

import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { useI18n } from '../../i18n'
import styles from './ShareTripDialog.module.css'

const QR_SIZE = 200

interface ShareTripDialogProps {
  url: string
  title: string
  onClose: () => void
}

/**
 * Share sheet for a planned trip: a scannable QR of the trip link so someone
 * standing next to you can open the same journey on their own phone, plus the
 * usual share/copy actions. Mirrors the Android ShareTripDialog.
 */
export default function ShareTripDialog({ url, title, onClose }: ShareTripDialogProps) {
  const { t } = useI18n()
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null)
  const [qrFailed, setQrFailed] = useState(false)
  const [copied, setCopied] = useState(false)
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    let cancelled = false
    // Always black on white, regardless of app theme — a themed QR with low
    // contrast is a QR that will not scan.
    QRCode.toDataURL(url, {
      width: QR_SIZE,
      margin: 1,
      color: { dark: '#000000', light: '#ffffff' },
    })
      .then(dataUrl => { if (!cancelled) setQrDataUrl(dataUrl) })
      .catch(() => { if (!cancelled) setQrFailed(true) })
    return () => { cancelled = true }
  }, [url])

  // Escape closes, and focus starts inside the dialog so keyboard users are not
  // left behind on the page underneath.
  useEffect(() => {
    closeRef.current?.focus()
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2500)
    } catch {
      setCopied(false)
    }
  }

  const handleShare = async () => {
    if (!navigator.share) return
    try {
      await navigator.share({ title, url })
    } catch {
      // Dismissing the OS share sheet is a user choice, not an error.
    }
  }

  return (
    <div
      className={styles.backdrop}
      onClick={onClose}
      role="presentation"
      data-id="share-trip-backdrop"
    >
      <div
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-label={t('share.title')}
        onClick={e => e.stopPropagation()}
      >
        <div className={styles.header}>
          <span className={styles.title}>{t('share.title')}</span>
          <button
            ref={closeRef}
            type="button"
            className={styles.closeBtn}
            onClick={onClose}
            aria-label={t('share.close')}
            data-id="close-share-trip"
          >
            &times;
          </button>
        </div>

        <div className={styles.tripName} dir="auto">{title}</div>

        <div className={styles.qrBox}>
          {qrDataUrl ? (
            /* eslint-disable-next-line @next/next/no-img-element */
            <img src={qrDataUrl} alt={t('share.qrAlt')} width={QR_SIZE} height={QR_SIZE} />
          ) : qrFailed ? (
            <span className={styles.qrError}>{t('share.qrFailed')}</span>
          ) : (
            <span className={styles.qrPlaceholder} />
          )}
        </div>
        <div className={styles.scanHint}>{t('share.scan')}</div>

        <div className={styles.linkBox} dir="ltr">{url}</div>

        <div className={styles.actions}>
          <button
            type="button"
            className={`${styles.copyBtn} ${copied ? styles.copyBtnDone : ''}`}
            onClick={handleCopy}
            data-id="copy-trip-link"
          >
            {copied ? t('share.copied') : t('share.copy')}
          </button>
          {typeof navigator !== 'undefined' && !!navigator.share && (
            <button
              type="button"
              className={styles.shareBtn}
              onClick={handleShare}
              data-id="share-trip-link"
            >
              {t('share.link')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
