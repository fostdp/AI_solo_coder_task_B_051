import { useEffect, useState, useCallback } from 'react';
import { Tomb, Chamber, Device, SaltData, EnvData, AnalysisResult, Alarm, RiskLevel, AlarmLevel, AlarmStatus, CycleCountData } from '@/types';

export function useMockData() {
  const [tombs, setTombs] = useState<Tomb[]>([]);
  const [chambers, setChambers] = useState<Chamber[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [saltData, setSaltData] = useState<SaltData[]>([]);
  const [envData, setEnvData] = useState<EnvData[]>([]);
  const [analysisResults, setAnalysisResults] = useState<AnalysisResult[]>([]);
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [cycleCountDataList, setCycleCountDataList] = useState<CycleCountData[]>([]);
  const [loading, setLoading] = useState(true);

  const generateMockData = useCallback(() => {
    const mockTombs: Tomb[] = [
      {
        id: 'T001',
        name: '懿德太子墓',
        code: 'YDTZ',
        dynasty: '唐代',
        description: '懿德太子李重润墓，乾陵陪葬墓之一',
        longitude: 108.2175,
        latitude: 34.5703,
        address: '陕西省咸阳市乾县',
      },
      {
        id: 'T002',
        name: '永泰公主墓',
        code: 'YTGZ',
        dynasty: '唐代',
        description: '永泰公主李仙蕙墓，乾陵陪葬墓之一',
        longitude: 108.2180,
        latitude: 34.5698,
        address: '陕西省咸阳市乾县',
      },
    ];

    const mockChambers: Chamber[] = [
      { id: 'C001', tombId: 'T001', name: '墓道', code: 'YD-MD', width: 3.8, height: 2.5, length: 26.3, description: '懿德太子墓墓道' },
      { id: 'C002', tombId: 'T001', name: '前室', code: 'YD-QS', width: 4.5, height: 5.0, length: 4.8, description: '懿德太子墓前室' },
      { id: 'C003', tombId: 'T001', name: '后室', code: 'YD-HS', width: 5.3, height: 5.5, length: 5.0, description: '懿德太子墓后室' },
      { id: 'C004', tombId: 'T002', name: '墓道', code: 'YT-MD', width: 3.9, height: 2.4, length: 27.5, description: '永泰公主墓墓道' },
      { id: 'C005', tombId: 'T002', name: '前室', code: 'YT-QS', width: 4.6, height: 5.2, length: 4.9, description: '永泰公主墓前室' },
      { id: 'C006', tombId: 'T002', name: '后室', code: 'YT-HS', width: 5.4, height: 5.6, length: 5.1, description: '永泰公主墓后室' },
    ];

    const mockDevices: Device[] = [];
    const saltDeviceIds: string[] = [];
    const envDeviceIds: string[] = [];

    for (let i = 1; i <= 40; i++) {
      const id = `D${String(i).padStart(3, '0')}`;
      saltDeviceIds.push(id);
      const chamberIndex = Math.floor(Math.random() * 6);
      const chamber = mockChambers[chamberIndex];
      mockDevices.push({
        id,
        tombId: chamber.tombId,
        chamberId: chamber.id,
        code: `SALT-${String(i).padStart(3, '0')}`,
        name: `盐离子传感器${i}`,
        type: 'SALT',
        model: 'SALT-200',
        status: Math.random() > 0.1 ? 'ONLINE' : 'OFFLINE',
        position: {
          x: (Math.random() - 0.5) * (chamber.width || 5) * 0.8,
          y: 0.5 + Math.random() * ((chamber.height || 5) - 1),
          z: (Math.random() - 0.5) * (chamber.length || 5) * 0.8,
        },
        installTime: new Date('2024-01-15').getTime(),
      });
    }

    for (let i = 1; i <= 30; i++) {
      const id = `E${String(i).padStart(3, '0')}`;
      envDeviceIds.push(id);
      const chamberIndex = Math.floor(Math.random() * 6);
      const chamber = mockChambers[chamberIndex];
      mockDevices.push({
        id,
        tombId: chamber.tombId,
        chamberId: chamber.id,
        code: `ENV-${String(i).padStart(3, '0')}`,
        name: `微环境传感器${i}`,
        type: 'ENV',
        model: 'ENV-300',
        status: Math.random() > 0.1 ? 'ONLINE' : 'OFFLINE',
        position: {
          x: (Math.random() - 0.5) * (chamber.width || 5) * 0.8,
          y: 0.5 + Math.random() * ((chamber.height || 5) - 1),
          z: (Math.random() - 0.5) * (chamber.length || 5) * 0.8,
        },
        installTime: new Date('2024-01-15').getTime(),
      });
    }

    const now = Date.now();
    const mockSaltData: SaltData[] = [];
    const mockEnvData: EnvData[] = [];
    const mockAnalysis: AnalysisResult[] = [];

    for (let h = 0; h < 24; h++) {
      const timestamp = now - h * 7200000;

      saltDeviceIds.forEach((deviceId, idx) => {
        const device = mockDevices.find(d => d.id === deviceId)!;
        const highRisk = idx >= 14 && idx < 20;
        const baseSalt = highRisk ? 4.0 : 1.5;
        const variation = Math.sin(timestamp / 3600000 + idx) * 0.5;
        const trend = h * 0.05;

        const naPlus = Math.max(0.1, (baseSalt * 0.3 + variation + trend) * (0.8 + Math.random() * 0.4));
        const ca2Plus = Math.max(0.1, (baseSalt * 0.25 + variation * 0.8 + trend) * (0.8 + Math.random() * 0.4));
        const so42Minus = Math.max(0.1, (baseSalt * 0.25 + variation * 1.2 + trend) * (0.8 + Math.random() * 0.4));
        const clMinus = Math.max(0.1, (baseSalt * 0.2 + variation + trend) * (0.8 + Math.random() * 0.4));
        const totalSalt = naPlus + ca2Plus + so42Minus + clMinus;

        mockSaltData.push({
          timestamp,
          deviceId,
          tombId: device.tombId,
          chamberId: device.chamberId,
          naPlus,
          ca2Plus,
          so42Minus,
          clMinus,
          totalSalt,
          position: device.position,
        });

        if (h < 12 && totalSalt > 3.0) {
          const velMag = 0.5e-6 + Math.random() * 2e-6;
          const angle = Math.random() * Math.PI * 2;
          mockAnalysis.push({
            timestamp,
            deviceId,
            location: device.position,
            migrationVelocity: {
              x: Math.cos(angle) * velMag,
              y: Math.sin(angle * 0.5) * velMag * 0.3,
              z: Math.sin(angle) * velMag,
            },
            crystallizationPressure: Math.max(0, (totalSalt - 3.0) * 1.5 + Math.random() * 0.5),
            riskLevel: totalSalt > 5.0 ? RiskLevel.CRITICAL : totalSalt > 4.0 ? RiskLevel.HIGH : totalSalt > 3.0 ? RiskLevel.MEDIUM : RiskLevel.LOW,
            predictionHours: 72,
            predictedTotalSalt: totalSalt * (1 + h * 0.02),
            predictedCrystallizationPressure: Math.max(0, (totalSalt * (1 + h * 0.02) - 3.0) * 1.5),
          });
        }
      });

      envDeviceIds.forEach((deviceId, idx) => {
        const device = mockDevices.find(d => d.id === deviceId)!;
        const highHumidity = idx >= 7 && idx < 10;
        const baseTemp = 15 + Math.sin(timestamp / 86400000 * Math.PI * 2) * 3;
        const baseHumidity = highHumidity ? 78 : 55;

        mockEnvData.push({
          timestamp,
          deviceId,
          tombId: device.tombId,
          chamberId: device.chamberId,
          temperature: baseTemp + (Math.random() - 0.5) * 2,
          humidity: Math.max(30, Math.min(95, baseHumidity + Math.sin(timestamp / 3600000 + idx) * 10 + (Math.random() - 0.5) * 5)),
          windSpeed: 0.05 + Math.random() * 0.3,
          position: device.position,
        });
      });
    }

    const mockAlarms: Alarm[] = [
      {
        id: 'A001',
        type: 'SALT_EXCEED',
        level: AlarmLevel.CRITICAL,
        message: '懿德太子墓后室东壁盐分总量达6.2 mg/cm²，超过阈值5.0 mg/cm²',
        deviceId: 'D015',
        tombId: 'T001',
        chamberId: 'C003',
        timestamp: now - 3600000,
        status: AlarmStatus.PENDING,
        value: 6.2,
        threshold: 5.0,
      },
      {
        id: 'A002',
        type: 'SALT_EXCEED',
        level: AlarmLevel.CRITICAL,
        message: '懿德太子墓后室西壁盐分总量达5.8 mg/cm²，超过阈值5.0 mg/cm²',
        deviceId: 'D017',
        tombId: 'T001',
        chamberId: 'C003',
        timestamp: now - 7200000,
        status: AlarmStatus.PROCESSING,
        value: 5.8,
        threshold: 5.0,
      },
      {
        id: 'A003',
        type: 'HUMIDITY_EXCEED',
        level: AlarmLevel.WARNING,
        message: '懿德太子墓后室相对湿度持续超过75%达52小时',
        deviceId: 'E007',
        tombId: 'T001',
        chamberId: 'C003',
        timestamp: now - 14400000,
        status: AlarmStatus.PENDING,
        value: 78.5,
        threshold: 75.0,
      },
      {
        id: 'A004',
        type: 'DEVICE_OFFLINE',
        level: AlarmLevel.WARNING,
        message: '盐离子传感器D025离线超过4小时',
        deviceId: 'D025',
        tombId: 'T002',
        chamberId: 'C004',
        timestamp: now - 28800000,
        status: AlarmStatus.RESOLVED,
        value: 5,
        threshold: 4,
      },
    ];

    const mockCycleCountDataList: CycleCountData[] = mockChambers.map((chamber, chamberIdx) => {
      const now = Date.now();
      const isHighRisk = chamberIdx === 2 || chamberIdx === 5;
      
      const totalCycles = isHighRisk ? 128 : 56;
      const fullCycles = Math.floor(totalCycles * 0.6);
      const partialCycles = totalCycles - fullCycles;
      const crossingCycles = isHighRisk ? 45 : 18;
      const maxRange = isHighRisk ? 45 : 35;
      const minRange = 5;
      const averageRange = isHighRisk ? 22 : 15;
      
      const totalDamage = isHighRisk ? 0.1567 : 0.0423;
      const damageLevel = isHighRisk ? 'HIGH' : 'MEDIUM';
      
      const amplitudeHistogram = [
        { range: [0, 10] as [number, number], count: Math.floor(totalCycles * 0.3) },
        { range: [10, 20] as [number, number], count: Math.floor(totalCycles * 0.25) },
        { range: [20, 30] as [number, number], count: Math.floor(totalCycles * 0.2) },
        { range: [30, 40] as [number, number], count: Math.floor(totalCycles * 0.15) },
        { range: [40, 50] as [number, number], count: Math.floor(totalCycles * 0.1) },
      ];
      
      const rhTimeSeries = [];
      for (let i = 23; i >= 0; i--) {
        const time = new Date(now - i * 3600000).toISOString();
        const baseRh = isHighRisk ? 70 : 55;
        const variation = Math.sin((23 - i) / 4) * 15;
        const rh = Math.max(30, Math.min(95, baseRh + variation + (Math.random() - 0.5) * 5));
        rhTimeSeries.push({ time, rh: parseFloat(rh.toFixed(1)) });
      }
      
      return {
        id: `CC-${chamber.id}`,
        tombId: chamber.tombId,
        chamberId: chamber.id,
        totalCycles,
        fullCycles,
        partialCycles,
        crossingCycles,
        averageRange,
        maxRange,
        minRange,
        totalDamage,
        damageLevel: damageLevel as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL',
        amplitudeHistogram,
        rhTimeSeries,
        periodStart: new Date(now - 7 * 24 * 3600000).toISOString(),
        periodEnd: new Date(now).toISOString(),
        createdAt: new Date(now).toISOString(),
      };
    });

    setTombs(mockTombs);
    setChambers(mockChambers);
    setDevices(mockDevices);
    setSaltData(mockSaltData);
    setEnvData(mockEnvData);
    setAnalysisResults(mockAnalysis);
    setAlarms(mockAlarms);
    setCycleCountDataList(mockCycleCountDataList);
    setLoading(false);
  }, []);

  useEffect(() => {
    generateMockData();
    
    const interval = setInterval(() => {
      const now = Date.now();
      setSaltData(prev => {
        const newData = prev.slice(0, 40 * 23).map(d => ({ ...d }));
        
        prev.filter(d => d.timestamp === Math.max(...prev.map(p => p.timestamp))).forEach(d => {
          const device = devices.find(dev => dev.id === d.deviceId);
          if (!device) return;
          
          const idx = parseInt(d.deviceId.substring(1)) - 1;
          const highRisk = idx >= 14 && idx < 20;
          const baseSalt = highRisk ? 4.2 : 1.6;
          
          const naPlus = Math.max(0.1, baseSalt * 0.3 * (0.9 + Math.random() * 0.2));
          const ca2Plus = Math.max(0.1, baseSalt * 0.25 * (0.9 + Math.random() * 0.2));
          const so42Minus = Math.max(0.1, baseSalt * 0.25 * (0.9 + Math.random() * 0.2));
          const clMinus = Math.max(0.1, baseSalt * 0.2 * (0.9 + Math.random() * 0.2));
          
          newData.unshift({
            ...d,
            timestamp: now,
            naPlus,
            ca2Plus,
            so42Minus,
            clMinus,
            totalSalt: naPlus + ca2Plus + so42Minus + clMinus,
          });
        });
        
        return newData;
      });

      setEnvData(prev => {
        const newData = prev.slice(0, 30 * 23).map(d => ({ ...d }));
        
        prev.filter(d => d.timestamp === Math.max(...prev.map(p => p.timestamp))).forEach(d => {
          const device = devices.find(dev => dev.id === d.deviceId);
          if (!device) return;
          
          const idx = parseInt(d.deviceId.substring(1)) - 1;
          const highHumidity = idx >= 7 && idx < 10;
          
          newData.unshift({
            ...d,
            timestamp: now,
            temperature: 15 + Math.sin(now / 86400000 * Math.PI * 2) * 3 + (Math.random() - 0.5) * 2,
            humidity: highHumidity ? 78 + (Math.random() - 0.5) * 4 : 55 + (Math.random() - 0.5) * 10,
            windSpeed: 0.05 + Math.random() * 0.3,
          });
        });
        
        return newData;
      });
    }, 10000);

    return () => clearInterval(interval);
  }, [generateMockData, devices]);

  return {
    tombs,
    chambers,
    devices,
    saltData,
    envData,
    analysisResults,
    alarms,
    cycleCountDataList,
    loading,
    refresh: generateMockData,
  };
}
