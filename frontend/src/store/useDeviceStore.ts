import { create } from 'zustand'
import type { Device } from '@/types'

interface DeviceState {
  devices: Device[]
  isLoading: boolean
  error: string | null
  selectedDevice: Device | null

  setDevices: (devices: Device[]) => void
  addDevice: (device: Device) => void
  updateDevice: (deviceId: string, updates: Partial<Device>) => void
  removeDevice: (deviceId: string) => void
  setLoading: (loading: boolean) => void
  setError: (error: string | null) => void
  setSelectedDevice: (device: Device | null) => void
  getDevicesByStatus: (status: Device['status']) => Device[]
  getDevicesByChamber: (chamberId: string) => Device[]
  getOnlineCount: () => number
  getOfflineCount: () => number
  clearDevices: () => void
}

const useDeviceStore = create<DeviceState>((set, get) => ({
  devices: [],
  isLoading: false,
  error: null,
  selectedDevice: null,

  setDevices: (devices: Device[]) => {
    set({ devices })
  },

  addDevice: (device: Device) => {
    set((state) => ({
      devices: [...state.devices, device]
    }))
  },

  updateDevice: (deviceId: string, updates: Partial<Device>) => {
    set((state) => ({
      devices: state.devices.map((device) =>
        device.id === deviceId ? { ...device, ...updates } : device
      )
    }))
  },

  removeDevice: (deviceId: string) => {
    set((state) => ({
      devices: state.devices.filter((device) => device.id !== deviceId)
    }))
  },

  setLoading: (loading: boolean) => {
    set({ isLoading: loading })
  },

  setError: (error: string | null) => {
    set({ error })
  },

  setSelectedDevice: (device: Device | null) => {
    set({ selectedDevice: device })
  },

  getDevicesByStatus: (status: Device['status']): Device[] => {
    return get().devices.filter((device) => device.status === status)
  },

  getDevicesByChamber: (chamberId: string): Device[] => {
    return get().devices.filter((device) => device.chamberId === chamberId)
  },

  getOnlineCount: (): number => {
    return get().devices.filter((d) => d.status === 'ONLINE').length
  },

  getOfflineCount: (): number => {
    return get().devices.filter((d) => d.status === 'OFFLINE' || d.status === 'ERROR').length
  },

  clearDevices: () => {
    set({
      devices: [],
      selectedDevice: null,
      error: null
    })
  }
}))

export default useDeviceStore
