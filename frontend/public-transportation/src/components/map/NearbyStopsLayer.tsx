import { useEffect, useMemo } from 'react'
import { Marker, Popup, useMap } from 'react-leaflet'
import L from 'leaflet'
import { formatStopDistance, walkMinutes } from '../../utils/distance'
import type { NearbyStop } from '../../services/transport-api'
import type { Coordinates } from '../../types'

interface NearbyStopsLayerProps {
  stops: NearbyStop[]
  userLocation: Coordinates | null
  focusSeq: number
  onStopSelect: (stop: NearbyStop) => void
}

// Fixed colors (not theme variables): divIcon HTML lives outside the app's
// CSS-variable scope, and the blue-on-white badge reads well on the map
// tiles in both themes.
const stopIcon = (index: number) => L.divIcon({
  className: '',
  html: `<div style="width:24px;height:24px;border-radius:50%;background:#2196F3;color:#fff;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,0.4);font:700 11px/20px sans-serif;text-align:center;">${index}</div>`,
  iconSize: [24, 24],
  iconAnchor: [12, 12],
})

const userIcon = L.divIcon({
  className: '',
  html: '<div style="width:18px;height:18px;border-radius:50%;background:#1565c0;border:3px solid #fff;box-shadow:0 0 0 2px rgba(21,101,192,0.35),0 1px 4px rgba(0,0,0,0.4);"></div>',
  iconSize: [18, 18],
  iconAnchor: [9, 9],
})

const NearbyStopsLayer = ({ stops, userLocation, focusSeq, onStopSelect }: NearbyStopsLayerProps) => {
  const map = useMap()

  // Refit whenever a fresh result set lands (locate / radius change), never on
  // ordinary re-renders or tab switches.
  useEffect(() => {
    if (focusSeq === 0 || !userLocation) return
    if (stops.length === 0) {
      map.setView(userLocation, 16, { animate: true })
      return
    }
    const bounds = stops.reduce(
      (b, s) => b.extend([s.lat, s.lon] as Coordinates),
      L.latLngBounds(userLocation, userLocation)
    )
    map.fitBounds(bounds, { padding: [40, 40], maxZoom: 17, animate: true })
  }, [focusSeq]) // eslint-disable-line react-hooks/exhaustive-deps

  const icons = useMemo(() => stops.map((_, i) => stopIcon(i + 1)), [stops])

  return (
    <>
      {userLocation && (
        <Marker position={userLocation} icon={userIcon} zIndexOffset={1000}>
          <Popup>You are here</Popup>
        </Marker>
      )}
      {stops.map((stop, i) => (
        <Marker key={`nearby-${stop.stopCode}`} position={[stop.lat, stop.lon]} icon={icons[i]}>
          <Popup>
            <strong dir="auto">{stop.stopName}</strong><br />
            Stop {stop.stopCode} · {formatStopDistance(stop.distanceMeters)} · ~{walkMinutes(stop.distanceMeters)} min walk<br />
            <button
              type="button"
              style={{ marginTop: 6, padding: '4px 10px', border: 'none', borderRadius: 6, background: '#2196F3', color: '#fff', fontWeight: 600, cursor: 'pointer' }}
              onClick={() => onStopSelect(stop)}
              data-id="nearby-marker-arrivals"
            >
              Live arrivals →
            </button>
          </Popup>
        </Marker>
      ))}
    </>
  )
}

export default NearbyStopsLayer
