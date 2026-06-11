/**
 * 盐离子数据生成器
 * 生成Na⁺、Ca²⁺、SO₄²⁻、Cl⁻浓度数据
 * 模拟真实盐害变化趋势
 */

class SaltDataGenerator {
  constructor() {
    this.baselineCache = new Map();
    this.lastValueCache = new Map();
  }

  generate(device, timestamp = new Date()) {
    const time = timestamp instanceof Date ? timestamp : new Date(timestamp);
    const { deviceId, params, highRiskArea } = device;

    if (!this.baselineCache.has(deviceId)) {
      this.baselineCache.set(deviceId, this._generateBaselines(params));
      this.lastValueCache.set(deviceId, this._generateBaselines(params));
    }

    const baselines = this.baselineCache.get(deviceId);
    const lastValues = this.lastValueCache.get(deviceId);

    const seasonalFactor = this._calculateSeasonalFactor(time);
    const dayNightFactor = this._calculateDayNightFactor(time);
    const trendFactor = this._calculateTrendFactor(time, highRiskArea);
    const randomFluctuation = this._generateRandomFluctuation();

    const data = {
      deviceId,
      deviceType: 'salt_ion_sensor',
      timestamp: time.toISOString(),
      data: {}
    };

    ['na_plus', 'ca_2_plus', 'so4_2_minus', 'cl_minus'].forEach(ion => {
      const param = params[ion];
      const baseline = baselines[ion];
      const lastValue = lastValues[ion];

      let value = baseline;
      value *= trendFactor;
      value *= seasonalFactor;
      value *= dayNightFactor;
      value += randomFluctuation * (param.max - param.min) * 0.05;

      if (highRiskArea && Math.random() < 0.15) {
        value += (param.max - param.min) * 0.2;
      }

      value = lastValue + (value - lastValue) * 0.3;
      value = Math.max(param.min, Math.min(param.max * 1.2, value));
      value = Math.round(value * 1000) / 1000;

      data.data[this._getIonName(ion)] = value;
      lastValues[ion] = value;
    });

    this.lastValueCache.set(deviceId, lastValues);

    return data;
  }

  generateBatch(devices, timestamp = new Date()) {
    return devices.map(device => this.generate(device, timestamp));
  }

  _generateBaselines(params) {
    const baselines = {};
    ['na_plus', 'ca_2_plus', 'so4_2_minus', 'cl_minus'].forEach(ion => {
      const param = params[ion];
      baselines[ion] = param.min + Math.random() * (param.max - param.min) * 0.3;
    });
    return baselines;
  }

  _calculateSeasonalFactor(time) {
    const month = time.getMonth();
    const summerMonths = [5, 6, 7, 8];
    const winterMonths = [11, 0, 1];

    if (summerMonths.includes(month)) {
      return 1.15 + Math.random() * 0.1;
    } else if (winterMonths.includes(month)) {
      return 0.85 + Math.random() * 0.1;
    } else {
      return 0.95 + Math.random() * 0.1;
    }
  }

  _calculateDayNightFactor(time) {
    const hour = time.getHours();
    if (hour >= 6 && hour < 18) {
      return 1.02 + Math.random() * 0.03;
    } else {
      return 0.98 + Math.random() * 0.03;
    }
  }

  _calculateTrendFactor(time, highRiskArea) {
    const startOfYear = new Date(time.getFullYear(), 0, 1);
    const daysSinceStart = (time - startOfYear) / (1000 * 60 * 60 * 24);
    const baseTrend = 1 + (daysSinceStart / 365) * (highRiskArea ? 0.5 : 0.1);

    const suddenIncreaseChance = highRiskArea ? 0.05 : 0.01;
    if (Math.random() < suddenIncreaseChance) {
      return baseTrend * (1.2 + Math.random() * 0.3);
    }

    return baseTrend;
  }

  _generateRandomFluctuation() {
    const u1 = Math.random();
    const u2 = Math.random();
    return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
  }

  _getIonName(key) {
    const names = {
      na_plus: 'na_plus',
      ca_2_plus: 'ca_2_plus',
      so4_2_minus: 'so4_2_minus',
      cl_minus: 'cl_minus'
    };
    return names[key] || key;
  }

  resetCache() {
    this.baselineCache.clear();
    this.lastValueCache.clear();
  }
}

module.exports = SaltDataGenerator;
