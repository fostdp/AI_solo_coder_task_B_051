const path = require('path');
const fs = require('fs');

class RuleEngine {
    constructor(rulesPath) {
        const resolvedPath = rulesPath || path.join(__dirname, '..', 'config', 'alertRules.json');
        const raw = fs.readFileSync(resolvedPath, 'utf-8');
        this.rules = JSON.parse(raw);
    }

    getEscalationRule(sensorType, level) {
        const sensor = this.rules.sensorTypes[sensorType];
        if (!sensor || !sensor.escalationRules) return null;
        return sensor.escalationRules[level] || null;
    }

    getConvergenceConfig() {
        return { ...this.rules.convergence };
    }

    getStateTransitions() {
        return { ...this.rules.stateTransitions };
    }

    getLevelOrder() {
        return [...this.rules.levelOrder];
    }

    getSensorTypeName(sensorType) {
        const sensor = this.rules.sensorTypes[sensorType];
        return sensor ? sensor.name : sensorType;
    }

    getEscalationRules() {
        const result = {};
        for (const [typeKey, typeDef] of Object.entries(this.rules.sensorTypes)) {
            if (typeDef.escalationRules) {
                result[typeKey] = { ...typeDef.escalationRules };
            }
        }
        return result;
    }
}

const ruleEngine = new RuleEngine();

const ALERT_LEVELS = ruleEngine.getLevelOrder();

const ALERT_STATE_TRANSITIONS = ruleEngine.getStateTransitions();

const ESCALATION_RULES = ruleEngine.getEscalationRules();

const CONVERGENCE_CONFIG = ruleEngine.getConvergenceConfig();

class AlertEngine {
    constructor(db) {
        this.db = db;
        this.ruleEngine = new RuleEngine();
        this.activeAlerts = new Map();
        this.escalationTimers = new Map();
        this.convergenceCache = new Map();
        this.loadActiveAlerts();
    }

    loadActiveAlerts() {
        const alerts = this.db.getActiveAlerts();
        for (const alert of alerts) {
            this.activeAlerts.set(alert.id, alert);
            this.scheduleEscalation(alert);
        }
    }

    evaluateSensorReading(sensorId, zoneId, sensorType, value, thresholds) {
        const parsedThresholds = typeof thresholds === 'string' ? JSON.parse(thresholds) : thresholds;
        let triggeredLevel = null;

        if (parsedThresholds.emergency !== undefined && value >= parsedThresholds.emergency) {
            triggeredLevel = 'emergency';
        } else if (parsedThresholds.critical !== undefined && value >= parsedThresholds.critical) {
            triggeredLevel = 'critical';
        } else if (parsedThresholds.warning !== undefined && value >= parsedThresholds.warning) {
            triggeredLevel = 'warning';
        }

        const existingAlert = this.findActiveAlert(sensorId, sensorType);

        if (triggeredLevel) {
            if (existingAlert) {
                this.updateAlertLevel(existingAlert, triggeredLevel, value);
            } else {
                return this.createAlert(sensorId, zoneId, sensorType, triggeredLevel, value);
            }
        } else {
            if (existingAlert) {
                this.resolveAlert(existingAlert.id, 'value_normal');
                return { action: 'resolved', alertId: existingAlert.id };
            }
        }

        return null;
    }

    createAlert(sensorId, zoneId, sensorType, level, triggerValue) {
        const { v4: uuidv4 } = require('uuid');
        const alertId = `alert-${uuidv4().slice(0, 8)}`;
        const now = new Date().toISOString();

        const alert = {
            id: alertId,
            sensor_id: sensorId,
            zone_id: zoneId,
            level: level,
            type: sensorType,
            message: this.generateAlertMessage(sensorType, level, triggerValue),
            state: 'active',
            escalated_from: null,
            converged_group: null,
            started_at: now,
            acknowledged_at: null,
            resolved_at: null
        };

        this.db.insertAlert(alert);
        this.recordTransition(alertId, null, 'active', level, 'created');
        this.activeAlerts.set(alertId, alert);
        this.scheduleEscalation(alert);
        this.checkConvergence(alert);

        return { action: 'created', alert };
    }

