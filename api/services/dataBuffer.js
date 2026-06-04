const DATA_BUFFER_MAX_SIZE = 10000;
const FLUSH_INTERVAL = 1000;
const WS_BACKPRESSURE_LIMIT = 100;

class DataBuffer {
    constructor(db, wss) {
        this.db = db;
        this.wss = wss;
        this.writeBuffer = [];
        this.broadcastQueue = [];
        this.isFlushing = false;
        this.flushTimer = null;
        this.stats = {
            buffered: 0,
            flushed: 0,
            dropped: 0,
            broadcastSent: 0,
            broadcastDropped: 0
        };

        this.startFlushCycle();
    }

    push(sensorId, value, timestamp, quality) {
        if (this.writeBuffer.length >= DATA_BUFFER_MAX_SIZE) {
            this.stats.dropped++;
            return false;
        }

        this.writeBuffer.push({
            sensor_id: sensorId,
            value: value,
            timestamp: timestamp || new Date().toISOString(),
            quality: quality || 'good'
        });

        this.stats.buffered++;

        if (this.writeBuffer.length >= 50) {
            this.flush();
        }

        return true;
    }

    broadcastUpdate(sensorId, value, timestamp, alertInfo) {
        const message = JSON.stringify({
            type: 'sensor_update',
            data: {
                sensor_id: sensorId,
                value: value,
                timestamp: timestamp || new Date().toISOString(),
                alert: alertInfo || null
            }
        });

        this.broadcastQueue.push(message);

        if (this.broadcastQueue.length > WS_BACKPRESSURE_LIMIT * 10) {
            this.broadcastQueue.splice(0, this.broadcastQueue.length - WS_BACKPRESSURE_LIMIT * 5);
            this.stats.broadcastDropped += WS_BACKPRESSURE_LIMIT * 5;
        }

        setImmediate(() => this.flushBroadcast());
    }

    broadcastAlert(alertAction) {
        const message = JSON.stringify({
            type: 'alert_update',
            data: alertAction
        });

        this.broadcastQueue.push(message);
        setImmediate(() => this.flushBroadcast());
    }

    flush() {
        if (this.isFlushing || this.writeBuffer.length === 0) return;

        this.isFlushing = true;
        const batch = this.writeBuffer.splice(0, this.writeBuffer.length);

        try {
            for (const item of batch) {
                this.db.insertSensorData(item);
            }
            this.stats.flushed += batch.length;
        } catch (err) {
            console.error('Buffer flush error:', err.message);
            for (const item of batch) {
                this.writeBuffer.push(item);
            }
        }

        this.isFlushing = false;
    }

    flushBroadcast() {
        if (this.broadcastQueue.length === 0) return;

        const messages = this.broadcastQueue.splice(0, this.broadcastQueue.length);

        for (const client of this.wss.clients) {
            if (client.readyState === 1) {
                if (client.bufferedAmount > WS_BACKPRESSURE_LIMIT * 1024) {
                    this.stats.broadcastDropped += messages.length;
                    continue;
                }

                for (const msg of messages) {
                    try {
                        client.send(msg);
                    } catch (err) {
                        this.stats.broadcastDropped++;
                    }
                }
                this.stats.broadcastSent += messages.length;
            }
        }
    }

    startFlushCycle() {
        this.flushTimer = setInterval(() => {
            this.flush();
            this.flushBroadcast();
        }, FLUSH_INTERVAL);
    }

    getStats() {
        return {
            ...this.stats,
            writeBufferLength: this.writeBuffer.length,
            broadcastQueueLength: this.broadcastQueue.length
        };
    }

    destroy() {
        if (this.flushTimer) {
            clearInterval(this.flushTimer);
        }
        this.flush();
        this.flushBroadcast();
    }
}

module.exports = { DataBuffer, DATA_BUFFER_MAX_SIZE, FLUSH_INTERVAL, WS_BACKPRESSURE_LIMIT };
