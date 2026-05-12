import axios from 'axios'
import type { Coordinates } from '../../types'

export const buildAddressLabel = (data: { display_name?: string; address?: Record<string, string> }): string => {
  const addr = data.address
  if (addr) {
    const road = addr.road || addr.pedestrian || addr.neighbourhood || ''
    const houseNumber = addr.house_number || ''
    const settlement = addr.village || addr.hamlet || addr.town || addr.city || ''
    const street = houseNumber ? `${road} ${houseNumber}`.trim() : road
    if (street && settlement) return `${street}, ${settlement}`
    if (settlement) return settlement
    if (street) return street
  }
  if (data.display_name) {
    return data.display_name.split(',').slice(0, 3).join(',').trim()
  }
  return 'Address not found'
}

export const fetchAddress = async (lat: number, lon: number, setAddress: (addr: string) => void) => {
  try {
    const response = await axios.get(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=he&addressdetails=1&countrycodes=il`
    )
    setAddress(buildAddressLabel(response.data))
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
