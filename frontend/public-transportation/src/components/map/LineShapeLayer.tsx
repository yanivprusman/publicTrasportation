import { Fragment, useEffect, useMemo } from 'react'
import { Polyline, CircleMarker, Popup, useMap } from 'react-leaflet'
import L from 'leaflet'
import { simplifyShape } from '../../utils/ShapeSimplifier'
import { getDirectionColor } from '../../utils/mode-colors'
import { formatHeadsign } from '../../utils/line-name'
import { useI18n } from '../../i18n'
import type { LineShapeData, Coordinates } from '../../types'
import type { LineFocusRequest } from '../../hooks/useLineExplorer'

interface LineShapeLayerProps {
  line: string
  data: LineShapeData | null
  hiddenDirections: Record<string, boolean>
  focus: LineFocusRequest
}

const fitToPoints = (map: L.Map, points: Coordinates[]) => {
  if (points.length === 0) return
  const bounds = points.reduce(
    (b, p) => b.extend(p),
    L.latLngBounds(points[0], points[0])
  )
  map.fitBounds(bounds, { padding: [40, 40], maxZoom: 15, animate: true })
}

const LineShapeLayer = ({ line, data, hiddenDirections, focus }: LineShapeLayerProps) => {
  const { t } = useI18n()
  const map = useMap()

  const directions = useMemo(() => {
    if (!data) return []
    return Object.entries(data.directions)
      .filter(([, points]) => Array.isArray(points) && points.length > 1)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([direction, points]) => ({
        direction,
        points,
        drawPoints: points.length > 300 ? simplifyShape(points, 0.0001) : points,
        color: getDirectionColor(direction),
        headsign: formatHeadsign(data.headsigns[direction], direction),
      }))
  }, [data])

  // Refit only when explicitly requested (new line loaded / "Show on map"),
  // not on every visibility toggle.
  useEffect(() => {
    if (focus.seq === 0 || directions.length === 0) return
    if (focus.direction !== null) {
      const target = directions.find(d => d.direction === focus.direction)
      if (target) fitToPoints(map, target.points)
      return
    }
    const visiblePoints = directions
      .filter(d => !hiddenDirections[d.direction])
      .flatMap(d => d.points)
    fitToPoints(map, visiblePoints)
  }, [focus.seq]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <>
      {directions.filter(d => !hiddenDirections[d.direction]).map(d => {
        const start = d.points[0]
        const end = d.points[d.points.length - 1]
        return (
          <Fragment key={`line-dir-${d.direction}`}>
            <Polyline
              positions={d.drawPoints}
              pathOptions={{ color: d.color, weight: 5, opacity: 0.8, lineCap: 'round' }}
            >
              <Popup>
                <strong>{t('lines.line', { n: line })}</strong><br />
                {d.headsign}
              </Popup>
            </Polyline>
            <CircleMarker
              center={start}
              radius={6}
              pathOptions={{ color: d.color, weight: 3, fillColor: '#ffffff', fillOpacity: 1 }}
            >
              <Popup><strong>{t('lines.line', { n: line })}</strong> {t('lines.popupDeparts', { h: d.headsign })}</Popup>
            </CircleMarker>
            <CircleMarker
              center={end}
              radius={6}
              pathOptions={{ color: '#ffffff', weight: 2, fillColor: d.color, fillOpacity: 1 }}
            >
              <Popup><strong>{t('lines.line', { n: line })}</strong> {t('lines.popupTerminus', { h: d.headsign })}</Popup>
            </CircleMarker>
          </Fragment>
        )
      })}
    </>
  )
}

export default LineShapeLayer
