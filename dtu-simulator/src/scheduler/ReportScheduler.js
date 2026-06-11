/**
 * 上报调度器
 * Cron调度每2小时上报一次
 * 支持加速模式（可配置时间倍率）
 * 支持指定时间范围模拟历史数据
 */

const { CronJob } = require('cron');
const winston = require('winston');
const SaltDataGenerator = require('../generator/saltDataGenerator');
const EnvDataGenerator = require('../generator/envDataGenerator');
const HighSaltEventInjector = require('../generator/highSaltEventInjector');
const DTUClient = require('../client/DTUClient');
const { saltIonDevices, envDevices } = require('../config/devices');

class ReportScheduler {
  constructor(options = {}) {
    this.dtuClient = options.dtuClient || new DTUClient(options.clientOptions);
    this.saltGenerator = new SaltDataGenerator();
    this.envGenerator = new EnvDataGenerator();

    this.highSaltInjector = new HighSaltEventInjector({
      enabled: options.highSaltEvents !== false,
      eventProbabilityPerDay: options.highSaltEventProbability || 0.08,
      defaultMultiplier: options.highSaltMultiplier || 5,
      defaultDurationHours: options.highSaltDurationHours || 6,
      targetIons: ['so4_2_minus', 'na_plus'],
      affectedRatio: 0.7,
      rampUpHours: 1,
      rampDownHours: 1
    });

    this.reportIntervalHours = options.reportIntervalHours || 2;
    this.speedMultiplier = options.speedMultiplier || 1;
    this.startTime = options.startTime ? new Date(options.startTime) : null;
    this.endTime = options.endTime ? new Date(options.endTime) : null;

    this.logger = winston.createLogger({
      level: 'info',
      format: winston.format.combine(
        winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
        winston.format.colorize(),
        winston.format.printf(info => `${info.timestamp} [${info.level}] ${info.message}`)
      ),
      transports: [
        new winston.transports.Console(),
        new winston.transports.File({ filename: 'logs/scheduler.log' })
      ]
    });

    this.currentSimulatedTime = this.startTime ? new Date(this.startTime) : null;
    this.cronJob = null;
    this.isRunning = false;
    this.reportCount = 0;
  }

  start(mode = 'realtime') {
    if (this.isRunning) {
      this.logger.warn('调度器已经在运行');
      return;
    }

    this.isRunning = true;
    this.logger.info('========================================');
    this.logger.info('DTU模拟器启动');
    this.logger.info(`模式: ${mode}`);
    this.logger.info(`设备总数: ${saltIonDevices.length + envDevices.length}`);
    this.logger.info(`  - 盐离子传感器: ${saltIonDevices.length}台`);
    this.logger.info(`  - 微环境传感器: ${envDevices.length}台`);
    this.logger.info(`上报间隔: ${this.reportIntervalHours}小时`);
    this.logger.info(`服务地址: ${this.dtuClient.baseUrl}${this.dtuClient.endpoint}`);
    this.logger.info(`高盐事件注入: ${this.highSaltInjector.enabled ? '开启' : '关闭'}`);
    if (this.highSaltInjector.enabled) {
      this.logger.info(`  - 日均概率: ${this.highSaltInjector.eventProbabilityPerDay * 100}%`);
      this.logger.info(`  - 强度倍数: ~${this.highSaltInjector.defaultMultiplier}x`);
      this.logger.info(`  - 持续时长: ~${this.highSaltInjector.defaultDurationHours}小时`);
      this.logger.info(`  - 影响范围: ~${this.highSaltInjector.affectedRatio * 100}% 设备`);
    }
    this.logger.info('========================================');

    if (mode === 'fast') {
      this._startFastMode();
    } else if (mode === 'historical') {
      this._startHistoricalMode();
    } else {
      this._startRealtimeMode();
    }
  }

  stop() {
    if (this.cronJob) {
      this.cronJob.stop();
    }
    this.isRunning = false;
    this.logger.info('调度器已停止');
    this._printStats();
  }

  _startRealtimeMode() {
    this.logger.info('启动实时模式 - 每2小时自动上报一次');

    this._executeReport(new Date());

    const cronExpression = `0 0 */${this.reportIntervalHours} * * *`;
    this.cronJob = new CronJob(cronExpression, () => {
      this._executeReport(new Date());
    }, null, true, 'Asia/Shanghai');

    this.logger.info(`Cron表达式: ${cronExpression}`);
    this.logger.info('下次上报时间: ' + this.cronJob.nextDate().toLocaleString('zh-CN'));
  }

  _startFastMode() {
    this.logger.info(`启动加速模式 - 时间倍率: ${this.speedMultiplier}x`);

    const realIntervalMs = (this.reportIntervalHours * 60 * 60 * 1000) / this.speedMultiplier;
    this.logger.info(`实际上报间隔: ${realIntervalMs / 1000}秒 (模拟${this.reportIntervalHours}小时)`);

    this.currentSimulatedTime = this.currentSimulatedTime || new Date();
    this._executeReport(this.currentSimulatedTime);

    const fastInterval = setInterval(() => {
      if (!this.isRunning) {
        clearInterval(fastInterval);
        return;
      }

      this.currentSimulatedTime = new Date(
        this.currentSimulatedTime.getTime() + this.reportIntervalHours * 60 * 60 * 1000
      );
      this._executeReport(this.currentSimulatedTime);
    }, realIntervalMs);

    this._scheduleAutoStop(fastInterval);
  }

