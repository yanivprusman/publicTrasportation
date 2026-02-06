import { useMemo } from 'react'
import { Polyline } from 'react-leaflet'
import { simplifyShape } from '../../utils/ShapeSimplifier'
import type { Coordinates } from '../../types'

interface RouteLayerProps {
  routeShape: Coordinates[] | null
  route: Coordinates[] | null
}

const RouteLayer = ({ routeShape, route }: RouteLayerProps) => {
  const optimizedRouteShape = useMemo(() => {
    if (!routeShape || !Array.isArray(routeShape) || routeShape.length < 3) {
      return routeShape
    }

    if (routeShape.length > 300) {
      const factor = routeShape.length > 1000 ? 0.00015 : 0.0001
      return simplifyShape(routeShape, factor)
    }

    return routeShape
  }, [routeShape])

  return (
    <>
      {route && route.length > 0 && (
        <Polyline
          positions={route}
          pathOptions={{
            color: 'blue',
            weight: 4,
            opacity: 0.8
          }}
        />
      )}

      {optimizedRouteShape && optimizedRouteShape.length > 0 && (
        <Polyline
          positions={optimizedRouteShape}
          pathOptions={{
            color: 'red',
            weight: 3,
            opacity: 0.7,
            dashArray: '5, 5',
            lineCap: 'round'
          }}
        />
      )}
    </>
  )
}

export default RouteLayer
