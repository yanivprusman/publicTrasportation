// Israeli GTFS encodes a route's endpoints in route_long_name as
// "Origin<->Destination-1#" (the trailing "-N#" is an internal alternative
// marker). Turn that into a human-readable "Origin → Destination".

export interface ParsedHeadsign {
  from: string
  to: string
}

const stripAlternativeSuffix = (name: string): string =>
  name.replace(/-\d+#?\s*$/, '').trim()

export function parseHeadsign(longName: string | undefined): ParsedHeadsign | null {
  if (!longName) return null
  const parts = longName.split('<->')
  if (parts.length !== 2) return null
  const from = parts[0].trim()
  const to = stripAlternativeSuffix(parts[1])
  if (!from || !to) return null
  return { from, to }
}

export function formatHeadsign(longName: string | undefined, direction: string): string {
  const parsed = parseHeadsign(longName)
  if (parsed) return `${parsed.from} → ${parsed.to}`
  // GTFS direction_id is 0-based; label 1-based for humans.
  const n = parseInt(direction, 10)
  return `Direction ${Number.isNaN(n) ? direction : n + 1}`
}
