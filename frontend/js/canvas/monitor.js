class MonitorCanvas {
    constructor(canvasId) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext('2d');
        this.transform = new ViewTransform(this.canvas);
        this.sensorMarker = new SensorMarker(this.ctx, this.transform);
        this.crossSection = new CrossSection(this.ctx, this.transform);
        this.animationId = null;
        this.drawStats = {
            totalFrameTime: 0,
            sensorDrawTime: 0,
            crossSectionDrawTime: 0,
            frameCount: 0,
            lastFps: 0,
            fpsCounter: 0,
            fpsTime: 0
        };

        this._resize();
        window.addEventListener('resize', () => this._resize());

        this.transform.onTransformChange = () => {
            this._requestDraw();
        };

        this.canvas.addEventListener('click', (e) => {
            const rect = this.canvas.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            const hitId = this.sensorMarker.hitTest(x, y);
            if (hitId && this.sensorMarker.onSensorClick) {
                this.sensorMarker.onSensorClick(hitId);
            }
        });

        this.canvas.addEventListener('mousemove', (e) => {
            const rect = this.canvas.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            const hitId = this.sensorMarker.hitTest(x, y);
            this.sensorMarker.setHovered(hitId);
            this.canvas.style.cursor = hitId ? 'pointer' : 'grab';
        });

        this._startRenderLoop();
    }

    _resize() {
        const container = this.canvas.parentElement;
        const dpr = window.devicePixelRatio || 1;
        this.canvas.width = container.clientWidth * dpr;
        this.canvas.height = container.clientHeight * dpr;
        this.ctx.scale(dpr, dpr);
        this.canvas.style.width = container.clientWidth + 'px';
        this.canvas.style.height = container.clientHeight + 'px';
    }

    _requestDraw() {
    }

    _startRenderLoop() {
        const loop = (time) => {
            this._drawFrame(time);
            this.animationId = requestAnimationFrame(loop);
        };
        this.animationId = requestAnimationFrame(loop);
    }

    _drawFrame(time) {
        const t0 = performance.now();

        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.ctx.fillStyle = '#0a1628';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        this.ctx.save();
        this.ctx.translate(this.transform.offsetX, this.transform.offsetY);
        this.ctx.scale(this.transform.scale, this.transform.scale);

        const crossSectionTime = this.crossSection.draw(time);
        const sensorTime = this.sensorMarker.draw(time);

        this.ctx.restore();

        this._drawZoneHighlightLabels();

        this._drawPerformanceOverlay(time, crossSectionTime, sensorTime);

        const totalFrameTime = performance.now() - t0;
        this.drawStats.totalFrameTime = totalFrameTime;
        this.drawStats.sensorDrawTime = sensorTime;
        this.drawStats.crossSectionDrawTime = crossSectionTime;
        this.drawStats.frameCount++;
        this.drawStats.fpsCounter++;

        if (time - this.drawStats.fpsTime >= 1000) {
            this.drawStats.lastFps = this.drawStats.fpsCounter;
            this.drawStats.fpsCounter = 0;
            this.drawStats.fpsTime = time;
        }
    }

    _drawPerformanceOverlay(time, crossSectionTime, sensorTime) {
        const ctx = this.ctx;
        ctx.save();
        ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
        ctx.fillRect(10, this.canvas.height / (window.devicePixelRatio || 1) - 50, 200, 44);
        ctx.font = '11px monospace';
        ctx.fillStyle = '#8899aa';
        const yBase = this.canvas.height / (window.devicePixelRatio || 1) - 34;
        ctx.fillText(`FPS: ${this.drawStats.lastFps}  Frame: ${this.drawStats.totalFrameTime.toFixed(1)}ms`, 16, yBase);
        ctx.fillText(`Sensors: ${sensorTime.toFixed(2)}ms  Zones: ${crossSectionTime.toFixed(2)}ms`, 16, yBase + 16);
        ctx.restore();
    }

    updateSensors(sensors) {
        this.sensorMarker.updateSensors(sensors);
    }

    updateSensorData(sensorId, value, alertInfo) {
        this.sensorMarker.updateSensorData(sensorId, value, alertInfo);
    }

    updateZones(zones) {
        this.crossSection.updateZones(zones);
    }

    updateZoneRisks(risks) {
        this.crossSection.updateZoneRisks(risks);
    }

    setCrossSectionData(data) {
        this.crossSection.setCrossSectionData(data);
    }

    _drawZoneHighlightLabels() {
        const ctx = this.ctx;
        const labels = this.crossSection.getHighlightLabels();
        const dpr = window.devicePixelRatio || 1;

        for (const label of labels) {
            ctx.save();
            ctx.font = `${Math.max(12, 14 * this.transform.scale)}px sans-serif`;
            const textMetrics = ctx.measureText(label.text);
            const labelWidth = textMetrics.width + 20;
            const labelHeight = 24;
            const x = label.x - labelWidth / 2;
            const y = label.y - labelHeight / 2;

            ctx.fillStyle = 'rgba(255, 0, 0, 0.9)';
            ctx.beginPath();
            ctx.roundRect(x, y, labelWidth, labelHeight, 4);
            ctx.fill();

            ctx.fillStyle = '#ffffff';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(label.text, label.x, label.y);

            ctx.restore();
        }
    }

    zoomIn() {
        this.transform.zoomIn();
    }

    zoomOut() {
        this.transform.zoomOut();
    }

    resetView() {
        this.transform.reset();
    }

    destroy() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
        }
    }
}
