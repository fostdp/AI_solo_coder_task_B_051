import request from './request'
import type { ApiResponse, PageResult } from '@/types'

export interface EnvState {
  currentRh: number
  rhTrend: number
  currentHour: number
  dehumidifierStatus: boolean
  humidifierStatus: boolean
  recommendedAction: number
  recommendedActionName: string
  expectedReward: number
  targetRh?: number
  controlMode?: 'MANUAL' | 'AUTO_DQN' | 'SCHEDULE'
}

export interface ControlRecord {
  timestamp: string
  currentRh: number
  dehumidifierOn: boolean
  humidifierOn: boolean
  energyConsumption: number
  rewardScore: number
  action: number
}

export interface EnergyStats {
  todayEnergy: number
  weekEnergy: number
  monthEnergy: number
}

export interface ControlParams {
  chamberId: string
  dehumidifierOn?: boolean
  humidifierOn?: boolean
  targetRh?: number
  controlMode?: 'MANUAL' | 'AUTO_DQN' | 'SCHEDULE'
}

export interface HistoryParams {
  chamberId: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

export const microClimateApi = {
  fetchEnvState: (chamberId: string): Promise<ApiResponse<EnvState>> => {
    return request.get<ApiResponse<EnvState>>(`/microclimate/env/${chamberId}`)
  },

  executeControl: (params: ControlParams): Promise<ApiResponse<EnvState>> => {
    return request.post<ApiResponse<EnvState>>('/microclimate/control', params)
  },

  fetchControlHistory: (params: HistoryParams): Promise<ApiResponse<PageResult<ControlRecord>>> => {
    return request.get<ApiResponse<PageResult<ControlRecord>>>('/microclimate/history', { params })
  },

  fetchEnergyStats: (chamberId: string): Promise<ApiResponse<EnergyStats>> => {
    return request.get<ApiResponse<EnergyStats>>(`/microclimate/energy/${chamberId}`)
  },

  trainDqnModel: (chamberId: string, episodes: number): Promise<ApiResponse<{ success: boolean; episodes: number }>> => {
    return request.post<ApiResponse<{ success: boolean; episodes: number }>>('/microclimate/train', {
      chamberId,
      episodes
    })
  }
}

export default microClimateApi
