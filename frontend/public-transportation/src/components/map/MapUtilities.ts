import axios from 'axios'
import { reverseGeocode } from '../../services/routing-api'
import type { Coordinates } from '../../types'

export const fetchAddress = async (lat: number, lon: number, setAddress: (addr: string) => void) => {
  try {
    setAddress(await reverseGeocode(lat, lon))
  } catch {
    setAddress('Error fetching address')
  }
}

export const findRoute = async (position: Coordinates, destination: Coordinates): Promise<Coordinates[]> => {
  try {
    if (!position || !position[0] || !position[1] ||
      !destination || !destination[0] || !destination[1]) {
      return [position, destination]
    }

    const response = await axios.get('/api/directions', {
      params: {
        start: `${position[1]},${position[0]}`,
        end: `${destination[1]},${destination[0]}`,
      },
    })

    if (response.data?.features?.[0]?.geometry?.coordinates) {
      const routeGeometry = response.data.features[0].geometry.coordinates
      const formattedRoute: Coordinates[] = routeGeometry.map(([lon, lat]: [number, number]) => [lat, lon])
      return formattedRoute
    }

    return [
      [position[0], position[1]],
      [destination[0], destination[1]]
    ]
  } catch {
    return [
      [position[0], position[1]],
      [destination[0], destination[1]]
    ]
  }
}
