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
  BIKE: { color: '#00ACC1', icon: 'bike' },
  CAR: { color: '#546E7A', icon: 'car' },
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

/**
 * The text colour to print ON a line colour — whichever of near-black and white
 * the eye can actually read there.
 *
 * Line badges were white on the mode colour unconditionally, which is 2.78:1 on
 * this suite's bus green and fails WCAG AA. Route colours also arrive from the
 * operator's own GTFS feed, so no fixed pair can be right for all of them; the
 * choice has to follow the background's luminance. Uses WCAG relative luminance,
 * not an RGB mean — mid greens and oranges are far brighter to the eye than their
 * mean suggests, which is exactly why they defeat white text.
 */
export function onColorFor(background: string): string {
  const hex = background.replace('#', '')
  if (hex.length !== 6) return '#ffffff'
  const channel = (v: number) => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  }
  const r = channel(parseInt(hex.slice(0, 2), 16))
  const g = channel(parseInt(hex.slice(2, 4), 16))
  const b = channel(parseInt(hex.slice(4, 6), 16))
  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
  return luminance > 0.35 ? '#10130f' : '#ffffff'
}
