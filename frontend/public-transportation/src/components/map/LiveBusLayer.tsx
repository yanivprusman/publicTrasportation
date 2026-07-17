import { useMemo } from 'react'
import { Marker, Popup } from 'react-leaflet'
import L from 'leaflet'
import { formatStopDistance } from '../../utils/distance'
import { formatTime } from '../../utils/time-format'
import type { LiveBusVehicle } from '../../hooks/useLiveBus'

export interface LiveBusMarkerData {
  vehicle: LiveBusVehicle
  lineNumber: string
  stopName: string
  expectedArrival: string | null
}

// divIcon HTML lives outside React: escape the GTFS-sourced line number.
function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

// Fixed colors (not theme variables): divIcon HTML renders outside the app's
// CSS-variable scope, and green-on-white reads well on map tiles in both themes.
const busIcon = (line: string) => L.divIcon({
  className: '',
  html:
    '<div style="position:relative;width:34px;height:34px;">' +
    '<div class="pt-live-bus-pulse"></div>' +
    '<div style="position:absolute;inset:0;border-radius:50%;background:#2e7d32;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;font-size:16px;">\u{1F68C}</div>' +
    `<div style="position:absolute;top:-9px;right:-11px;background:#fff;color:#1b5e20;border:2px solid #2e7d32;border-radius:9px;padding:0 5px;font:700 11px/16px sans-serif;white-space:nowrap;">${escapeHtml(line)}</div>` +
    '</div>',
  iconSize: [34, 34],
  iconAnchor: [17, 17],
})

/** The tracked vehicle of the selected itinerary's next bus leg, pulsing live. */
const LiveBusLayer = ({ bus }: { bus: LiveBusMarkerData | null }) => {
  const icon = useMemo(() => (bus ? busIcon(bus.lineNumber) : null), [bus?.lineNumber]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!bus || !icon) return null

  return (
    <Marker position={bus.vehicle.position} icon={icon} zIndexOffset={1500}>
      <Popup>
        <strong>Bus {bus.lineNumber} — your ride</strong><br />
        {formatStopDistance(bus.vehicle.distanceFromStopMeters)} from <span dir="auto">{bus.stopName}</span>
        {bus.expectedArrival && (
          <>
            <br />
            Expected arrival: {formatTime(bus.expectedArrival)}
          </>
        )}
      </Popup>
    </Marker>
  )
}

export default LiveBusLayer