  _startHistoricalMode() {
    if (!this.startTime || !this.endTime) {
      this.logger.error('历史模式需要指定开始时间和结束时间');
      return;
    }

    this.logger.info('启动历史数据模拟模式');
    this.logger.info(`时间范围: ${this.startTime.toLocaleString('zh-CN')} ~ ${this.endTime.toLocaleString('zh-CN')}`);

    this._simulateHistoricalData().then(() => {
      this.logger.info('历史数据模拟完成');
      this.stop();
    });
  }

  async _simulateHistoricalData() {
    let currentTime = new Date(this.startTime);
    const intervalMs = this.reportIntervalHours * 60 * 60 * 1000;

    while (currentTime <= this.endTime) {
      await this._executeReport(currentTime);
      currentTime = new Date(currentTime.getTime() + intervalMs);
      await this._delay(100);
    }
  }

  async _executeReport(timestamp) {
    this.reportCount++;
    const reportStartTime = Date.now();

    this.logger.info(`\n[第${this.reportCount}次上报] ${timestamp.toLocaleString('zh-CN')}`);
    this.logger.info('-' .repeat(50));

    try {
      this.highSaltInjector.cleanupExpiredEvents(timestamp);

      let saltData = this.saltGenerator.generateBatch(saltIonDevices, timestamp);
      saltData = this.highSaltInjector.applyEventsBatch(saltData, timestamp);

      const envData = this.envGenerator.generateBatch(envDevices, timestamp);
      const allData = [...saltData, ...envData];

      this._logDataPreview(saltData, envData, timestamp);

      const result = await this.dtuClient.reportBatchParallel(allData, 7);

      const duration = Date.now() - reportStartTime;
      this.logger.info('-' .repeat(50));
      this.logger.info(`上报完成 - 成功: ${result.success}/${result.total}, 耗时: ${duration}ms`);

      if (result.failed > 0) {
        this.logger.warn(`失败: ${result.failed}个设备`);
      }

      return result;
    } catch (error) {
      this.logger.error('上报过程中发生错误:', error.message);
      return { success: false, error: error.message };
    }
  }

  _logDataPreview(saltData, envData, timestamp) {
    const highRiskSaltData = saltData.filter(d => {
      const values = Object.values(d.data);
      return values.some(v => v > 3.0);
    });

    const highHumidityData = envData.filter(d => d.data.humidity > 75);

    if (highRiskSaltData.length > 0) {
      this.logger.warn(`⚠️  盐离子异常设备: ${highRiskSaltData.length}台`);
      highRiskSaltData.slice(0, 3).forEach(d => {
        const maxIon = Object.entries(d.data).reduce((a, b) => a[1] > b[1] ? a : b);
        this.logger.warn(`   ${d.deviceId}: ${maxIon[0]}=${maxIon[1].toFixed(3)} mg/cm²`);
      });
    }

    if (highHumidityData.length > 0) {
      this.logger.warn(`⚠️  高湿度设备: ${highHumidityData.length}台`);
      highHumidityData.slice(0, 3).forEach(d => {
        this.logger.warn(`   ${d.deviceId}: humidity=${d.data.humidity.toFixed(1)}%`);
      });
    }

    const sampleSalt = saltData[0];
    const sampleEnv = envData[0];
    this.logger.info(`盐离子样本 [${sampleSalt.deviceId}]: ` +
      `Na⁺=${sampleSalt.data.na_plus.toFixed(3)}, ` +
      `Ca²⁺=${sampleSalt.data.ca_2_plus.toFixed(3)}, ` +
      `SO₄²⁻=${sampleSalt.data.so4_2_minus.toFixed(3)}, ` +
      `Cl⁻=${sampleSalt.data.cl_minus.toFixed(3)}`
    );
    this.logger.info(`微环境样本 [${sampleEnv.deviceId}]: ` +
      `温度=${sampleEnv.data.temperature.toFixed(1)}℃, ` +
      `湿度=${sampleEnv.data.humidity.toFixed(1)}%, ` +
      `风速=${sampleEnv.data.windSpeed.toFixed(3)}m/s`
    );
  }

  _scheduleAutoStop(interval) {
    const maxReports = 1000;
    const checkInterval = setInterval(() => {
      if (this.reportCount >= maxReports) {
        this.logger.info(`已达到最大上报次数 (${maxReports})，自动停止`);
        clearInterval(interval);
        clearInterval(checkInterval);
        this.stop();
      }
      if (!this.isRunning) {
        clearInterval(checkInterval);
      }
    }, 5000);
  }

  _printStats() {
    const stats = this.dtuClient.getStats();
    this.logger.info('\n========================================');
    this.logger.info('运行统计');
    this.logger.info('========================================');
    this.logger.info(`总上报次数: ${this.reportCount}`);
    this.logger.info(`总请求数: ${stats.totalRequests}`);
    this.logger.info(`成功: ${stats.successfulRequests}`);
    this.logger.info(`失败: ${stats.failedRequests}`);
    this.logger.info(`重试次数: ${stats.retries}`);
    this.logger.info('========================================\n');
  }

  _delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

module.exports = ReportScheduler;
