import { useState } from 'react'
import axios from 'axios'
import { fetchAddress, findRoute } from '../components/map/MapUtilities'
import type { Coordinates } from '../types'

const useMapHandlers = (
  position: Coordinates,
  setPosition: (pos: Coordinates) => void,
  destination: Coordinates | null,
  onDestinationSet: (dest: Coordinates) => void,
  setMapCenter: (center: Coordinates) => void,
  mapCenter: Coordinates
) => {
  const [searchQuery, setSearchQuery] = useState('')
  const [searchError, setSearchError] = useState<string | null>(null)
  const [positionAddress, setPositionAddress] = useState('')
  const [destinationAddress, setDestinationAddress] = useState('')

  const handleSearch = async () => {
    if (!searchQuery.trim()) return

    try {
      setSearchError(null)
      const response = await axios.get(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(searchQuery)}&format=json`
      )
      if (response.data.length > 0) {
        const { lat, lon } = response.data[0]
        setMapCenter([parseFloat(lat), parseFloat(lon)])
      } else {
        setSearchError('mapCtl.notFound')
      }
    } catch {
      setSearchError('mapCtl.searchFailed')
    }
  }

  const handleSetStartPoint = (coords?: Coordinates | React.MouseEvent) => {
    const targetPoint = (Array.isArray(coords) && coords.length === 2) ? coords as Coordinates : mapCenter

    if (targetPoint && Array.isArray(targetPoint) && targetPoint.length === 2) {
      setPosition([targetPoint[0], targetPoint[1]])
      fetchAddress(targetPoint[0], targetPoint[1], setPositionAddress)
    }
  }

  const handleSetDestinationPoint = (coords?: Coordinates | React.MouseEvent) => {
    const targetPoint = (Array.isArray(coords) && coords.length === 2) ? coords as Coordinates : mapCenter

    if (targetPoint && Array.isArray(targetPoint) && targetPoint.length === 2) {
      onDestinationSet(targetPoint)
      fetchAddress(targetPoint[0], targetPoint[1], setDestinationAddress)
    }
  }

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch()
    }
  }

  const handleFindRoute = async (): Promise<Coordinates[] | null> => {
    if (!destination || !destination[0] || !destination[1]) {
      setSearchError('mapCtl.needDest')
      return null
    }

    try {
      setSearchError(null)
      const routeData = await findRoute(position, destination)

      if (routeData) {
        return routeData
      } else {
        setSearchError('mapCtl.noRoute')
        return null
      }
    } catch {
      setSearchError('mapCtl.routeFailed')
      return null
    }
  }

  return {
    searchQuery,
    setSearchQuery,
    searchError,
    setSearchError,
    positionAddress,
    destinationAddress,
    setPositionAddress,
    setDestinationAddress,
    handleSearch,
    handleSetStartPoint,
    handleSetDestinationPoint,
    handleKeyPress,
    handleFindRoute
  }
}

export default useMapHandlers
