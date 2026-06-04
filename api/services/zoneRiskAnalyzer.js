const { v4: uuidv4 } = require('uuid');

const ZONE_RISK_CONFIG = {
    alertThresholdPercent: 40,
    minAlertSensors: 3,
    checkIntervalSeconds: 30,
    riskLevel: 'critical',
    highlightColor: '#ff0000'
};

class RegionAnalyzer {
    constructor(db, alertEngine, dataBuffer) {
        this.db = db;
        this.alertEngine = alertEngine;
        this.dataBuffer = dataBuffer;
        this.zoneRisks = new Map();
        this.checkTimer = null;
        this.init();
    }

    init() {
        if (this.checkTimer) return;
        this.checkTimer = setInterval(() => {
            this.checkAllZones();
        }, ZONE_RISK_CONFIG.checkIntervalSeconds * 1000);
    }

    start() {
        this.init();
    }

    stop() {
        if (this.checkTimer) {
            clearInterval(this.checkTimer);
            this.checkTimer = null;
        }
    }

    analyze(zoneId) {
        return this.analyzeZoneRisk(zoneId);
    }

    analyzeAll() {
        this.checkAllZones();
    }

    getRisks(zoneId) {
        return this.getZoneRisks(zoneId);
    }

    getStatus() {
        return {
            running: this.checkTimer !== null,
            activeRisks: Array.from(this.zoneRisks.values()).filter(r => r.active).length,
            config: { ...ZONE_RISK_CONFIG }
        };
    }

    analyzeZoneRisk(zoneId) {
        const sensors = this.db.getSensorsByZone(zoneId);
        if (sensors.length === 0) return null;

        const activeAlerts = this.alertEngine.getActiveAlertsForZone(zoneId);
        const alertedSensorIds = new Set();

        for (const alert of activeAlerts) {
            if (!alert.is_prediction && !alert.is_zone_risk) {
                alertedSensorIds.add(alert.sensor_id);
            }
        }

        const alertedCount = alertedSensorIds.size;
        const totalCount = sensors.length;
        const percentage = totalCount > 0 ? (alertedCount / totalCount) * 100 : 0;

        const existingRisk = this.zoneRisks.get(zoneId);
        const isRisk = percentage >= ZONE_RISK_CONFIG.alertThresholdPercent &&
                       alertedCount >= ZONE_RISK_CONFIG.minAlertSensors;

        if (isRisk && (!existingRisk || !existingRisk.active)) {
            return this.createZoneRisk(zoneId, sensors, alertedCount, totalCount, percentage);
        } else if (!isRisk && existingRisk && existingRisk.active) {
            return this.clearZoneRisk(zoneId);
        } else if (isRisk && existingRisk && existingRisk.active) {
            existingRisk.alerted_count = alertedCount;
            existingRisk.total_count = totalCount;
            existingRisk.percentage = percentage;
            existingRisk.updated_at = new Date().toISOString();
            this.broadcastZoneRisk(existingRisk, 'updated');
            return { action: 'risk_updated', risk: existingRisk };
        }

        return null;
    }

    createZoneRisk(zoneId, sensors, alertedCount, totalCount, percentage) {
        const zone = this.db.getZone(zoneId);
        const riskId = `zone-risk-${uuidv4().slice(0, 8)}`;
        const now = new Date().toISOString();

        const risk = {
            id: riskId,
            zone_id: zoneId,
            zone_name: zone ? zone.name : zoneId,
            level: ZONE_RISK_CONFIG.riskLevel,
            alerted_count: alertedCount,
            total_count: totalCount,
            percentage: percentage,
            highlight_color: ZONE_RISK_CONFIG.highlightColor,
            active: true,
            started_at: now,
            updated_at: now
        };

        this.zoneRisks.set(zoneId, risk);

        const alertId = `alert-zone-${uuidv4().slice(0, 8)}`;
        const zoneAlert = {
            id: alertId,
            zone_id: zoneId,
            sensor_id: null,
            level: ZONE_RISK_CONFIG.riskLevel,
            type: 'zone_risk',
            message: `【坝段级风险】${zone ? zone.name : zoneId} 有 ${alertedCount}/${totalCount} (${percentage.toFixed(1)}%) 个传感器同时告警，判定为坝段级风险`,
            state: 'active',
            escalated_from: null,
            converged_group: null,
            started_at: now,
            acknowledged_at: null,
            resolved_at: null,
            is_zone_risk: true,
            risk_id: riskId
        };

        this.db.insertAlert(zoneAlert);
        this.alertEngine.recordTransition(alertId, null, 'active', ZONE_RISK_CONFIG.riskLevel, 'zone_risk_created');
        this.alertEngine.activeAlerts.set(alertId, zoneAlert);

        this.broadcastZoneRisk(risk, 'created', zoneAlert);

        return {
            action: 'risk_created',
            risk,
            alert: zoneAlert
        };
    }

    clearZoneRisk(zoneId) {
        const risk = this.zoneRisks.get(zoneId);
        if (!risk) return null;

        risk.active = false;
        risk.ended_at = new Date().toISOString();
        this.zoneRisks.set(zoneId, risk);

        for (const [alertId, alert] of this.alertEngine.activeAlerts) {
            if (alert.is_zone_risk && alert.zone_id === zoneId) {
                this.alertEngine.resolveAlert(alertId, 'zone_risk_cleared');
                this.dataBuffer.broadcastAlert({
                    action: 'resolved',
                    alert_id: alertId
                });
            }
        }

        this.broadcastZoneRisk(risk, 'cleared');

        return {
            action: 'risk_cleared',
            risk
        };
    }

    broadcastZoneRisk(risk, action, alert) {
        const message = JSON.stringify({
            type: 'zone_risk_update',
            data: {
                action,
                risk,
                alert
            }
        });

        this.dataBuffer.broadcastQueue.push(message);
        setImmediate(() => this.dataBuffer.flushBroadcast());
    }

    checkAllZones() {
        const zones = this.db.getAllZones();
        for (const zone of zones) {
            this.analyzeZoneRisk(zone.id);
        }
    }

    getZoneRisks(zoneId) {
        if (zoneId) {
            const risk = this.zoneRisks.get(zoneId);
            return risk ? [risk] : [];
        }
        return Array.from(this.zoneRisks.values()).filter(r => r.active);
    }

    destroy() {
        if (this.checkTimer) {
            clearInterval(this.checkTimer);
            this.checkTimer = null;
        }
        this.zoneRisks.clear();
    }
}

const ZoneRiskAnalyzer = RegionAnalyzer;

module.exports = { RegionAnalyzer, ZoneRiskAnalyzer, ZONE_RISK_CONFIG };
