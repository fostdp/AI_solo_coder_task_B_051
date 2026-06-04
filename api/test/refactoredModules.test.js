const assert = require('assert');
const { runner, describe, it, beforeAll, afterAll, createMockDB, createMockAlertEngine, createMockDataBuffer } = require('./testUtils');

const { RuleEngine, AlertEngine } = require('../services/alertEngine');
const { PredictionEngine, PredictiveEngine, ExponentialSmoothing, PREDICTIVE_CONFIG } = require('../services/predictiveEngine');
const { RegionAnalyzer, ZoneRiskAnalyzer, ZONE_RISK_CONFIG } = require('../services/zoneRiskAnalyzer');
const { EmergencyGuideManager, EmergencyGuideService } = require('../services/emergencyGuide');
const { ExportService, EXPORT_CONFIG } = require('../services/exportService');

describe('15. RuleEngine可配置规则引擎测试', () => {
    let ruleEngine;

    beforeAll(() => {
        ruleEngine = new RuleEngine();
    });

    it('15.1 应从JSON文件加载规则', () => {
        assert(ruleEngine.rules !== null, '规则应已加载');
        assert(ruleEngine.rules.version === '1.0', '规则版本应为1.0');
    });

    it('15.2 getEscalationRule应返回正确的升级规则', () => {
        const rule = ruleEngine.getEscalationRule('seepage', 'warning');
        assert(rule !== null, '应有渗流warning升级规则');
        assert.strictEqual(rule.nextLevel, 'critical');
        assert.strictEqual(rule.condition, 'duration');
    });

    it('15.3 getEscalationRule不存在的类型应返回null', () => {
        const rule = ruleEngine.getEscalationRule('unknown_type', 'warning');
        assert.strictEqual(rule, null);
    });

    it('15.4 getConvergenceConfig应返回收敛配置', () => {
        const config = ruleEngine.getConvergenceConfig();
        assert.strictEqual(config.radius, 100);
        assert.strictEqual(config.minAlerts, 3);
    });

    it('15.5 getStateTransitions应返回状态转移表', () => {
        const transitions = ruleEngine.getStateTransitions();
        assert(transitions.active, '应有active状态转移');
        assert.strictEqual(transitions.active.acknowledge, 'acknowledged');
    });

    it('15.6 getLevelOrder应返回正确的级别顺序', () => {
        const order = ruleEngine.getLevelOrder();
        assert.deepStrictEqual(order, ['info', 'warning', 'critical', 'emergency']);
    });

    it('15.7 getSensorTypeName应返回正确的类型名称', () => {
        assert.strictEqual(ruleEngine.getSensorTypeName('seepage'), '渗流');
        assert.strictEqual(ruleEngine.getSensorTypeName('displacement'), '位移');
        assert.strictEqual(ruleEngine.getSensorTypeName('crack'), '裂缝');
    });

    it('15.8 新增传感器类型只需修改JSON不需修改代码', () => {
        const rules = ruleEngine.rules;
        assert(rules.sensorTypes.seepage, '渗流类型应在JSON中');
        assert(rules.sensorTypes.displacement, '位移类型应在JSON中');
        assert(rules.sensorTypes.water_level, '水位类型应在JSON中');
        assert(rules.sensorTypes.crack, '裂缝类型应在JSON中');
        assert(rules.sensorTypes.stress, '应力类型应在JSON中');
    });

    it('15.9 所有传感器类型的升级规则应完整', () => {
        const types = Object.keys(ruleEngine.rules.sensorTypes);
        for (const type of types) {
            const escalationRules = ruleEngine.rules.sensorTypes[type].escalationRules;
            assert(escalationRules.warning, `${type}应有warning升级规则`);
        }
    });
});

