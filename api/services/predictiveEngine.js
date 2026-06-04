const { v4: uuidv4 } = require('uuid');

const PREDICTIVE_CONFIG = {
    historyHours: 24,
    predictMinutes: 30,
    dataIntervalMinutes: 3,
    alpha: 0.3,
    beta: 0.1,
    checkIntervalMinutes: 5,
    minDataPoints: 20,
    warningLeadMinutes: 15,
    maxConsecutiveMissing: 10
};

const ALERT_LEVELS = ['info', 'warning', 'critical', 'emergency'];

class ExponentialSmoothing {
    constructor(alpha = 0.3, beta = 0.1) {
        this.alpha = alpha;
        this.beta = beta;
        this.level = null;
        this.trend = null;
    }

    initialize(data) {
        if (data.length < 2) return;
        this.level = data[0];
        this.trend = data[1] - data[0];
        for (let i = 1; i < data.length; i++) {
            this.update(data[i]);
        }
    }

    update(value) {
        if (this.level === null) {
            this.level = value;
            this.trend = 0;
            return;
        }
        const prevLevel = this.level;
        this.level = this.alpha * value + (1 - this.alpha) * (this.level + this.trend);
        this.trend = this.beta * (this.level - prevLevel) + (1 - this.beta) * this.trend;
    }

    forecast(steps) {
        const predictions = [];
        for (let i = 1; i <= steps; i++) {
            predictions.push(this.level + i * this.trend);
        }
        return predictions;
    }
}

class PredictionEngine {
    constructor(db, alertEngine, dataBuffer) {
        this.db = db;
        this.alertEngine = alertEngine;
        this.dataBuffer = dataBuffer;
        this.activePredictions = new Map();
        this.checkTimer = null;
        this._running = false;
        this.startCheckCycle();
    }

    init() {
        if (!this._running) {
            this.startCheckCycle();
        }
    }

    start() {
        this.init();
    }

    stop() {
        if (this.checkTimer) {
            clearInterval(this.checkTimer);
            this.checkTimer = null;
        }
        this._running = false;
        this.activePredictions.clear();
    }

    predict(sensorId, sensorType) {
        if (sensorType !== 'seepage') {
            return null;
        }

        const history = this.getSensorHistory(sensorId);
        if (history.length < Math.min(PREDICTIVE_CONFIG.minDataPoints, 10)) {
            return null;
        }

        const sorted = [...history].sort((a, b) => a.timestamp.localeCompare(b.timestamp));

        const fillResult = this.detectAndFillMissingValues(sorted);
        if (!fillResult.valid) {
            return null;
        }

        const filledHistory = fillResult.filled;
        const values = filledHistory.map(d => d.value);
        const timestamps = filledHistory.map(d => new Date(d.timestamp).getTime());

        const smoothing = new ExponentialSmoothing(PREDICTIVE_CONFIG.alpha, PREDICTIVE_CONFIG.beta);
        smoothing.initialize(values);

        const steps = Math.ceil(PREDICTIVE_CONFIG.predictMinutes / PREDICTIVE_CONFIG.dataIntervalMinutes);
        const predictions = smoothing.forecast(steps);

        const lastTimestamp = timestamps[timestamps.length - 1];
        const predictedTimestamps = [];
        for (let i = 1; i <= steps; i++) {
            predictedTimestamps.push(lastTimestamp + i * PREDICTIVE_CONFIG.dataIntervalMinutes * 60 * 1000);
        }

        return {
            values,
            timestamps,
            predictions,
            predictedTimestamps,
            currentLevel: smoothing.level,
            currentTrend: smoothing.trend,
            fillInfo: {
                maxConsecutiveMissing: fillResult.maxConsecutiveMissing,
                interpolatedCount: fillResult.interpolatedCount,
                totalPoints: filledHistory.length
            }
        };
    }

