import type { NormalisedVehicle } from '../lib/siri-vehicles'

export interface LatLng {
  lat: number
  lng: number
}

export type Coordinates = [number, number]

/**
 * A vehicle as the server hands it over, plus the map position the UI draws with.
 *
 * Shape and field names come from lib/siri-vehicles.ts — the client no longer decides
 * what SIRI means. Note `tripTravelledMeters`: it is NOT a distance to the stop or to
 * you, and anything phrased "X away" must be computed from two positions.
 */
export interface VehicleMarker extends Omit<NormalisedVehicle, 'lat' | 'lon'> {
  position: Coordinates
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
  _vehicles?: NormalisedVehicle[]
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
  /** What the service is signed for — which of a line's two directions this is. */
  headsign?: string
  /** Street length in meters; the router sets it on walk/bike legs only. */
  distanceMeters?: number
  /** True when the times came from a realtime feed rather than the timetable. */
  realTime?: boolean
  scheduledStartTime?: string
  /** Stop code as printed on the pole — the number signs and other apps use. */
  fromStopCode?: string
  toStopCode?: string
  /** Single-ride price for this leg from the operators' fare table, in ILS. */
  fare?: number
}

export interface Itinerary {
  duration: number
  startTime: string
  endTime: string
  transfers: number
  legs: RouteLeg[]
  /**
   * The journey's price from the operators' own fare table, absent when any ride
   * on it has no rule there. A missing total is shown as no price at all — the
   * flat per-leg estimate this replaced quoted ~₪11 for a trip that costs ₪19.
   */
  fareTotal?: number
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

// One scheduled way to make the trip, as returned by the day-overview sweep:
// when it leaves, how long it takes, and which lines it rides.
export interface DayDeparture {
  startTime: string
  endTime: string
  /** Total trip time in seconds. */
  duration: number
  transfers: number
  lines: { mode: TransitMode; name: string }[]
}

export interface DayOverviewResult {
  /** Every scheduled departure in the requested window, sorted by start time. */
  departures: DayDeparture[]
  /** True when the sweep hit its request cap before reaching the window's end. */
  truncated: boolean
}

export interface GeocodeSuggestion {
  name: string
  lat: number
  lon: number
  type?: string
  /**
   * MOTIS place id (e.g. "israel_13684"), present on STOP results and passed
   * straight through by /api/geocode. Needed to look a stop's schedule up via
   * /api/stoptimes, which keys on MOTIS ids rather than GTFS stop codes.
   */
  id?: string
}