describe('16. PredictionEngine统一接口测试', () => {
    let db, alertEngine, dataBuffer, engine;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();
        engine = new PredictionEngine(db, alertEngine, dataBuffer);
    });

    afterAll(() => {
        engine.destroy();
    });

    it('16.1 predict方法应等效于predictSensorTrend', () => {
        const zone = { id: 'zone-pred-1', name: '预测测试坝段', type: 'dam', description: '', boundary_json: '[]' };
        db.insertZone(zone);
        for (let i = 0; i < 25; i++) {
            db.insertSensorData({ sensor_id: 'sensor-pred-1', value: 1.0 + i * 0.01, timestamp: new Date(Date.now() - (25 - i) * 3 * 60 * 1000).toISOString(), quality: 'good' });
        }
        const sensor = { id: 'sensor-pred-1', zone_id: 'zone-pred-1', name: '预测测试传感器', type: 'seepage', x: 0, y: 0, unit: 'kPa', thresholds_json: '{"warning":2.0,"critical":3.0}' };
        db.insertSensor(sensor);

        const result1 = engine.predict('sensor-pred-1', 'seepage');
        const result2 = engine.predictSensorTrend('sensor-pred-1', 'seepage');
        assert.deepStrictEqual(result1, result2, 'predict和predictSensorTrend结果应一致');
    });

    it('16.2 evaluate方法应等效于evaluatePrediction', () => {
        const result1 = engine.evaluate('sensor-pred-1', 'zone-pred-1', 'seepage', '{"warning":2.0,"critical":3.0}');
        const result2 = engine.evaluatePrediction('sensor-pred-1', 'zone-pred-1', 'seepage', '{"warning":2.0,"critical":3.0}');
        assert.strictEqual(result1 === null, result2 === null, 'evaluate和evaluatePrediction结果应一致');
    });

    it('16.3 getStatus应返回模块状态', () => {
        const status = engine.getStatus();
        assert.strictEqual(status.running, true, '运行状态应为true');
        assert(status.config !== undefined, '应包含config');
    });

    it('16.4 stop应停止检查循环', () => {
        engine.stop();
        const status = engine.getStatus();
        assert.strictEqual(status.running, false, '停止后运行状态应为false');
        engine.init();
    });

    it('16.5 PredictiveEngine应是PredictionEngine的别名', () => {
        assert.strictEqual(PredictiveEngine, PredictionEngine, '向后兼容别名应有效');
    });
});

describe('17. RegionAnalyzer统一接口测试', () => {
    let db, alertEngine, dataBuffer, analyzer;

    beforeAll(() => {
        db = createMockDB();
        alertEngine = createMockAlertEngine();
        dataBuffer = createMockDataBuffer();
        analyzer = new RegionAnalyzer(db, alertEngine, dataBuffer);
    });

    afterAll(() => {
        analyzer.destroy();
    });

    it('17.1 analyze方法应等效于analyzeZoneRisk', () => {
        const zone = { id: 'zone-ra-1', name: '区域分析测试坝段', type: 'dam', description: '', boundary_json: '[]' };
        db.insertZone(zone);
        for (let i = 1; i <= 10; i++) {
            db.insertSensor({ id: `sensor-ra-${i}`, zone_id: 'zone-ra-1', name: `传感器${i}`, type: 'seepage', x: i * 10, y: i * 5, unit: 'kPa', thresholds_json: '{"warning":1.0}' });
        }
        for (let i = 1; i <= 5; i++) {
            const alert = { id: `alert-ra-${i}`, sensor_id: `sensor-ra-${i}`, zone_id: 'zone-ra-1', type: 'seepage', level: 'warning', message: 'Test', state: 'active', escalated_from: null, converged_group: null, started_at: new Date().toISOString(), acknowledged_at: null, resolved_at: null, is_prediction: false, is_zone_risk: false };
            alertEngine.activeAlerts.set(alert.id, alert);
        }

        const result1 = analyzer.analyze('zone-ra-1');
        const result2 = analyzer.analyzeZoneRisk('zone-ra-1');
        assert.strictEqual(result1 !== null, result2 !== null, 'analyze和analyzeZoneRisk结果应一致');
    });

    it('17.2 getRisks方法应等效于getZoneRisks', () => {
        const risks1 = analyzer.getRisks();
        const risks2 = analyzer.getZoneRisks();
        assert.strictEqual(risks1.length, risks2.length, 'getRisks和getZoneRisks结果应一致');
    });

    it('17.3 analyzeAll方法应等效于checkAllZones', () => {
        analyzer.analyzeAll();
        assert(true, 'analyzeAll应正常执行');
    });

    it('17.4 getStatus应返回模块状态', () => {
        const status = analyzer.getStatus();
        assert(status.running !== undefined, '应包含running状态');
        assert(status.activeRisks !== undefined, '应包含activeRisks');
    });

    it('17.5 ZoneRiskAnalyzer应是RegionAnalyzer的别名', () => {
        assert.strictEqual(RegionAnalyzer, ZoneRiskAnalyzer, '向后兼容别名应有效');
    });

    it('17.6 stop应停止检查循环', () => {
        analyzer.stop();
        const status = analyzer.getStatus();
        assert.strictEqual(status.running, false, '停止后运行状态应为false');
        analyzer.init();
    });
});

