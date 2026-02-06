export interface LatLng {
  lat: number
  lng: number
}

export type Coordinates = [number, number]

export interface VehicleMarker {
  position: Coordinates
  vehicleRef: string
  lineNumber: string
  expectedArrival: string
  distanceFromStop: number
}

export interface StopInfo {
  stopId: string
  stopName: string
  lat: number
  lon: number
}

export interface SiriData {
  Siri: {
    ServiceDelivery: {
      StopMonitoringDelivery: Array<{
        MonitoredStopVisit: MonitoredStopVisit[]
      }>
    }
  }
}

export interface MonitoredStopVisit {
  ItemIdentifier: string
  MonitoredVehicleJourney: MonitoredVehicleJourney
}

export interface MonitoredVehicleJourney {
  PublishedLineName: string
  DirectionRef: string
  DestinationRef: string
  VehicleRef: string
  VehicleLocation?: { Latitude: number; Longitude: number }
  MonitoredCall?: {
    ExpectedArrivalTime: string
    DistanceFromStop: number
  }
}

export interface RouteShapeData {
  [direction: string]: Coordinates[]
}
