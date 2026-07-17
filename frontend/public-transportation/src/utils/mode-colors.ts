import type { TransitMode } from '../types'
import { getLanguage, translate } from '../i18n'
import { isTranslationKey } from '../i18n/translations'

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

// Line Explorer direction colors — deliberately distinct from the itinerary
// leg palette above (walk grey, bus green, rail blue, tram orange).
const DIRECTION_COLORS: Record<string, string> = {
  '0': '#8e24aa',
  '1': '#e91e63',
}

export function getDirectionColor(direction: string): string {
  return DIRECTION_COLORS[direction] ?? '#00897b'
}

export function getModeLabel(mode: TransitMode): string {
  const key = `modes.${mode}`
  return isTranslationKey(key) ? translate(getLanguage(), key) : mode
}
