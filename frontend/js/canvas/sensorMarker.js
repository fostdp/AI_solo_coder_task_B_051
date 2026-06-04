const SENSOR_COLORS = {
    normal: '#4caf50',
    warning: '#ff9800',
    critical: '#f44336',
    emergency: '#9c27b0'
};

const SENSOR_RADIUS = 6;
const SENSOR_HIT_RADIUS = 12;

class SensorMarker {
    constructor(ctx, transform) {
        this.ctx = ctx;
        this.transform = transform;
        this.sensors = new Map();
        this.hoveredSensor = null;
        this.selectedSensor = null;
        this.onSensorClick = null;
    }

    updateSensors(sensors) {
        this.sensors.clear();
        for (const s of sensors) {
            this.sensors.set(s.id, {
                ...s,
                lastValue: null,
                status: 'normal',
                alertLevel: null,
                pulsePhase: Math.random() * Math.PI * 2
            });
        }
    }

    updateSensorData(sensorId, value, alertInfo) {
        const sensor = this.sensors.get(sensorId);
        if (!sensor) return;
        sensor.lastValue = value;

        if (alertInfo && alertInfo.alert) {
            sensor.alertLevel = alertInfo.alert.level;
            sensor.status = alertInfo.alert.level;
        } else {
            sensor.alertLevel = null;
            sensor.status = 'normal';
        }
    }

    draw(time) {
        const ctx = this.ctx;
        const t0 = performance.now();

        for (const [id, sensor] of this.sensors) {
            const screen = this.transform.worldToScreen(sensor.x, sensor.y);
            const r = SENSOR_RADIUS * this.transform.scale;

            if (screen.x < -20 || screen.x > ctx.canvas.width + 20 ||
                screen.y < -20 || screen.y > ctx.canvas.height + 20) {
                continue;
            }

            const color = SENSOR_COLORS[sensor.status] || SENSOR_COLORS.normal;

            if (sensor.status === 'emergency' || sensor.status === 'critical') {
                const pulse = Math.sin(time / 500 + sensor.pulsePhase) * 0.5 + 0.5;
                const pulseR = r * (1.5 + pulse * 0.8);
                ctx.beginPath();
                ctx.arc(screen.x, screen.y, pulseR, 0, Math.PI * 2);
                ctx.fillStyle = color + '40';
                ctx.fill();
            }

            ctx.beginPath();
            ctx.arc(screen.x, screen.y, Math.max(r, 3), 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();

            if (this.hoveredSensor === id) {
                ctx.beginPath();
                ctx.arc(screen.x, screen.y, Math.max(r + 3, 6), 0, Math.PI * 2);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 2;
                ctx.stroke();
            }

            if (this.selectedSensor === id) {
                ctx.beginPath();
                ctx.arc(screen.x, screen.y, Math.max(r + 5, 8), 0, Math.PI * 2);
                ctx.strokeStyle = '#7eb8ff';
                ctx.lineWidth = 2;
                ctx.setLineDash([3, 3]);
                ctx.stroke();
                ctx.setLineDash([]);
            }
        }

        const t1 = performance.now();
        return t1 - t0;
    }

    hitTest(screenX, screenY) {
        for (const [id, sensor] of this.sensors) {
            const screen = this.transform.worldToScreen(sensor.x, sensor.y);
            const dist = Math.sqrt(
                Math.pow(screenX - screen.x, 2) +
                Math.pow(screenY - screen.y, 2)
            );
            if (dist < SENSOR_HIT_RADIUS * this.transform.scale) {
                return id;
            }
        }
        return null;
    }

    setHovered(sensorId) {
        this.hoveredSensor = sensorId;
    }

    setSelected(sensorId) {
        this.selectedSensor = sensorId;
    }
}
