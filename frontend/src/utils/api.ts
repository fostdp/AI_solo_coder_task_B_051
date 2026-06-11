import request from './request'
import type {
  SaltData,
  EnvData,
  RealTimeData,
  Tomb,
  Chamber,
  Device,
  Alarm,
  AlarmStats,
  AlarmStatus,
  AnalysisResult,
  AnalysisRequest,
  CycleCountData,
  CycleCountRequest,
  ApiResponse,
  PageResult
} from '@/types'

interface SensorDataRequest {
  sensorType?: string
  deviceId?: string
  chamberId?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

export const sensorApi = {
  getSaltData: (params: SensorDataRequest): Promise<ApiResponse<PageResult<SaltData>>> => {
    return request.get<ApiResponse<PageResult<SaltData>>>('/sensor/salt', { params })
  },

  getEnvData: (params: SensorDataRequest): Promise<ApiResponse<PageResult<EnvData>>> => {
    return request.get<ApiResponse<PageResult<EnvData>>>('/sensor/env', { params })
  },

  getRealTimeData: (deviceId?: string): Promise<ApiResponse<RealTimeData[]>> => {
    return request.get<ApiResponse<RealTimeData[]>>('/sensor/realtime', {
      params: deviceId ? { deviceId } : undefined
    })
  },

  getLatestData: (deviceId: string): Promise<ApiResponse<SaltData | EnvData>> => {
    return request.get<ApiResponse<SaltData | EnvData>>(`/sensor/latest/${deviceId}`)
  },

  exportData: (params: SensorDataRequest): Promise<ApiResponse<{ url: string }>> => {
    return request.post<ApiResponse<{ url: string }>>('/sensor/export', params)
  }
}

export const monitorApi = {
  getOverview: (): Promise<ApiResponse<{
    onlineDevices: number
    totalDevices: number
    activeAlarms: number
    todayDataPoints: number
  }>> => {
    return request.get<ApiResponse<{
      onlineDevices: number
      totalDevices: number
      activeAlarms: number
      todayDataPoints: number
    }>>('/monitor/overview')
  },

  getChamberMonitor: (chamberId: string): Promise<ApiResponse<{
    chamber: Chamber
    latestData: (SaltData | EnvData)[]
    activeAlarms: Alarm[]
  }>> => {
    return request.get<ApiResponse<{
      chamber: Chamber
      latestData: (SaltData | EnvData)[]
      activeAlarms: Alarm[]
    }>>(`/monitor/chamber/${chamberId}`)
  },

  getTrendData: (chamberId: string, parameter: string, startTime: string, endTime: string): Promise<ApiResponse<{
    timestamps: string[]
    values: number[]
  }>> => {
    return request.get<ApiResponse<{
      timestamps: string[]
      values: number[]
    }>>('/monitor/trend', {
      params: { chamberId, parameter, startTime, endTime }
    })
  }
}

export const analysisApi = {
  createAnalysis: (data: AnalysisRequest): Promise<ApiResponse<{ analysisId: string }>> => {
    return request.post<ApiResponse<{ analysisId: string }>>('/analysis', data)
  },

  getAnalysis: (analysisId: string): Promise<ApiResponse<AnalysisResult>> => {
    return request.get<ApiResponse<AnalysisResult>>(`/analysis/${analysisId}`)
  },

  getAnalysisList: (chamberId?: string, page?: number, pageSize?: number): Promise<ApiResponse<PageResult<AnalysisResult>>> => {
    return request.get<ApiResponse<PageResult<AnalysisResult>>>('/analysis', {
      params: { chamberId, page, pageSize }
    })
  },

  getHeatmapData: (chamberId: string, parameter: string): Promise<ApiResponse<{
    positions: { x: number; y: number; z: number }[]
    values: number[]
  }>> => {
    return request.get<ApiResponse<{
      positions: { x: number; y: number; z: number }[]
      values: number[]
    }>>('/analysis/heatmap', {
      params: { chamberId, parameter }
    })
  },

  getPrediction: (chamberId: string, parameter: string, days: number): Promise<ApiResponse<{
    timestamps: string[]
    predictedValues: number[]
    lowerBound: number[]
    upperBound: number[]
  }>> => {
    return request.get<ApiResponse<{
      timestamps: string[]
      predictedValues: number[]
      lowerBound: number[]
      upperBound: number[]
    }>>('/analysis/prediction', {
      params: { chamberId, parameter, days }
    })
  },

  exportReport: (analysisId: string): Promise<ApiResponse<{ url: string }>> => {
    return request.get<ApiResponse<{ url: string }>>(`/analysis/${analysisId}/export`)
  }
}

export const alarmApi = {
  getAlarmList: (params?: {
    status?: AlarmStatus
    level?: string
    page?: number
    pageSize?: number
  }): Promise<ApiResponse<PageResult<Alarm>>> => {
    return request.get<ApiResponse<PageResult<Alarm>>>('/alarm', { params })
  },

  getAlarmStats: (): Promise<ApiResponse<AlarmStats>> => {
    return request.get<ApiResponse<AlarmStats>>('/alarm/stats')
  },

  getAlarmDetail: (alarmId: string): Promise<ApiResponse<Alarm>> => {
    return request.get<ApiResponse<Alarm>>(`/alarm/${alarmId}`)
  },

  acknowledgeAlarm: (alarmId: string, note?: string): Promise<ApiResponse<void>> => {
    return request.put<ApiResponse<void>>(`/alarm/${alarmId}/acknowledge`, { note })
  },

  resolveAlarm: (alarmId: string, resolutionNote: string): Promise<ApiResponse<void>> => {
    return request.put<ApiResponse<void>>(`/alarm/${alarmId}/resolve`, { resolutionNote })
  },

  dismissAlarm: (alarmId: string, reason: string): Promise<ApiResponse<void>> => {
    return request.put<ApiResponse<void>>(`/alarm/${alarmId}/dismiss`, { reason })
  }
}

export const deviceApi = {
  getDeviceList: (params?: {
    chamberId?: string
    status?: string
    page?: number
    pageSize?: number
  }): Promise<ApiResponse<PageResult<Device>>> => {
    return request.get<ApiResponse<PageResult<Device>>>('/device', { params })
  },

  getDeviceDetail: (deviceId: string): Promise<ApiResponse<Device>> => {
    return request.get<ApiResponse<Device>>(`/device/${deviceId}`)
  },

  createDevice: (data: Omit<Device, 'id'>): Promise<ApiResponse<Device>> => {
    return request.post<ApiResponse<Device>>('/device', data)
  },

  updateDevice: (deviceId: string, data: Partial<Device>): Promise<ApiResponse<Device>> => {
    return request.put<ApiResponse<Device>>(`/device/${deviceId}`, data)
  },

  deleteDevice: (deviceId: string): Promise<ApiResponse<void>> => {
    return request.delete<ApiResponse<void>>(`/device/${deviceId}`)
  },

  restartDevice: (deviceId: string): Promise<ApiResponse<void>> => {
    return request.post<ApiResponse<void>>(`/device/${deviceId}/restart`)
  },

  calibrateDevice: (deviceId: string): Promise<ApiResponse<void>> => {
    return request.post<ApiResponse<void>>(`/device/${deviceId}/calibrate`)
  }
}

export const tombApi = {
  getTombList: (): Promise<ApiResponse<Tomb[]>> => {
    return request.get<ApiResponse<Tomb[]>>('/tomb')
  },

  getTombDetail: (tombId: string): Promise<ApiResponse<Tomb>> => {
    return request.get<ApiResponse<Tomb>>(`/tomb/${tombId}`)
  },

  createTomb: (data: Omit<Tomb, 'id' | 'createdAt' | 'updatedAt' | 'chambers'>): Promise<ApiResponse<Tomb>> => {
    return request.post<ApiResponse<Tomb>>('/tomb', data)
  },

  updateTomb: (tombId: string, data: Partial<Tomb>): Promise<ApiResponse<Tomb>> => {
    return request.put<ApiResponse<Tomb>>(`/tomb/${tombId}`, data)
  },

  deleteTomb: (tombId: string): Promise<ApiResponse<void>> => {
    return request.delete<ApiResponse<void>>(`/tomb/${tombId}`)
  },

  getChamberList: (tombId: string): Promise<ApiResponse<Chamber[]>> => {
    return request.get<ApiResponse<Chamber[]>>(`/tomb/${tombId}/chambers`)
  },

  createChamber: (tombId: string, data: Omit<Chamber, 'id' | 'devices'>): Promise<ApiResponse<Chamber>> => {
    return request.post<ApiResponse<Chamber>>(`/tomb/${tombId}/chambers`, data)
  },

  updateChamber: (chamberId: string, data: Partial<Chamber>): Promise<ApiResponse<Chamber>> => {
    return request.put<ApiResponse<Chamber>>(`/tomb/chambers/${chamberId}`, data)
  },

  deleteChamber: (chamberId: string): Promise<ApiResponse<void>> => {
    return request.delete<ApiResponse<void>>(`/tomb/chambers/${chamberId}`)
  }
}

export const cycleCountApi = {
  getCycleCountList: (params: CycleCountRequest & { page?: number; pageSize?: number }): Promise<ApiResponse<PageResult<CycleCountData>>> => {
    return request.get<ApiResponse<PageResult<CycleCountData>>>('/cycle-count', { params })
  },

  runCycleCount: (params: CycleCountRequest): Promise<ApiResponse<CycleCountData>> => {
    return request.post<ApiResponse<CycleCountData>>('/cycle-count/run', params)
  },

  getLatestCycleCount: (tombId?: string, chamberId?: string): Promise<ApiResponse<CycleCountData>> => {
    return request.get<ApiResponse<CycleCountData>>('/cycle-count/latest', {
      params: { tombId, chamberId }
    })
  },

  getCycleCountDetail: (id: string): Promise<ApiResponse<CycleCountData>> => {
    return request.get<ApiResponse<CycleCountData>>(`/cycle-count/${id}`)
  },

  deleteCycleCount: (id: string): Promise<ApiResponse<void>> => {
    return request.delete<ApiResponse<void>>(`/cycle-count/${id}`)
  }
}
