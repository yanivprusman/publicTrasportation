import styles from './TimePicker.module.css'

type TimeMode = 'now' | 'depart' | 'arrive'

interface TimePickerProps {
  departureTime: Date | null
  setDepartureTime: (d: Date | null) => void
  arriveBy: boolean
  setArriveBy: (b: boolean) => void
}

export default function TimePicker({ departureTime, setDepartureTime, arriveBy, setArriveBy }: TimePickerProps) {
  // Derived from routing state, not local state: TimePicker unmounts when the
  // sheet collapses or the tab switches, and a local mode would reset to 'now'
  // while departureTime/arriveBy keep steering the actual search.
  const mode: TimeMode = departureTime === null ? 'now' : arriveBy ? 'arrive' : 'depart'

  const handleModeChange = (m: TimeMode) => {
    if (m === 'now') {
      // null = resolve to the current time when the search actually runs
      setDepartureTime(null)
      setArriveBy(false)
      return
    }
    if (departureTime === null) setDepartureTime(new Date())
    setArriveBy(m === 'arrive')
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
            data-id={`time-mode-${m}`}
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
          data-id="departure-datetime"
          className={styles.datetime}
          value={toLocalDatetime(departureTime ?? new Date())}
          onChange={e => {
            const d = new Date(e.target.value)
            if (!isNaN(d.getTime())) setDepartureTime(d)
          }}
        />
      )}
    </div>
  )
}
