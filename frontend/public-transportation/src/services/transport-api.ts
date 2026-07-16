import axios from 'axios'
import type { SiriData, VehicleMarker, LineShapeData, Coordinates } from '../types'

export interface StopResult {
  stopCode: string
  stopName: string
  lat: number
  lon: number
}

export const searchStops = async (query: string): Promise<StopResult[]> => {
  if (!query.trim()) return []
  const response = await axios.get<StopResult[]>(`/api/stops?q=${encodeURIComponent(query)}`)
  return response.data
}

export interface NearbyStop extends StopResult {
  distanceMeters: number
}

export const fetchNearbyStops = async (lat: number, lon: number, radiusMeters: number): Promise<NearbyStop[]> => {
  const response = await axios.get<NearbyStop[]>('/api/stops', {
    params: { lat, lon, radius: radiusMeters },
  })
  return response.data
}

export const fetchStopsInBounds = async (
  minLat: number,
  minLon: number,
  maxLat: number,
  maxLon: number,
): Promise<StopResult[]> => {
  const response = await axios.get<StopResult[]>('/api/stops', {
    params: { bbox: `${minLat},${minLon},${maxLat},${maxLon}` },
  })
  return response.data
}

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
        // SIRI can omit PublishedLineName; default to '' so the string contract
        // holds and consumers like the map's line filter (m.lineNumber.toLowerCase())
        // don't crash on undefined.
        lineNumber: journey.PublishedLineName ?? '',
        expectedArrival: journey.MonitoredCall?.ExpectedArrivalTime ?? '',
        distanceFromStop: journey.MonitoredCall?.DistanceFromStop ?? 0
      }
    }
    return null
  }).filter((m): m is VehicleMarker => m !== null)
}

export const fetchLineShape = async (lineNumber: string): Promise<LineShapeData> => {
  const response = await fetch(`/api/line-shape?line=${encodeURIComponent(lineNumber)}&meta=full`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Cache-Control': 'no-cache'
    }
  })

  const data = await response.json() as {
    error?: string
    directions?: Record<string, Coordinates[]>
    headsigns?: Record<string, string>
  }

  if (data.error) {
    throw new Error(data.error)
  }

  const directions = data.directions ?? {}
  const hasPoints = Object.values(directions).some(pts => Array.isArray(pts) && pts.length > 0)
  if (!hasPoints) {
    throw new Error(`No route shape found for line ${lineNumber}`)
  }

  return { directions, headsigns: data.headsigns ?? {} }
}
