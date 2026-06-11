import { create } from 'zustand'
import type { Alarm, AlarmStats, AlarmStatus, AlarmLevel } from '@/types'

interface AlarmState {
  alarms: Alarm[]
  stats: AlarmStats | null
  isLoading: boolean
  error: string | null
  selectedAlarm: Alarm | null

  setAlarms: (alarms: Alarm[]) => void
  addAlarm: (alarm: Alarm) => void
  updateAlarm: (alarmId: string, updates: Partial<Alarm>) => void
  setStats: (stats: AlarmStats) => void
  setLoading: (loading: boolean) => void
  setError: (error: string | null) => void
  setSelectedAlarm: (alarm: Alarm | null) => void
  getAlarmsByStatus: (status: AlarmStatus) => Alarm[]
  getAlarmsByLevel: (level: AlarmLevel) => Alarm[]
  getPendingCount: () => number
  clearAlarms: () => void
}

const initialStats: AlarmStats = {
  total: 0,
  pending: 0,
  acknowledged: 0,
  resolved: 0,
  critical: 0,
  error: 0,
  warning: 0,
  info: 0
}

const useAlarmStore = create<AlarmState>((set, get) => ({
  alarms: [],
  stats: null,
  isLoading: false,
  error: null,
  selectedAlarm: null,

  setAlarms: (alarms: Alarm[]) => {
    set({ alarms })
  },

  addAlarm: (alarm: Alarm) => {
    set((state) => ({
      alarms: [alarm, ...state.alarms]
    }))
  },

  updateAlarm: (alarmId: string, updates: Partial<Alarm>) => {
    set((state) => ({
      alarms: state.alarms.map((alarm) =>
        alarm.id === alarmId ? { ...alarm, ...updates } : alarm
      )
    }))
  },

  setStats: (stats: AlarmStats) => {
    set({ stats })
  },

  setLoading: (loading: boolean) => {
    set({ isLoading: loading })
  },

  setError: (error: string | null) => {
    set({ error })
  },

  setSelectedAlarm: (alarm: Alarm | null) => {
    set({ selectedAlarm: alarm })
  },

  getAlarmsByStatus: (status: AlarmStatus): Alarm[] => {
    return get().alarms.filter((alarm) => alarm.status === status)
  },

  getAlarmsByLevel: (level: AlarmLevel): Alarm[] => {
    return get().alarms.filter((alarm) => alarm.level === level)
  },

  getPendingCount: (): number => {
    return get().stats?.pending ?? get().alarms.filter((a) => a.status === 'PENDING').length
  },

  clearAlarms: () => {
    set({
      alarms: [],
      stats: initialStats,
      selectedAlarm: null,
      error: null
    })
  }
}))

export default useAlarmStore
