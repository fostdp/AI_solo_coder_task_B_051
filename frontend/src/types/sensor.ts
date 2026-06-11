export interface SaltData {
  timestamp: number;
  deviceId: string;
  tombId: string;
  chamberId: string;
  naPlus: number;
  ca2Plus: number;
  so42Minus: number;
  clMinus: number;
  totalSalt: number;
  position: {
    x: number;
    y: number;
    z: number;
  };
}

export interface EnvData {
  timestamp: number;
  deviceId: string;
  tombId: string;
  chamberId: string;
  temperature: number;
  humidity: number;
  windSpeed: number;
  position: {
    x: number;
    y: number;
    z: number;
  };
}

export enum SensorType {
  SALT = 'SALT',
  ENV = 'ENV',
}

export interface RealTimeData {
  deviceId: string;
  chamberId: string;
  type: SensorType;
  data: Partial<SaltData> | Partial<EnvData>;
  timestamp: number;
}
