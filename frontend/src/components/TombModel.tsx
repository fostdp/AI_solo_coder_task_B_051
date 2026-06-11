import { useRef, useMemo, useEffect } from 'react';
import * as THREE from 'three';
import { Chamber } from '@/types';

interface TombModelProps {
  chamber: Chamber;
  showWireframe?: boolean;
}

export default function TombModel({ chamber, showWireframe = false }: TombModelProps) {
  const groupRef = useRef<THREE.Group>(null);

  const dimensions = useMemo(() => {
    const width = chamber.width || 5;
    const height = chamber.height || 5;
    const length = chamber.length || 5;
    return { width, height, length };
  }, [chamber]);

  const materials = useMemo(() => ({
    wall: new THREE.MeshStandardMaterial({
      color: 0x8B7355,
      roughness: 0.9,
      metalness: 0.1,
      side: THREE.DoubleSide,
    }),
    floor: new THREE.MeshStandardMaterial({
      color: 0x6B5344,
      roughness: 0.95,
      metalness: 0.05,
    }),
    wallPaint: new THREE.MeshStandardMaterial({
      color: 0xC4A35A,
      roughness: 0.8,
      metalness: 0.2,
      transparent: true,
      opacity: 0.9,
    }),
  }), []);

  const wallGeometry = useMemo(() => ({
    back: { args: [dimensions.width, dimensions.height, 0.3] as [number, number, number], pos: [0, dimensions.height / 2, -dimensions.length / 2] as [number, number, number] },
    right: { args: [dimensions.length, dimensions.height, 0.3] as [number, number, number], pos: [dimensions.width / 2, dimensions.height / 2, 0] as [number, number, number], rot: [0, Math.PI / 2, 0] as [number, number, number] },
    left: { args: [dimensions.length, dimensions.height, 0.3] as [number, number, number], pos: [-dimensions.width / 2, dimensions.height / 2, 0] as [number, number, number], rot: [0, -Math.PI / 2, 0] as [number, number, number] },
    front: { args: [dimensions.width, dimensions.height, 0.3] as [number, number, number], pos: [0, dimensions.height / 2, dimensions.length / 2] as [number, number, number], rot: [Math.PI, 0, 0] as [number, number, number] },
  }), [dimensions]);

  const muralPlanes = useMemo(() => ({
    back: { args: [dimensions.width - 1, dimensions.height - 1] as [number, number], pos: [0, dimensions.height / 2, -dimensions.length / 2 + 0.2] as [number, number, number] },
    right: { args: [dimensions.length - 1, dimensions.height - 1] as [number, number], pos: [dimensions.width / 2 - 0.2, dimensions.height / 2, 0] as [number, number, number], rot: [0, Math.PI / 2, 0] as [number, number, number] },
    left: { args: [dimensions.length - 1, dimensions.height - 1] as [number, number], pos: [-dimensions.width / 2 + 0.2, dimensions.height / 2, 0] as [number, number, number], rot: [0, -Math.PI / 2, 0] as [number, number, number] },
  }), [dimensions]);

  useEffect(() => {
    return () => {
      materials.wall.dispose();
      materials.floor.dispose();
      materials.wallPaint.dispose();
    };
  }, [materials]);

  return (
    <group ref={groupRef}>
      {/* 地面 */}
      <mesh position={[0, 0, 0]} rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
        <planeGeometry args={[dimensions.width, dimensions.length]} />
        <primitive object={materials.floor} attach="material" />
      </mesh>

      {/* 天花板 */}
      <mesh position={[0, dimensions.height, 0]} rotation={[Math.PI / 2, 0, 0]} receiveShadow>
        <planeGeometry args={[dimensions.width, dimensions.length]} />
        <primitive object={materials.wall} attach="material" />
      </mesh>

      {/* 后墙 */}
      <mesh position={wallGeometry.back.pos} receiveShadow castShadow>
        <boxGeometry args={wallGeometry.back.args} />
        <primitive object={materials.wall} attach="material" />
      </mesh>

      {/* 右墙 */}
      <mesh position={wallGeometry.right.pos} rotation={wallGeometry.right.rot!} receiveShadow castShadow>
        <boxGeometry args={wallGeometry.right.args} />
        <primitive object={materials.wall} attach="material" />
      </mesh>

      {/* 左墙 */}
      <mesh position={wallGeometry.left.pos} rotation={wallGeometry.left.rot!} receiveShadow castShadow>
        <boxGeometry args={wallGeometry.left.args} />
        <primitive object={materials.wall} attach="material" />
      </mesh>

      {/* 前墙 */}
      <mesh position={wallGeometry.front.pos} rotation={wallGeometry.front.rot!} receiveShadow castShadow>
        <boxGeometry args={wallGeometry.front.args} />
        <primitive object={materials.wall} attach="material" />
      </mesh>

      {/* 壁画面（后墙） */}
      <mesh position={muralPlanes.back.pos}>
        <planeGeometry args={muralPlanes.back.args} />
        <primitive object={materials.wallPaint} attach="material" />
      </mesh>

      {/* 壁画面（右墙） */}
      <mesh position={muralPlanes.right.pos} rotation={muralPlanes.right.rot!}>
        <planeGeometry args={muralPlanes.right.args} />
        <primitive object={materials.wallPaint} attach="material" />
      </mesh>

      {/* 壁画面（左墙） */}
      <mesh position={muralPlanes.left.pos} rotation={muralPlanes.left.rot!}>
        <planeGeometry args={muralPlanes.left.args} />
        <primitive object={materials.wallPaint} attach="material" />
      </mesh>

      {/* 线框 */}
      {showWireframe && (
        <lineSegments>
          <edgesGeometry args={[new THREE.BoxGeometry(dimensions.width, dimensions.height, dimensions.length)]} />
          <lineBasicMaterial color={0x4A7C59} linewidth={2} />
        </lineSegments>
      )}

      {/* 灯柱装饰 */}
      <group position={[dimensions.width / 2 - 0.15, 0.2, -dimensions.length / 4]}>
        <mesh>
          <boxGeometry args={[0.1, 0.4, 0.1]} />
          <meshStandardMaterial color={0xE8A838} emissive={0xE8A838} emissiveIntensity={0.3} />
        </mesh>
      </group>
      <group position={[-dimensions.width / 2 + 0.15, 0.2, dimensions.length / 4]}>
        <mesh>
          <boxGeometry args={[0.1, 0.4, 0.1]} />
          <meshStandardMaterial color={0xE8A838} emissive={0xE8A838} emissiveIntensity={0.3} />
        </mesh>
      </group>
    </group>
  );
}