    evaluate(sensorId, zoneId, sensorType, thresholds) {
        const result = this.predict(sensorId, sensorType);
        if (!result) return null;

        const parsedThresholds = typeof thresholds === 'string' ? JSON.parse(thresholds) : thresholds;
        const { predictions, predictedTimestamps } = result;
        const now = Date.now();
        const warningLeadMs = PREDICTIVE_CONFIG.warningLeadMinutes * 60 * 1000;

        let predictedLevel = null;
        let predictedTime = null;

        for (let level of ['emergency', 'critical', 'warning']) {
            const threshold = parsedThresholds[level];
            if (threshold === undefined) continue;

            for (let i = 0; i < predictions.length; i++) {
                if (predictions[i] >= threshold) {
                    const timeToThreshold = predictedTimestamps[i] - now;
                    if (timeToThreshold <= warningLeadMs) {
                        predictedLevel = level;
                        predictedTime = new Date(predictedTimestamps[i]).toISOString();
                        break;
                    }
                }
            }
            if (predictedLevel) break;
        }

        if (!predictedLevel) {
            this.clearPrediction(sensorId);
            return null;
        }

        const existingPrediction = this.activePredictions.get(sensorId);
        if (existingPrediction && existingPrediction.level === predictedLevel) {
            return null;
        }

        const prediction = {
            id: `pred-${uuidv4().slice(0, 8)}`,
            sensor_id: sensorId,
            zone_id: zoneId,
            type: sensorType,
            level: predictedLevel,
            predicted_at: predictedTime,
            current_value: result.values[result.values.length - 1],
            predicted_value: result.predictions.find((v, i) => {
                const threshold = parsedThresholds[predictedLevel];
                return v >= threshold;
            }),
            trend: result.currentTrend,
            created_at: new Date().toISOString()
        };

        this.activePredictions.set(sensorId, prediction);
        this.createPredictionAlert(prediction, parsedThresholds);

        return {
            action: 'prediction_created',
            prediction,
            forecast: result
        };
    }

    getStatus() {
        return {
            running: this._running,
            predictions: this.activePredictions.size,
            config: { ...PREDICTIVE_CONFIG }
        };
    }

    getSensorHistory(sensorId) {
        const now = Date.now();
        const from = new Date(now - PREDICTIVE_CONFIG.historyHours * 60 * 60 * 1000).toISOString();
        const limit = Math.ceil(PREDICTIVE_CONFIG.historyHours * 60 / PREDICTIVE_CONFIG.dataIntervalMinutes);
        return this.db.getSensorData(sensorId, limit, from, null);
    }

    detectAndFillMissingValues(sortedHistory) {
        if (sortedHistory.length < 2) {
            return { filled: sortedHistory, valid: false, reason: 'insufficient_data' };
        }

        const intervalMs = PREDICTIVE_CONFIG.dataIntervalMinutes * 60 * 1000;
        const filled = [];
        let consecutiveMissing = 0;
        let maxConsecutiveMissing = 0;

        const firstTime = new Date(sortedHistory[0].timestamp).getTime();
        const lastTime = new Date(sortedHistory[sortedHistory.length - 1].timestamp).getTime();
        const historyMap = new Map();

        for (const point of sortedHistory) {
            historyMap.set(new Date(point.timestamp).getTime(), point.value);
        }

        for (let currentTime = firstTime; currentTime <= lastTime; currentTime += intervalMs) {
            if (historyMap.has(currentTime)) {
                filled.push({
                    timestamp: new Date(currentTime).toISOString(),
                    value: historyMap.get(currentTime)
                });
                consecutiveMissing = 0;
            } else {
                const prevTime = currentTime - intervalMs;
                const nextTime = currentTime + intervalMs;

                if (historyMap.has(prevTime) && historyMap.has(nextTime)) {
                    const prevValue = historyMap.get(prevTime);
                    const nextValue = historyMap.get(nextTime);
                    const interpolatedValue = prevValue + (nextValue - prevValue) * 0.5;

                    filled.push({
                        timestamp: new Date(currentTime).toISOString(),
                        value: interpolatedValue,
                        interpolated: true
                    });
                } else {
                    consecutiveMissing++;
                    maxConsecutiveMissing = Math.max(maxConsecutiveMissing, consecutiveMissing);

                    if (consecutiveMissing > PREDICTIVE_CONFIG.maxConsecutiveMissing) {
                        return {
                            filled: null,
                            valid: false,
                            reason: 'too_many_consecutive_missing',
                            maxConsecutiveMissing
                        };
                    }

                    if (filled.length > 0) {
                        const lastValue = filled[filled.length - 1].value;
                        filled.push({
                            timestamp: new Date(currentTime).toISOString(),
                            value: lastValue,
                            interpolated: true,
                            forwardFilled: true
                        });
                    }
                }
            }
        }

        if (filled.length < PREDICTIVE_CONFIG.minDataPoints) {
            return { filled, valid: false, reason: 'insufficient_filled_data' };
        }

        return {
            filled,
            valid: true,
            maxConsecutiveMissing,
            interpolatedCount: filled.filter(f => f.interpolated).length
        };
    }

