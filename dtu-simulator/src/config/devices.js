/**
 * 设备配置文件
 * 包含70台传感器的详细配置信息
 * - 40台盐离子传感器
 * - 30台微环境传感器
 */

const TOMB_AREAS = [
  '墓道',
  '前室',
  '中室',
  '后室',
  '耳室东',
  '耳室西',
  '侧室北',
  '侧室南'
];

const HIGH_RISK_DEVICES = ['D015', 'D016', 'D017', 'D018', 'D019', 'D020'];

function generateSaltIonDevices() {
  const devices = [];
  const areaPositions = {
    '墓道': { x: [0, 5], y: [0, 2] },
    '前室': { x: [5, 12], y: [2, 8] },
    '中室': { x: [12, 20], y: [4, 12] },
    '后室': { x: [20, 28], y: [6, 14] },
    '耳室东': { x: [8, 15], y: [12, 18] },
    '耳室西': { x: [8, 15], y: [-4, 2] },
    '侧室北': { x: [20, 26], y: [14, 20] },
    '侧室南': { x: [20, 26], y: [-6, 0] }
  };

  for (let i = 1; i <= 40; i++) {
    const deviceId = `D${i.toString().padStart(3, '0')}`;
    const areaIndex = Math.floor((i - 1) / 5);
    const area = TOMB_AREAS[areaIndex % TOMB_AREAS.length];
    const pos = areaPositions[area];
    const posInArea = (i - 1) % 5;

    const isHighRisk = HIGH_RISK_DEVICES.includes(deviceId);

    devices.push({
      deviceId,
      deviceType: 'salt_ion_sensor',
      deviceName: `盐离子传感器-${deviceId}`,
      area,
      position: {
        x: pos.x[0] + (pos.x[1] - pos.x[0]) * (posInArea / 4) + (Math.random() - 0.5) * 0.5,
        y: pos.y[0] + (pos.y[1] - pos.y[0]) * (posInArea / 4) + (Math.random() - 0.5) * 0.5,
        z: 0.5 + Math.random() * 1.5
      },
      highRiskArea: isHighRisk,
      params: {
        na_plus: {
          min: isHighRisk ? 2.0 : 0.3,
          max: isHighRisk ? 8.0 : 2.5,
          trend: isHighRisk ? 0.05 : 0.005
        },
        ca_2_plus: {
          min: isHighRisk ? 1.5 : 0.2,
          max: isHighRisk ? 7.0 : 2.0,
          trend: isHighRisk ? 0.04 : 0.004
        },
        so4_2_minus: {
          min: isHighRisk ? 1.0 : 0.1,
          max: isHighRisk ? 6.0 : 1.8,
          trend: isHighRisk ? 0.03 : 0.003
        },
        cl_minus: {
          min: isHighRisk ? 1.8 : 0.2,
          max: isHighRisk ? 7.5 : 2.2,
          trend: isHighRisk ? 0.045 : 0.0045
        }
      }
    });
  }
  return devices;
}

function generateEnvDevices() {
  const devices = [];
  const areaPositions = {
    '墓道': { x: [2, 4], y: [0.5, 1.5] },
    '前室': { x: [7, 10], y: [3, 7] },
    '中室': { x: [14, 18], y: [5, 11] },
    '后室': { x: [22, 26], y: [7, 13] },
    '耳室东': { x: [10, 13], y: [13, 17] },
    '耳室西': { x: [10, 13], y: [-3, 1] },
    '侧室北': { x: [22, 25], y: [15, 19] },
    '侧室南': { x: [22, 25], y: [-5, -1] }
  };

  for (let i = 1; i <= 30; i++) {
    const deviceId = `E${i.toString().padStart(3, '0')}`;
    const areaIndex = Math.floor((i - 1) / 4);
    const area = TOMB_AREAS[areaIndex % TOMB_AREAS.length];
    const pos = areaPositions[area];
    const posInArea = (i - 1) % 4;

    const isHumidityHighRisk = area === '后室' || area === '侧室北';
    const isWindHigh = area === '墓道';

    devices.push({
      deviceId,
      deviceType: 'environment_sensor',
      deviceName: `微环境传感器-${deviceId}`,
      area,
      position: {
        x: pos.x[0] + (pos.x[1] - pos.x[0]) * (posInArea / 3) + (Math.random() - 0.5) * 0.3,
        y: pos.y[0] + (pos.y[1] - pos.y[0]) * (posInArea / 3) + (Math.random() - 0.5) * 0.3,
        z: 1.5 + Math.random() * 1.0
      },
      params: {
        temperature: {
          min: 12,
          max: 22,
          seasonalVariation: 4
        },
        humidity: {
          min: isHumidityHighRisk ? 55 : 45,
          max: isHumidityHighRisk ? 85 : 75,
          rainySeasonBoost: isHumidityHighRisk ? 10 : 5
        },
        windSpeed: {
          min: 0,
          max: isWindHigh ? 0.5 : 0.2
        }
      }
    });
  }
  return devices;
}

const saltIonDevices = generateSaltIonDevices();
const envDevices = generateEnvDevices();
const allDevices = [...saltIonDevices, ...envDevices];

module.exports = {
  saltIonDevices,
  envDevices,
  allDevices,
  TOMB_AREAS,
  HIGH_RISK_DEVICES,

  getDeviceById(deviceId) {
    return allDevices.find(d => d.deviceId === deviceId);
  },

  getDevicesByArea(area) {
    return allDevices.filter(d => d.area === area);
  },

  getHighRiskDevices() {
    return saltIonDevices.filter(d => d.highRiskArea);
  },

  getDevicesByType(type) {
    return allDevices.filter(d => d.deviceType === type);
  }
};
