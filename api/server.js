const express = require('express');
const http = require('http');
const { WebSocketServer } = require('ws');
const path = require('path');
const { initDatabase, seedData } = require('./db');
const { AlertEngine } = require('./services/alertEngine');
const { DataBuffer } = require('./services/dataBuffer');
const { WsHandler } = require('./services/wsHandler');
const { createApiRouter } = require('./routes/api');
const { PredictionEngine } = require('./services/predictiveEngine');
const { RegionAnalyzer } = require('./services/zoneRiskAnalyzer');
const { EmergencyGuideManager } = require('./services/emergencyGuide');
const { ExportService } = require('./services/exportService');

const PORT = process.env.PORT || 3000;

class MonitorServer {
    constructor() {
        this.app = express();
        this.server = http.createServer(this.app);
        this.wss = new WebSocketServer({ server: this.server, path: '/ws' });

        this.app.use(express.json());
        this.app.use(express.static(path.join(__dirname, '..', 'frontend')));

        this.db = initDatabase();
        seedData();

        this.alertEngine = new AlertEngine(this.db);
        this.dataBuffer = new DataBuffer(this.db, this.wss);
        this.wsHandler = new WsHandler(this.wss, this.alertEngine, this.dataBuffer, this.db);
        this.predictionEngine = new PredictionEngine(this.db, this.alertEngine, this.dataBuffer);
        this.regionAnalyzer = new RegionAnalyzer(this.db, this.alertEngine, this.dataBuffer);
        this.emergencyGuideManager = new EmergencyGuideManager();
        this.exportService = new ExportService(this.db);

        this.app.use('/api', createApiRouter(
            this.db, this.alertEngine, this.dataBuffer,
            this.predictionEngine, this.regionAnalyzer,
            this.emergencyGuideManager, this.exportService
        ));

        this.simulator = null;
    }

    start() {
        this.server.listen(PORT, () => {
            console.log(`水利工程安全监测平台已启动: http://localhost:${PORT}`);
            console.log(`WebSocket 服务: ws://localhost:${PORT}/ws`);
            this.startSimulation();
        });
    }

    startSimulation() {
        const sensors = this.db.getAllSensors();
        const sensorMap = new Map();
        for (const s of sensors) {
            sensorMap.set(s.id, s);
        }

        this.simulator = setInterval(() => {
            const batch = [];
            for (const [id, sensor] of sensorMap) {
                const thresholds = JSON.parse(sensor.thresholds_json || '{}');
                const baseValue = thresholds.warning ? thresholds.warning * 0.6 : 1.0;
                const spike = Math.random() < 0.03;
                const value = baseValue * (0.7 + Math.random() * 0.6) + (spike ? baseValue * 1.5 : 0);

                batch.push({ sensorId: id, value: parseFloat(value.toFixed(4)) });
            }

            for (const item of batch) {
                const sensor = sensorMap.get(item.sensorId);
                if (!sensor) continue;

                this.dataBuffer.push(item.sensorId, item.value, new Date().toISOString(), 'good');

                const alertResult = this.alertEngine.evaluateSensorReading(
                    item.sensorId, sensor.zone_id, sensor.type, item.value, sensor.thresholds_json
                );

                if (alertResult) {
                    this.dataBuffer.broadcastAlert(alertResult);
                }

                this.dataBuffer.broadcastUpdate(item.sensorId, item.value, new Date().toISOString(), alertResult);
            }
        }, 3000);
    }

    stop() {
        if (this.simulator) clearInterval(this.simulator);
        this.dataBuffer.destroy();
        this.alertEngine.destroy();
        this.predictionEngine.destroy();
        this.regionAnalyzer.destroy();
        this.exportService.destroy();
        this.server.close();
    }
}

const server = new MonitorServer();
server.start();

process.on('SIGINT', () => {
    server.stop();
    process.exit(0);
});
