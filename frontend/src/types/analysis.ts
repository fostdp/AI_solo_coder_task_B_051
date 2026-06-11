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
