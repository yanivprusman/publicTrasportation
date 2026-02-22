import { Fragment, useState } from 'react'
import type { SiriData } from '../../types'

interface StationArrivalsProps {
  siriData: SiriData | null
  loading: boolean
  error: string | null
  stationCode: string
}

function StationArrivals({ siriData, loading, error, stationCode }: StationArrivalsProps) {
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null)

  if (loading) return <h2>Loading...</h2>
  if (error) return <h2>Error: {error}</h2>
  if (!siriData) return <h2>No data available</h2>

  const monitoredStopVisits = siriData?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit || []

  if (monitoredStopVisits.length === 0) {
    return <h2>No vehicles are currently being monitored for station {stationCode}</h2>
  }

  return (
    <div>
      <h2>Monitored Vehicles:</h2>
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
              const isExpanded = expandedIndex === index

              let arrivalTime = 'N/A'
              let fullArrivalTime = 'N/A'
              if (monitoredCall?.ExpectedArrivalTime) {
                const date = new Date(monitoredCall.ExpectedArrivalTime)
                arrivalTime = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                fullArrivalTime = date.toLocaleString()
              }

              let distance = 'N/A'
              const exactMeters = monitoredCall?.DistanceFromStop
              if (exactMeters != null) {
                distance = exactMeters >= 1000 ? `${(exactMeters / 1000).toFixed(1)} km` : `${exactMeters} m`
              }

              const vehicleLocation = journey.VehicleLocation

              return (
                <Fragment key={visit.ItemIdentifier || index}>
                  <tr
                    className="expandable-row"
                    onClick={() => setExpandedIndex(isExpanded ? null : index)}
                  >
                    <td>{journey.PublishedLineName || 'N/A'}</td>
                    <td>{journey.DirectionRef || 'N/A'}</td>
                    <td>{journey.DestinationRef || 'N/A'}</td>
                    <td>{arrivalTime}</td>
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
                          {vehicleLocation && (
                            <div className="detail-item">
                              <span className="detail-label">GPS:</span> {vehicleLocation.Latitude}, {vehicleLocation.Longitude}
                            </div>
                          )}
                          <div className="detail-item">
                            <span className="detail-label">Full arrival:</span> {fullArrivalTime}
                          </div>
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
