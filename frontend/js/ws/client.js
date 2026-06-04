class WsClient {
    constructor(url) {
        this.url = url;
        this.ws = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.reconnectDelay = 1000;
        this.heartbeatInterval = null;
        this.handlers = new Map();
        this.subscriptions = new Set();
    }

    connect() {
        try {
            this.ws = new WebSocket(this.url);

            this.ws.onopen = () => {
                console.log('WebSocket connected');
                this.reconnectAttempts = 0;
                this._startHeartbeat();

                for (const zoneId of this.subscriptions) {
                    this.send({ type: 'subscribe_zone', data: { zone_id: zoneId } });
                }

                this._emit('connected', {});
            };

            this.ws.onmessage = (event) => {
                try {
                    const msg = JSON.parse(event.data);
                    this._emit(msg.type, msg.data);
                } catch (err) {
                    console.error('WS message parse error:', err);
                }
            };

            this.ws.onclose = () => {
                console.log('WebSocket disconnected');
                this._stopHeartbeat();
                this._emit('disconnected', {});
                this._scheduleReconnect();
            };

            this.ws.onerror = (err) => {
                console.error('WebSocket error:', err);
            };
        } catch (err) {
            console.error('WebSocket connect error:', err);
            this._scheduleReconnect();
        }
    }

    send(msg) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(msg));
        }
    }

    subscribe(zoneId) {
        this.subscriptions.add(zoneId);
        this.send({ type: 'subscribe_zone', data: { zone_id: zoneId } });
    }

    unsubscribe(zoneId) {
        this.subscriptions.delete(zoneId);
        this.send({ type: 'unsubscribe_zone', data: { zone_id: zoneId } });
    }

    on(eventType, handler) {
        if (!this.handlers.has(eventType)) {
            this.handlers.set(eventType, []);
        }
        this.handlers.get(eventType).push(handler);
    }

    off(eventType, handler) {
        const list = this.handlers.get(eventType);
        if (list) {
            const idx = list.indexOf(handler);
            if (idx >= 0) list.splice(idx, 1);
        }
    }

    _emit(eventType, data) {
        const list = this.handlers.get(eventType);
        if (list) {
            for (const handler of list) {
                try {
                    handler(data);
                } catch (err) {
                    console.error(`Handler error for ${eventType}:`, err);
                }
            }
        }
    }

    _startHeartbeat() {
        this.heartbeatInterval = setInterval(() => {
            this.send({ type: 'ping', data: { timestamp: Date.now() } });
        }, 30000);
    }

    _stopHeartbeat() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }
    }

    _scheduleReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnect attempts reached');
            return;
        }

        const delay = this.reconnectDelay * Math.pow(1.5, this.reconnectAttempts);
        this.reconnectAttempts++;

        setTimeout(() => {
            console.log(`Reconnect attempt ${this.reconnectAttempts}`);
            this.connect();
        }, Math.min(delay, 30000));
    }

    disconnect() {
        this._stopHeartbeat();
        this.reconnectAttempts = this.maxReconnectAttempts;
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }
}
