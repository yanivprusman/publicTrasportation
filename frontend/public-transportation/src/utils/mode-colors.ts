import type { TransitMode } from '../types'

interface ModeStyle {
  color: string
  dashArray?: string
  icon: string
}

const MODE_STYLES: Record<TransitMode, ModeStyle> = {
  WALK: { color: '#888888', dashArray: '6 8', icon: 'walk' },
  BUS: { color: '#4CAF50', icon: 'bus' },
  RAIL: { color: '#2196F3', icon: 'train' },
  TRAM: { color: '#FF5722', icon: 'tram' },
  SUBWAY: { color: '#9C27B0', icon: 'subway' },
}

export function getModeStyle(mode: TransitMode, routeColor?: string): ModeStyle {
  const base = MODE_STYLES[mode] || MODE_STYLES.WALK
  if (routeColor && mode !== 'WALK') {
    return { ...base, color: routeColor.startsWith('#') ? routeColor : `#${routeColor}` }
  }
  return base
}

export function getModeLabel(mode: TransitMode): string {
  switch (mode) {
    case 'WALK': return 'Walk'
    case 'BUS': return 'Bus'
    case 'RAIL': return 'Train'
    case 'TRAM': return 'Tram'
    case 'SUBWAY': return 'Subway'
    default: return mode
  }
}
