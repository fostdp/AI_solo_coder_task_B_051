import request from './request'
import type { ApiResponse, PageResult } from '@/types'

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface DelaminationRiskData {
  delaminationProbability: number
  riskLevel: RiskLevel
  featureContributions: { name: string; value: number; featureKey: string }[]
  crystallizationPressure: number
  adhesionStrength: number
  pressureAdhesionRatio: number
  cycleCount7d: number
  avgDailyRhFluctuation: number
  temperatureVariation: number
  suggestion?: string
  assessmentTime?: string
}

export interface HistoryItem {
  date: string
  probability: number
  level: RiskLevel
}

export interface DelaminationRiskParams {
  crystallizationPressure?: number
  adhesionStrength?: number
  cycleCount7d?: number
  avgDailyRhFluctuation?: number
  temperatureVariation?: number
  chamberId?: string
  tombId?: string
}

export interface RiskStatistics {
  totalAssessments: number
  lowRiskCount: number
  mediumRiskCount: number
  highRiskCount: number
  criticalRiskCount: number
  averageProbability: number
  maxProbability: number
  minProbability: number
}

export const delaminationApi = {
  assessRisk: (params: DelaminationRiskParams): Promise<ApiResponse<DelaminationRiskData>> => {
    return request.post<ApiResponse<DelaminationRiskData>>('/delamination/assess', params)
  },

  fetchRiskList: (params: DelaminationRiskParams & {
    page?: number
    pageSize?: number
    startTime?: string
    endTime?: string
  }): Promise<ApiResponse<PageResult<DelaminationRiskData>>> => {
    return request.get<ApiResponse<PageResult<DelaminationRiskData>>>('/delamination/list', { params })
  },

  fetchLatestRisk: (tombId?: string, chamberId?: string): Promise<ApiResponse<DelaminationRiskData>> => {
    return request.get<ApiResponse<DelaminationRiskData>>('/delamination/latest', {
      params: { tombId, chamberId }
    })
  },

  fetchRiskStatistics: (params: {
    tombId?: string
    chamberId?: string
    startTime?: string
    endTime?: string
  }): Promise<ApiResponse<RiskStatistics>> => {
    return request.get<ApiResponse<RiskStatistics>>('/delamination/statistics', { params })
  },

  fetchHistoryTrend: (params: {
    tombId?: string
    chamberId?: string
    days?: number
  }): Promise<ApiResponse<HistoryItem[]>> => {
    return request.get<ApiResponse<HistoryItem[]>>('/delamination/trend', { params })
  }
}

export default delaminationApi