    updateAlertLevel(alert, newLevel, currentValue) {
        const levelOrder = this.ruleEngine.getLevelOrder();
        const levelIndex = levelOrder.indexOf(newLevel);
        const currentLevelIndex = levelOrder.indexOf(alert.level);

        if (levelIndex > currentLevelIndex) {
            const oldLevel = alert.level;
            alert.level = newLevel;
            alert.message = this.generateAlertMessage(alert.type, newLevel, currentValue);

            this.db.updateAlert(alert.id, { level: newLevel, message: alert.message });

            this.recordTransition(alert.id, alert.state, alert.state, newLevel, 'level_upgraded');
            this.activeAlerts.set(alert.id, alert);
            this.scheduleEscalation(alert);
            this.checkConvergence(alert);

            return { action: 'upgraded', alert, fromLevel: oldLevel, toLevel: newLevel };
        }

        return null;
    }

    transitionAlert(alertId, action) {
        const alert = this.activeAlerts.get(alertId);
        if (!alert) return { error: 'alert_not_found' };

        const transitions = this.ruleEngine.getStateTransitions();
        const stateTransitions = transitions[alert.state];
        if (!stateTransitions || !stateTransitions[action]) {
            return { error: 'invalid_transition', from: alert.state, action };
        }

        const newState = stateTransitions[action];
        const oldState = alert.state;
        alert.state = newState;

        const now = new Date().toISOString();

        if (newState === 'acknowledged') {
            alert.acknowledged_at = now;
            this.db.updateAlert(alertId, { state: newState, acknowledged_at: now });
        } else if (newState === 'resolved') {
            alert.resolved_at = now;
            this.db.updateAlert(alertId, { state: newState, resolved_at: now });
            this.activeAlerts.delete(alertId);
            this.cancelEscalation(alertId);
        } else {
            this.db.updateAlert(alertId, { state: newState });
        }

        this.recordTransition(alertId, oldState, newState, alert.level, action);
        this.activeAlerts.set(alertId, alert);

        return { action: 'transitioned', alert, fromState: oldState, toState: newState };
    }

    escalateAlert(alertId, targetLevel, reason) {
        const alert = this.activeAlerts.get(alertId);
        if (!alert) return { error: 'alert_not_found' };

        const oldLevel = alert.level;
        const oldId = alert.id;

        const rule = this.ruleEngine.getEscalationRule(alert.type, alert.level);
        if (targetLevel === 'emergency' && rule && rule.rename) {
            alert.message = `【${rule.rename}】${alert.message}`;
        }

        alert.level = targetLevel;
        alert.escalated_from = oldId;
        alert.state = 'escalated';

        this.db.updateAlert(alertId, {
            level: targetLevel, state: 'escalated',
            escalated_from: oldId, message: alert.message
        });

        this.recordTransition(alertId, 'active', 'escalated', targetLevel, reason || 'auto_escalate');
        this.cancelEscalation(alertId);
        this.activeAlerts.set(alertId, alert);
        this.checkConvergence(alert);

        return { action: 'escalated', alert, fromLevel: oldLevel, toLevel: targetLevel };
    }

    resolveAlert(alertId, reason) {
        const alert = this.activeAlerts.get(alertId);
        if (!alert) return;

        const now = new Date().toISOString();
        alert.state = 'resolved';
        alert.resolved_at = now;

        this.db.updateAlert(alertId, { state: 'resolved', resolved_at: now });

        this.recordTransition(alertId, alert.state, 'resolved', alert.level, reason || 'manual');
        this.activeAlerts.delete(alertId);
        this.cancelEscalation(alertId);
    }

    scheduleEscalation(alert) {
        this.cancelEscalation(alert.id);

        const rule = this.ruleEngine.getEscalationRule(alert.type, alert.level);
        if (!rule) return;

        if (rule.condition === 'duration') {
            const timer = setTimeout(() => {
                const currentAlert = this.activeAlerts.get(alert.id);
                if (currentAlert && currentAlert.state !== 'resolved') {
                    this.escalateAlert(alert.id, rule.nextLevel, 'auto_escalate_duration');
                }
            }, rule.threshold);

            this.escalationTimers.set(alert.id, timer);
        }
    }

