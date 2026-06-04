class MemoryDB {
    constructor() {
        this.zones = new Map();
        this.sensors = new Map();
        this.sensorData = [];
        this.alerts = new Map();
        this.alertHistory = [];
        this._nextSensorDataId = 1;
        this._nextAlertHistoryId = 1;
    }

    insertZone(zone) {
        this.zones.set(zone.id, { ...zone });
    }

    getZone(id) {
        return this.zones.get(id) || null;
    }

    getAllZones() {
        return Array.from(this.zones.values());
    }

    insertSensor(sensor) {
        this.sensors.set(sensor.id, { ...sensor, status: sensor.status || 'normal' });
    }

    getSensor(id) {
        return this.sensors.get(id) || null;
    }

    getSensorsByZone(zoneId) {
        return Array.from(this.sensors.values()).filter(s => s.zone_id === zoneId);
    }

    getAllSensors() {
        return Array.from(this.sensors.values());
    }

    querySensors(filters) {
        let result = this.getAllSensors();
        if (filters.zone_id) result = result.filter(s => s.zone_id === filters.zone_id);
        if (filters.type) result = result.filter(s => s.type === filters.type);
        if (filters.status) result = result.filter(s => s.status === filters.status);
        return result;
    }

    insertSensorData(data) {
        this.sensorData.push({
            id: this._nextSensorDataId++,
            ...data,
            timestamp: data.timestamp || new Date().toISOString(),
            quality: data.quality || 'good'
        });
        if (this.sensorData.length > 50000) {
            this.sensorData = this.sensorData.slice(-30000);
        }
    }

    getSensorData(sensorId, limit, from, to) {
        let data = this.sensorData.filter(d => d.sensor_id === sensorId);
        if (from) data = data.filter(d => d.timestamp >= from);
        if (to) data = data.filter(d => d.timestamp <= to);
        data.sort((a, b) => b.timestamp.localeCompare(a.timestamp));
        return data.slice(0, limit || 100);
    }

    insertAlert(alert) {
        this.alerts.set(alert.id, { ...alert });
    }

    getAlert(id) {
        return this.alerts.get(id) || null;
    }

    updateAlert(id, updates) {
        const alert = this.alerts.get(id);
        if (alert) {
            Object.assign(alert, updates);
        }
    }

    getActiveAlerts() {
        return Array.from(this.alerts.values()).filter(
            a => a.state === 'active' || a.state === 'acknowledged' || a.state === 'escalated'
        );
    }

    getAlertsByZone(zoneId) {
        return this.getActiveAlerts().filter(a => a.zone_id === zoneId);
    }

    queryAlerts(filters) {
        let result = Array.from(this.alerts.values());
        if (filters.zone_id) result = result.filter(a => a.zone_id === filters.zone_id);
        if (filters.level) result = result.filter(a => a.level === filters.level);
        if (filters.state) result = result.filter(a => a.state === filters.state);
        if (filters.type) result = result.filter(a => a.type === filters.type);
        result.sort((a, b) => b.started_at.localeCompare(a.started_at));
        return result.slice(0, 200);
    }

    insertAlertHistory(entry) {
        this.alertHistory.push({
            id: this._nextAlertHistoryId++,
            ...entry,
            timestamp: entry.timestamp || new Date().toISOString()
        });
    }

    getAlertHistory(alertId) {
        return this.alertHistory
            .filter(h => h.alert_id === alertId)
            .sort((a, b) => a.timestamp.localeCompare(b.timestamp));
    }
}

let db = null;

function initDatabase() {
    db = new MemoryDB();
    return db;
}

function getDb() {
    if (!db) return initDatabase();
    return db;
}

function closeDatabase() {
    db = null;
}

function seedData() {
    const database = getDb();
    if (database.zones.size > 0) return;

    database.insertZone({
        id: 'zone-1', name: '主坝监测区', type: 'dam', description: '主坝体安全监测',
        boundary_json: JSON.stringify([
            { x: 100, y: 200 }, { x: 300, y: 150 }, { x: 500, y: 180 },
            { x: 700, y: 200 }, { x: 700, y: 400 }, { x: 100, y: 400 }
        ])
    });

    database.insertZone({
        id: 'zone-2', name: '溢洪道监测区', type: 'spillway', description: '溢洪道结构监测',
        boundary_json: JSON.stringify([
            { x: 800, y: 100 }, { x: 1000, y: 80 }, { x: 1100, y: 200 },
            { x: 1000, y: 350 }, { x: 800, y: 300 }
        ])
    });

    database.insertZone({
        id: 'zone-3', name: '输水隧洞监测区', type: 'tunnel', description: '输水隧洞渗压监测',
        boundary_json: JSON.stringify([
            { x: 200, y: 500 }, { x: 400, y: 480 }, { x: 600, y: 500 },
            { x: 600, y: 650 }, { x: 200, y: 650 }
        ])
    });

    const sensorTypes = ['seepage', 'displacement', 'stress', 'crack', 'water_level'];
    const sensorUnits = { seepage: 'L/s', displacement: 'mm', stress: 'MPa', crack: 'mm', water_level: 'm' };
    const thresholdsBase = {
        seepage: { warning: 2.0, critical: 5.0, emergency: 10.0 },
        displacement: { warning: 5.0, critical: 15.0, emergency: 30.0 },
        stress: { warning: 1.5, critical: 3.0, emergency: 5.0 },
        crack: { warning: 0.5, critical: 2.0, emergency: 5.0 },
        water_level: { warning: 145.0, critical: 148.0, emergency: 150.0 }
    };

    let sensorIdx = 0;
    const zones = [
        { id: 'zone-1', xMin: 150, xMax: 650, yMin: 220, yMax: 380, count: 50 },
        { id: 'zone-2', xMin: 820, xMax: 1080, yMin: 120, yMax: 320, count: 35 },
        { id: 'zone-3', xMin: 250, xMax: 550, yMin: 520, yMax: 630, count: 35 }
    ];

    for (const zone of zones) {
        for (let i = 0; i < zone.count; i++) {
            const sType = sensorTypes[sensorIdx % sensorTypes.length];
            const x = zone.xMin + Math.random() * (zone.xMax - zone.xMin);
            const y = zone.yMin + Math.random() * (zone.yMax - zone.yMin);
            const sId = `sensor-${String(sensorIdx + 1).padStart(3, '0')}`;
            const zoneName = zone.id === 'zone-1' ? '主坝' : zone.id === 'zone-2' ? '溢洪道' : '隧洞';
            const typeName = sType === 'seepage' ? '渗压' : sType === 'displacement' ? '位移' : sType === 'stress' ? '应力' : sType === 'crack' ? '裂缝' : '水位';

            database.insertSensor({
                id: sId, zone_id: zone.id,
                name: `${zoneName}${typeName}${i + 1}`,
                type: sType, x, y,
                unit: sensorUnits[sType],
                thresholds_json: JSON.stringify(thresholdsBase[sType])
            });
            sensorIdx++;
        }
    }
}

module.exports = { initDatabase, getDb, closeDatabase, seedData };
