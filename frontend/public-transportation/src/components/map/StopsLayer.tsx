import { useCallback, useEffect, useRef, useState } from 'react'
import { CircleMarker, Popup, useMap, useMapEvents } from 'react-leaflet'
import type L from 'leaflet'
import { fetchStopsInBounds, type StopResult } from '../../services/transport-api'
import { useI18n } from '../../i18n'

// Below this zoom the layer is empty — a city-wide viewport would drown the
// map in thousands of dots (and the API caps the response anyway).
export const MIN_STOPS_ZOOM = 15

// Fixed colors (not theme variables): canvas markers live outside the app's
// CSS-variable scope, and blue-on-white dots read well on the map tiles in
// both themes.
const BASE_STYLE: L.PathOptions = { color: '#1976d2', weight: 2, fillColor: '#ffffff', fillOpacity: 1 }
const HOVER_STYLE: L.PathOptions = { color: '#1976d2', weight: 2, fillColor: '#bbdefb', fillOpacity: 1 }
const ACTIVE_STYLE: L.PathOptions = { color: '#ffffff', weight: 2, fillColor: '#1976d2', fillOpacity: 1 }

const dotRadius = (zoom: number) => (zoom >= 17 ? 6 : zoom >= 16 ? 5 : 4)

interface StopsLayerProps {
  // Stop code currently open in the arrivals tab — drawn filled so the user
  // can see which dot they're watching.
  activeStopCode: string | null
  onStopSelect: (stop: StopResult) => void
}

// Every transit stop in the viewport as a tappable dot, visible once the map
// is zoomed to street level. Tapping a dot opens a popup with the stop's name
// and a jump to its live departure board.
const StopsLayer = ({ activeStopCode, onStopSelect }: StopsLayerProps) => {
  const { t } = useI18n()
  const map = useMap()
  const [stops, setStops] = useState<StopResult[]>([])
  const [zoom, setZoom] = useState(() => map.getZoom())

  // Only the most recently started fetch may update the list — a slow
  // response for an old viewport must not clobber a newer one.
  const seqRef = useRef(0)
  const timerRef = useRef<number | null>(null)

  const refresh = useCallback(() => {
    const currentZoom = map.getZoom()
    setZoom(currentZoom)
    if (currentZoom < MIN_STOPS_ZOOM) {
      seqRef.current++
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
      setStops([])
      return
    }
    // Debounce: consecutive moveend events during a zoom animation or a
    // multi-fling pan should produce one request, not one per event.
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    timerRef.current = window.setTimeout(async () => {
      const seq = ++seqRef.current
      // Pad the viewport so small pans land on already-loaded stops.
      const bounds = map.getBounds().pad(0.15)
      try {
        const result = await fetchStopsInBounds(
          bounds.getSouth(), bounds.getWest(), bounds.getNorth(), bounds.getEast(),
        )
        if (seq !== seqRef.current) return
        setStops(result)
      } catch (err) {
        if (seq !== seqRef.current) return
        setStops([])
        console.error('Failed to load stops in view:', err)
      }
    }, 250)
  }, [map])

  useMapEvents({ moveend: refresh })

  useEffect(() => {
    refresh()
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    }
  }, [refresh])

  if (zoom < MIN_STOPS_ZOOM) return null

  const radius = dotRadius(zoom)

  return (
    <>
      {stops.map(stop => {
        const isActive = stop.stopCode === activeStopCode
        const restingStyle = isActive ? ACTIVE_STYLE : BASE_STYLE
        return (
          <CircleMarker
            key={`stop-${stop.stopCode}`}
            center={[stop.lat, stop.lon]}
            radius={isActive ? radius + 2 : radius}
            pathOptions={restingStyle}
            eventHandlers={{
              mouseover: (e) => (e.target as L.CircleMarker).setStyle(HOVER_STYLE),
              mouseout: (e) => (e.target as L.CircleMarker).setStyle(restingStyle),
            }}
          >
            <Popup>
              <strong dir="auto">{stop.stopName}</strong><br />
              {t('map.stop', { code: stop.stopCode })}<br />
              <button
                type="button"
                style={{ marginTop: 6, padding: '4px 10px', border: 'none', borderRadius: 6, background: '#2196F3', color: '#fff', fontWeight: 600, cursor: 'pointer' }}
                onClick={() => {
                  map.closePopup()
                  onStopSelect(stop)
                }}
                data-id="map-stop-arrivals"
              >
                {t('map.liveArrivals')}
              </button>
            </Popup>
          </CircleMarker>
        )
      })}
    </>
  )
}

export default StopsLayer
