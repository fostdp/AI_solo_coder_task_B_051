import { useRef, useMemo, useEffect } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { arrowVertexShader, arrowFragmentShader } from '@/shaders/crystalShader';
import { AnalysisResult } from '@/types';

interface MigrationArrowsProps {
  analysisResults: AnalysisResult[];
  time: number;
  scale?: number;
}

export default function MigrationArrows({ analysisResults, time, scale = 1 }: MigrationArrowsProps) {
  const instancedMeshRef = useRef<THREE.InstancedMesh>(null);
  const arrowGeometry = useRef<THREE.ConeGeometry>(null);
  
  const arrowCount = useMemo(() => {
    return Math.min(analysisResults.length, 50);
  }, [analysisResults]);
  
  const { instanceMatrices, directionData, scaleData, phaseData } = useMemo(() => {
    const matrices: THREE.Matrix4[] = [];
    const dirX: number[] = [];
    const dirY: number[] = [];
    const dirZ: number[] = [];
    const scales: number[] = [];
    const phases: number[] = [];
    
    for (let i = 0; i < arrowCount; i++) {
      const result = analysisResults[i];
      if (!result) continue;
      
      const matrix = new THREE.Matrix4();
      const position = new THREE.Vector3(
        result.location.x,
        result.location.y + 0.1,
        result.location.z
      );
      matrix.setPosition(position);
      
      matrices.push(matrix);
      
      const velocity = result.migrationVelocity;
      const speed = Math.sqrt(
        velocity.x * velocity.x +
        velocity.y * velocity.y +
        velocity.z * velocity.z
      );
      
      const normalizedSpeed = Math.min(1.0, speed * 1e6);
      
      dirX.push(velocity.x / Math.max(speed, 1e-10));
      dirY.push(velocity.y / Math.max(speed, 1e-10) + 0.1);
      dirZ.push(velocity.z / Math.max(speed, 1e-10));
      scales.push(0.15 + normalizedSpeed * 0.2);
      phases.push(i / arrowCount);
    }
    
    return {
      instanceMatrices: matrices,
      directionData: { x: dirX, y: dirY, z: dirZ },
      scaleData: scales,
      phaseData: phases,
    };
  }, [analysisResults, arrowCount]);
  
  const arrowMaterial = useMemo(() => {
    const material = new THREE.ShaderMaterial({
      vertexShader: arrowVertexShader,
      fragmentShader: arrowFragmentShader,
      uniforms: {
        uTime: { value: 0 },
        uColor: { value: new THREE.Color(0x4A7C59) },
      },
      transparent: true,
      side: THREE.DoubleSide,
      depthWrite: false,
    });
    
    return material;
  }, []);
  
  const instancedAttributes = useMemo(() => {
    const directionX = new Float32Array(arrowCount);
    const directionY = new Float32Array(arrowCount);
    const directionZ = new Float32Array(arrowCount);
    const aScale = new Float32Array(arrowCount);
    const aPhase = new Float32Array(arrowCount);
    
    for (let i = 0; i < arrowCount; i++) {
      directionX[i] = directionData.x[i] || 0;
      directionY[i] = directionData.y[i] || 0;
      directionZ[i] = directionData.z[i] || 0;
      aScale[i] = (scaleData[i] || 0.2) * scale;
      aPhase[i] = phaseData[i] || 0;
    }
    
    return {
      directionX,
      directionY,
      directionZ,
      aScale,
      aPhase,
    };
  }, [directionData, scaleData, phaseData, arrowCount, scale]);
  
  useEffect(() => {
    if (instancedMeshRef.current) {
      const mesh = instancedMeshRef.current;
      for (let i = 0; i < arrowCount; i++) {
        if (instanceMatrices[i]) {
          mesh.setMatrixAt(i, instanceMatrices[i]);
        }
      }
      mesh.instanceMatrix.needsUpdate = true;
    }
  }, [instanceMatrices, arrowCount]);

  useFrame(() => {
    if (arrowMaterial) {
      arrowMaterial.uniforms.uTime.value = time;
    }
  });
  
  if (arrowCount === 0) return null;
  
  return (
    <instancedMesh
      ref={instancedMeshRef}
      args={[undefined, undefined, arrowCount]}
      frustumCulled={false}
    >
      <coneGeometry args={[0.03, 0.15, 6]} ref={arrowGeometry}>
        <instancedBufferAttribute
          attach="attributes-aDirectionX"
          args={[instancedAttributes.directionX, 1]}
        />
        <instancedBufferAttribute
          attach="attributes-aDirectionY"
          args={[instancedAttributes.directionY, 1]}
        />
        <instancedBufferAttribute
          attach="attributes-aDirectionZ"
          args={[instancedAttributes.directionZ, 1]}
        />
        <instancedBufferAttribute
          attach="attributes-aScale"
          args={[instancedAttributes.aScale, 1]}
        />
        <instancedBufferAttribute
          attach="attributes-aPhase"
          args={[instancedAttributes.aPhase, 1]}
        />
      </coneGeometry>
      <primitive object={arrowMaterial} attach="material" />
    </instancedMesh>
  );
}
