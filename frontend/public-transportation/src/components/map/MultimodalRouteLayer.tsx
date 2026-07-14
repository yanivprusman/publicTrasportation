import { useEffect } from 'react'
import { Polyline, CircleMarker, Tooltip, useMap } from 'react-leaflet'
import L from 'leaflet'
import type { Itinerary } from '../../types'
import { decodePolyline } from '../../utils/polyline-decoder'
import { getModeStyle } from '../../utils/mode-colors'

interface MultimodalRouteLayerProps {
  itinerary: Itinerary | null
}

export default function MultimodalRouteLayer({ itinerary }: MultimodalRouteLayerProps) {
  const map = useMap()

  useEffect(() => {
    if (!itinerary) return
    const allPoints: [number, number][] = []
    for (const leg of itinerary.legs) {
      if (leg.polyline) {
        allPoints.push(...decodePolyline(leg.polyline))
      }
    }
    if (allPoints.length > 0) {
      const bounds = L.latLngBounds(allPoints.map(p => L.latLng(p[0], p[1])))
      map.fitBounds(bounds, { padding: [40, 40] })
    }
  }, [itinerary, map])

  if (!itinerary) return null

  // A transfer marker belongs only where the rider changes vehicles, i.e. the
  // alighting stop of each transit leg except the last one. Marking every leg
  // boundary (the old behavior) drew dark "transfer" dots at the boarding and
  // alighting stops of ordinary walk<->transit trips, showing 2 transfers for a
  // walk/bus/walk itinerary whose itinerary.transfers is 0.
  const transitLegs = itinerary.legs.filter(leg => leg.mode !== 'WALK')
  const transferPoints: [number, number][] = transitLegs
    .slice(0, -1)
    .map(leg => [leg.to.lat, leg.to.lon])

  return (
    <>
      {itinerary.legs.map((leg, i) => {
        if (!leg.polyline) return null
        const positions = decodePolyline(leg.polyline)
        const style = getModeStyle(leg.mode, leg.routeColor)
        return (
          <Polyline
            key={i}
            positions={positions}
            pathOptions={{
              color: style.color,
              weight: 5,
              opacity: 0.85,
              dashArray: style.dashArray,
            }}
          />
        )
      })}
      {itinerary.legs.map((leg, i) => {
        if (leg.mode === 'WALK') return null
        const style = getModeStyle(leg.mode, leg.routeColor)
        const stops: { lat: number; lon: number; name: string }[] = []
        if (leg.from?.name) stops.push(leg.from)
        if (leg.intermediateStops) stops.push(...leg.intermediateStops)
        if (leg.to?.name) stops.push(leg.to)
        return stops.map((stop, j) => (
          <CircleMarker
            key={`stop-${i}-${j}`}
            center={[stop.lat, stop.lon]}
            radius={4}
            pathOptions={{
              color: style.color,
              weight: 2,
              fillColor: '#fff',
              fillOpacity: 1,
            }}
          >
            <Tooltip direction="top" offset={[0, -6]}>
              {stop.name}
            </Tooltip>
          </CircleMarker>
        ))
      })}
      {transferPoints.map((pos, i) => (
        <CircleMarker
          key={`transfer-${i}`}
          center={pos}
          radius={6}
          pathOptions={{
            color: '#fff',
            weight: 2,
            fillColor: '#333',
            fillOpacity: 1,
          }}
        />
      ))}
    </>
  )
}
