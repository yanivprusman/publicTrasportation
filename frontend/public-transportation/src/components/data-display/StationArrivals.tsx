import { Fragment, useState, useEffect } from 'react'
import type { SiriData, MonitoredStopVisit } from '../../types'
import { useI18n } from '../../i18n'
import { formatStopDistance } from '../../utils/distance'

interface StationArrivalsProps {
  siriData: SiriData | null
  error: string | null
  stationCode: string
  lineFilter?: string
  onVehicleSelect?: (lat: number, lon: number) => void
}

function StationArrivals({ siriData, error, stationCode, lineFilter, onVehicleSelect }: StationArrivalsProps) {
  const { t } = useI18n()
  // Expansion is tracked by a stable per-visit key, not list index: the list
  // refreshes every 15s and rows shift as buses arrive/depart (and the line
  // filter changes which rows exist), so an index would silently point the
  // expanded details at a different vehicle.
  const [expandedKey, setExpandedKey] = useState<string | null>(null)
  const [, setTick] = useState(0)

  useEffect(() => {
    const id = setInterval(() => setTick(t => t + 1), 30000)
    return () => clearInterval(id)
  }, [])

  if (error) return <h2>{t('arrivals.error', { message: error })}</h2>
  if (!siriData) return <h2>{t('arrivals.loading')}</h2>

  const allVisits = siriData?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit || []
  const stopNames = siriData?._stopNames || {}

  if (allVisits.length === 0) {
    return <h2>{t('arrivals.noneMonitored', { code: stationCode })}</h2>
  }

  const trimmedFilter = lineFilter?.trim().toLowerCase() || ''
  const filteredVisits = trimmedFilter
    ? allVisits.filter(visit =>
        (visit.MonitoredVehicleJourney.PublishedLineName || '').toString().toLowerCase().trim() === trimmedFilter
      )
    : allVisits

  // Order by soonest expected arrival. SIRI returns visits in no guaranteed
  // order, but an arrivals board must show the next vehicle first. Copy before
  // sorting so the array held in siriData state is never mutated. Visits with a
  // missing or unparseable ExpectedArrivalTime sort to the end.
  const arrivalMs = (visit: MonitoredStopVisit): number => {
    const t = visit.MonitoredVehicleJourney.MonitoredCall?.ExpectedArrivalTime
    const ms = t ? Date.parse(t) : NaN
    return isNaN(ms) ? Infinity : ms
  }
  const monitoredStopVisits = [...filteredVisits].sort((a, b) => arrivalMs(a) - arrivalMs(b))

  if (trimmedFilter && monitoredStopVisits.length === 0) {
    return <h2>{t('arrivals.noMatch', { line: lineFilter?.trim() ?? '' })}</h2>
  }

  const heading = trimmedFilter
    ? t('arrivals.showing', { n: monitoredStopVisits.length, m: allVisits.length })
    : t('arrivals.monitored', { n: allVisits.length })

  return (
    <div>
      <h2>{heading}</h2>
      <div style={{ overflowX: 'auto' }}>
        <table className="vehicle-table">
          <thead>
            <tr>
              <th>{t('arrivals.thLine')}</th>
              <th>{t('arrivals.thDir')}</th>
              <th>{t('arrivals.thDest')}</th>
              <th>{t('arrivals.thArrival')}</th>
              <th>{t('arrivals.thDistance')}</th>
            </tr>
          </thead>
          <tbody>
            {monitoredStopVisits.map((visit, index) => {
              const journey = visit.MonitoredVehicleJourney
              const monitoredCall = journey.MonitoredCall
              const rowKey = visit.ItemIdentifier || journey.VehicleRef || `row-${index}`
              const isExpanded = expandedKey === rowKey

              let arrivalDisplay: { primary: string; secondary?: string } = { primary: 'N/A' }
              let fullArrivalTime = 'N/A'
              if (monitoredCall?.ExpectedArrivalTime) {
                const date = new Date(monitoredCall.ExpectedArrivalTime)
                const absTime = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                fullArrivalTime = date.toLocaleString()
                const diffMin = Math.round((date.getTime() - Date.now()) / 60000)
                if (diffMin < 1) {
                  arrivalDisplay = { primary: diffMin < 0 ? absTime : t('arrivals.now') }
                } else if (diffMin <= 60) {
                  arrivalDisplay = { primary: t('arrivals.inMin', { n: diffMin }), secondary: absTime }
                } else {
                  arrivalDisplay = { primary: absTime }
                }
              }

              // SIRI's DistanceFromStop is how far the vehicle has driven on this trip,
              // not how far it is from this stop — the column is named accordingly.
              const travelledMeters = monitoredCall?.DistanceFromStop
              const travelled = travelledMeters != null ? formatStopDistance(travelledMeters) : '—'

              const vehicleLocation = journey.VehicleLocation

              return (
                <Fragment key={rowKey}>
                  <tr
                    className="expandable-row"
                    data-id="toggle-arrival-details"
                    onClick={() => setExpandedKey(isExpanded ? null : rowKey)}
                  >
                    <td>{journey.PublishedLineName || 'N/A'}</td>
                    <td>{journey.DirectionRef || 'N/A'}</td>
                    <td>{stopNames[journey.DestinationRef] || '—'}</td>
                    <td>
                      {arrivalDisplay.primary}
                      {arrivalDisplay.secondary && (
                        <span style={{ marginLeft: 4, fontSize: '0.8em', color: '#888' }}>{arrivalDisplay.secondary}</span>
                      )}
                    </td>
                    <td>{travelled}</td>
                  </tr>
                  {isExpanded && (
                    <tr className="expanded-detail-row">
                      <td colSpan={5}>
                        <div className="detail-content">
                          <div className="detail-item">
                            <span className="detail-label">{t('arrivals.vehicleId')}</span> {journey.VehicleRef || 'N/A'}
                          </div>
                          <div className="detail-item">
                            <span className="detail-label">{t('arrivals.exactDistance')}</span> {travelledMeters != null ? t('dist.m', { n: travelledMeters }) : '—'}
                          </div>
                          <div className="detail-item">
                            <span className="detail-label">{t('arrivals.fullArrival')}</span> {fullArrivalTime}
                          </div>
                          {vehicleLocation && onVehicleSelect && (
                            <div className="detail-item">
                              <button
                                type="button"
                                data-id="show-vehicle-on-map"
                                onClick={(e) => {
                                  e.stopPropagation()
                                  onVehicleSelect(vehicleLocation.Latitude, vehicleLocation.Longitude)
                                }}
                                className="show-on-map-btn"
                              >
                                {t('arrivals.showOnMap')}
                              </button>
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default StationArrivals
