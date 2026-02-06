import { useEffect } from 'react'
import { MapContainer, TileLayer, Polyline, useMap, Marker, Popup } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Coordinates } from '../../types'
import styles from './RouteMapView.module.css'

interface FitToBoundsProps {
  points: Coordinates[]
}

const FitToBounds = ({ points }: FitToBoundsProps) => {
  const map = useMap()

  useEffect(() => {
    if (points && points.length > 0) {
      try {
        const bounds = points.reduce(
          (b, point) => b.extend([point[0], point[1]]),
          L.latLngBounds(points[0], points[0])
        )
        map.fitBounds(bounds, {
          padding: [50, 50],
          maxZoom: 15
        })
      } catch (e) {
        console.error('Error fitting to bounds:', e)
      }
    }
  }, [map, points])

  return null
}

interface RouteMapViewProps {
  routeShape: Coordinates[] | null
}

const RouteMapView = ({ routeShape }: RouteMapViewProps) => {
  const defaultCenter: Coordinates = [32.0, 35.0]

  return (
    <div className={styles.container}>
      <MapContainer
        center={defaultCenter}
        zoom={8}
        style={{ height: '100%', width: '100%' }}
        preferCanvas={true}
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />

        {routeShape && routeShape.length > 1 && (
          <>
            <Marker position={routeShape[0]}>
              <Popup>Start point</Popup>
            </Marker>
            <Marker position={routeShape[routeShape.length - 1]}>
              <Popup>End point</Popup>
            </Marker>
          </>
        )}

        {routeShape && routeShape.length > 0 && (
          <>
            <Polyline
              positions={routeShape}
              pathOptions={{
                color: 'blue',
                weight: 4,
                opacity: 0.7
              }}
            />
            <FitToBounds points={routeShape} />
          </>
        )}
      </MapContainer>
    </div>
  )
}

export default RouteMapView
