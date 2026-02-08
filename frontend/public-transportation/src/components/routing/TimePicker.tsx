import { useState } from 'react'
import styles from './TimePicker.module.css'

type TimeMode = 'now' | 'depart' | 'arrive'

interface TimePickerProps {
  departureTime: Date
  setDepartureTime: (d: Date) => void
  arriveBy: boolean
  setArriveBy: (b: boolean) => void
}

export default function TimePicker({ departureTime, setDepartureTime, arriveBy, setArriveBy }: TimePickerProps) {
  const [mode, setMode] = useState<TimeMode>('now')

  const handleModeChange = (m: TimeMode) => {
    setMode(m)
    if (m === 'now') {
      setDepartureTime(new Date())
      setArriveBy(false)
    } else if (m === 'depart') {
      setArriveBy(false)
    } else {
      setArriveBy(true)
    }
  }

  const toLocalDatetime = (d: Date) => {
    const offset = d.getTimezoneOffset()
    const local = new Date(d.getTime() - offset * 60000)
    return local.toISOString().slice(0, 16)
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.modes}>
        {(['now', 'depart', 'arrive'] as TimeMode[]).map(m => (
          <button
            key={m}
            type="button"
            className={`${styles.modeBtn} ${mode === m ? styles.active : ''}`}
            onClick={() => handleModeChange(m)}
          >
            {m === 'now' ? 'Now' : m === 'depart' ? 'Depart At' : 'Arrive By'}
          </button>
        ))}
      </div>
      {mode !== 'now' && (
        <input
          type="datetime-local"
          className={styles.datetime}
          value={toLocalDatetime(departureTime)}
          onChange={e => setDepartureTime(new Date(e.target.value))}
        />
      )}
    </div>
  )
}