    cancelEscalation(alertId) {
        const timer = this.escalationTimers.get(alertId);
        if (timer) {
            clearTimeout(timer);
            this.escalationTimers.delete(alertId);
        }
    }

    checkConvergence(newAlert) {
        const convergenceConfig = this.ruleEngine.getConvergenceConfig();

        const zoneAlerts = [];
        for (const [, alert] of this.activeAlerts) {
            if (alert.zone_id === newAlert.zone_id && alert.state !== 'resolved') {
                zoneAlerts.push(alert);
            }
        }

        if (zoneAlerts.length < convergenceConfig.minAlerts) return;

        const now = Date.now();
        const timeWindow = convergenceConfig.timeWindow;
        const recentAlerts = zoneAlerts.filter(a => {
            return (now - new Date(a.started_at).getTime()) < timeWindow;
        });

        if (recentAlerts.length < convergenceConfig.minAlerts) return;

        const groups = this.findGeographicGroups(recentAlerts);
        for (const group of groups) {
            if (group.length >= convergenceConfig.minAlerts) {
                const groupId = `conv-${newAlert.zone_id}-${Date.now()}`;
                for (const alert of group) {
                    this.db.updateAlert(alert.id, { converged_group: groupId });
                    alert.converged_group = groupId;
                    this.activeAlerts.set(alert.id, alert);
                }

                return {
                    action: 'converged',
                    groupId,
                    alertCount: group.length,
                    zoneId: newAlert.zone_id
                };
            }
        }
    }

    findGeographicGroups(alerts) {
        const convergenceConfig = this.ruleEngine.getConvergenceConfig();
        const groups = [];
        const visited = new Set();

        for (let i = 0; i < alerts.length; i++) {
            if (visited.has(alerts[i].id)) continue;
            const group = [alerts[i]];
            visited.add(alerts[i].id);
            const groupZoneId = alerts[i].zone_id;

            for (let j = i + 1; j < alerts.length; j++) {
                if (visited.has(alerts[j].id)) continue;
                if (alerts[j].zone_id !== groupZoneId) continue;
                const dist = this.estimateAlertDistance(alerts[i], alerts[j]);
                if (dist < convergenceConfig.radius) {
                    group.push(alerts[j]);
                    visited.add(alerts[j].id);
                }
            }

            groups.push(group);
        }

        return groups;
    }

    estimateAlertDistance(a, b) {
        const sensorA = this.db.getSensor(a.sensor_id);
        const sensorB = this.db.getSensor(b.sensor_id);
        if (!sensorA || !sensorB) return Infinity;
        return Math.sqrt(Math.pow(sensorA.x - sensorB.x, 2) + Math.pow(sensorA.y - sensorB.y, 2));
    }

    findActiveAlert(sensorId, sensorType) {
        for (const [, alert] of this.activeAlerts) {
            if (alert.sensor_id === sensorId && alert.type === sensorType && alert.state !== 'resolved') {
                return alert;
            }
        }
        return null;
    }

    generateAlertMessage(sensorType, level, value) {
        const typeName = this.ruleEngine.getSensorTypeName(sensorType);
        const levelNames = {
            info: '提示', warning: '警告', critical: '严重', emergency: '紧急'
        };
        return `${levelNames[level]}：${typeName}监测值 ${value} 超过${levelNames[level]}阈值`;
    }

    recordTransition(alertId, fromState, toState, level, action) {
        this.db.insertAlertHistory({
            alert_id: alertId,
            from_state: fromState,
            to_state: toState,
            level: level,
            action: action,
            timestamp: new Date().toISOString()
        });
    }

    getActiveAlertsForZone(zoneId) {
        const result = [];
        for (const [, alert] of this.activeAlerts) {
            if (!zoneId || alert.zone_id === zoneId) {
                result.push(alert);
            }
        }
        return result;
    }

    getAlertHistory(alertId) {
        return this.db.getAlertHistory(alertId);
    }

    destroy() {
        for (const [id, timer] of this.escalationTimers) {
            clearTimeout(timer);
        }
        this.escalationTimers.clear();
        this.activeAlerts.clear();
    }
}

module.exports = { AlertEngine, RuleEngine, ALERT_LEVELS, ALERT_STATE_TRANSITIONS, ESCALATION_RULES, CONVERGENCE_CONFIG };
