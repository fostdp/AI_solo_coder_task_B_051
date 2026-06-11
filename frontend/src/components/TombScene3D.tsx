import { useRef, useState, useEffect } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';
import { EffectComposer, Bloom, Vignette } from '@react-three/postprocessing';
import TombModel from './TombModel';
import SaltTexture from './SaltTexture';
import MigrationArrows from './MigrationArrows';
import FlowLines from './FlowLines';
import SensorMarkers from './SensorMarkers';
import { Chamber, Device, SaltData, EnvData, AnalysisResult } from '@/types';

interface TombScene3DProps {
  chamber: Chamber;
  devices: Device[];
  saltData: SaltData[];
  envData: EnvData[];
  analysisResults: AnalysisResult[];
  onSensorClick?: (device: Device) => void;
  showSaltDamage?: boolean;
  showArrows?: boolean;
  showSensors?: boolean;
  showWireframe?: boolean;
  useFlowMode?: boolean;
  particlesPerResult?: number;
}

function SceneLighting() {
  return (
    <>
      <ambientLight intensity={0.4} color={0xFFEEDD} />
      
      <pointLight
        position={[0, 4.5, 0]}
        intensity={1.2}
        color={0xFFE4B5}
        distance={15}
        decay={2}
        castShadow
      />
      
      <pointLight
        position={[2, 1, -3]}
        intensity={0.6}
        color={0xFFD700}
        distance={8}
        decay={2}
      />
      
      <pointLight
        position={[-2, 1, 3]}
        intensity={0.6}
        color={0xFFD700}
        distance={8}
        decay={2}
      />
      
      <spotLight
        position={[0, 5, -5]}
        angle={0.5}
        penumbra={0.5}
        intensity={0.8}
        color={0xFFE4B5}
        castShadow
        shadow-mapSize={[1024, 1024]}
      />
      
      <fog attach="fog" args={[0x1a1a2e, 10, 40]} />
    </>
  );
}

function TimeUpdater({ onTimeUpdate }: { onTimeUpdate: (time: number) => void }) {
  const timeRef = useRef(0);
  
  useFrame((_, delta) => {
    timeRef.current += delta;
    onTimeUpdate(timeRef.current);
  });
  
  return null;
}

function CameraController({ chamber }: { chamber: Chamber }) {
  const { camera } = useThree();
  
  useEffect(() => {
    const width = chamber.width || 5;
    const length = chamber.length || 5;
    const height = chamber.height || 5;
    
    camera.position.set(
      width * 1.2,
      height * 1.5,
      length * 1.2
    );
    camera.lookAt(0, height / 2, 0);
  }, [camera, chamber]);
  
  return null;
}

export default function TombScene3D({
  chamber,
  devices,
  saltData,
  envData,
  analysisResults,
  onSensorClick,
  showSaltDamage = true,
  showArrows = true,
  showSensors = true,
  showWireframe = false,
  useFlowMode = false,
}: TombScene3DProps) {
  const [time, setTime] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  
  const width = chamber.width || 5;
  const height = chamber.height || 5;
  const length = chamber.length || 5;
  
  return (
    <div ref={containerRef} className="w-full h-full relative">
      <Canvas
        shadows
        camera={{ position: [width * 1.2, height * 1.5, length * 1.2], fov: 60 }}
        gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
        dpr={[1, 2]}
      >
        <color attach="background" args={[0x0f0f1a]} />
        
        <CameraController chamber={chamber} />
        <SceneLighting />
        <TimeUpdater onTimeUpdate={setTime} />
        
        <TombModel chamber={chamber} showWireframe={showWireframe} />
        
        {showSaltDamage && (
          <SaltTexture
            chamberWidth={width}
            chamberLength={length}
            saltData={saltData}
            time={time}
          />
        )}
        
        {showArrows && analysisResults.length > 0 && (
          useFlowMode ? (
            <FlowLines
              analysisResults={analysisResults}
              time={time}
              particlesPerResult={20}
              showTrails={false}
              speedScale={0.8}
              lineLength={0.5}
            />
          ) : (
            <MigrationArrows
              analysisResults={analysisResults}
              time={time}
              scale={1}
            />
          )
        )}
        
        {showSensors && (
          <SensorMarkers
            devices={devices}
            saltData={saltData}
            envData={envData}
            onSensorClick={onSensorClick}
          />
        )}
        
        <OrbitControls
          enableDamping
          dampingFactor={0.05}
          minDistance={2}
          maxDistance={30}
          maxPolarAngle={Math.PI / 2 + 0.1}
          target={[0, height / 2, 0]}
        />
        
        <EffectComposer>
          <Bloom
            intensity={0.8}
            luminanceThreshold={0.2}
            luminanceSmoothing={0.9}
            mipmapBlur
          />
          <Vignette
            offset={0.5}
            darkness={0.5}
          />
        </EffectComposer>
      </Canvas>
      
      <div className="absolute bottom-4 left-4 bg-black/60 backdrop-blur-sm rounded-lg p-3 text-white text-sm space-y-1">
        <div className="font-medium text-ochre mb-2">图例</div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-green-500"></div>
          <span>正常</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-yellow-500"></div>
          <span>警告</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-red-500"></div>
          <span>严重</span>
        </div>
        <div className="flex items-center gap-2 mt-2">
          <div className="w-3 h-3 bg-white opacity-80"></div>
          <span>盐结晶区域</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-0 h-0 border-l-[6px] border-l-transparent border-r-[6px] border-r-transparent border-b-[10px] border-b-bronze"></div>
          <span>盐分运移方向</span>
        </div>
      </div>
      
      <div className="absolute top-4 right-4 bg-black/60 backdrop-blur-sm rounded-lg p-3 text-white text-xs space-y-1">
        <div className="text-gray-400">操作提示</div>
        <div>🖱️ 左键拖拽: 旋转视角</div>
        <div>🖱️ 滚轮: 缩放</div>
        <div>🖱️ 右键拖拽: 平移</div>
        <div>🖱️ 双击传感器: 查看详情</div>
      </div>
    </div>
  );
}
