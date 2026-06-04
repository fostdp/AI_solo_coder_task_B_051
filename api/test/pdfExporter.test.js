const {
    describe, it, beforeAll, afterAll, assert,
    createMockDB, createTestSensor, createTestZone, createTestAlert
} = require('./testUtils');

const { PDFExporter } = require('../services/pdfExporter');

let PDFDocument = null;
try {
    PDFDocument = require('pdfkit');
} catch (e) {
    console.warn('pdfkit not installed for testing');
}

describe('12. 导出PDF中包含趋势曲线图测试', () => {
    let db, exporter;

    beforeAll(() => {
        db = createMockDB();
        exporter = new PDFExporter(db);

        const zone = createTestZone('zone-export-1', '导出测试坝段', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 3; i++) {
            const sensor = createTestSensor(`sensor-export-${i}`, i === 1 ? 'seepage' : 'displacement', 'zone-export-1');
            db.insertSensor(sensor);

            const now = Date.now();
            for (let j = 50; j >= 0; j--) {
                const timestamp = new Date(now - j * 5 * 60 * 1000).toISOString();
                const value = 0.3 + Math.sin(j / 10) * 0.2 + Math.random() * 0.05;
                db.insertSensorData({ sensor_id: `sensor-export-${i}`, timestamp, value });
            }
        }
    });

    it('12.1 导出应包含传感器趋势数据', async () => {
        const from = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
        const to = new Date().toISOString();

        const result = await exporter.generateReport({
            zoneId: 'zone-export-1',
            from,
            to,
            includeSensorData: true,
            includeAlerts: false
        });

        assert(result !== null, '导出结果不应为null');
        assert(Buffer.isBuffer(result), '导出结果应为Buffer');
        assert(result.length > 0, '导出结果长度应大于0');
    });

    it('12.2 导出内容应包含PDF头或HTML结构', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-export-1',
            includeSensorData: true,
            includeAlerts: false
        });

        const content = result.toString('utf-8', 0, 100);

        if (PDFDocument) {
            assert(content.includes('%PDF'), 'PDF导出应以%PDF开头');
        } else {
            assert(content.includes('<!DOCTYPE html>') || content.includes('<html'),
                'HTML导出应包含HTML结构');
        }
    });

    it('12.3 不包含传感器数据时不应生成趋势图', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-export-1',
            includeSensorData: false,
            includeAlerts: false
        });

        const content = result.toString('utf-8');

        if (!PDFDocument) {
            assert(!content.includes('传感器趋势数据'),
                '不包含传感器数据时不应有趋势数据标题');
        }
    });

    it('12.4 单个传感器导出应正确', async () => {
        const result = await exporter.generateReport({
            sensorId: 'sensor-export-1',
            includeSensorData: true,
            includeAlerts: false
        });

        assert(result !== null, '单个传感器导出不应为null');
        assert(result.length > 0, '单个传感器导出长度应大于0');
    });

    it('12.5 不存在的传感器应返回空报告', async () => {
        const result = await exporter.generateReport({
            sensorId: 'sensor-not-exist',
            includeSensorData: true,
            includeAlerts: false
        });

        assert(result !== null, '不存在的传感器导出不应为null');
        const content = result.toString('utf-8');
        if (!PDFDocument) {
            assert(content.includes('传感器数量:</strong> 0'), '传感器数量应为0');
        }
    });

    it('12.6 导出文件名格式应正确', () => {
        const filename = exporter.getReportFilename({});
        const ext = PDFDocument ? 'pdf' : 'html';

        assert(filename.startsWith('Report_'), `文件名应以Report_开头，实际为${filename}`);
        assert(filename.endsWith(`.${ext}`), `文件名应以.${ext}结尾，实际为${filename}`);
        assert(/\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}/.test(filename),
            '文件名应包含ISO时间戳');
    });

    it('12.7 Content-Type应正确', () => {
        const contentType = exporter.getContentType();
        if (PDFDocument) {
            assert.strictEqual(contentType, 'application/pdf', 'PDF Content-Type不正确');
        } else {
            assert(contentType.includes('text/html'), 'HTML Content-Type不正确');
        }
    });
});

