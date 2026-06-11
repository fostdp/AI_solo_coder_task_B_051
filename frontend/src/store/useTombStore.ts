import { create } from 'zustand'
import type { Tomb, Chamber } from '@/types'

interface TombState {
  tombs: Tomb[]
  chambers: Chamber[]
  selectedTomb: Tomb | null
  selectedChamber: Chamber | null
  isLoading: boolean
  error: string | null

  setTombs: (tombs: Tomb[]) => void
  setChambers: (chambers: Chamber[]) => void
  setSelectedTomb: (tomb: Tomb | null) => void
  setSelectedChamber: (chamber: Chamber | null) => void
  setLoading: (loading: boolean) => void
  setError: (error: string | null) => void
  getChambersByTomb: (tombId: string) => Chamber[]
  clearState: () => void
}

const useTombStore = create<TombState>((set, get) => ({
  tombs: [],
  chambers: [],
  selectedTomb: null,
  selectedChamber: null,
  isLoading: false,
  error: null,

  setTombs: (tombs: Tomb[]) => {
    set({ tombs })
  },

  setChambers: (chambers: Chamber[]) => {
    set({ chambers })
  },

  setSelectedTomb: (tomb: Tomb | null) => {
    set({
      selectedTomb: tomb,
      selectedChamber: null
    })
  },

  setSelectedChamber: (chamber: Chamber | null) => {
    set({ selectedChamber: chamber })
  },

  setLoading: (loading: boolean) => {
    set({ isLoading: loading })
  },

  setError: (error: string | null) => {
    set({ error })
  },

  getChambersByTomb: (tombId: string): Chamber[] => {
    return get().chambers.filter((c) => c.tombId === tombId)
  },

  clearState: () => {
    set({
      tombs: [],
      chambers: [],
      selectedTomb: null,
      selectedChamber: null,
      error: null
    })
  }
}))

export default useTombStore
