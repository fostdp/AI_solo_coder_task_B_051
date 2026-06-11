import { create } from 'zustand'
import type { SaltData, EnvData, RealTimeData } from '@/types'

interface MonitorState {
  saltData: SaltData[]
  envData: EnvData[]
  realTimeData: RealTimeData[]
  isLoading: boolean
  error: string | null
  lastUpdate: string | null

  setSaltData: (data: SaltData[]) => void
  setEnvData: (data: EnvData[]) => void
  addRealTimeData: (data: RealTimeData) => void
  setRealTimeData: (data: RealTimeData[]) => void
  setLoading: (loading: boolean) => void
  setError: (error: string | null) => void
  clearData: () => void
  getLatestSaltData: () => SaltData | null
  getLatestEnvData: () => EnvData | null
}

const useMonitorStore = create<MonitorState>((set, get) => ({
  saltData: [],
  envData: [],
  realTimeData: [],
  isLoading: false,
  error: null,
  lastUpdate: null,

  setSaltData: (data: SaltData[]) => {
    set({
      saltData: data,
      lastUpdate: new Date().toISOString()
    })
  },

  setEnvData: (data: EnvData[]) => {
    set({
      envData: data,
      lastUpdate: new Date().toISOString()
    })
  },

  addRealTimeData: (data: RealTimeData) => {
    set((state) => ({
      realTimeData: [...state.realTimeData.slice(-99), data],
      lastUpdate: new Date().toISOString()
    }))
  },

  setRealTimeData: (data: RealTimeData[]) => {
    set({
      realTimeData: data,
      lastUpdate: new Date().toISOString()
    })
  },

  setLoading: (loading: boolean) => {
    set({ isLoading: loading })
  },

  setError: (error: string | null) => {
    set({ error })
  },

  clearData: () => {
    set({
      saltData: [],
      envData: [],
      realTimeData: [],
      error: null,
      lastUpdate: null
    })
  },

  getLatestSaltData: (): SaltData | null => {
    const { saltData } = get()
    return saltData.length > 0 ? saltData[saltData.length - 1] : null
  },

  getLatestEnvData: (): EnvData | null => {
    const { envData } = get()
    return envData.length > 0 ? envData[envData.length - 1] : null
  }
}))

export default useMonitorStore