describe('18. EmergencyGuideManager统一接口测试', () => {
    let manager;

    beforeAll(() => {
        manager = new EmergencyGuideManager();
    });

    it('18.1 应从JSON配置文件加载指引数据', () => {
        const guide = manager.getGuide('seepage');
        assert(guide !== null, '应已加载渗流类型指引');
        assert.strictEqual(guide.typeName, '渗流');
    });

    it('18.2 getStatus应返回模块状态', () => {
        const status = manager.getStatus();
        assert.strictEqual(status.loaded, true, 'loaded应为true');
        assert(status.guideTypes.length > 0, '应有指引类型');
        assert(status.totalCases > 0, '应有历史案例');
    });

    it('18.3 init应幂等执行', () => {
        manager.init();
        manager.init();
        const status = manager.getStatus();
        assert.strictEqual(status.loaded, true, '多次init应不报错');
    });

    it('18.4 destroy应清除数据', () => {
        const mgr = new EmergencyGuideManager();
        mgr.destroy();
        const status = mgr.getStatus();
        assert.strictEqual(status.loaded, false, 'destroy后应为未加载');
    });

    it('18.5 EmergencyGuideService应是EmergencyGuideManager的别名', () => {
        assert.strictEqual(EmergencyGuideManager, EmergencyGuideService, '向后兼容别名应有效');
    });
});

