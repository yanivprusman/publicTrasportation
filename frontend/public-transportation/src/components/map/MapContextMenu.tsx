import { useState } from 'react'
import { useMapEvents } from 'react-leaflet'
import styles from './MapContextMenu.module.css'

interface MapContextMenuProps {
  onRouteFrom: (lat: number, lon: number) => void
  onRouteTo: (lat: number, lon: number) => void
}

interface ContextMenuState {
  lat: number
  lng: number
  x: number
  y: number
}

export default function MapContextMenu({ onRouteFrom, onRouteTo }: MapContextMenuProps) {
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null)

  useMapEvents({
    contextmenu: (e) => {
      e.originalEvent.preventDefault()
      setContextMenu({
        lat: e.latlng.lat,
        lng: e.latlng.lng,
        x: e.originalEvent.clientX,
        y: e.originalEvent.clientY
      })
    },
    click: () => {
      if (contextMenu) setContextMenu(null)
    },
    dragstart: () => {
      if (contextMenu) setContextMenu(null)
    },
    zoomstart: () => {
      if (contextMenu) setContextMenu(null)
    }
  })

  if (!contextMenu) return null

  const handleRouteFrom = (e: React.MouseEvent) => {
    e.stopPropagation()
    onRouteFrom(contextMenu.lat, contextMenu.lng)
    setContextMenu(null)
  }

  const handleRouteTo = (e: React.MouseEvent) => {
    e.stopPropagation()
    onRouteTo(contextMenu.lat, contextMenu.lng)
    setContextMenu(null)
  }

  return (
    <div className={styles.menu} style={{ top: contextMenu.y, left: contextMenu.x }}>
      <div onMouseDown={handleRouteFrom} className={styles.item} data-id="route-from-here">
        Route from here
      </div>
      <div onMouseDown={handleRouteTo} className={styles.item} data-id="route-to-here">
        Route to here
      </div>
      <div onMouseDown={() => setContextMenu(null)} className={styles.cancel} data-id="close-context-menu">
        Cancel
      </div>
    </div>
  )
}
