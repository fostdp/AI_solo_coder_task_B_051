const {
    describe, it, beforeAll, afterAll, assert,
    createMockDB, createMockAlertEngine, createMockDataBuffer,
    createTestSensor, createTestZone, createTestAlert
} = require('./testUtils');

const { ZoneRiskAnalyzer, ZONE_RISK_CONFIG } = require('../services/zoneRiskAnalyzer');

describe('5. 区域关联分析40%阈值触发条件测试', () => {
    let db, alertEngine, dataBuffer, analyzer;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();

        const zone = createTestZone('zone-risk-1', '测试坝段A', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 10; i++) {
            const sensor = createTestSensor(`sensor-zone-${i}`, 'seepage', 'zone-risk-1');
            db.insertSensor(sensor);
        }
    });

    afterAll(() => {
        if (analyzer) analyzer.destroy();
    });

    it('5.1 40%传感器告警且>=3个时应触发坝段级风险（10个传感器4个告警）', () => {
        alertEngine.activeAlerts.clear();
        analyzer = new ZoneRiskAnalyzer(db, alertEngine, dataBuffer);

        for (let i = 1; i <= 4; i++) {
            const alert = createTestAlert(`alert-${i}`, `sensor-zone-${i}`, 'zone-risk-1', 'seepage', 'warning');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-risk-1');

        assert(result !== null, '40%传感器告警但未触发坝段级风险');
        assert.strictEqual(result.action, 'risk_created');
        assert(result.risk.active === true, '风险状态应为active');
        assert.strictEqual(result.risk.alerted_count, 4);
        assert.strictEqual(result.risk.total_count, 10);
        assert.strictEqual(result.risk.percentage, 40);
        assert(result.risk.highlight_color === ZONE_RISK_CONFIG.highlightColor, '高亮颜色配置错误');
    });

    it('5.2 39%传感器告警不应触发坝段级风险（10个传感器3个告警）', () => {
        alertEngine.activeAlerts.clear();
        analyzer.zoneRisks.clear();

        for (let i = 1; i <= 3; i++) {
            const alert = createTestAlert(`alert-${i}`, `sensor-zone-${i}`, 'zone-risk-1', 'seepage', 'warning');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-risk-1');

        assert.strictEqual(result, null, '39%传感器告警但错误触发了坝段级风险');
    });

    it('5.3 刚好40%但不足3个传感器不应触发（5个传感器2个告警）', () => {
        const zone2 = createTestZone('zone-risk-small', '小坝段', 'dam');
        db.insertZone(zone2);

        for (let i = 1; i <= 5; i++) {
            const sensor = createTestSensor(`sensor-small-${i}`, 'seepage', 'zone-risk-small');
            db.insertSensor(sensor);
        }

        alertEngine.activeAlerts.clear();
        for (let i = 1; i <= 2; i++) {
            const alert = createTestAlert(`alert-small-${i}`, `sensor-small-${i}`, 'zone-risk-small', 'seepage', 'warning');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-risk-small');

        assert.strictEqual(result, null, '40%但不足3个传感器告警但错误触发了风险');
        assert(result === null || result.action !== 'risk_created', '不应创建风险');
    });

    it('5.4 超过40%且>=3个传感器应触发（10个传感器5个告警=50%）', () => {
        alertEngine.activeAlerts.clear();
        analyzer.zoneRisks.clear();

        for (let i = 1; i <= 5; i++) {
            const alert = createTestAlert(`alert-${i}`, `sensor-zone-${i}`, 'zone-risk-1', 'seepage', 'critical');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-risk-1');

        assert(result !== null, '50%传感器告警但未触发坝段级风险');
        assert.strictEqual(result.risk.percentage, 50);
        assert.strictEqual(result.risk.alerted_count, 5);
    });

    it('5.5 预测告警不计入区域风险统计', () => {
        alertEngine.activeAlerts.clear();
        analyzer.zoneRisks.clear();

        for (let i = 1; i <= 4; i++) {
            const isPrediction = i <= 2;
            const alert = createTestAlert(`alert-${i}`, `sensor-zone-${i}`, 'zone-risk-1', 'seepage', 'warning', isPrediction);
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-risk-1');

        assert.strictEqual(result, null, '预测告警计入统计导致错误触发风险');
    });

    it('5.6 同一传感器多个告警只计一次', () => {
        alertEngine.activeAlerts.clear();
        analyzer.zoneRisks.clear();

        for (let i = 1; i <= 4; i++) {
            for (let j = 1; j <= 2; j++) {
                const alert = createTestAlert(`alert-${i}-${j}`, `sensor-zone-${i}`, 'zone-risk-1', 'seepage', 'warning');
                alertEngine.activeAlerts.set(alert.id, alert);
            }
        }

        const result = analyzer.analyzeZoneRisk('zone-risk-1');

        assert(result !== null, '同一传感器多次告警未正确去重');
        assert.strictEqual(result.risk.alerted_count, 4, '去重后的告警传感器数量应为4');
    });
});

describe('6. 关联告警消息推送格式测试', () => {
    let db, alertEngine, dataBuffer, analyzer;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();

        const zone = createTestZone('zone-risk-2', '测试坝段B', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 10; i++) {
            const sensor = createTestSensor(`sensor-zone2-${i}`, 'seepage', 'zone-risk-2');
            db.insertSensor(sensor);
        }

        analyzer = new ZoneRiskAnalyzer(db, alertEngine, dataBuffer);
    });

    afterAll(() => {
        if (analyzer) analyzer.destroy();
    });

    it('6.1 区域风险WebSocket消息格式应正确', () => {
        alertEngine.activeAlerts.clear();
        dataBuffer.broadcastQueue = [];

        for (let i = 1; i <= 4; i++) {
            const alert = createTestAlert(`alert-format-${i}`, `sensor-zone2-${i}`, 'zone-risk-2', 'seepage', 'critical');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-risk-2');
        assert(result !== null, '应触发区域风险');

        const broadcastCount = dataBuffer.broadcastQueue.length;
        assert(broadcastCount > 0, '应产生广播消息');

        const message = JSON.parse(dataBuffer.broadcastQueue[broadcastCount - 1]);
        assert.strictEqual(message.type, 'zone_risk_update', '消息类型应为zone_risk_update');
        assert(message.data.action === 'created' || message.data.action === 'updated', '消息action应为created或updated');
        assert(message.data.risk, '消息应包含risk数据');
        assert(message.data.risk.id, '风险应有id');
        assert(message.data.risk.zone_id === 'zone-risk-2', 'zone_id应正确');
        assert(message.data.risk.zone_name === '测试坝段B', 'zone_name应正确');
    });

    it('6.2 区域风险告警消息应包含正确的字段', () => {
        const alert = db.queryAlerts({ zone_id: 'zone-risk-2' }).find(a => a.is_zone_risk);
        assert(alert, '应创建区域风险告警');
        assert(alert.is_zone_risk === true, '告警应有is_zone_risk标记');
        assert(alert.type === 'zone_risk', '告警类型应为zone_risk');
        assert(alert.level === ZONE_RISK_CONFIG.riskLevel, '告警级别配置错误');
        assert(alert.message.includes('测试坝段B'), '告警消息应包含坝段名称');
        assert(alert.message.includes('4/10'), '告警消息应包含告警数量/总数');
        assert(alert.message.includes('40.0%'), '告警消息应包含百分比');
    });

    it('6.3 区域风险更新时应推送updated消息', () => {
        dataBuffer.broadcastQueue = [];

        for (let i = 5; i <= 6; i++) {
            const alert = createTestAlert(`alert-update-${i}`, `sensor-zone2-${i}`, 'zone-risk-2', 'seepage', 'critical');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-risk-2');
        assert(result !== null, '应更新区域风险');
        assert.strictEqual(result.action, 'risk_updated');

        const broadcastCount = dataBuffer.broadcastQueue.length;
        const message = JSON.parse(dataBuffer.broadcastQueue[broadcastCount - 1]);
        assert.strictEqual(message.data.action, 'updated', '更新时消息action应为updated');
        assert.strictEqual(message.data.risk.alerted_count, 6, '告警数量应更新为6');
        assert.strictEqual(message.data.risk.percentage, 60, '百分比应更新为60');
    });
});

describe('7. 阈值边界条件测试', () => {
    let db, alertEngine, dataBuffer, analyzer;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();
        analyzer = new ZoneRiskAnalyzer(db, alertEngine, dataBuffer);
    });

    afterAll(() => {
        if (analyzer) analyzer.destroy();
    });

    it('7.1 刚好40%且刚好3个传感器时应触发（10个传感器4个告警=40%）', () => {
        const zone = createTestZone('zone-boundary-1', '边界测试坝段1', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 10; i++) {
            const sensor = createTestSensor(`sensor-boundary1-${i}`, 'seepage', 'zone-boundary-1');
            db.insertSensor(sensor);
        }

        alertEngine.activeAlerts.clear();
        for (let i = 1; i <= 4; i++) {
            const alert = createTestAlert(`alert-boundary1-${i}`, `sensor-boundary1-${i}`, 'zone-boundary-1', 'seepage', 'warning');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-boundary-1');
        assert(result !== null, '刚好40%且>=3个传感器时应触发风险');
        assert.strictEqual(result.action, 'risk_created');
    });

    it('7.2 刚好40%但不足3个传感器时不应触发（5个传感器2个告警=40%）', () => {
        const zone = createTestZone('zone-boundary-2', '边界测试坝段2', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 5; i++) {
            const sensor = createTestSensor(`sensor-boundary2-${i}`, 'seepage', 'zone-boundary-2');
            db.insertSensor(sensor);
        }

        alertEngine.activeAlerts.clear();
        for (let i = 1; i <= 2; i++) {
            const alert = createTestAlert(`alert-boundary2-${i}`, `sensor-boundary2-${i}`, 'zone-boundary-2', 'seepage', 'warning');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-boundary-2');
        assert.strictEqual(result, null, '40%但不足3个传感器时不应触发风险');
    });

    it('7.3 刚好39.9%时不应触发（8个传感器3个告警=37.5%）', () => {
        const zone = createTestZone('zone-boundary-3', '边界测试坝段3', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 8; i++) {
            const sensor = createTestSensor(`sensor-boundary3-${i}`, 'seepage', 'zone-boundary-3');
            db.insertSensor(sensor);
        }

        alertEngine.activeAlerts.clear();
        for (let i = 1; i <= 3; i++) {
            const alert = createTestAlert(`alert-boundary3-${i}`, `sensor-boundary3-${i}`, 'zone-boundary-3', 'seepage', 'warning');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-boundary-3');
        assert.strictEqual(result, null, '37.5%<40%时不应触发风险');
    });

    it('7.4 刚好超过40%且刚好3个传感器应触发（7个传感器3个告警=42.86%）', () => {
        const zone = createTestZone('zone-boundary-4', '边界测试坝段4', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 7; i++) {
            const sensor = createTestSensor(`sensor-boundary4-${i}`, 'seepage', 'zone-boundary-4');
            db.insertSensor(sensor);
        }

        alertEngine.activeAlerts.clear();
        for (let i = 1; i <= 3; i++) {
            const alert = createTestAlert(`alert-boundary4-${i}`, `sensor-boundary4-${i}`, 'zone-boundary-4', 'seepage', 'warning');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-boundary-4');
        assert(result !== null, '42.86%>=40%且>=3个传感器时应触发风险');
        assert(result.risk.percentage > 40, '百分比应大于40');
    });

    it('7.5 传感器总数为0时应返回null', () => {
        const zone = createTestZone('zone-empty', '空坝段', 'dam');
        db.insertZone(zone);

        const result = analyzer.analyzeZoneRisk('zone-empty');
        assert.strictEqual(result, null, '空坝段分析应返回null');
    });

    it('7.6 不存在的zoneId应返回null', () => {
        const result = analyzer.analyzeZoneRisk('zone-not-exist');
        assert.strictEqual(result, null, '不存在的zoneId应返回null');
    });
});

describe('8. 区域风险生命周期测试', () => {
    let db, alertEngine, dataBuffer, analyzer;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();

        const zone = createTestZone('zone-lifecycle', '生命周期测试坝段', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 10; i++) {
            const sensor = createTestSensor(`sensor-lifecycle-${i}`, 'seepage', 'zone-lifecycle');
            db.insertSensor(sensor);
        }

        analyzer = new ZoneRiskAnalyzer(db, alertEngine, dataBuffer);
    });

    afterAll(() => {
        if (analyzer) analyzer.destroy();
    });

    it('8.1 风险创建后告警数量下降到阈值以下应清除风险', () => {
        alertEngine.activeAlerts.clear();
        dataBuffer.broadcastQueue = [];

        for (let i = 1; i <= 5; i++) {
            const alert = createTestAlert(`alert-lifecycle-${i}`, `sensor-lifecycle-${i}`, 'zone-lifecycle', 'seepage', 'critical');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        let result = analyzer.analyzeZoneRisk('zone-lifecycle');
        assert(result !== null, '应创建风险');
        assert(analyzer.zoneRisks.has('zone-lifecycle'), '风险应在zoneRisks中');

        for (let i = 3; i <= 5; i++) {
            const alert = alertEngine.activeAlerts.get(`alert-lifecycle-${i}`);
            if (alert) {
                alert.state = 'resolved';
                alertEngine.activeAlerts.set(alert.id, alert);
            }
        }

        result = analyzer.analyzeZoneRisk('zone-lifecycle');
        assert(result !== null, '风险清除应返回结果');
        assert.strictEqual(result.action, 'risk_cleared');
        assert(result.risk.active === false, '风险状态应为false');
        assert(result.risk.ended_at, '风险应有结束时间');
    });

    it('8.2 清除风险时应解析相关区域风险告警', () => {
        const zoneAlert = Array.from(alertEngine.activeAlerts.values()).find(a => a.is_zone_risk && a.zone_id === 'zone-lifecycle');
        if (zoneAlert) {
            assert.strictEqual(zoneAlert.state, 'resolved', '区域风险告警状态应为resolved');
            assert(zoneAlert.resolved_at, '区域风险告警应有resolved_at');
        }
    });

    it('8.3 清除风险时应推送cleared消息', () => {
        const broadcastCount = dataBuffer.broadcastQueue.length;
        assert(broadcastCount > 0, '清除风险应产生广播消息');

        const lastMessage = JSON.parse(dataBuffer.broadcastQueue[broadcastCount - 1]);
        assert.strictEqual(lastMessage.data.action, 'cleared', '清除风险消息action应为cleared');
        assert(lastMessage.data.risk.active === false, '消息中风险状态应为false');
    });

    it('8.4 已清除的风险重新满足条件应重新创建', () => {
        alertEngine.activeAlerts.clear();
        dataBuffer.broadcastQueue = [];

        for (let i = 1; i <= 4; i++) {
            const alert = createTestAlert(`alert-recreate-${i}`, `sensor-lifecycle-${i}`, 'zone-lifecycle', 'seepage', 'warning');
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result = analyzer.analyzeZoneRisk('zone-lifecycle');
        assert(result !== null, '应重新创建风险');
        assert.strictEqual(result.action, 'risk_created');
        assert(result.risk.active === true, '风险状态应为true');
    });

    it('8.5 destroy应清除所有定时器和数据', () => {
        assert(analyzer.checkTimer !== null, '应有checkTimer');
        analyzer.destroy();
        assert(analyzer.checkTimer === null, 'destroy后checkTimer应为null');
        assert.strictEqual(analyzer.zoneRisks.size, 0, 'destroy后zoneRisks应为空');
    });
});
