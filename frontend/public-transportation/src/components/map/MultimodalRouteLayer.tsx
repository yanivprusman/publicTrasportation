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

  const transferPoints: [number, number][] = []
  for (let i = 0; i < itinerary.legs.length - 1; i++) {
    const leg = itinerary.legs[i]
    transferPoints.push([leg.to.lat, leg.to.lon])
  }

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
      {itinerary.legs.map((leg, i) => {
        if (leg.mode === 'WALK') return null
        const style = getModeStyle(leg.mode, leg.routeColor)
        const stops: { lat: number; lon: number; name: string }[] = []
        if (leg.from?.name) stops.push(leg.from)
        if (leg.intermediateStops) stops.push(...leg.intermediateStops)
        if (leg.to?.name) stops.push(leg.to)
        return stops.map((stop, j) => {
          const isGetOff = j === stops.length - 1 && i < itinerary.legs.length - 1
          return (
            <CircleMarker
              key={`stop-${i}-${j}`}
              center={[stop.lat, stop.lon]}
              radius={isGetOff ? 8 : 4}
              pathOptions={isGetOff ? {
                color: '#fff',
                weight: 3,
                fillColor: style.color,
                fillOpacity: 1,
              } : {
                color: style.color,
                weight: 2,
                fillColor: '#fff',
                fillOpacity: 1,
              }}
            >
              <Tooltip direction="top" offset={[0, isGetOff ? -10 : -6]}>
                {stop.name}
              </Tooltip>
            </CircleMarker>
          )
        })
      })}
    </>
  )
}
