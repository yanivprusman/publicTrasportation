import { useState, useRef, useCallback } from 'react'

interface UseAutocompleteOptions<T> {
  searchFn: (query: string) => Promise<T[]>
  debounceMs?: number
  maxResults?: number
}

export interface UseAutocompleteReturn<T> {
  text: string
  setText: (text: string) => void
  suggestions: T[]
  showDropdown: boolean
  highlightIndex: number
  inputRef: React.RefObject<HTMLInputElement | null>
  handleInput: (e: React.ChangeEvent<HTMLInputElement>) => void
  handleSelect: (item: T) => void
  handleClear: () => void
  handleFocus: () => void
  handleBlur: () => void
  handleKeyDown: (e: React.KeyboardEvent<HTMLInputElement>, onSelect: (item: T) => void) => void
  forceSearch: (query: string) => Promise<T[]>
}

export function useAutocomplete<T>({ searchFn, debounceMs = 300, maxResults }: UseAutocompleteOptions<T>): UseAutocompleteReturn<T> {
  const [text, setText] = useState('')
  const [suggestions, setSuggestions] = useState<T[]>([])
  const [showDropdown, setShowDropdown] = useState(false)
  const [highlightIndex, setHighlightIndex] = useState(-1)
  const timerRef = useRef<ReturnType<typeof setTimeout>>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  // Guards against out-of-order responses: only the most recently issued
  // search (or clear/select, which bump the sequence) may update suggestions.
  const searchSeqRef = useRef(0)

  const cancelPendingSearch = useCallback(() => {
    searchSeqRef.current++
    if (timerRef.current) clearTimeout(timerRef.current)
  }, [])

  const doSearch = useCallback((query: string) => {
    cancelPendingSearch()
    if (!query.trim()) {
      setSuggestions([])
      setShowDropdown(false)
      return
    }
    const seq = searchSeqRef.current
    timerRef.current = setTimeout(async () => {
      const results = await searchFn(query)
      if (seq !== searchSeqRef.current) return
      const limited = maxResults ? results.slice(0, maxResults) : results
      setSuggestions(limited)
      setShowDropdown(limited.length > 0)
      setHighlightIndex(-1)
    }, debounceMs)
  }, [cancelPendingSearch, searchFn, debounceMs, maxResults])

  const handleInput = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    setText(val)
    doSearch(val)
  }, [doSearch])

  const handleSelect = useCallback((_item: T) => {
    cancelPendingSearch()
    setSuggestions([])
    setShowDropdown(false)
  }, [cancelPendingSearch])

  const handleClear = useCallback(() => {
    cancelPendingSearch()
    setText('')
    setSuggestions([])
    setShowDropdown(false)
    inputRef.current?.focus()
  }, [cancelPendingSearch])

  const handleFocus = useCallback(() => {
    if (suggestions.length > 0) setShowDropdown(true)
  }, [suggestions])

  const handleBlur = useCallback(() => {
    // Invalidate the pending debounce and any in-flight search: a response
    // landing after blur would reopen the dropdown over an unfocused input.
    cancelPendingSearch()
    setTimeout(() => setShowDropdown(false), 200)
  }, [cancelPendingSearch])

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>, onSelect: (item: T) => void) => {
    if (e.key === 'ArrowDown' && showDropdown && suggestions.length > 0) {
      e.preventDefault()
      setHighlightIndex(i => (i + 1) % suggestions.length)
      return
    }
    if (e.key === 'ArrowUp' && showDropdown && suggestions.length > 0) {
      e.preventDefault()
      // From the unselected state (-1), Up should jump to the last item. The
      // modulo form (i - 1 + n) % n maps -1 to n-2, skipping the last item, so
      // treat both -1 and 0 as "wrap to the end".
      setHighlightIndex(i => (i <= 0 ? suggestions.length - 1 : i - 1))
      return
    }
    if (e.key === 'Enter') {
      e.preventDefault()
      if (suggestions.length > 0) {
        onSelect(suggestions[highlightIndex >= 0 ? highlightIndex : 0])
      }
    }
  }, [showDropdown, suggestions, highlightIndex])

  const forceSearch = useCallback(async (query: string): Promise<T[]> => {
    cancelPendingSearch()
    const seq = searchSeqRef.current
    const results = await searchFn(query)
    // Discard responses superseded while in flight (further typing, blur,
    // clear, select, or another forceSearch): the caller acts on the result
    // (e.g. selects it), which must not happen for a stale query.
    if (seq !== searchSeqRef.current) return []
    return maxResults ? results.slice(0, maxResults) : results
  }, [cancelPendingSearch, searchFn, maxResults])

  return {
    text, setText, suggestions, showDropdown, highlightIndex, inputRef,
    handleInput, handleSelect, handleClear, handleFocus, handleBlur, handleKeyDown, forceSearch,
  }
}
