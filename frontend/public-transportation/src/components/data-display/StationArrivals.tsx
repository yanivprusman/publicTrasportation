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
      <table className="vehicle-table">
        <thead>
          <tr>
            <th>Line</th>
            <th>Direction</th>
            <th>Destination</th>
            <th>Arrival Time</th>
            <th>Vehicle</th>
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
              arrivalTime = date.toLocaleTimeString()
            }

            return (
              <tr key={visit.ItemIdentifier || index}>
                <td>{journey.PublishedLineName || 'N/A'}</td>
                <td>{journey.DirectionRef || 'N/A'}</td>
                <td>{journey.DestinationRef || 'N/A'}</td>
                <td>{arrivalTime}</td>
                <td>{journey.VehicleRef || 'N/A'}</td>
                <td>{monitoredCall?.DistanceFromStop ? `${monitoredCall.DistanceFromStop} meters` : 'N/A'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default StationArrivals
