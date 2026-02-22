import type { SiriData } from '../../types'

interface StationArrivalsProps {
  siriData: SiriData | null
  loading: boolean
  error: string | null
  stationCode: string
}

function StationArrivals({ siriData, loading, error, stationCode }: StationArrivalsProps) {
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

              let arrivalTime = 'N/A'
              if (monitoredCall?.ExpectedArrivalTime) {
                const date = new Date(monitoredCall.ExpectedArrivalTime)
                arrivalTime = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
              }

              let distance = 'N/A'
              if (monitoredCall?.DistanceFromStop) {
                const m = monitoredCall.DistanceFromStop
                distance = m >= 1000 ? `${(m / 1000).toFixed(1)} km` : `${m} m`
              }

              return (
                <tr key={visit.ItemIdentifier || index}>
                  <td>{journey.PublishedLineName || 'N/A'}</td>
                  <td>{journey.DirectionRef || 'N/A'}</td>
                  <td>{journey.DestinationRef || 'N/A'}</td>
                  <td>{arrivalTime}</td>
                  <td>{distance}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default StationArrivals
