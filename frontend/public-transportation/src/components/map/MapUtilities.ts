import axios from 'axios'
import type { Coordinates } from '../../types'

export const simplifyAddress = (address: string): string => {
  const parts = address.split(',').slice(0, 2)
  return parts.join(',').trim()
}

export const fetchAddress = async (lat: number, lon: number, setAddress: (addr: string) => void) => {
  try {
    const response = await axios.get(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=he&addressdetails=1&countrycodes=il`
    )
    const fullAddress: string = response.data.display_name || 'Address not found'
    setAddress(simplifyAddress(fullAddress))
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
