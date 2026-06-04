const express = require('express');

function createApiRouter(db, alertEngine, dataBuffer, predictionEngine, regionAnalyzer, emergencyGuideManager, exportService) {
    const router = express.Router();

    router.get('/zones', (req, res) => {
        const zones = db.getAllZones();
        for (const zone of zones) {
            const sensors = db.getSensorsByZone(zone.id);
            zone.sensor_count = sensors.length;
            const activeAlerts = alertEngine.getActiveAlertsForZone(zone.id);
            zone.active_alerts = activeAlerts.length;
            if (zone.boundary_json) {
                zone.boundary = JSON.parse(zone.boundary_json);
            }
        }
        res.json({ data: zones });
    });

    router.get('/zones/:zoneId', (req, res) => {
        const zone = db.getZone(req.params.zoneId);
        if (!zone) return res.status(404).json({ error: 'Zone not found' });
        if (zone.boundary_json) {
            zone.boundary = JSON.parse(zone.boundary_json);
        }
        res.json({ data: zone });
    });

    router.get('/sensors', (req, res) => {
        const { zone_id, type, status } = req.query;
        const sensors = db.querySensors({ zone_id, type, status });
        for (const s of sensors) {
            if (s.thresholds_json) {
                s.thresholds = JSON.parse(s.thresholds_json);
            }
        }
        res.json({ data: sensors });
    });

    router.get('/sensors/:sensorId', (req, res) => {
        const sensor = db.getSensor(req.params.sensorId);
        if (!sensor) return res.status(404).json({ error: 'Sensor not found' });
        if (sensor.thresholds_json) {
            sensor.thresholds = JSON.parse(sensor.thresholds_json);
        }
        res.json({ data: sensor });
    });

    router.get('/sensors/:sensorId/data', (req, res) => {
        const { limit, from, to } = req.query;
        const data = db.getSensorData(
            req.params.sensorId,
            Math.min(parseInt(limit) || 100, 1000),
            from, to
        );
        res.json({ data });
    });

    router.post('/sensors/:sensorId/data', (req, res) => {
        const { value, timestamp, quality } = req.body;
        const sensor = db.getSensor(req.params.sensorId);
        if (!sensor) return res.status(404).json({ error: 'Sensor not found' });

        const ts = timestamp || new Date().toISOString();
        const q = quality || 'good';

        dataBuffer.push(sensor.id, value, ts, q);

        const alertResult = alertEngine.evaluateSensorReading(
            sensor.id, sensor.zone_id, sensor.type, value, sensor.thresholds_json
        );

        if (alertResult) {
            dataBuffer.broadcastAlert(alertResult);
        }

        dataBuffer.broadcastUpdate(sensor.id, value, ts, alertResult);

        res.json({ success: true, alert: alertResult });
    });

    router.get('/alerts', (req, res) => {
        const { zone_id, level, state, type } = req.query;
        const alerts = db.queryAlerts({ zone_id, level, state, type });
        res.json({ data: alerts });
    });

    router.post('/alerts/:alertId/acknowledge', (req, res) => {
        const result = alertEngine.transitionAlert(req.params.alertId, 'acknowledge');
        if (result.error) return res.status(400).json(result);
        dataBuffer.broadcastAlert(result);
        res.json({ data: result });
    });

    router.post('/alerts/:alertId/resolve', (req, res) => {
        alertEngine.resolveAlert(req.params.alertId, 'manual_resolve');
        dataBuffer.broadcastAlert({ action: 'resolved', alert_id: req.params.alertId });
        res.json({ data: { success: true } });
    });

    router.get('/alerts/:alertId/history', (req, res) => {
        const history = alertEngine.getAlertHistory(req.params.alertId);
        res.json({ data: history });
    });

    router.get('/buffer/stats', (req, res) => {
        res.json({ data: dataBuffer.getStats() });
    });

    router.post('/zones', (req, res) => {
        const { id, name, type, description, boundary } = req.body;
        if (!id || !name) return res.status(400).json({ error: 'id and name are required' });
        try {
            db.insertZone({
                id, name, type: type || 'dam',
                description: description || '',
                boundary_json: JSON.stringify(boundary || [])
            });
            res.json({ data: { id, name } });
        } catch (err) {
            res.status(500).json({ error: err.message });
        }
    });

    router.post('/sensors', (req, res) => {
        const { id, zone_id, name, type, x, y, unit, thresholds } = req.body;
        if (!id || !zone_id || !name || !type) {
            return res.status(400).json({ error: 'id, zone_id, name, type are required' });
        }
        try {
            db.insertSensor({
                id, zone_id, name, type,
                x: x || 0, y: y || 0,
                unit: unit || '',
                thresholds_json: JSON.stringify(thresholds || {})
            });
            res.json({ data: { id } });
        } catch (err) {
            res.status(500).json({ error: err.message });
        }
    });

    router.get('/predictions', (req, res) => {
        const { zone_id } = req.query;
        const predictions = predictionEngine.getPredictions(zone_id);
        res.json({ data: predictions });
    });

    router.get('/predictions/:sensorId', (req, res) => {
        const sensor = db.getSensor(req.params.sensorId);
        if (!sensor) return res.status(404).json({ error: 'Sensor not found' });
        const forecast = predictionEngine.predict(sensor.id, sensor.type);
        if (!forecast) {
            return res.json({ data: null, message: 'Insufficient data for prediction' });
        }
        res.json({ data: forecast });
    });

    router.post('/predictions/evaluate', (req, res) => {
        const { sensor_id } = req.body;
        const sensor = db.getSensor(sensor_id);
        if (!sensor) return res.status(404).json({ error: 'Sensor not found' });
        const result = predictionEngine.evaluate(
            sensor.id, sensor.zone_id, sensor.type, sensor.thresholds_json
        );
        res.json({ data: result });
    });

    router.get('/zone-risks', (req, res) => {
        const { zone_id } = req.query;
        const risks = regionAnalyzer.getRisks(zone_id);
        res.json({ data: risks });
    });

    router.post('/zone-risks/analyze', (req, res) => {
        const { zone_id } = req.body;
        if (zone_id) {
            const risk = regionAnalyzer.analyze(zone_id);
            res.json({ data: risk });
        } else {
            regionAnalyzer.analyzeAll();
            res.json({ data: { success: true, message: 'Analyzed all zones' } });
        }
    });

    router.get('/emergency-guide', (req, res) => {
        const { alert_type } = req.query;
        if (alert_type) {
            const guide = emergencyGuideManager.getGuide(alert_type);
            if (!guide) return res.status(404).json({ error: 'Guide not found' });
            res.json({ data: guide });
        } else {
            res.json({ data: emergencyGuideManager.getAllGuides() });
        }
    });

    router.get('/emergency-guide/:alertType/procedures', (req, res) => {
        const procedures = emergencyGuideManager.getProcedures(req.params.alertType);
        res.json({ data: procedures });
    });

    router.get('/emergency-guide/:alertType/contacts', (req, res) => {
        const contacts = emergencyGuideManager.getContacts(req.params.alertType);
        res.json({ data: contacts });
    });

    router.get('/emergency-guide/:alertType/cases', (req, res) => {
        const cases = emergencyGuideManager.getHistoricalCases(req.params.alertType);
        res.json({ data: cases });
    });

    router.get('/export/report', async (req, res) => {
        const { zone_id, sensor_id, from, to, include_data, include_alerts } = req.query;
        try {
            const options = {
                zoneId: zone_id,
                sensorId: sensor_id,
                from,
                to,
                includeSensorData: include_data !== 'false',
                includeAlerts: include_alerts !== 'false'
            };
            const report = await exportService.generateReport(options);
            const filename = exportService.getReportFilename(options);
            const contentType = exportService.getContentType();
            res.setHeader('Content-Type', contentType);
            res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
            res.send(report);
        } catch (err) {
            res.status(500).json({ error: err.message });
        }
    });

    router.post('/export/async', (req, res) => {
        const { zone_id, sensor_id, from, to, include_data, include_alerts } = req.body;
        const options = {
            zoneId: zone_id,
            sensorId: sensor_id,
            from,
            to,
            includeSensorData: include_data !== false,
            includeAlerts: include_alerts !== false
        };
        const result = exportService.submitExport(options);
        res.json({ data: result });
    });

    router.get('/export/async/:exportId/status', (req, res) => {
        const status = exportService.getExportStatus(req.params.exportId);
        if (!status) {
            return res.status(404).json({ error: 'Export task not found' });
        }
        res.json({ data: status });
    });

    router.get('/export/async/:exportId/result', (req, res) => {
        const result = exportService.getExportResult(req.params.exportId);
        if (!result) {
            return res.status(404).json({ error: 'Export result not found' });
        }
        if (result.status !== 'completed') {
            return res.status(400).json({ error: 'Export not completed', status: result.status });
        }
        res.json({ data: result.result });
    });

    router.get('/export/async/:exportId/download/:fileIndex', (req, res) => {
        const result = exportService.getExportResult(req.params.exportId);
        if (!result || result.status !== 'completed') {
            return res.status(404).json({ error: 'Export result not found or not completed' });
        }

        const fileIndex = parseInt(req.params.fileIndex);
        if (isNaN(fileIndex) || fileIndex < 0 || fileIndex >= result.result.files.length) {
            return res.status(404).json({ error: 'File index out of range' });
        }

        const file = result.result.files[fileIndex];
        const buffer = Buffer.from(file.data, 'base64');

        res.setHeader('Content-Type', file.name.endsWith('.pdf') ? 'application/pdf' : 'text/html; charset=utf-8');
        res.setHeader('Content-Disposition', `attachment; filename="${file.name}"`);
        res.send(buffer);
    });

    return router;
}

module.exports = { createApiRouter };
