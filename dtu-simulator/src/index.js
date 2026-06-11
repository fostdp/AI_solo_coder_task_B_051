/**
 * 4G DTU模拟器 - 主入口文件
 * 命令行参数解析
 * 支持两种模式：实时模式（每2小时上报）、快速模式（可加速模拟）
 */

require('dotenv').config();
const { Command } = require('commander');
const winston = require('winston');
const ReportScheduler = require('./scheduler/ReportScheduler');
const DTUClient = require('./client/DTUClient');
const { allDevices, saltIonDevices, envDevices } = require('./config/devices');

const program = new Command();

const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.colorize(),
    winston.format.simple()
  ),
  transports: [new winston.transports.Console()]
});

program
  .name('dtu-simulator')
  .description('4G DTU模拟器 - 模拟文物墓葬盐离子和微环境传感器数据上报')
  .version('1.0.0');

program
  .option('-m, --mode <mode>', '运行模式: realtime(实时), fast(加速), historical(历史)', 'realtime')
  .option('-s, --speed <multiplier>', '加速模式时间倍率', '24')
  .option('--interval <hours>', '上报间隔（小时）', '2')
  .option('--start-time <datetime>', '历史模式开始时间 (YYYY-MM-DD HH:mm:ss)')
  .option('--end-time <datetime>', '历史模式结束时间 (YYYY-MM-DD HH:mm:ss)')
  .option('--host <url>', '上报服务器地址', 'http://localhost:8080')
  .option('--endpoint <path>', '上报接口路径', '/api/sensor/data')
  .option('--max-retries <count>', '失败重试次数', '3')
  .option('--packet-loss-rate <rate>', '基础丢包率 (0~1)', '0.02')
  .option('--packet-loss-variation <rate>', '丢包率波动幅度 (0~1)', '0.03')
  .option('--high-salt-events <enabled>', '高盐事件注入: true/false', 'true')
  .option('--high-salt-probability <rate>', '高盐事件日均概率 (0~1)', '0.08')
  .option('--high-salt-multiplier <n>', '高盐事件强度倍数', '5')
  .option('--high-salt-duration <hours>', '高盐事件持续时长', '6')
  .option('--trigger-event', '启动时立即触发一次高盐事件')
  .option('--list-devices', '列出所有设备配置')
  .option('--test-report', '测试单次上报后退出');

program.parse(process.argv);
const options = program.opts();

async function main() {
  if (options.listDevices) {
    listDevices();
    return;
  }

  validateOptions(options);

  const dtuClient = new DTUClient({
    baseUrl: options.host,
    endpoint: options.endpoint,
    maxRetries: parseInt(options.maxRetries),
    packetLossRate: parseFloat(options.packetLossRate),
    packetLossVariation: parseFloat(options.packetLossVariation)
  });

  if (options.testReport) {
    await testSingleReport(dtuClient);
    return;
  }

  const scheduler = new ReportScheduler({
    dtuClient,
    reportIntervalHours: parseFloat(options.interval),
    speedMultiplier: parseFloat(options.speed),
    startTime: options.startTime,
    endTime: options.endTime,
    highSaltEvents: options.highSaltEvents === 'true',
    highSaltEventProbability: parseFloat(options.highSaltProbability),
    highSaltMultiplier: parseFloat(options.highSaltMultiplier),
    highSaltDurationHours: parseFloat(options.highSaltDuration)
  });

  if (options.triggerEvent) {
    setTimeout(() => {
      const event = scheduler.highSaltInjector.triggerEvent({
        type: 'manual',
        description: '启动时手动注入的高盐事件',
        multiplier: parseFloat(options.highSaltMultiplier),
        durationHours: parseFloat(options.highSaltDuration)
      });
      logger.info(`🚀 已手动触发高盐事件: ${event.id}`);
    }, 2000);
  }

  setupGracefulShutdown(scheduler);

  scheduler.start(options.mode);
}

