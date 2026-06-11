export interface Vector3D {
  x: number;
  y: number;
  z: number;
}

export enum RiskLevel {
  NONE = 'NONE',
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL',
}

export interface AnalysisResult {
  timestamp: number;
  deviceId: string;
  location: Vector3D;
  migrationVelocity: Vector3D;
  crystallizationPressure: number;
  riskLevel: RiskLevel;
  predictionHours: number;
  predictedTotalSalt: number;
  predictedCrystallizationPressure: number;
}

export interface AnalysisRequest {
  chamberId: string;
  analysisType: 'salt_damage' | 'structural' | 'environment' | 'comprehensive';
  periodStart?: string;
  periodEnd?: string;
}

export type DamageLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface AmplitudeHistogramItem {
  range: [number, number];
  count: number;
}

export interface RhTimeSeriesItem {
  time: string;
  rh: number;
}

export interface CycleCountData {
  id?: string;
  tombId?: string;
  chamberId?: string;
  deviceId?: string;
  totalCycles: number;
  fullCycles: number;
  partialCycles: number;
  crossingCycles: number;
  averageRange: number;
  maxRange: number;
  minRange: number;
  totalDamage: number;
  damageLevel: DamageLevel;
  amplitudeHistogram: AmplitudeHistogramItem[] | number[][];
  rhTimeSeries?: RhTimeSeriesItem[];
  periodStart?: string;
  periodEnd?: string;
  createdAt?: string;
}

export interface CycleCountRequest {
  chamberId?: string;
  deviceId?: string;
  periodStart?: string;
  periodEnd?: string;
  rhThreshold?: number;
}
