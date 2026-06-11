import { useRef, useState } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { Device, SaltData, EnvData } from '@/types';

interface SensorMarkersProps {
  devices: Device[];
  saltData: SaltData[];
  envData: EnvData[];
  onSensorClick?: (device: Device) => void;
}

export default function SensorMarkers({ devices, saltData, envData, onSensorClick }: SensorMarkersProps) {
  const groupRef = useRef<THREE.Group>(null);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  
  const getDeviceStatus = (device: Device) => {
    const latestSalt = saltData.find(d => d.deviceId === device.id);
    const latestEnv = envData.find(d => d.deviceId === device.id);
    
    if (device.status === 'OFFLINE') return 'offline';
    if (device.status === 'MAINTENANCE') return 'maintenance';
    
    if (latestSalt && latestSalt.totalSalt > 5.0) return 'critical';
    if (latestSalt && latestSalt.totalSalt > 3.0) return 'warning';
    if (latestEnv && latestEnv.humidity > 75) return 'warning';
    
    return 'normal';
  };
  
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'critical': return 0xD64545;
      case 'warning': return 0xE8A838;
      case 'offline': return 0x6B7280;
      case 'maintenance': return 0x6366F1;
      default: return 0x52B788;
    }
  };
  
  useFrame((state) => {
    if (groupRef.current) {
      groupRef.current.children.forEach((child, index) => {
        const mesh = child as THREE.Mesh;
        const pulse = 0.8 + 0.2 * Math.sin(state.clock.elapsedTime * 2 + index * 0.3);
        mesh.scale.setScalar(pulse);
        
        const material = mesh.material as THREE.MeshStandardMaterial;
        if (material.emissive) {
          material.emissiveIntensity = 0.3 + pulse * 0.3;
        }
      });
    }
  });
  
  return (
    <group ref={groupRef}>
      {devices.map((device) => {
        const status = getDeviceStatus(device);
        const color = getStatusColor(status);
        const isSaltSensor = device.type === 'SALT';
        
        return (
          <group key={device.id}>
            <mesh
              position={[device.position.x, device.position.y, device.position.z]}
              onClick={(e) => {
                e.stopPropagation();
                onSensorClick?.(device);
              }}
              onPointerOver={(e) => {
                e.stopPropagation();
                setHoveredId(device.id);
                document.body.style.cursor = 'pointer';
              }}
              onPointerOut={() => {
                setHoveredId(null);
                document.body.style.cursor = 'auto';
              }}
            >
              {isSaltSensor ? (
                <octahedronGeometry args={[0.08, 0]} />
              ) : (
                <sphereGeometry args={[0.08, 16, 16]} />
              )}
              <meshStandardMaterial
                color={color}
                emissive={color}
                emissiveIntensity={hoveredId === device.id ? 0.8 : 0.4}
                transparent
                opacity={0.9}
              />
            </mesh>
            
            <mesh position={[device.position.x, device.position.y, device.position.z]}>
              <ringGeometry args={[0.1, 0.12, 32]} />
              <meshBasicMaterial
                color={color}
                transparent
                opacity={0.6}
                side={THREE.DoubleSide}
              />
            </mesh>
            
            {hoveredId === device.id && (
              <group position={[device.position.x, device.position.y + 0.25, device.position.z]}>
                <mesh>
                  <planeGeometry args={[0.5, 0.15]} />
                  <meshBasicMaterial
                    color={0x1F2937}
                    transparent
                    opacity={0.9}
                  />
                </mesh>
              </group>
            )}
            
            <mesh position={[device.position.x, device.position.y / 2, device.position.z]}>
              <cylinderGeometry args={[0.005, 0.005, device.position.y, 4]} />
              <meshBasicMaterial color={color} transparent opacity={0.5} />
            </mesh>
          </group>
        );
      })}
      
      {devices.map((device) => {
        const status = getDeviceStatus(device);
        const color = getStatusColor(status);
        
        return (
          <mesh key={`halo-${device.id}`} position={[device.position.x, 0.01, device.position.z]} rotation={[-Math.PI / 2, 0, 0]}>
            <ringGeometry args={[0.15, 0.2, 32]} />
            <meshBasicMaterial
              color={color}
              transparent
              opacity={0.3}
              side={THREE.DoubleSide}
            />
          </mesh>
        );
      })}
    </group>
  );
}