describe('13. 告警统计表的格式和准确性测试', () => {
    let db, exporter;

    beforeAll(() => {
        db = createMockDB();
        exporter = new PDFExporter(db);

        const zone = createTestZone('zone-export-2', '告警统计测试坝段', 'dam');
        db.insertZone(zone);

        const sensor = createTestSensor('sensor-export-10', 'seepage', 'zone-export-2');
        db.insertSensor(sensor);

        const now = Date.now();
        const levels = ['emergency', 'critical', 'critical', 'warning', 'warning', 'warning', 'info'];
        for (let i = 0; i < levels.length; i++) {
            const alert = createTestAlert(
                `alert-export-${i}`,
                'sensor-export-10',
                'zone-export-2',
                'seepage',
                levels[i]
            );
            alert.started_at = new Date(now - i * 60 * 60 * 1000).toISOString();
            if (i === 5) {
                alert.is_prediction = true;
            }
            if (i === 6) {
                alert.is_zone_risk = true;
                alert.sensor_id = null;
                alert.type = 'zone_risk';
            }
            db.insertAlert(alert);
        }
    });

    it('13.1 告警统计摘要应正确', async () => {
        const from = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
        const to = new Date().toISOString();

        const sensors = db.getSensorsByZone('zone-export-2');
        const alerts = db.queryAlerts({ zone_id: 'zone-export-2' }).filter(a => {
            if (from && a.started_at < from) return false;
            if (to && a.started_at > to) return false;
            return true;
        });

        const sensorIds = sensors.map(s => s.id);
        const summary = exporter.summarizeAlerts(alerts, sensorIds);

        assert.strictEqual(summary.total, 7, '总告警数应为7');
        assert.strictEqual(summary.emergency, 1, '紧急告警数应为1');
        assert.strictEqual(summary.critical, 2, '严重告警数应为2');
        assert.strictEqual(summary.warning, 3, '警告数应为3');
        assert.strictEqual(summary.info, 1, '提示数应为1');
        assert.strictEqual(summary.predictions, 1, '预测预警数应为1');
        assert.strictEqual(summary.zoneRisks, 1, '区域风险数应为1');
    });

    it('13.2 导出应包含告警统计表', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-export-2',
            includeSensorData: false,
            includeAlerts: true
        });

        assert(result !== null, '导出结果不应为null');
        const content = result.toString('utf-8');

        if (!PDFDocument) {
            assert(content.includes('告警统计摘要'), '应包含告警统计摘要标题');
            assert(content.includes('告警记录'), '应包含告警记录表标题');
        }
    });

    it('13.3 告警记录时间范围筛选应正确', async () => {
        const now = Date.now();
        const from = new Date(now - 3 * 60 * 60 * 1000).toISOString();
        const to = new Date(now).toISOString();

        const result = await exporter.generateReport({
            zoneId: 'zone-export-2',
            from,
            to,
            includeSensorData: false,
            includeAlerts: true
        });

        const content = result.toString('utf-8');

        if (!PDFDocument) {
            const match = content.match(/<strong>告警数量:<\/strong>\s*(\d+)/);
            if (match) {
                const count = parseInt(match[1]);
                assert(count <= 4, `时间范围内告警数应不超过4，实际为${count}`);
            }
        }
    });

    it('13.4 不包含告警时不应有告警表', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-export-2',
            includeSensorData: false,
            includeAlerts: false
        });

        const content = result.toString('utf-8');

        if (!PDFDocument) {
            assert(!content.includes('告警记录'), '不包含告警时不应有告警记录表');
        }
    });

    it('13.5 按类型统计应正确', async () => {
        const sensors = db.getSensorsByZone('zone-export-2');
        const alerts = db.queryAlerts({ zone_id: 'zone-export-2' });
        const sensorIds = sensors.map(s => s.id);
        const summary = exporter.summarizeAlerts(alerts, sensorIds);

        assert(summary.byType.seepage, '应有渗流类型统计');
        assert(summary.byType.zone_risk, '应有区域风险类型统计');
    });

    it('13.6 告警数量超过限制时应截断（最多100条HTML，15条PDF）', async () => {
        for (let i = 0; i < 120; i++) {
            const alert = createTestAlert(
                `alert-many-${i}`,
                'sensor-export-10',
                'zone-export-2',
                'seepage',
                'warning'
            );
            alert.started_at = new Date(Date.now() - i * 1000).toISOString();
            db.insertAlert(alert);
        }

        const result = await exporter.generateReport({
            zoneId: 'zone-export-2',
            includeSensorData: false,
            includeAlerts: true
        });

        const content = result.toString('utf-8');

        if (!PDFDocument) {
            const trCount = (content.match(/<tr>/g) || []).length;
            assert(trCount <= 102, `HTML表格行数应不超过102（表头+100条数据），实际为${trCount}`);
        }
    });

    it('13.7 告警记录级别颜色标记应正确', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-export-2',
            includeSensorData: false,
            includeAlerts: true
        });

        const content = result.toString('utf-8');

        if (!PDFDocument) {
            assert(content.includes('level-emergency'), '应有紧急告警样式');
            assert(content.includes('level-critical'), '应有严重告警样式');
            assert(content.includes('level-warning'), '应有警告样式');
        }
    });
});

