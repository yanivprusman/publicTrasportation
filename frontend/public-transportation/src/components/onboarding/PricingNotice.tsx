'use client'

import { useI18n } from '../../i18n'
import styles from './PricingNotice.module.css'

interface PricingNoticeProps {
  onAcknowledge: () => void
}

/**
 * Up-front pricing disclosure, shown once on first visit and before
 * registration is asked for — nobody hands over contact details without first
 * being told what they are being contacted about. Matches Android's
 * PricingNoticeDialog.
 */
export default function PricingNotice({ onAcknowledge }: PricingNoticeProps) {
  const { t } = useI18n()

  return (
    <div className={styles.backdrop} role="presentation">
      <div
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="pricing-notice-title"
        data-id="pricing-notice"
      >
        <h2 className={styles.title} id="pricing-notice-title">{t('pricing.title')}</h2>
        <p className={styles.lead}>{t('pricing.free')}</p>
        <p className={styles.body}>{t('pricing.future')}</p>
        <p className={styles.warning}>{t('pricing.warning')}</p>
        <p className={styles.founder}>{t('pricing.founder')}</p>
        <button
          type="button"
          className={styles.ackBtn}
          onClick={onAcknowledge}
          data-id="acknowledge-pricing-notice"
        >
          {t('pricing.ack')}
        </button>
      </div>
    </div>
  )
}
