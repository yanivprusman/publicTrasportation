import { memo, useMemo } from 'react'
import { Marker, Popup } from 'react-leaflet'
import L from 'leaflet'
import { formatStopDistance } from '../../utils/distance'
import { formatTime } from '../../utils/time-format'
import { useI18n } from '../../i18n'
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
// 44px rather than the 34px this started at: the heading chevron sits INSIDE the disc,
// because the line badge is pinned to the top-right and an arrow orbiting outside would
// vanish behind it for a whole sector of the compass. 34px left no clear room between the
// glyph and the edge. (createBusIcon in MapMarkers.ts puts its arrow outside instead —
// its number is centred on the glyph, so there the middle is the occupied space.)
const busIcon = (line: string, bearingDegrees: number | null) => L.divIcon({
  className: '',
  html:
    '<div style="position:relative;width:44px;height:44px;">' +
    '<div class="pt-live-bus-pulse"></div>' +
    '<div style="position:absolute;inset:0;border-radius:50%;background:#2e7d32;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;font-size:16px;">\u{1F68C}</div>' +
    (bearingDegrees == null
      ? ''
      : '<svg style="position:absolute;inset:0;" viewBox="0 0 44 44" width="44" height="44">' +
        `<path d="M22 5 L25.5 10 L18.5 10 Z" fill="#fff" transform="rotate(${bearingDegrees} 22 22)"/>` +
        '</svg>') +
    `<div style="position:absolute;top:-9px;right:-11px;background:#fff;color:#1b5e20;border:2px solid #2e7d32;border-radius:9px;padding:0 5px;font:700 11px/16px sans-serif;white-space:nowrap;">${escapeHtml(line)}</div>` +
    '</div>',
  iconSize: [44, 44],
  iconAnchor: [22, 22],
})

/** The tracked vehicle of the selected itinerary's next bus leg, pulsing live. */
const LiveBusLayer = ({ bus }: { bus: LiveBusMarkerData | null }) => {
  const { t } = useI18n()
  const icon = useMemo(
    () => (bus ? busIcon(bus.lineNumber, bus.vehicle.bearingDegrees) : null),
    [bus?.lineNumber, bus?.vehicle.bearingDegrees] // eslint-disable-line react-hooks/exhaustive-deps
  )

  if (!bus || !icon) return null

  return (
    <Marker position={bus.vehicle.position} icon={icon} zIndexOffset={1500}>
      <Popup>
        <strong>{t('liveBus.marker', { line: bus.lineNumber })}</strong><br />
        {t('liveBus.markerFrom', { distance: formatStopDistance(bus.vehicle.metersFromStop) })} <span dir="auto">{bus.stopName}</span>
        {bus.expectedArrival && (
          <>
            <br />
            {t('popup.expectedArrival')} {formatTime(bus.expectedArrival)}
          </>
        )}
      </Popup>
    </Marker>
  )
}

// Memoized: react-leaflet calls popup.update() whenever the popup's children
// change identity, and an open popup that does not fit auto-pans the map on
// update. Re-rendering this layer for every unrelated map move turned that into
// a pan -> render -> pan loop.
export default memo(LiveBusLayer)
