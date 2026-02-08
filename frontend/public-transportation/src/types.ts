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

export type TransitMode = 'WALK' | 'BUS' | 'RAIL' | 'TRAM' | 'SUBWAY'

export interface Place {
  name: string
  lat: number
  lon: number
}

export interface RouteLeg {
  mode: TransitMode
  from: Place
  to: Place
  startTime: string
  endTime: string
  duration: number
  routeShortName?: string
  routeColor?: string
  agencyName?: string
  polyline: string
  intermediateStops?: Place[]
}

export interface Itinerary {
  duration: number
  startTime: string
  endTime: string
  transfers: number
  legs: RouteLeg[]
}

export interface RouteResult {
  itineraries: Itinerary[]
}

export interface GeocodeSuggestion {
  name: string
  lat: number
  lon: number
  type?: string
}
