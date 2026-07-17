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
  _stopNames?: Record<string, string>
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

export interface LineShapeData {
  directions: Record<string, Coordinates[]>
  // GTFS route_long_name per direction, e.g. "Origin<->Destination-1#"
  headsigns: Record<string, string>
}

export type TransitMode = 'WALK' | 'BIKE' | 'CAR' | 'BUS' | 'RAIL' | 'TRAM' | 'SUBWAY'

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

// A direct street route (no transit) offered next to the transit results so
// the rider can compare ways to make the same trip.
export interface DirectAlternative {
  mode: 'BIKE' | 'CAR'
  /** Total street distance in meters (0 when the router didn't report it). */
  distance: number
  itinerary: Itinerary
}

export interface RouteResult {
  itineraries: Itinerary[]
  // Fastest bike and car routes for the same trip; only sent with the first
  // page (direct routes are time-independent, so paging never changes them).
  alternatives?: DirectAlternative[]
  // Opaque MOTIS paging cursors: present when trips before/after the shown
  // window exist. Passed back via searchRoute's pageCursor to load that page.
  previousPageCursor?: string
  nextPageCursor?: string
}

export interface GeocodeSuggestion {
  name: string
  lat: number
  lon: number
  type?: string
}
