export enum AlarmLevel {
  INFO = 'INFO',
  WARNING = 'WARNING',
  ERROR = 'ERROR',
  CRITICAL = 'CRITICAL',
}

export enum AlarmStatus {
  PENDING = 'PENDING',
  PROCESSING = 'PROCESSING',
  ACKNOWLEDGED = 'ACKNOWLEDGED',
  RESOLVED = 'RESOLVED',
  DISMISSED = 'DISMISSED',
}

export interface Alarm {
  id: string;
  type: string;
  level: AlarmLevel;
  message: string;
  deviceId: string;
  tombId: string;
  chamberId: string;
  timestamp: number;
  status: AlarmStatus;
  value: number;
  threshold: number;
  title?: string;
  acknowledgedAt?: number;
  resolvedAt?: number;
  resolvedBy?: string;
  resolutionNote?: string;
}

export interface AlarmStats {
  total: number;
  pending: number;
  acknowledged: number;
  resolved: number;
  critical: number;
  error: number;
  warning: number;
  info: number;
}
