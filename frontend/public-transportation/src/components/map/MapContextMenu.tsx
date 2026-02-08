import { useState } from 'react'
import { useMapEvents } from 'react-leaflet'
import type { Coordinates } from '../../types'
import styles from './MapContextMenu.module.css'

interface MapContextMenuProps {
  onSetStart: (coords: Coordinates) => void
  onSetDestination: (coords: Coordinates) => void
}

interface ContextMenuState {
  lat: number
  lng: number
  x: number
  y: number
}

export default function MapContextMenu({ onSetStart, onSetDestination }: MapContextMenuProps) {
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

  const handleSetStart = (e: React.MouseEvent) => {
    e.stopPropagation()
    onSetStart([contextMenu.lat, contextMenu.lng])
    setContextMenu(null)
  }

  const handleSetDestination = (e: React.MouseEvent) => {
    e.stopPropagation()
    onSetDestination([contextMenu.lat, contextMenu.lng])
    setContextMenu(null)
  }

  return (
    <div className={styles.menu} style={{ top: contextMenu.y, left: contextMenu.x }}>
      <div onMouseDown={handleSetStart} className={styles.item}>
        Set as Start
      </div>
      <div onMouseDown={handleSetDestination} className={styles.item}>
        Set as Destination
      </div>
      <div onMouseDown={() => setContextMenu(null)} className={styles.cancel}>
        Cancel
      </div>
    </div>
  )
}
