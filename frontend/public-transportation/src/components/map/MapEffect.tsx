import { useEffect, useRef } from 'react'
import { useMap } from 'react-leaflet'
import L from 'leaflet'
import type { Coordinates } from '../../types'

interface MapEffectProps {
  routeShape: Coordinates[] | null
}

const MapEffect = ({ routeShape }: MapEffectProps) => {
  const map = useMap()
  const fittedRef = useRef(false)
  const routeIdRef = useRef<string | null>(null)
  const processingRef = useRef(false)

  const currentRouteId = routeShape ? JSON.stringify(routeShape.slice(0, 2)) : null

  useEffect(() => {
    if (processingRef.current ||
       (routeIdRef.current === currentRouteId && fittedRef.current)) {
      return
    }

    if (routeShape && routeShape.length > 0) {
      processingRef.current = true

      try {
        setTimeout(() => {
          try {
            const bounds = routeShape.reduce((b, point) => {
              b.extend(point)
              return b
            }, L.latLngBounds(routeShape[0], routeShape[0]))

            map.fitBounds(bounds, {
              padding: [50, 50],
              maxZoom: 15,
              animate: true
            })

            fittedRef.current = true
            routeIdRef.current = currentRouteId
          } catch (e) {
            console.error('Error fitting map to route:', e)
          } finally {
            processingRef.current = false
          }
        }, 100)
      } catch {
        processingRef.current = false
      }
    }
  }, [map, routeShape, currentRouteId])

  useEffect(() => {
    return () => {
      processingRef.current = false
    }
  }, [])

  return null
}

export default MapEffect
