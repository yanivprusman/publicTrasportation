import { useRef, useCallback, useEffect, useState } from 'react'
import styles from './BottomSheet.module.css'

export type SheetState = 'collapsed' | 'half' | 'expanded'

interface BottomSheetProps {
  state: SheetState
  onStateChange: (s: SheetState) => void
  children: React.ReactNode
}

const SNAP_HEIGHTS: Record<SheetState, number> = {
  collapsed: 60,
  half: 280,
  expanded: 80, // percent of viewport
}

export default function BottomSheet({ state, onStateChange, children }: BottomSheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null)
  const dragRef = useRef({ startY: 0, startHeight: 0, dragging: false })
  const [height, setHeight] = useState(SNAP_HEIGHTS[state])
  const [isDesktop, setIsDesktop] = useState(window.innerWidth > 768)

  useEffect(() => {
    const onResize = () => setIsDesktop(window.innerWidth > 768)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  useEffect(() => {
    if (state === 'expanded') {
      setHeight(window.innerHeight * 0.8)
    } else {
      setHeight(SNAP_HEIGHTS[state])
    }
  }, [state])

  const snapToNearest = useCallback((h: number) => {
    const vh = window.innerHeight
    const expandedPx = vh * 0.8
    const distances: [SheetState, number][] = [
      ['collapsed', Math.abs(h - SNAP_HEIGHTS.collapsed)],
      ['half', Math.abs(h - SNAP_HEIGHTS.half)],
      ['expanded', Math.abs(h - expandedPx)],
    ]
    distances.sort((a, b) => a[1] - b[1])
    onStateChange(distances[0][0])
  }, [onStateChange])

  const onDragStart = useCallback((clientY: number) => {
    dragRef.current = { startY: clientY, startHeight: height, dragging: true }
  }, [height])

  const onDragMove = useCallback((clientY: number) => {
    if (!dragRef.current.dragging) return
    const diff = dragRef.current.startY - clientY
    const newH = Math.max(SNAP_HEIGHTS.collapsed, Math.min(window.innerHeight * 0.9, dragRef.current.startHeight + diff))
    setHeight(newH)
  }, [])

  const onDragEnd = useCallback(() => {
    if (!dragRef.current.dragging) return
    dragRef.current.dragging = false
    snapToNearest(height)
  }, [height, snapToNearest])

  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    onDragStart(e.touches[0].clientY)
  }, [onDragStart])

  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    onDragMove(e.touches[0].clientY)
  }, [onDragMove])

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    onDragStart(e.clientY)
    const onMove = (ev: MouseEvent) => onDragMove(ev.clientY)
    const onUp = () => {
      onDragEnd()
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
    }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
  }, [onDragStart, onDragMove, onDragEnd])

  if (isDesktop) {
    return (
      <div className={styles.sidePanel}>
        {children}
      </div>
    )
  }

  return (
    <div
      ref={sheetRef}
      className={`${styles.sheet} ${styles[state]}`}
      style={{ height }}
    >
      <div
        className={styles.handle}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={onDragEnd}
        onMouseDown={handleMouseDown}
      >
        <div className={styles.handleBar} />
      </div>
      <div className={styles.content}>
        {children}
      </div>
    </div>
  )
}
