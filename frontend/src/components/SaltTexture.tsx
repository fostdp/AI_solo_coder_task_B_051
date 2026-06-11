import { useRef, useMemo } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { crystalVertexShader, crystalFragmentShader, heatmapVertexShader, heatmapFragmentShader } from '@/shaders/crystalShader';
import { SaltData } from '@/types';

interface SaltTextureProps {
  chamberWidth: number;
  chamberLength: number;
  saltData: SaltData[];
  time: number;
}

interface SaltDamageArea {
  position: THREE.Vector3;
  intensity: number;
  size: number;
}

export default function SaltTexture({
  chamberWidth,
  chamberLength,
  saltData,
  time,
}: SaltTextureProps) {
  const crystalGroupRef = useRef<THREE.Group>(null);
  const heatmapRef = useRef<THREE.Mesh>(null);

  const saltDamageAreas = useMemo((): SaltDamageArea[] => {
    if (!saltData.length) return [];

    const areas: SaltDamageArea[] = [];
    const gridSize = 10;

    for (let i = 0; i < gridSize; i++) {
      for (let j = 0; j < gridSize; j++) {
        const x = (i / gridSize - 0.5) * (chamberWidth - 1);
        const z = (j / gridSize - 0.5) * (chamberLength - 1);

        let nearestData = saltData[0];
        let minDist = Infinity;

        saltData.forEach(data => {
          const dist = Math.sqrt(
            Math.pow(data.position.x - x, 2) +
            Math.pow(data.position.z - z, 2)
          );
          if (dist < minDist) {
            minDist = dist;
            nearestData = data;
          }
        });

        if (nearestData && nearestData.totalSalt > 3.0) {
          const intensity = Math.min(1.0, (nearestData.totalSalt - 3.0) / 4.0);
          if (intensity > 0.2) {
            areas.push({
              position: new THREE.Vector3(x, 0.01, z),
              intensity,
              size: 0.3 + intensity * 0.5,
            });
          }
        }
      }
    }

    return areas;
  }, [saltData, chamberWidth, chamberLength]);

  const crystalUniforms = useMemo(() => ({
    uTime: { value: 0 },
    uGrowthProgress: { value: 0.7 },
    uColor: { value: new THREE.Color(0xFFFFFF) },
    uOpacity: { value: 0.85 },
    uLightPosition: { value: new THREE.Vector3(0, 5, 0) },
  }), []);

  const heatmapUniforms = useMemo(() => ({
    uTime: { value: 0 },
  }), []);

  const heatmapGeometry = useMemo(() => {
    const geometry = new THREE.PlaneGeometry(chamberWidth - 0.5, chamberLength - 0.5, 20, 20);
    const positions = geometry.attributes.position;
    const intensities = new Float32Array(positions.count);

    for (let i = 0; i < positions.count; i++) {
      const x = positions.getX(i);
      const z = positions.getY(i);

      let maxIntensity = 0;
      saltDamageAreas.forEach(area => {
        const dist = Math.sqrt(
          Math.pow(area.position.x - x, 2) +
          Math.pow(area.position.z - z, 2)
        );
        const influence = Math.max(0, 1 - dist / 2.0) * area.intensity;
        maxIntensity = Math.max(maxIntensity, influence);
      });

      intensities[i] = maxIntensity;
    }

    geometry.setAttribute('aIntensity', new THREE.BufferAttribute(intensities, 1));
    return geometry;
  }, [saltDamageAreas, chamberWidth, chamberLength]);

  const wallCrystals = useMemo(() => {
    return saltData
      .filter(d => d.totalSalt > 4.0)
      .map(d => ({
        position: [d.position.x, d.position.y, d.position.z] as [number, number, number],
        growthProgress: Math.min(1.0, (d.totalSalt - 4.0) / 3.0),
      }));
  }, [saltData]);

  useFrame(() => {
    crystalUniforms.uTime.value = time;
    heatmapUniforms.uTime.value = time;

    if (crystalGroupRef.current) {
      crystalGroupRef.current.children.forEach((child, index) => {
        const mesh = child as THREE.Mesh;
        const material = mesh.material as THREE.ShaderMaterial;

        const phase = index * 0.1;
        const growth = (Math.sin(time * 0.3 + phase) + 1) / 2;
        material.uniforms.uGrowthProgress.value = 0.5 + growth * 0.5;

        mesh.scale.setScalar(0.8 + growth * 0.4);
      });
    }
  });

  return (
    <group>
      {/* 热力图底面 */}
      <mesh ref={heatmapRef} rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.02, 0]}>
        <primitive object={heatmapGeometry} attach="geometry" />
        <shaderMaterial
          vertexShader={heatmapVertexShader}
          fragmentShader={heatmapFragmentShader}
          uniforms={heatmapUniforms}
          transparent
          side={THREE.DoubleSide}
          depthWrite={false}
        />
      </mesh>

      {/* 地面结晶簇 */}
      <group ref={crystalGroupRef}>
        {saltDamageAreas.map((area, index) => (
          <group key={`cluster-${index}`} position={[area.position.x, 0.05, area.position.z]}>
            {Array.from({ length: 5 }).map((_, k) => {
              const angle = (k / 5) * Math.PI * 2;
              const radius = area.size * 0.3;
              const x = Math.cos(angle) * radius;
              const z = Math.sin(angle) * radius;
              const height = area.intensity * (0.3 + Math.random() * 0.2);

              return (
                <mesh key={k} position={[x, height / 2, z]}>
                  <coneGeometry args={[0.05 + area.intensity * 0.05, height, 6]} />
                  <shaderMaterial
                    vertexShader={crystalVertexShader}
                    fragmentShader={crystalFragmentShader}
                    uniforms={{
                      ...crystalUniforms,
                      uGrowthProgress: { value: 0.6 + area.intensity * 0.4 },
                    }}
                    transparent
                    side={THREE.DoubleSide}
                    depthWrite={false}
                  />
                </mesh>
              );
            })}

            {/* 结晶晕圈 */}
            <mesh position={[0, 0.02, 0]} rotation={[-Math.PI / 2, 0, 0]}>
              <circleGeometry args={[area.size, 32]} />
              <shaderMaterial
                vertexShader={`
                  varying vec2 vUv;
                  void main() {
                    vUv = uv;
                    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
                  }
                `}
                fragmentShader={`
                  varying vec2 vUv;
                  uniform float uTime;
                  void main() {
                    float dist = length(vUv - 0.5);
                    float ring = smoothstep(0.4, 0.5, dist) * (1.0 - smoothstep(0.48, 0.5, dist));
                    float pulse = 0.5 + 0.5 * sin(uTime * 3.0 + dist * 10.0);
                    gl_FragColor = vec4(1.0, 1.0, 1.0, ring * pulse * 0.8);
                  }
                `}
                uniforms={{ uTime: { value: time } }}
                transparent
                depthWrite={false}
              />
            </mesh>
          </group>
        ))}
      </group>

      {/* 墙面结晶点 */}
      {wallCrystals.map((crystal, index) => (
        <mesh key={`wall-crystal-${index}`} position={crystal.position}>
          <octahedronGeometry args={[0.08, 0]} />
          <shaderMaterial
            vertexShader={crystalVertexShader}
            fragmentShader={crystalFragmentShader}
            uniforms={{
              ...crystalUniforms,
              uGrowthProgress: { value: crystal.growthProgress },
            }}
            transparent
            side={THREE.DoubleSide}
          />
        </mesh>
      ))}
    </group>
  );
}
