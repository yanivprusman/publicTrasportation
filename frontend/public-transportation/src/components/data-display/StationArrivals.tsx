import { Fragment, useState, useEffect } from 'react'
import type { SiriData } from '../../types'

interface StationArrivalsProps {
  siriData: SiriData | null
  error: string | null
  stationCode: string
  lineFilter?: string
  onVehicleSelect?: (lat: number, lon: number) => void
}

function StationArrivals({ siriData, error, stationCode, lineFilter, onVehicleSelect }: StationArrivalsProps) {
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

  if (error) return <h2>Error: {error}</h2>
  if (!siriData) return <h2>Loading...</h2>

  const allVisits = siriData?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit || []
  const stopNames = siriData?._stopNames || {}

  if (allVisits.length === 0) {
    return <h2>No vehicles are currently being monitored for station {stationCode}</h2>
  }

  const trimmedFilter = lineFilter?.trim().toLowerCase() || ''
  const monitoredStopVisits = trimmedFilter
    ? allVisits.filter(visit =>
        (visit.MonitoredVehicleJourney.PublishedLineName || '').toString().toLowerCase().trim() === trimmedFilter
      )
    : allVisits

  if (trimmedFilter && monitoredStopVisits.length === 0) {
    return <h2>No vehicles match line {lineFilter?.trim()}</h2>
  }

  const heading = trimmedFilter
    ? `Showing ${monitoredStopVisits.length} of ${allVisits.length} vehicles`
    : `Monitored Vehicles: ${allVisits.length}`

  return (
    <div>
      <h2>{heading}</h2>
      <div style={{ overflowX: 'auto' }}>
        <table className="vehicle-table">
          <thead>
            <tr>
              <th>Line</th>
              <th>Dir</th>
              <th>Dest</th>
              <th>Arrival</th>
              <th>Distance</th>
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
                  arrivalDisplay = { primary: diffMin < 0 ? absTime : 'now' }
                } else if (diffMin <= 60) {
                  arrivalDisplay = { primary: `in ${diffMin} min`, secondary: absTime }
                } else {
                  arrivalDisplay = { primary: absTime }
                }
              }

              let distance = 'N/A'
              const exactMeters = monitoredCall?.DistanceFromStop
              if (exactMeters != null) {
                distance = exactMeters >= 1000 ? `${(exactMeters / 1000).toFixed(1)} km` : `${exactMeters} m`
              }

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
                    <td>{stopNames[journey.DestinationRef] || journey.DestinationRef || 'N/A'}</td>
                    <td>
                      {arrivalDisplay.primary}
                      {arrivalDisplay.secondary && (
                        <span style={{ marginLeft: 4, fontSize: '0.8em', color: '#888' }}>{arrivalDisplay.secondary}</span>
                      )}
                    </td>
                    <td>{distance}</td>
                  </tr>
                  {isExpanded && (
                    <tr className="expanded-detail-row">
                      <td colSpan={5}>
                        <div className="detail-content">
                          <div className="detail-item">
                            <span className="detail-label">Vehicle ID:</span> {journey.VehicleRef || 'N/A'}
                          </div>
                          <div className="detail-item">
                            <span className="detail-label">Exact distance:</span> {exactMeters != null ? `${exactMeters} m` : 'N/A'}
                          </div>
                          <div className="detail-item">
                            <span className="detail-label">Full arrival:</span> {fullArrivalTime}
                          </div>
                          {vehicleLocation && onVehicleSelect && (
                            <div className="detail-item">
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation()
                                  onVehicleSelect(vehicleLocation.Latitude, vehicleLocation.Longitude)
                                }}
                                style={{
                                  padding: '4px 12px',
                                  border: '1px solid #2196F3',
                                  borderRadius: 6,
                                  background: '#e3f2fd',
                                  color: '#1565c0',
                                  fontSize: 13,
                                  fontWeight: 600,
                                  cursor: 'pointer',
                                }}
                              >
                                Show on map
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
