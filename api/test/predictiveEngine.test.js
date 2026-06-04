const {
    describe, it, beforeAll, afterAll, assert,
    createMockDB, createMockAlertEngine, createMockDataBuffer,
    generateSensorData, createTestSensor, createTestZone
} = require('./testUtils');

const { ExponentialSmoothing, PredictiveEngine, PREDICTIVE_CONFIG } = require('../services/predictiveEngine');

describe('1. 指数平滑法预测准确度测试', () => {
    let smoothing;

    beforeAll(() => {
        smoothing = new ExponentialSmoothing(0.3, 0.1);
    });

    it('1.1 线性增长数据的预测误差应在可接受范围内（<5%）', () => {
        const linearData = [];
        for (let i = 0; i < 100; i++) {
            linearData.push(0.1 + i * 0.005);
        }

        smoothing.initialize(linearData.slice(0, 80));

        for (let i = 80; i < 100; i++) {
            smoothing.update(linearData[i]);
        }

        const predictions = smoothing.forecast(10);
        const actualNext10 = [];
        for (let i = 100; i < 110; i++) {
            actualNext10.push(0.1 + i * 0.005);
        }

        let totalError = 0;
        for (let i = 0; i < predictions.length; i++) {
            const error = Math.abs((predictions[i] - actualNext10[i]) / actualNext10[i]);
            totalError += error;
        }
        const avgError = totalError / predictions.length;

        assert(avgError < 0.05, `平均预测误差 ${(avgError * 100).toFixed(2)}% 超过5%阈值`);
    });

    it('1.2 稳定数据的预测应接近实际值', () => {
        const stableData = [];
        for (let i = 0; i < 100; i++) {
            stableData.push(0.5 + (Math.random() - 0.5) * 0.02);
        }

        smoothing.initialize(stableData);
        const predictions = smoothing.forecast(5);

        for (const pred of predictions) {
            assert(pred > 0.45 && pred < 0.55, `稳定数据预测值 ${pred.toFixed(4)} 超出预期范围`);
        }
    });

    it('1.3 初始化数据不足时应返回null', () => {
        const shortSmoothing = new ExponentialSmoothing(0.3, 0.1);
        shortSmoothing.initialize([0.5]);
        assert.strictEqual(shortSmoothing.level, null);
        assert.strictEqual(shortSmoothing.trend, null);
    });

    it('1.4 首次update应正确初始化level和trend', () => {
        const newSmoothing = new ExponentialSmoothing(0.3, 0.1);
        newSmoothing.update(0.5);
        assert.strictEqual(newSmoothing.level, 0.5);
        assert.strictEqual(newSmoothing.trend, 0);
    });

    it('1.5 趋势方向应与数据变化方向一致', () => {
        const increasingData = [];
        for (let i = 0; i < 50; i++) {
            increasingData.push(0.1 + i * 0.01);
        }

        smoothing.initialize(increasingData);
        assert(smoothing.trend > 0, `上升趋势数据的trend应为正，实际为 ${smoothing.trend}`);

        const decreasingData = [];
        for (let i = 0; i < 50; i++) {
            decreasingData.push(1.0 - i * 0.01);
        }

        smoothing.initialize(decreasingData);
        assert(smoothing.trend < 0, `下降趋势数据的trend应为负，实际为 ${smoothing.trend}`);
    });
});