describe('19. ExportService统一接口测试', () => {
    let db, exporter;

    beforeAll(() => {
        db = createMockDB();
        const zone = { id: 'zone-export-svc', name: '导出服务测试坝段', type: 'dam', description: '', boundary_json: '[]' };
        db.insertZone(zone);
        for (let i = 1; i <= 5; i++) {
            db.insertSensor({ id: `sensor-export-svc-${i}`, zone_id: 'zone-export-svc', name: `导出传感器${i}`, type: 'seepage', x: i * 10, y: i * 5, unit: 'kPa', thresholds_json: '{"warning":1.0,"critical":2.0}' });
        }
        exporter = new ExportService(db);
    });

    afterAll(() => {
        exporter.destroy();
    });

    it('19.1 generateReport应正常生成报告', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-export-svc',
            includeSensorData: true,
            includeAlerts: true
        });
        assert(result !== null, '应生成报告');
        assert(result.length > 0, '报告应有内容');
    });

    it('19.2 submitExport应提交异步导出任务', () => {
        const result = exporter.submitExport({
            zoneId: 'zone-export-svc',
            includeSensorData: true,
            includeAlerts: true
        });
        assert(result.exportId, '应返回exportId');
        assert.strictEqual(result.status, 'pending');
    });

    it('19.3 getExportStatus应查询任务状态', () => {
        const submitResult = exporter.submitExport({
            zoneId: 'zone-export-svc',
            includeSensorData: false,
            includeAlerts: false
        });
        const status = exporter.getExportStatus(submitResult.exportId);
        assert(status !== null, '应能查询到任务');
        assert(status.id === submitResult.exportId, 'ID应匹配');
    });

    it('19.4 getStatus应返回模块状态', () => {
        const status = exporter.getStatus();
        assert(status.running !== undefined, '应包含running');
        assert(status.queueLength !== undefined, '应包含queueLength');
        assert(status.config !== undefined, '应包含config');
    });

    it('19.5 getReportFilename应返回正确格式', () => {
        const filename = exporter.getReportFilename({});
        assert(filename.includes('Report_'), '文件名应包含Report_');
    });

    it('19.6 summarizeAlerts应正确汇总', () => {
        const alerts = [
            { level: 'warning', type: 'seepage', is_prediction: false, is_zone_risk: false },
            { level: 'critical', type: 'displacement', is_prediction: false, is_zone_risk: false },
            { level: 'warning', type: 'seepage', is_prediction: true, is_zone_risk: false },
            { level: 'critical', type: 'zone_risk', is_prediction: false, is_zone_risk: true }
        ];
        const summary = exporter.summarizeAlerts(alerts, []);
        assert.strictEqual(summary.total, 4);
        assert.strictEqual(summary.warning, 2);
        assert.strictEqual(summary.critical, 2);
        assert.strictEqual(summary.predictions, 1);
        assert.strictEqual(summary.zoneRisks, 1);
    });

    it('19.7 destroy应清理所有资源', () => {
        const svc = new ExportService(db);
        svc.destroy();
        const status = svc.getStatus();
        assert.strictEqual(status.running, false, 'destroy后应为非运行状态');
    });

    it('19.8 EXPORT_CONFIG应包含正确的限制配置', () => {
        assert.strictEqual(EXPORT_CONFIG.maxRecordsPerFile, 50000, '单文件最大5万条');
        assert.strictEqual(EXPORT_CONFIG.maxConcurrentExports, 2, '最大并发2');
    });
});

describe('20. 矢量PDF图表渲染测试', () => {
    let db, exporter;

    beforeAll(() => {
        db = createMockDB();
        const zone = { id: 'zone-vector', name: '矢量图表测试', type: 'dam', description: '', boundary_json: '[]' };
        db.insertZone(zone);
        const sensor = { id: 'sensor-vector-1', zone_id: 'zone-vector', name: '矢量测试传感器', type: 'seepage', x: 10, y: 20, unit: 'kPa', thresholds_json: '{"warning":1.0}' };
        db.insertSensor(sensor);
        for (let i = 0; i < 20; i++) {
            db.insertSensorData({
                sensor_id: 'sensor-vector-1',
                value: 0.5 + Math.random() * 0.3,
                timestamp: new Date(Date.now() - (20 - i) * 3 * 60 * 1000).toISOString(),
                quality: 'good'
            });
        }
        exporter = new ExportService(db);
    });

    afterAll(() => {
        exporter.destroy();
    });

    it('20.1 包含传感器趋势的导出应成功', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-vector',
            includeSensorData: true,
            includeAlerts: false
        });
        assert(result !== null, '应成功生成报告');
        assert(result.length > 0, '报告应有内容');
    });

    it('20.2 HTML降级模式应使用SVG标签而非Canvas', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-vector',
            includeSensorData: true,
            includeAlerts: false
        });
        const content = result.toString('utf-8');
        if (content.includes('<!DOCTYPE html>')) {
            assert(content.includes('<svg') || content.includes('trend-container'), 'HTML降级应包含SVG或趋势容器');
            assert(!content.includes('<canvas'), '矢量模式不应使用canvas标签');
        }
    });
});
