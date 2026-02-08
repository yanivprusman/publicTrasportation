import { useEffect } from 'react'
import { Polyline, CircleMarker, useMap } from 'react-leaflet'
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
    </>
  )
}