describe('2. 预测阈值触发预警及时性测试', () => {
    let db, alertEngine, dataBuffer, engine;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();

        const zone = createTestZone('zone-test', '测试坝段', 'dam');
        db.insertZone(zone);

        const sensor = createTestSensor('sensor-pred-1', 'seepage', 'zone-test', {
            warning: 0.6,
            critical: 0.75,
            emergency: 0.85
        });
        db.insertSensor(sensor);
    });

    afterAll(() => {
        if (engine) engine.destroy();
    });

    it('2.1 预测值15分钟内超过阈值应触发预警', () => {
        db.sensorData = [];

        const now = Date.now();
        const baseValue = 0.4;
        const trend = 0.35;

        for (let i = 480; i >= 0; i--) {
            const timestamp = new Date(now - i * 3 * 60 * 1000).toISOString();
            const progress = 1 - (i / 480);
            const value = baseValue + progress * trend;
            db.insertSensorData({ sensor_id: 'sensor-pred-1', timestamp, value });
        }

        engine = new PredictiveEngine(db, alertEngine, dataBuffer);

        const result = engine.evaluatePrediction('sensor-pred-1', 'zone-test', 'seepage', {
            warning: 0.6,
            critical: 0.75,
            emergency: 0.85
        });

        assert(result !== null, '预测值将超阈值但未触发预警');
        assert.strictEqual(result.action, 'prediction_created');
        assert(result.prediction.level, '预测告警应有级别');
        assert(result.prediction.predicted_at, '预测告警应有预测时间');

        const predictedTime = new Date(result.prediction.predicted_at).getTime();
        const timeToThreshold = predictedTime - now;
        const minutesTo = timeToThreshold / 60000;

        assert(minutesTo <= PREDICTIVE_CONFIG.warningLeadMinutes,
            `预测预警时间 ${minutesTo.toFixed(1)}分钟 超过提前预警阈值 ${PREDICTIVE_CONFIG.warningLeadMinutes}分钟`);
    });

    it('2.2 非渗流传感器应返回null', () => {
        const result = engine.predictSensorTrend('sensor-pred-1', 'displacement');
        assert.strictEqual(result, null, '非渗流传感器不应进行预测');
    });

    it('2.3 相同级别重复预测不应重复创建告警', () => {
        const initialCount = alertEngine.transitions.length;

        const result = engine.evaluatePrediction('sensor-pred-1', 'zone-test', 'seepage', {
            warning: 0.6,
            critical: 0.75,
            emergency: 0.85
        });

        assert.strictEqual(result, null, '相同级别预测重复触发了告警');
        assert.strictEqual(alertEngine.transitions.length, initialCount,
            '相同级别预测不应产生新的状态转换记录');
    });

    it('2.4 预测值15分钟后才超过阈值不应触发预警', () => {
        db.sensorData = [];
        engine.activePredictions.clear();

        const now = Date.now();
        const baseValue = 0.4;
        const slowTrend = 0.15;

        for (let i = 480; i >= 0; i--) {
            const timestamp = new Date(now - i * 3 * 60 * 1000).toISOString();
            const progress = 1 - (i / 480);
            const value = baseValue + progress * slowTrend;
            db.insertSensorData({ sensor_id: 'sensor-pred-1', timestamp, value });
        }

        const result = engine.evaluatePrediction('sensor-pred-1', 'zone-test', 'seepage', {
            warning: 0.6,
            critical: 0.75,
            emergency: 0.85
        });

        assert.strictEqual(result, null, '预测值15分钟后才超阈值但错误触发了预警');
    });
});