function validateOptions(opts) {
  const validModes = ['realtime', 'fast', 'historical'];
  if (!validModes.includes(opts.mode)) {
    logger.error(`无效的运行模式: ${opts.mode}。有效模式: ${validModes.join(', ')}`);
    process.exit(1);
  }

  if (opts.mode === 'historical') {
    if (!opts.startTime || !opts.endTime) {
      logger.error('历史模式需要指定 --start-time 和 --end-time 参数');
      process.exit(1);
    }

    const start = new Date(opts.startTime);
    const end = new Date(opts.endTime);

    if (isNaN(start.getTime())) {
      logger.error(`无效的开始时间格式: ${opts.startTime}。请使用 YYYY-MM-DD HH:mm:ss 格式`);
      process.exit(1);
    }
    if (isNaN(end.getTime())) {
      logger.error(`无效的结束时间格式: ${opts.endTime}。请使用 YYYY-MM-DD HH:mm:ss 格式`);
      process.exit(1);
    }
    if (start >= end) {
      logger.error('开始时间必须早于结束时间');
      process.exit(1);
    }
  }

  const speed = parseFloat(opts.speed);
  if (isNaN(speed) || speed <= 0) {
    logger.error(`无效的时间倍率: ${opts.speed}`);
    process.exit(1);
  }

  const interval = parseFloat(opts.interval);
  if (isNaN(interval) || interval <= 0) {
    logger.error(`无效的上报间隔: ${opts.interval}`);
    process.exit(1);
  }
}

function listDevices() {
  console.log('\n========================================');
  console.log('设备配置列表');
  console.log('========================================\n');

  console.log(`【盐离子传感器】共 ${saltIonDevices.length} 台`);
  console.log('-'.repeat(60));
  saltIonDevices.forEach(device => {
    const risk = device.highRiskArea ? ' ⚠️ 高风险' : '';
    console.log(`  ${device.deviceId} | ${device.area.padEnd(6)} | (${device.position.x.toFixed(1)}, ${device.position.y.toFixed(1)}, ${device.position.z.toFixed(1)})${risk}`);
  });

  console.log(`\n【微环境传感器】共 ${envDevices.length} 台`);
  console.log('-'.repeat(60));
  envDevices.forEach(device => {
    console.log(`  ${device.deviceId} | ${device.area.padEnd(6)} | (${device.position.x.toFixed(1)}, ${device.position.y.toFixed(1)}, ${device.position.z.toFixed(1)})`);
  });

  console.log('\n========================================');
  console.log(`总计: ${allDevices.length} 台设备`);
  console.log('========================================\n');
}

async function testSingleReport(dtuClient) {
  logger.info('执行单次上报测试...');

  const SaltDataGenerator = require('./generator/saltDataGenerator');
  const EnvDataGenerator = require('./generator/envDataGenerator');

  const saltGenerator = new SaltDataGenerator();
  const envGenerator = new EnvDataGenerator();

  const now = new Date();
  const saltData = saltGenerator.generateBatch(saltIonDevices, now);
  const envData = envGenerator.generateBatch(envDevices, now);
  const allData = [...saltData, ...envData];

  logger.info(`生成 ${allData.length} 条数据，开始上报...`);

  const result = await dtuClient.reportBatchParallel(allData, 7);

  console.log('\n========================================');
  console.log('测试结果');
  console.log('========================================');
  console.log(`总设备数: ${result.total}`);
  console.log(`成功: ${result.success}`);
  console.log(`失败: ${result.failed}`);
  console.log(`耗时: ${result.duration}ms`);
  console.log('========================================\n');

  if (result.failed > 0) {
    const failures = result.results.filter(r => !r.success);
    console.log('失败详情:');
    failures.slice(0, 5).forEach((f, i) => {
      console.log(`  ${i + 1}. ${f.error}`);
    });
    if (failures.length > 5) {
      console.log(`  ... 还有 ${failures.length - 5} 个失败`);
    }
    console.log('');
    process.exit(1);
  }

  process.exit(0);
}

function setupGracefulShutdown(scheduler) {
  let shuttingDown = false;

  const shutdown = (signal) => {
    if (shuttingDown) return;
    shuttingDown = true;

    logger.info(`\n收到 ${signal} 信号，正在优雅关闭...`);
    scheduler.stop();

    setTimeout(() => {
      logger.info('已退出');
      process.exit(0);
    }, 1000);
  };

  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));

  process.on('uncaughtException', (error) => {
    logger.error('未捕获的异常:', error.message);
    logger.error(error.stack);
    if (!shuttingDown) {
      scheduler.stop();
      process.exit(1);
    }
  });

  process.on('unhandledRejection', (reason, promise) => {
    logger.error('未处理的 Promise 拒绝:', reason);
  });
}

main().catch(error => {
  logger.error('程序启动失败:', error.message);
  logger.error(error.stack);
  process.exit(1);
});
