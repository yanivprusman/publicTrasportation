import { useEffect, useRef, useState } from 'react'
import { fetchNearbyStops, fetchStationArrivals } from '../services/transport-api'
import type { Coordinates, Itinerary, MonitoredStopVisit, RouteLeg } from '../types'

export interface LiveBusVehicle {
  position: Coordinates
  distanceFromStopMeters: number
  vehicleRef: string
}

export type LiveBusPhase =
  /** No upcoming bus leg in the selected itinerary — nothing to track. */
  | 'idle'
  /** Resolving the boarding stop and waiting for the first SIRI response. */
  | 'locating'
  /** The bus is reporting a GPS position — it can be shown on the map. */
  | 'live'
  /** SIRI knows the bus is coming (has an ETA) but no GPS position yet. */
  | 'tracked'
  /** No vehicle of this line is approaching the stop within SIRI's window. */
  | 'no-vehicle'
  | 'error'

export interface LiveBusState {
  phase: LiveBusPhase
  lineNumber: string
  stopName: string
  vehicle: LiveBusVehicle | null
  /** ISO expected arrival at the boarding stop; null when SIRI omits it. */
  expectedArrival: string | null
  error: string | null
}

const IDLE_STATE: LiveBusState = {
  phase: 'idle',
  lineNumber: '',
  stopName: '',
  vehicle: null,
  expectedArrival: null,
  error: null,
}

const POLL_MS = 15000
// MOTIS boarding-stop coordinates come from the same GTFS as /api/stops, so
// the matching station is essentially at distance 0; the radius only absorbs
// rounding differences between the two datasets.
const STOP_MATCH_RADIUS_M = 150

function arrivalMs(visit: MonitoredStopVisit): number {
  const t = Date.parse(visit.MonitoredVehicleJourney?.MonitoredCall?.ExpectedArrivalTime ?? '')
  return Number.isNaN(t) ? Number.MAX_SAFE_INTEGER : t
}

/**
 * The leg worth tracking: the first bus leg not already ridden. SIRI stop
 * monitoring covers buses only, so rail/tram legs are never tracked.
 */
function findTrackedLeg(itinerary: Itinerary | null): RouteLeg | null {
  if (!itinerary) return null
  const now = Date.now()
  for (const leg of itinerary.legs) {
    if (leg.mode !== 'BUS' || !leg.routeShortName) continue
    const end = Date.parse(leg.endTime)
    if (Number.isFinite(end) && end < now) continue
    return leg
  }
  return null
}

/**
 * Tracks the actual vehicle serving the selected itinerary's next bus leg:
 * resolves the boarding stop to its station code, then polls the SIRI feed
 * for that stop and picks the soonest arrival of the leg's line.
 */
export function useLiveBus(itinerary: Itinerary | null, active: boolean): LiveBusState {
  const [state, setState] = useState<LiveBusState>(IDLE_STATE)
  // Guards against overlapping fetches resolving out of order (poll ticks +
  // itinerary switches): only fetches started for the current leg may update state.
  const seqRef = useRef(0)

  const leg = active ? findTrackedLeg(itinerary) : null
  // Restart tracking only when the tracked ride itself changes — paging can
  // replace the itinerary array identity without changing the selected trip.
  const legKey = leg ? `${leg.routeShortName}|${leg.from.lat},${leg.from.lon}|${leg.startTime}` : ''
  const legRef = useRef<RouteLeg | null>(null)
  legRef.current = leg

  useEffect(() => {
    const seq = ++seqRef.current
    const currentLeg = legRef.current
    if (!currentLeg) {
      setState(IDLE_STATE)
      return
    }

    const lineNumber = currentLeg.routeShortName!.trim()
    let timer: number | null = null

    setState({ phase: 'locating', lineNumber, stopName: currentLeg.from.name, vehicle: null, expectedArrival: null, error: null })

    const fail = (message: string, stopName: string) => {
      if (seq !== seqRef.current) return
      setState({ phase: 'error', lineNumber, stopName, vehicle: null, expectedArrival: null, error: message })
    }

    const poll = async (stopCode: string, stopName: string) => {
      try {
        const data = await fetchStationArrivals(stopCode)
        if (seq !== seqRef.current) return
        const visits = data?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit ?? []
        const matches = visits
          .filter(v => (v.MonitoredVehicleJourney?.PublishedLineName ?? '').trim() === lineNumber)
          .sort((a, b) => arrivalMs(a) - arrivalMs(b))
        const next = matches[0]
        if (!next) {
          setState({ phase: 'no-vehicle', lineNumber, stopName, vehicle: null, expectedArrival: null, error: null })
          return
        }
        const journey = next.MonitoredVehicleJourney
        const location = journey.VehicleLocation
        const expectedArrival = journey.MonitoredCall?.ExpectedArrivalTime ?? null
        if (location?.Latitude && location?.Longitude) {
          setState({
            phase: 'live',
            lineNumber,
            stopName,
            vehicle: {
              position: [location.Latitude, location.Longitude],
              distanceFromStopMeters: journey.MonitoredCall?.DistanceFromStop ?? 0,
              vehicleRef: journey.VehicleRef,
            },
            expectedArrival,
            error: null,
          })
        } else {
          setState({ phase: 'tracked', lineNumber, stopName, vehicle: null, expectedArrival, error: null })
        }
      } catch (err) {
        // Keep polling: a transient SIRI/proxy failure recovers on the next tick.
        fail(err instanceof Error ? err.message : String(err), stopName)
      }
    }

    const start = async () => {
      try {
        const stops = await fetchNearbyStops(currentLeg.from.lat, currentLeg.from.lon, STOP_MATCH_RADIUS_M)
        if (seq !== seqRef.current) return
        // /api/stops returns nearest-first.
        const stop = stops[0]
        if (!stop) {
          fail(`No station found matching the boarding stop "${currentLeg.from.name}"`, currentLeg.from.name)
          return
        }
        await poll(stop.stopCode, stop.stopName)
        if (seq !== seqRef.current) return
        timer = window.setInterval(() => poll(stop.stopCode, stop.stopName), POLL_MS)
      } catch (err) {
        fail(err instanceof Error ? err.message : String(err), currentLeg.from.name)
      }
    }
    start()

    return () => {
      seqRef.current++
      if (timer !== null) clearInterval(timer)
    }
  }, [legKey]) // eslint-disable-line react-hooks/exhaustive-deps

  return state
}