describe('3. 预测数据不足24小时时的降级处理测试', () => {
    let db, alertEngine, dataBuffer, engine;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();

        const zone = createTestZone('zone-test-2', '测试坝段2', 'dam');
        db.insertZone(zone);

        const sensor = createTestSensor('sensor-pred-2', 'seepage', 'zone-test-2');
        db.insertSensor(sensor);
    });

    afterAll(() => {
        if (engine) engine.destroy();
    });

    it('3.1 数据点少于minDataPoints应返回null', () => {
        db.sensorData = [];

        const now = Date.now();
        for (let i = 10; i >= 0; i--) {
            const timestamp = new Date(now - i * 3 * 60 * 1000).toISOString();
            db.insertSensorData({ sensor_id: 'sensor-pred-2', timestamp, value: 0.5 });
        }

        engine = new PredictiveEngine(db, alertEngine, dataBuffer);
        const result = engine.predictSensorTrend('sensor-pred-2', 'seepage');

        assert.strictEqual(result, null, `数据点不足${PREDICTIVE_CONFIG.minDataPoints}但未返回null`);
    });

    it('3.2 数据点刚好等于minDataPoints应正常工作', () => {
        db.sensorData = [];

        const now = Date.now();
        const minPoints = PREDICTIVE_CONFIG.minDataPoints;
        for (let i = minPoints - 1; i >= 0; i--) {
            const timestamp = new Date(now - i * 3 * 60 * 1000).toISOString();
            const value = 0.1 + (1 - i / minPoints) * 0.3;
            db.insertSensorData({ sensor_id: 'sensor-pred-2', timestamp, value });
        }

        const result = engine.predictSensorTrend('sensor-pred-2', 'seepage');
        assert(result !== null, `数据点等于${minPoints}但未正常返回预测结果`);
        assert(result.predictions.length > 0, '预测结果应包含预测值数组');
        assert(result.values.length === minPoints, '历史值数量应等于数据点数量');
    });

    it('3.3 历史数据仅1小时应降级处理', () => {
        db.sensorData = [];
        engine.activePredictions.clear();

        const now = Date.now();
        for (let i = 20; i >= 0; i--) {
            const timestamp = new Date(now - i * 3 * 60 * 1000).toISOString();
            const value = 0.5 + (1 - i / 20) * 0.1;
            db.insertSensorData({ sensor_id: 'sensor-pred-2', timestamp, value });
        }

        const history = engine.getSensorHistory('sensor-pred-2');
        assert(history.length >= PREDICTIVE_CONFIG.minDataPoints,
            `1小时数据应至少返回${PREDICTIVE_CONFIG.minDataPoints}个点`);
        assert(history.length < 100, '1小时数据不应返回过多点');
    });

    it('3.4 无历史数据时应优雅降级', () => {
        db.sensorData = [];
        const result = engine.predictSensorTrend('sensor-pred-2', 'seepage');
        assert.strictEqual(result, null, '无历史数据但未返回null');
    });
});

describe('4. 预测告警创建与广播测试', () => {
    let db, alertEngine, dataBuffer, engine;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();

        const zone = createTestZone('zone-test-3', '测试坝段3', 'dam');
        db.insertZone(zone);

        const sensor = createTestSensor('sensor-pred-3', 'seepage', 'zone-test-3');
        db.insertSensor(sensor);
    });

    afterAll(() => {
        if (engine) engine.destroy();
    });

    it('4.1 预测告警应正确设置is_prediction标记', () => {
        db.sensorData = [];
        const now = Date.now();

        for (let i = 480; i >= 0; i--) {
            const timestamp = new Date(now - i * 3 * 60 * 1000).toISOString();
            const progress = 1 - (i / 480);
            const value = 0.3 + progress * 0.4;
            db.insertSensorData({ sensor_id: 'sensor-pred-3', timestamp, value });
        }

        engine = new PredictiveEngine(db, alertEngine, dataBuffer);
        const result = engine.evaluatePrediction('sensor-pred-3', 'zone-test-3', 'seepage', {
            warning: 0.6,
            critical: 0.75,
            emergency: 0.85
        });

        assert(result !== null, '应触发预测预警');

        const createdAlert = db.getAlert(result.prediction.id.replace('pred-', 'alert-pred-'));
        if (createdAlert) {
            assert(createdAlert.is_prediction === true, '预测告警is_prediction标记应为true');
            assert(createdAlert.prediction_id, '预测告警应关联prediction_id');
            assert(createdAlert.predicted_at, '预测告警应包含predicted_at');
        }
    });

    it('4.2 预测告警应正确广播到前端', () => {
        const broadcastCount = dataBuffer.broadcastQueue.length;
        assert(broadcastCount > 0, '预测告警应产生广播消息');

        const lastBroadcast = JSON.parse(dataBuffer.broadcastQueue[broadcastCount - 1]);
        assert(lastBroadcast.is_prediction === true, '广播消息应包含is_prediction标记');
        assert(lastBroadcast.prediction, '广播消息应包含prediction数据');
    });

    it('4.3 清除预测应正确解析相关告警', () => {
        const initialActiveCount = alertEngine.activeAlerts.size;

        engine.clearPrediction('sensor-pred-3');
        assert.strictEqual(engine.activePredictions.size, 0, '清除预测后activePredictions应为空');

        for (const [, alert] of alertEngine.activeAlerts) {
            if (alert.is_prediction && alert.sensor_id === 'sensor-pred-3') {
                assert.strictEqual(alert.state, 'resolved', '相关预测告警状态应为resolved');
            }
        }
    });
});
