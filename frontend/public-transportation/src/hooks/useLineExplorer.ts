import { useState, useCallback, useEffect, useRef } from 'react'
import { fetchLineShape } from '../services/transport-api'
import { useSessionState } from './useSessionState'
import type { LineShapeData } from '../types'

// Bumped whenever the map should refit: on a fresh line load (direction null →
// fit everything visible) or on a per-direction "Show on map" click.
export interface LineFocusRequest {
  direction: string | null
  seq: number
}

export interface UseLineExplorerReturn {
  line: string
  data: LineShapeData | null
  loading: boolean
  error: string | null
  recentLines: string[]
  hiddenDirections: Record<string, boolean>
  focus: LineFocusRequest
  explore: (lineNumber: string) => void
  clear: () => void
  toggleDirection: (direction: string) => void
  focusDirection: (direction: string) => void
}

const MAX_RECENT_LINES = 8

export function useLineExplorer(): UseLineExplorerReturn {
  const [line, setLine] = useSessionState('exploredLine', '')
  const [recentLines, setRecentLines] = useSessionState<string[]>('recentLines', [])
  const [data, setData] = useState<LineShapeData | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hiddenDirections, setHiddenDirections] = useState<Record<string, boolean>>({})
  const [focus, setFocus] = useState<LineFocusRequest>({ direction: null, seq: 0 })

  // Only the most recently requested line may update state — a slow response
  // for line A must not clobber a faster one for line B searched afterwards.
  const seqRef = useRef(0)

  const explore = useCallback(async (lineNumber: string) => {
    const trimmed = lineNumber.trim()
    if (!trimmed) return
    const seq = ++seqRef.current
    setLoading(true)
    setError(null)
    try {
      const result = await fetchLineShape(trimmed)
      if (seq !== seqRef.current) return
      setData(result)
      setLine(trimmed)
      setHiddenDirections({})
      setRecentLines(prev => [trimmed, ...prev.filter(l => l !== trimmed)].slice(0, MAX_RECENT_LINES))
      setFocus(f => ({ direction: null, seq: f.seq + 1 }))
      setLoading(false)
    } catch (err) {
      if (seq !== seqRef.current) return
      setData(null)
      setError(err instanceof Error ? err.message : String(err))
      setLoading(false)
    }
  }, [setLine, setRecentLines])

  const clear = useCallback(() => {
    seqRef.current++
    setData(null)
    setLine('')
    setError(null)
    setLoading(false)
    setHiddenDirections({})
  }, [setLine])

  const toggleDirection = useCallback((direction: string) => {
    setHiddenDirections(prev => ({ ...prev, [direction]: !prev[direction] }))
  }, [])

  const focusDirection = useCallback((direction: string) => {
    setFocus(f => ({ direction, seq: f.seq + 1 }))
  }, [])

  // Restore the explored line after a reload/HMR — the line number survives in
  // sessionStorage but the shape data does not.
  useEffect(() => {
    if (line) explore(line)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return {
    line, data, loading, error, recentLines, hiddenDirections, focus,
    explore, clear, toggleDirection, focusDirection,
  }
}
