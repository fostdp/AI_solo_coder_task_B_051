const ZONE_FILL_COLORS = {
    dam: 'rgba(30, 80, 140, 0.35)',
    spillway: 'rgba(30, 120, 80, 0.35)',
    tunnel: 'rgba(80, 30, 120, 0.35)'
};

const ZONE_STROKE_COLORS = {
    dam: '#2a6cb8',
    spillway: '#2ab870',
    tunnel: '#8a2ab8'
};

class CrossSection {
    constructor(ctx, transform) {
        this.ctx = ctx;
        this.transform = transform;
        this.zones = [];
        this.crossSectionData = null;
        this.zoneRisks = new Map();
        this.highlightLabels = [];
    }

    updateZones(zones) {
        this.zones = zones;
    }

    setCrossSectionData(data) {
        this.crossSectionData = data;
    }

    updateZoneRisks(risks) {
        this.zoneRisks.clear();
        for (const risk of risks) {
            if (risk.active) {
                this.zoneRisks.set(risk.zone_id, risk);
            }
        }
    }

    draw(time) {
        const ctx = this.ctx;
        const t0 = performance.now();

        this.highlightLabels = [];

        for (const zone of this.zones) {
            if (!zone.boundary) continue;
            const risk = this.zoneRisks.get(zone.id);
            this._drawZonePolygon(zone, risk, time);
        }

        if (this.crossSectionData) {
            this._drawCrossSection();
        }

        this._drawGrid();

        const t1 = performance.now();
        return t1 - t0;
    }

    getHighlightLabels() {
        return this.highlightLabels;
    }

    _drawZonePolygon(zone, risk, time) {
        const ctx = this.ctx;
        const boundary = zone.boundary;
        if (!boundary || boundary.length < 3) return;

        const screenPoints = boundary.map(p => this.transform.worldToScreen(p.x, p.y));

        ctx.beginPath();
        ctx.moveTo(screenPoints[0].x, screenPoints[0].y);

        for (let i = 1; i < screenPoints.length; i++) {
            const prev = screenPoints[i - 1];
            const curr = screenPoints[i];
            const cpx = (prev.x + curr.x) / 2;
            const cpy = (prev.y + curr.y) / 2;
            ctx.quadraticCurveTo(prev.x, prev.y, cpx, cpy);
        }

        ctx.closePath();

        let fillColor = ZONE_FILL_COLORS[zone.type] || ZONE_FILL_COLORS.dam;
        let strokeColor = ZONE_STROKE_COLORS[zone.type] || ZONE_STROKE_COLORS.dam;
        let lineWidth = 2 * this.transform.scale;

        if (risk && risk.active) {
            const pulse = Math.sin(time / 300) * 0.3 + 0.7;
            const alpha = Math.floor(100 + 55 * pulse).toString(16).padStart(2, '0');
            fillColor = `rgba(255, 0, 0, ${0.4 + 0.2 * pulse})`;
            strokeColor = `#ff0000`;
            lineWidth = 4 * this.transform.scale;

            const centroid = this._computeCentroid(screenPoints);
            this.highlightLabels.push({
                x: centroid.x,
                y: centroid.y - 30 * this.transform.scale,
                text: `⚠️ ${zone.name} - 坝段级风险 (${risk.alerted_count}/${risk.total_count})`,
                zoneId: zone.id
            });
        }

        ctx.fillStyle = fillColor;
        ctx.fill();
        ctx.strokeStyle = strokeColor;
        ctx.lineWidth = lineWidth;
        ctx.stroke();

        const centroid = this._computeCentroid(screenPoints);
        const label = zone.name;
        ctx.font = `${Math.max(12, 14 * this.transform.scale)}px sans-serif`;
        ctx.fillStyle = risk && risk.active ? '#ffffff' : '#7eb8ff';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(label, centroid.x, centroid.y);
    }

    _drawCrossSection() {
        const ctx = this.ctx;
        const data = this.crossSectionData;
        if (!data || !data.points || data.points.length < 2) return;

        const screenPoints = data.points.map(p => this.transform.worldToScreen(p.x, p.y));

        ctx.beginPath();
        ctx.moveTo(screenPoints[0].x, screenPoints[0].y);

        for (let i = 1; i < screenPoints.length; i++) {
            ctx.lineTo(screenPoints[i].x, screenPoints[i].y);
        }

        if (data.closed) {
            ctx.closePath();
        }

        ctx.strokeStyle = data.strokeColor || '#4a8ac0';
        ctx.lineWidth = (data.lineWidth || 2) * this.transform.scale;
        ctx.stroke();

        if (data.fillColor) {
            ctx.fillStyle = data.fillColor;
            ctx.fill();
        }
    }

    _drawGrid() {
        const ctx = this.ctx;
        const w = ctx.canvas.width;
        const h = ctx.canvas.height;

        const gridSize = 100 * this.transform.scale;
        if (gridSize < 20) return;

        const startWorld = this.transform.screenToWorld(0, 0);
        const endWorld = this.transform.screenToWorld(w, h);

        const startX = Math.floor(startWorld.x / 100) * 100;
        const startY = Math.floor(startWorld.y / 100) * 100;

        ctx.strokeStyle = 'rgba(26, 58, 92, 0.4)';
        ctx.lineWidth = 0.5;

        for (let wx = startX; wx <= endWorld.x; wx += 100) {
            const s = this.transform.worldToScreen(wx, 0);
            ctx.beginPath();
            ctx.moveTo(s.x, 0);
            ctx.lineTo(s.x, h);
            ctx.stroke();
        }

        for (let wy = startY; wy <= endWorld.y; wy += 100) {
            const s = this.transform.worldToScreen(0, wy);
            ctx.beginPath();
            ctx.moveTo(0, s.y);
            ctx.lineTo(w, s.y);
            ctx.stroke();
        }
    }

    _computeCentroid(points) {
        let cx = 0, cy = 0;
        for (const p of points) {
            cx += p.x;
            cy += p.y;
        }
        return { x: cx / points.length, y: cy / points.length };
    }
}