    predictSensorTrend(sensorId, sensorType) {
        return this.predict(sensorId, sensorType);
    }

    evaluatePrediction(sensorId, zoneId, sensorType, thresholds) {
        return this.evaluate(sensorId, zoneId, sensorType, thresholds);
    }

    createPredictionAlert(prediction, thresholds) {
        const alertId = `alert-pred-${uuidv4().slice(0, 8)}`;
        const now = new Date().toISOString();
        const typeNames = {
            seepage: '渗流', displacement: '位移', stress: '应力',
            crack: '裂缝', water_level: '水位'
        };
        const levelNames = {
            info: '提示', warning: '警告', critical: '严重', emergency: '紧急'
        };

        const predictedTimeStr = new Date(prediction.predicted_at).toLocaleString('zh-CN');
        const minutesTo = Math.ceil((new Date(prediction.predicted_at) - new Date()) / 60000);

        const alert = {
            id: alertId,
            sensor_id: prediction.sensor_id,
            zone_id: prediction.zone_id,
            level: prediction.level,
            type: prediction.type,
            message: `【预测预警】${typeNames[prediction.type]}预计在${minutesTo}分钟后(${predictedTimeStr})达到${levelNames[prediction.level]}阈值，当前值 ${prediction.current_value.toFixed(4)}，趋势 ${prediction.trend > 0 ? '上升' : '下降'}`,
            state: 'active',
            escalated_from: null,
            converged_group: null,
            started_at: now,
            acknowledged_at: null,
            resolved_at: null,
            is_prediction: true,
            prediction_id: prediction.id,
            predicted_at: prediction.predicted_at,
            predicted_value: prediction.predicted_value
        };

        this.db.insertAlert(alert);
        this.alertEngine.recordTransition(alertId, null, 'active', prediction.level, 'prediction_created');
        this.alertEngine.activeAlerts.set(alertId, alert);
        this.alertEngine.scheduleEscalation(alert);

        this.dataBuffer.broadcastAlert({
            action: 'created',
            alert,
            is_prediction: true,
            prediction
        });
    }

    clearPrediction(sensorId) {
        const prediction = this.activePredictions.get(sensorId);
        if (prediction) {
            for (const [alertId, alert] of this.alertEngine.activeAlerts) {
                if (alert.is_prediction && alert.sensor_id === sensorId) {
                    this.alertEngine.resolveAlert(alertId, 'prediction_cleared');
                    this.dataBuffer.broadcastAlert({
                        action: 'resolved',
                        alert_id: alertId
                    });
                }
            }
            this.activePredictions.delete(sensorId);
        }
    }

    checkAllSensors() {
        const sensors = this.db.getAllSensors();
        for (const sensor of sensors) {
            if (sensor.type === 'seepage') {
                this.evaluatePrediction(
                    sensor.id, sensor.zone_id, sensor.type, sensor.thresholds_json
                );
            }
        }
    }

    startCheckCycle() {
        this._running = true;
        this.checkTimer = setInterval(() => {
            this.checkAllSensors();
        }, PREDICTIVE_CONFIG.checkIntervalMinutes * 60 * 1000);
    }

    getPredictions(zoneId) {
        const result = [];
        for (const [, prediction] of this.activePredictions) {
            if (!zoneId || prediction.zone_id === zoneId) {
                result.push(prediction);
            }
        }
        return result;
    }

    destroy() {
        this.stop();
    }
}

module.exports = { PredictionEngine, PredictiveEngine: PredictionEngine, ExponentialSmoothing, PREDICTIVE_CONFIG };