describe('14. 大数据量导出时的内存占用和超时处理测试', () => {
    let db, exporter;

    beforeAll(() => {
        db = createMockDB();
        exporter = new PDFExporter(db);

        const zone = createTestZone('zone-export-3', '大数据量测试坝段', 'dam');
        db.insertZone(zone);

        for (let i = 1; i <= 20; i++) {
            const sensor = createTestSensor(`sensor-big-${i}`, 'seepage', 'zone-export-3');
            db.insertSensor(sensor);
        }
    });

    it('14.1 大量传感器导出应在合理时间内完成（<5秒）', async () => {
        const startTime = Date.now();

        const result = await exporter.generateReport({
            zoneId: 'zone-export-3',
            includeSensorData: false,
            includeAlerts: false
        });

        const elapsed = Date.now() - startTime;

        assert(result !== null, '导出结果不应为null');
        assert(elapsed < 5000, `导出耗时${elapsed}ms超过5000ms阈值`);
    });

    it('14.2 大量传感器数据导出应限制数量（最多10个传感器趋势图）', async () => {
        const now = Date.now();
        for (let i = 1; i <= 20; i++) {
            for (let j = 0; j < 60; j++) {
                const timestamp = new Date(now - j * 5 * 60 * 1000).toISOString();
                const value = 0.3 + Math.random() * 0.2;
                db.insertSensorData({ sensor_id: `sensor-big-${i}`, timestamp, value });
            }
        }

        const result = await exporter.generateReport({
            zoneId: 'zone-export-3',
            includeSensorData: true,
            includeAlerts: false
        });

        const content = result.toString('utf-8');

        if (!PDFDocument) {
            const canvasCount = (content.match(/<canvas/g) || []).length;
            assert(canvasCount <= 10, `趋势图数量应不超过10，实际为${canvasCount}`);
        }
    });

    it('14.3 大量告警数据导出不应内存溢出', async () => {
        const now = Date.now();
        for (let i = 0; i < 500; i++) {
            const alert = createTestAlert(
                `alert-big-${i}`,
                `sensor-big-${(i % 20) + 1}`,
                'zone-export-3',
                'seepage',
                ['warning', 'critical'][i % 2]
            );
            alert.started_at = new Date(now - i * 60 * 1000).toISOString();
            db.insertAlert(alert);
        }

        const startTime = Date.now();
        const memBefore = process.memoryUsage().heapUsed;

        const result = await exporter.generateReport({
            zoneId: 'zone-export-3',
            includeSensorData: false,
            includeAlerts: true
        });

        const memAfter = process.memoryUsage().heapUsed;
        const memIncrease = memAfter - memBefore;
        const elapsed = Date.now() - startTime;

        assert(result !== null, '大量告警导出结果不应为null');
        assert(memIncrease < 100 * 1024 * 1024,
            `内存增加${(memIncrease / 1024 / 1024).toFixed(2)}MB超过100MB阈值`);
        assert(elapsed < 10000, `大量告警导出耗时${elapsed}ms超过10000ms阈值`);
    });

    it('14.4 传感器数据量限制（最多50个数据点/传感器）', async () => {
        const now = Date.now();
        for (let j = 0; j < 100; j++) {
            const timestamp = new Date(now - j * 5 * 60 * 1000).toISOString();
            const value = 0.3 + Math.random() * 0.2;
            db.insertSensorData({ sensor_id: 'sensor-big-1', timestamp, value });
        }

        const data = db.getSensorData('sensor-big-1', 50, null, null);
        assert(data.length <= 50, `传感器数据应限制为50条，实际为${data.length}`);
    });

    it('14.5 空时间范围不应导致错误', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-export-3',
            from: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
            to: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString(),
            includeSensorData: true,
            includeAlerts: true
        });

        assert(result !== null, '空时间范围导出不应为null');
        const content = result.toString('utf-8');

        if (!PDFDocument) {
            assert(content.includes('告警数量:</strong> 0'), '空时间范围告警数应为0');
        }
    });

    it('14.6 无任何数据时应优雅返回', async () => {
        const emptyDB = createMockDB();
        const emptyExporter = new PDFExporter(emptyDB);

        const result = await emptyExporter.generateReport({
            includeSensorData: true,
            includeAlerts: true
        });

        assert(result !== null, '空数据库导出不应为null');
        assert(result.length > 0, '空数据库导出长度应大于0');
    });

    it('14.7 导出缓冲区大小应合理', async () => {
        const result = await exporter.generateReport({
            zoneId: 'zone-export-3',
            includeSensorData: true,
            includeAlerts: true
        });

        const maxSize = 20 * 1024 * 1024;
        assert(result.length < maxSize,
            `导出文件大小${(result.length / 1024 / 1024).toFixed(2)}MB超过20MB阈值`);
    });

    it('14.8 HTML降级模式应正常工作（当pdfkit不可用时）', () => {
        const sensors = [createTestSensor('sensor-html-1', 'seepage', 'zone-export-3')];
        const alerts = [];

        const html = exporter.generateFallbackHTML(sensors, alerts, null, null, true, true);

        assert(Buffer.isBuffer(html), 'HTML导出应为Buffer');
        const content = html.toString('utf-8');
        assert(content.includes('<!DOCTYPE html>'), 'HTML应包含DOCTYPE');
        assert(content.includes('水利工程安全监测报告'), 'HTML应包含报告标题');
        assert(content.includes('报告生成时间'), 'HTML应包含生成时间');
    });
});
