const assert = require('assert');

class TestRunner {
    constructor() {
        this.suites = [];
        this.currentSuite = null;
        this.results = [];
    }

    describe(name, fn) {
        const suite = { name, tests: [], beforeAll: [], afterAll: [] };
        this.suites.push(suite);
        this.currentSuite = suite;
        fn();
        this.currentSuite = null;
    }

    beforeAll(fn) {
        if (this.currentSuite) {
            this.currentSuite.beforeAll.push(fn);
        }
    }

    afterAll(fn) {
        if (this.currentSuite) {
            this.currentSuite.afterAll.push(fn);
        }
    }

    it(name, fn) {
        if (this.currentSuite) {
            this.currentSuite.tests.push({ name, fn });
        }
    }

    async run() {
        let passed = 0;
        let failed = 0;

        for (const suite of this.suites) {
            console.log(`\n\x1b[36m=== ${suite.name} ===\x1b[0m`);

            for (const before of suite.beforeAll) {
                await before();
            }

            for (const test of suite.tests) {
                try {
                    await test.fn();
                    console.log(`  \x1b[32m✓ ${test.name}\x1b[0m`);
                    this.results.push({ suite: suite.name, test: test.name, passed: true });
                    passed++;
                } catch (error) {
                    console.log(`  \x1b[31m✗ ${test.name}\x1b[0m`);
                    console.log(`    \x1b[31m${error.message}\x1b[0m`);
                    if (error.stack) {
                        console.log(`    \x1b[90m${error.stack.split('\n').slice(0, 3).join('\n    ')}\x1b[0m`);
                    }
                    this.results.push({ suite: suite.name, test: test.name, passed: false, error: error.message });
                    failed++;
                }
            }

            for (const after of suite.afterAll) {
                await after();
            }
        }

        console.log(`\n\x1b[1m=== 测试结果 ===\x1b[0m`);
        console.log(`通过: \x1b[32m${passed}\x1b[0m`);
        console.log(`失败: \x1b[31m${failed}\x1b[0m`);
        console.log(`总计: ${passed + failed}`);

        return failed === 0;
    }
}

const runner = new TestRunner();

function describe(name, fn) { runner.describe(name, fn); }
function it(name, fn) { runner.it(name, fn); }
function beforeAll(fn) { runner.beforeAll(fn); }
function afterAll(fn) { runner.afterAll(fn); }

function createMockDB() {
    return {
        zones: new Map(),
        sensors: new Map(),
        sensorData: [],
        alerts: new Map(),
        getAllZones() { return Array.from(this.zones.values()); },
        getZone(id) { return this.zones.get(id) || null; },
        insertZone(zone) { this.zones.set(zone.id, { ...zone }); },
        getAllSensors() { return Array.from(this.sensors.values()); },
        getSensor(id) { return this.sensors.get(id) || null; },
        getSensorsByZone(zoneId) {
            return Array.from(this.sensors.values()).filter(s => s.zone_id === zoneId);
        },
        insertSensor(sensor) { this.sensors.set(sensor.id, { ...sensor }); },
        getSensorData(sensorId, limit, from, to) {
            let data = this.sensorData.filter(d => d.sensor_id === sensorId);
            if (from) data = data.filter(d => d.timestamp >= from);
            if (to) data = data.filter(d => d.timestamp <= to);
            data.sort((a, b) => b.timestamp.localeCompare(a.timestamp));
            return data.slice(0, limit || 100);
        },
        insertSensorData(data) {
            this.sensorData.push({
                id: this.sensorData.length + 1,
                ...data,
                timestamp: data.timestamp || new Date().toISOString(),
                quality: data.quality || 'good'
            });
        },
        insertAlert(alert) { this.alerts.set(alert.id, { ...alert }); },
        getAlert(id) { return this.alerts.get(id) || null; },
        queryAlerts(filters) {
            let result = Array.from(this.alerts.values());
            if (filters.zone_id) result = result.filter(a => a.zone_id === filters.zone_id);
            if (filters.state) result = result.filter(a => a.state === filters.state);
            return result;
        }
    };
}

function createMockAlertEngine() {
    const activeAlerts = new Map();
    return {
        activeAlerts,
        transitions: [],
        getActiveAlertsForZone(zoneId) {
            return Array.from(activeAlerts.values()).filter(a => 
                (!zoneId || a.zone_id === zoneId) && a.state !== 'resolved'
            );
        },
        recordTransition(alertId, fromState, toState, level, reason) {
            this.transitions.push({ alertId, fromState, toState, level, reason, time: new Date().toISOString() });
        },
        scheduleEscalation(alert) {},
        resolveAlert(alertId, reason) {
            const alert = activeAlerts.get(alertId);
            if (alert) {
                alert.state = 'resolved';
                alert.resolved_at = new Date().toISOString();
                activeAlerts.set(alertId, alert);
            }
        }
    };
}

function createMockDataBuffer() {
    return {
        broadcastQueue: [],
        broadcastAlert(data) { this.broadcastQueue.push(JSON.stringify(data)); },
        flushBroadcast() { const q = this.broadcastQueue; this.broadcastQueue = []; return q; }
    };
}

function generateSensorData(sensorId, hours, trend = 0, baseValue = 0.5, noiseLevel = 0.01) {
    const now = Date.now();
    const data = [];
    const dataIntervalMs = 3 * 60 * 1000;
    const points = Math.floor(hours * 60 / 3);

    for (let i = points; i >= 0; i--) {
        const timestamp = new Date(now - i * dataIntervalMs).toISOString();
        const progress = 1 - (i / points);
        const trendValue = baseValue + progress * trend;
        const noise = (Math.random() - 0.5) * noiseLevel;
        const value = Math.max(0, trendValue + noise);
        data.push({ sensor_id: sensorId, timestamp, value });
    }
    return data;
}

function createTestSensor(id, type, zoneId, thresholds) {
    return {
        id,
        name: `Test Sensor ${id}`,
        type,
        zone_id: zoneId,
        unit: type === 'seepage' ? 'kPa' : 'mm',
        status: 'normal',
        thresholds_json: thresholds || {
            warning: 0.6,
            critical: 0.75,
            emergency: 0.85
        },
        x: 100,
        y: 200,
        section: 'main'
    };
}

function createTestZone(id, name, type) {
    return {
        id,
        name,
        type,
        description: `Test zone ${name}`,
        boundary_json: '[{"x":100,"y":200},{"x":300,"y":150},{"x":500,"y":200}]',
        sensor_count: 0,
        active_alerts: 0
    };
}

function createTestAlert(id, sensorId, zoneId, type, level, isPrediction = false) {
    return {
        id,
        sensor_id: sensorId,
        zone_id: zoneId,
        type,
        level,
        message: `Test alert for ${sensorId}`,
        state: 'active',
        escalated_from: null,
        converged_group: null,
        started_at: new Date().toISOString(),
        acknowledged_at: null,
        resolved_at: null,
        is_prediction: isPrediction,
        is_zone_risk: false
    };
}

module.exports = {
    runner,
    describe,
    it,
    beforeAll,
    afterAll,
    assert,
    createMockDB,
    createMockAlertEngine,
    createMockDataBuffer,
    generateSensorData,
    createTestSensor,
    createTestZone,
    createTestAlert
};
