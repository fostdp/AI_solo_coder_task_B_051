/**
 * 微环境数据生成器
 * 生成温度、湿度、风速数据
 * 模拟墓葬内微环境变化
 */

class EnvDataGenerator {
  constructor() {
    this.humidityHighPeriods = new Map();
    this.lastValueCache = new Map();
    this.rainySeasonStart = this._calculateRainySeason();
  }

  generate(device, timestamp = new Date()) {
    const time = timestamp instanceof Date ? timestamp : new Date(timestamp);
    const { deviceId, area, params } = device;

    if (!this.lastValueCache.has(deviceId)) {
      this.lastValueCache.set(deviceId, {
        temperature: (params.temperature.min + params.temperature.max) / 2,
        humidity: (params.humidity.min + params.humidity.max) / 2,
        windSpeed: params.windSpeed.max / 2
      });
    }

    const lastValues = this.lastValueCache.get(deviceId);

    const isHighHumidityPeriod = this._isHighHumidityPeriod(deviceId, time);
    const isRainySeason = this._isRainySeason(time);
    const dayNightFactor = this._calculateDayNightFactor(time);
    const seasonalTempFactor = this._calculateSeasonalTempFactor(time);
    const windVariation = this._calculateWindVariation(time, area);

    const data = {
      deviceId,
      deviceType: 'environment_sensor',
      timestamp: time.toISOString(),
      data: {}
    };

    let temperature = (params.temperature.min + params.temperature.max) / 2;
    temperature += seasonalTempFactor;
    temperature *= dayNightFactor;
    temperature += (Math.random() - 0.5) * 1.5;
    temperature = lastValues.temperature + (temperature - lastValues.temperature) * 0.4;
    temperature = Math.max(params.temperature.min - 2, Math.min(params.temperature.max + 2, temperature));
    temperature = Math.round(temperature * 100) / 100;

    let humidity = (params.humidity.min + params.humidity.max) / 2;
    if (isHighHumidityPeriod) {
      humidity = Math.max(78, params.humidity.max - Math.random() * 3);
    } else if (isRainySeason) {
      humidity += params.humidity.rainySeasonBoost;
    }
    humidity *= (1 + (Math.random() - 0.5) * 0.05);
    humidity = lastValues.humidity + (humidity - lastValues.humidity) * 0.35;
    humidity = Math.max(params.humidity.min, Math.min(95, humidity));
    humidity = Math.round(humidity * 100) / 100;

    let windSpeed = params.windSpeed.max * windVariation;
    windSpeed += (Math.random() - 0.5) * 0.05;
    windSpeed = lastValues.windSpeed + (windSpeed - lastValues.windSpeed) * 0.5;
    windSpeed = Math.max(0, Math.min(params.windSpeed.max * 1.2, windSpeed));
    windSpeed = Math.round(windSpeed * 1000) / 1000;

    data.data.temperature = temperature;
    data.data.humidity = humidity;
    data.data.windSpeed = windSpeed;

    this.lastValueCache.set(deviceId, { temperature, humidity, windSpeed });

    return data;
  }

  generateBatch(devices, timestamp = new Date()) {
    return devices.map(device => this.generate(device, timestamp));
  }

  _isHighHumidityPeriod(deviceId, time) {
    if (!this.humidityHighPeriods.has(deviceId)) {
      const shouldHaveHighHumidity = Math.random() < 0.3;
      if (shouldHaveHighHumidity) {
        const startOffset = Math.floor(Math.random() * 30) * 24 * 60 * 60 * 1000;
        const startTime = new Date(time.getTime() - startOffset);
        const duration = (48 + Math.random() * 24) * 60 * 60 * 1000;
        this.humidityHighPeriods.set(deviceId, {
          startTime,
          endTime: new Date(startTime.getTime() + duration)
        });
      } else {
        this.humidityHighPeriods.set(deviceId, null);
      }
    }

    const period = this.humidityHighPeriods.get(deviceId);
    if (period && time >= period.startTime && time <= period.endTime) {
      return true;
    }

    if (period && time > period.endTime) {
      const shouldRestart = Math.random() < 0.1;
      if (shouldRestart) {
        const duration = (48 + Math.random() * 24) * 60 * 60 * 1000;
        this.humidityHighPeriods.set(deviceId, {
          startTime: time,
          endTime: new Date(time.getTime() + duration)
        });
        return true;
      } else {
        this.humidityHighPeriods.set(deviceId, null);
      }
    }

    return false;
  }

  _calculateRainySeason() {
    const now = new Date();
    const year = now.getFullYear();
    return {
      start: new Date(year, 5, 1),
      end: new Date(year, 8, 30)
    };
  }

  _isRainySeason(time) {
    const year = time.getFullYear();
    const rainyStart = new Date(year, this.rainySeasonStart.start.getMonth(), this.rainySeasonStart.start.getDate());
    const rainyEnd = new Date(year, this.rainySeasonStart.end.getMonth(), this.rainySeasonStart.end.getDate());
    return time >= rainyStart && time <= rainyEnd;
  }

  _calculateDayNightFactor(time) {
    const hour = time.getHours();
    const minute = time.getMinutes();
    const hourDecimal = hour + minute / 60;

    if (hourDecimal >= 11 && hourDecimal <= 15) {
      return 1.05 + (Math.sin((hourDecimal - 11) / 4 * Math.PI)) * 0.05;
    } else if (hourDecimal >= 23 || hourDecimal <= 5) {
      return 0.92 + (Math.cos((hourDecimal + 1) / 6 * Math.PI)) * 0.03;
    } else if (hourDecimal > 5 && hourDecimal < 11) {
      return 0.95 + (hourDecimal - 5) / 6 * 0.1;
    } else {
      return 1.0 - (hourDecimal - 15) / 8 * 0.08;
    }
  }

  _calculateSeasonalTempFactor(time) {
    const month = time.getMonth();
    const baseVariation = Math.sin((month - 1) / 12 * 2 * Math.PI) * 3;
    return baseVariation + (Math.random() - 0.5) * 1;
  }

  _calculateWindVariation(time, area) {
    const hour = time.getHours();
    let baseVariation = 0.3 + Math.sin(hour / 24 * 2 * Math.PI) * 0.2;

    if (area === '墓道') {
      baseVariation *= 1.5;
    } else if (area === '后室' || area.startsWith('侧室')) {
      baseVariation *= 0.5;
    }

    return Math.max(0, baseVariation + (Math.random() - 0.5) * 0.2);
  }

  resetCache() {
    this.humidityHighPeriods.clear();
    this.lastValueCache.clear();
  }
}

module.exports = EnvDataGenerator;
