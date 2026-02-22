import axios from 'axios'
import type { SiriData, VehicleMarker, RouteShapeData } from '../types'

export const fetchStationArrivals = async (stationCode: string, detailLevel = 'calls'): Promise<SiriData> => {
  try {
    const response = await axios.get<SiriData>(`/api/transport?station=${stationCode}&detail=${detailLevel}`)
    return response.data
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.data?.error) {
      throw new Error(err.response.data.error)
    }
    throw err
  }
}

export const extractVehicleMarkers = (siriData: SiriData): VehicleMarker[] => {
  if (!siriData?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit) {
    return []
  }

  const monitoredStopVisits = siriData.Siri.ServiceDelivery.StopMonitoringDelivery[0].MonitoredStopVisit

  return monitoredStopVisits.map(visit => {
    const journey = visit.MonitoredVehicleJourney
    const vehicleLocation = journey.VehicleLocation

    if (vehicleLocation?.Latitude && vehicleLocation?.Longitude) {
      return {
        position: [vehicleLocation.Latitude, vehicleLocation.Longitude] as [number, number],
        vehicleRef: journey.VehicleRef,
        lineNumber: journey.PublishedLineName,
        expectedArrival: journey.MonitoredCall?.ExpectedArrivalTime ?? '',
        distanceFromStop: journey.MonitoredCall?.DistanceFromStop ?? 0
      }
    }
    return null
  }).filter((m): m is VehicleMarker => m !== null)
}

export const fetchLineShape = async (lineNumber: string): Promise<RouteShapeData> => {
  const response = await fetch(`/api/line-shape?line=${lineNumber}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Cache-Control': 'no-cache'
    }
  })

  const responseText = await response.text()
  const data: RouteShapeData = JSON.parse(responseText)

  if (data.error) {
    throw new Error((data as unknown as { error: string }).error)
  }

  const hasDirection0 = Array.isArray(data['0']) && data['0'].length > 0
  const hasDirection1 = Array.isArray(data['1']) && data['1'].length > 0

  if (!hasDirection0 && !hasDirection1) {
    throw new Error('No valid shape data found')
  }

  return data
}
