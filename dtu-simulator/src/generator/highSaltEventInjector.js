/**
 * 高 Na₂SO₄ 事件注入器
 *
 * 模拟极端盐害事件，如：
 * - 雨季毛细水上升导致的大面积盐分富集
 * - 地下水位波动导致的硫酸盐爆发
 * - 季节性高湿导致的结壳加剧
 *
 * 功能：
 * - 事件触发：定时/随机/手动触发
 * - 影响范围：可指定区域或全部传感器
 * - 强度控制：硫酸盐浓度 2~10 倍突变
 * - 事件时长：2~48小时，带上升/下降沿
 */

class HighSaltEventInjector {
  constructor(options = {}) {
    this.enabled = options.enabled !== undefined ? options.enabled : false;
    this.eventProbabilityPerDay = options.eventProbabilityPerDay || 0.05;
    this.defaultMultiplier = options.defaultMultiplier || 5;
    this.defaultDurationHours = options.defaultDurationHours || 4;
    this.targetIons = options.targetIons || ['so4_2_minus', 'na_plus'];
    this.rampUpHours = options.rampUpHours || 1;
    this.rampDownHours = options.rampDownHours || 1;
    this.affectedRatio = options.affectedRatio || 0.7;

    this.activeEvents = new Map();
    this.eventHistory = [];
    this.eventCounter = 0;

    if (options.autoTrigger !== false) {
      this._scheduleAutoTrigger();
    }
  }

  /**
   * 手动触发一个高盐事件
   */
  triggerEvent(options = {}) {
    const eventId = `event_${++this.eventCounter}_${Date.now()}`;
    const now = options.startTime ? new Date(options.startTime) : new Date();

    const event = {
      id: eventId,
      name: options.name || `高盐事件-${this.eventCounter}`,
      type: options.type || 'capillary_rise',
      startTime: now,
      durationHours: options.durationHours || this.defaultDurationHours,
      multiplier: options.multiplier || this.defaultMultiplier,
      targetIons: options.targetIons || this.targetIons,
      affectedDevices: options.affectedDevices || null,
      affectedRatio: options.affectedRatio || this.affectedRatio,
      rampUpHours: options.rampUpHours || this.rampUpHours,
      rampDownHours: options.rampDownHours || this.rampDownHours,
      description: options.description || '硫酸盐/钠离子浓度突发上升',
      createdAt: new Date()
    };

    this.activeEvents.set(eventId, event);
    this.eventHistory.push(event);

    console.log(`⚠️  高盐事件触发 [${event.id}] ` +
      `强度=${event.multiplier}x, 时长=${event.durationHours}h, ` +
      `类型=${event.type}`);

    return event;
  }

  /**
   * 对生成的盐数据应用当前活动的事件
   */
  applyEvents(saltData, deviceId, timestamp) {
    if (!this.enabled || this.activeEvents.size === 0) {
      return saltData;
    }

    const time = timestamp instanceof Date ? timestamp : new Date(timestamp);
    const data = { ...saltData, data: { ...saltData.data } };

    for (const event of this.activeEvents.values()) {
      if (!this._isDeviceAffected(event, deviceId)) {
        continue;
      }

      const intensity = this._calculateEventIntensity(event, time);
      if (intensity <= 0) {
        continue;
      }

      event.targetIons.forEach(ion => {
        const ionName = this._getIonName(ion);
        if (data.data[ionName] !== undefined) {
          const baseValue = data.data[ionName];
          const increase = baseValue * (event.multiplier - 1) * intensity;
          data.data[ionName] = Math.round((baseValue + increase) * 1000) / 1000;
        }
      });
    }

    return data;
  }

  /**
   * 批量应用事件到所有盐数据
   */
  applyEventsBatch(saltDataList, timestamp) {
    if (!this.enabled || this.activeEvents.size === 0) {
      return saltDataList;
    }

    return saltDataList.map(d => this.applyEvents(d, d.deviceId, timestamp));
  }

  /**
   * 结束指定事件
   */
  endEvent(eventId) {
    const event = this.activeEvents.get(eventId);
    if (event) {
      this.activeEvents.delete(eventId);
      console.log(`✅ 高盐事件结束 [${eventId}] ` +
        `持续了 ${((Date.now() - event.startTime.getTime()) / (1000 * 60 * 60)).toFixed(2)} 小时`);
      return true;
    }
    return false;
  }

  /**
   * 获取所有活动事件
   */
  getActiveEvents() {
    return Array.from(this.activeEvents.values());
  }

  /**
   * 获取事件历史
   */
  getEventHistory() {
    return [...this.eventHistory].sort((a, b) => b.createdAt - a.createdAt);
  }

  /**
   * 清理过期事件
   */
  cleanupExpiredEvents(timestamp = new Date()) {
    const time = timestamp instanceof Date ? timestamp : new Date(timestamp);
    const expiredIds = [];

    for (const [id, event] of this.activeEvents) {
      const endTime = new Date(event.startTime.getTime() + event.durationHours * 60 * 60 * 1000);
      if (time > endTime) {
        expiredIds.push(id);
      }
    }

    expiredIds.forEach(id => this.endEvent(id));
    return expiredIds.length;
  }

  /**
   * 计算事件在指定时间点的强度 (0~1)
   * 梯形包络：上升沿 + 平台期 + 下降沿
   */
  _calculateEventIntensity(event, time) {
    const startTime = event.startTime.getTime();
    const durationMs = event.durationHours * 60 * 60 * 1000;
    const rampUpMs = event.rampUpHours * 60 * 60 * 1000;
    const rampDownMs = event.rampDownHours * 60 * 60 * 1000;
    const endTime = startTime + durationMs;

    const t = time.getTime();

    if (t < startTime || t > endTime) {
      return 0;
    }

    const elapsed = t - startTime;
    const remaining = endTime - t;

    if (elapsed < rampUpMs) {
      return elapsed / rampUpMs;
    }

    if (remaining < rampDownMs) {
      return remaining / rampDownMs;
    }

    return 1;
  }

  /**
   * 判断设备是否受事件影响
   */
  _isDeviceAffected(event, deviceId) {
    if (event.affectedDevices && event.affectedDevices.length > 0) {
      return event.affectedDevices.includes(deviceId);
    }

    return Math.random() < event.affectedRatio;
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

  /**
   * 自动随机触发事件
   */
  _scheduleAutoTrigger() {
    if (!this.enabled) return;

    const checkInterval = 60 * 60 * 1000;
    const hourlyProbability = this.eventProbabilityPerDay / 24;

    setInterval(() => {
      if (Math.random() < hourlyProbability && this.activeEvents.size < 3) {
        const duration = 2 + Math.random() * (this.defaultDurationHours - 2);
        const multiplier = 2 + Math.random() * (this.defaultMultiplier * 2 - 2);

        const types = ['capillary_rise', 'groundwater_fluctuation', 'seasonal_high_humidity', 'salt_crust_dissolution'];
        const type = types[Math.floor(Math.random() * types.length)];

        this.triggerEvent({
          durationHours: Math.round(duration * 10) / 10,
          multiplier: Math.round(multiplier * 10) / 10,
          type,
          description: `自动触发的${this._getEventTypeName(type)}事件`
        });
      }
    }, checkInterval);
  }

  _getEventTypeName(type) {
    const names = {
      capillary_rise: '毛细上升',
      groundwater_fluctuation: '地下水位波动',
      seasonal_high_humidity: '季节性高湿',
      salt_crust_dissolution: '盐壳溶解',
      manual: '手动注入'
    };
    return names[type] || type;
  }
}

module.exports = HighSaltEventInjector;
