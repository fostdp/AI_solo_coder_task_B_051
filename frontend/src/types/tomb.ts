export interface Position3D {
  x: number;
  y: number;
  z: number;
}

export interface Device {
  id: string;
  tombId: string;
  chamberId: string;
  code: string;
  name: string;
  type: 'SALT' | 'ENV';
  model: string;
  status: 'ONLINE' | 'OFFLINE' | 'ERROR' | 'MAINTENANCE';
  position: Position3D;
  installTime: number;
  manufacturer?: string;
  description?: string;
}

export interface Chamber {
  id: string;
  tombId: string;
  name: string;
  code: string;
  width: number;
  height: number;
  length: number;
  description?: string;
}

export interface Tomb {
  id: string;
  name: string;
  code: string;
  dynasty: string;
  description: string;
  longitude: number;
  latitude: number;
  address: string;
}
