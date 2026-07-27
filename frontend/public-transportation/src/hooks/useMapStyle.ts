import { useCallback, useEffect, useState } from 'react'

export type MapStyle = 'dark' | 'light' | 'satellite'

export const MAP_STYLES: MapStyle[] = ['dark', 'light', 'satellite']

const MAP_STYLE_STORAGE_KEY = 'pt-map-style'

/** OSM raster tiles, restyled to a dark basemap by a CSS filter in globals.css. */
export const OSM_TILES = {
  url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
  attribution:
    '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
}

/** Same imagery source the Android app uses for its satellite style. */
export const SATELLITE_TILES = {
  url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
  attribution: 'Tiles &copy; Esri, Maxar, Earthstar Geographics',
}

export function tilesFor(style: MapStyle) {
  return style === 'satellite' ? SATELLITE_TILES : OSM_TILES
}

function isMapStyle(value: unknown): value is MapStyle {
  return value === 'dark' || value === 'light' || value === 'satellite'
}

/**
 * The basemap style, independent of the app's light/dark theme — the same three
 * choices the Android app offers.
 *
 * Until the user picks one, the map follows the app theme, which is how the web
 * app behaved before this control existed. The first explicit choice is stored
 * and from then on the map keeps it regardless of the theme.
 */
export function useMapStyle(theme: 'light' | 'dark'): {
  mapStyle: MapStyle
  setMapStyle: (style: MapStyle) => void
} {
  const [stored, setStored] = useState<MapStyle | null>(null)

  // Read after mount so the server render and first client render agree.
  useEffect(() => {
    try {
      const raw = localStorage.getItem(MAP_STYLE_STORAGE_KEY)
      if (isMapStyle(raw)) setStored(raw)
    } catch {}
  }, [])

  const mapStyle: MapStyle = stored ?? (theme === 'dark' ? 'dark' : 'light')

  // Stamped on <html> so the tile filter is a plain CSS selector, matching how
  // data-theme already works.
  useEffect(() => {
    document.documentElement.dataset.mapStyle = mapStyle
  }, [mapStyle])

  const setMapStyle = useCallback((style: MapStyle) => {
    setStored(style)
    try {
      localStorage.setItem(MAP_STYLE_STORAGE_KEY, style)
    } catch {}
  }, [])

  return { mapStyle, setMapStyle }
}
