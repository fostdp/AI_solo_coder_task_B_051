const { v4: uuidv4 } = require('uuid');

class WsHandler {
    constructor(wss, alertEngine, dataBuffer, db) {
        this.wss = wss;
        this.alertEngine = alertEngine;
        this.dataBuffer = dataBuffer;
        this.db = db;
        this.subscriptions = new Map();

        this.wss.on('connection', (ws, req) => {
            const clientId = `ws-${uuidv4().slice(0, 8)}`;
            ws.clientId = clientId;
            ws.subscriptions = new Set();

            this.sendToClient(ws, {
                type: 'connected',
                data: { client_id: clientId }
            });

            this.sendToClient(ws, {
                type: 'initial_state',
                data: this.getInitialState()
            });

            ws.on('message', (raw) => {
                try {
                    const msg = JSON.parse(raw.toString());
                    this.handleMessage(ws, msg);
                } catch (err) {
                    this.sendToClient(ws, { type: 'error', data: { message: 'Invalid message format' } });
                }
            });

            ws.on('close', () => {
                this.subscriptions.delete(clientId);
            });

            ws.on('error', (err) => {
                console.error(`WebSocket client ${clientId} error:`, err.message);
            });
        });
    }

    handleMessage(ws, msg) {
        switch (msg.type) {
            case 'subscribe_zone':
                ws.subscriptions.add(msg.data.zone_id);
                this.sendToClient(ws, {
                    type: 'subscribed',
                    data: { zone_id: msg.data.zone_id }
                });
                break;

            case 'unsubscribe_zone':
                ws.subscriptions.delete(msg.data.zone_id);
                break;

            case 'acknowledge_alert':
                const ackResult = this.alertEngine.transitionAlert(msg.data.alert_id, 'acknowledge');
                this.dataBuffer.broadcastAlert(ackResult);
                break;

            case 'request_history':
                const history = this.getSensorHistory(msg.data.sensor_id, msg.data.limit);
                this.sendToClient(ws, {
                    type: 'sensor_history',
                    data: { sensor_id: msg.data.sensor_id, records: history }
                });
                break;

            case 'ping':
                this.sendToClient(ws, { type: 'pong', data: { timestamp: Date.now() } });
                break;

            default:
                this.sendToClient(ws, {
                    type: 'error',
                    data: { message: `Unknown message type: ${msg.type}` }
                });
        }
    }

    getInitialState() {
        const zones = this.db.getAllZones();
        const sensors = this.db.getAllSensors();
        const alerts = this.alertEngine.getActiveAlertsForZone();

        return { zones, sensors, active_alerts: alerts };
    }

    getSensorHistory(sensorId, limit) {
        limit = Math.min(limit || 100, 1000);
        return this.db.getSensorData(sensorId, limit);
    }

    sendToClient(ws, data) {
        if (ws.readyState === 1) {
            try {
                ws.send(JSON.stringify(data));
            } catch (err) {
                console.error('Send error:', err.message);
            }
        }
    }

    broadcastToSubscribers(zoneId, message) {
        for (const client of this.wss.clients) {
            if (client.subscriptions && client.subscriptions.has(zoneId)) {
                this.sendToClient(client, message);
            }
        }
    }
}

module.exports = { WsHandler };
