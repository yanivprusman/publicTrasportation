/**
 * SIRI, interpreted once, on the server both clients share.
 *
 * The web app and the mobile app each used to walk the raw SIRI tree themselves and each
 * decided independently what its fields meant. They reached the same wrong conclusion
 * about `DistanceFromStop` — that it is how far the bus is from the stop — and shipped it
 * in six places between them. It is not a distance to anything: sampled 80s apart it
 * INCREASES at road speed, so it is how far the vehicle has driven on this trip.
 *
 * A raw upstream feed handed to N clients gets N interpretations, and they only ever
 * agree by luck. Naming it here, once, is what stops the next client — iOS is not written
 * yet — from making the same guess.
 */

/** One vehicle, in this app's terms rather than the feed's. */
export interface NormalisedVehicle {
  vehicleRef: string
  /** What riders call the line, e.g. "64". SIRI's PublishedLineName. */
  lineNumber: string
  lat: number
  lon: number
  /** ISO-8601 with offset. */
  expectedArrival: string
  /**
   * When the operator last heard from the vehicle, NOT when we polled. A stalled feed and
   * a bus stuck in traffic look identical without it.
   */
  recordedAt: string | null
  /**
   * How far this vehicle has driven on its current trip, in metres — SIRI's
   * `DistanceFromStop`, under a name that says what it measures. It is NOT a distance to
   * the stop, to you, or to anything else on screen; a bus arriving in one minute can
   * report 221 km because it started 221 km ago. Anything phrased "X away" has to be
   * computed from two positions, not read from here.
   */
  tripTravelledMeters: number
  /** The monitored stop that reported this vehicle; lets a tapped marker be tracked. */
  stopCode: string | null
  /** SIRI LineRef = the GTFS route_id (LineRef 11057 = line 64). Identifies the route exactly. */
  lineRef: string | null
  destinationRef: string | null
  /**
   * The destination stop's name, resolved here from GTFS. Empty when unknown — never the
   * raw code, which reads as a destination called "15657" once a client puts it after an
   * arrow.
   */
  destinationName: string
}

/**
 * Extract every vehicle with a known position from a SIRI StopMonitoring response.
 *
 * Visits without a location or an expected arrival are dropped rather than defaulted:
 * a marker at 0,0 or a bus arriving at the epoch is worse than one that is absent.
 */
export function normaliseVehicles(
  data: unknown,
  stopNames: Record<string, string>
): NormalisedVehicle[] {
  /* eslint-disable @typescript-eslint/no-explicit-any */
  const deliveries = (data as any)?.Siri?.ServiceDelivery?.StopMonitoringDelivery
  if (!Array.isArray(deliveries)) return []

  const vehicles: NormalisedVehicle[] = []
  for (const delivery of deliveries) {
    const visits = delivery?.MonitoredStopVisit
    if (!Array.isArray(visits)) continue

    for (const visit of visits) {
      const journey = visit?.MonitoredVehicleJourney
      const location = journey?.VehicleLocation
      const call = journey?.MonitoredCall
      if (!journey || !location || !call) continue

      const lat = Number(location.Latitude)
      const lon = Number(location.Longitude)
      const expectedArrival = call.ExpectedArrivalTime
      if (!Number.isFinite(lat) || !Number.isFinite(lon) || !expectedArrival) continue

      const destinationRef = journey.DestinationRef != null ? String(journey.DestinationRef) : null

      vehicles.push({
        vehicleRef: journey.VehicleRef != null ? String(journey.VehicleRef) : '',
        lineNumber: journey.PublishedLineName ?? '',
        lat,
        lon,
        expectedArrival: String(expectedArrival),
        recordedAt: visit.RecordedAtTime ?? null,
        tripTravelledMeters: Number(call.DistanceFromStop) || 0,
        stopCode: visit.MonitoringRef != null ? String(visit.MonitoringRef) : null,
        lineRef: journey.LineRef != null ? String(journey.LineRef) : null,
        destinationRef,
        destinationName: (destinationRef && stopNames[destinationRef]) || '',
      })
    }
  }
  /* eslint-enable @typescript-eslint/no-explicit-any */
  return vehicles
}
