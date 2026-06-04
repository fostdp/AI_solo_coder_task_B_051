class App {
    constructor() {
        this.wsClient = null;
        this.monitorCanvas = null;
        this.alertPanel = null;
        this.zones = [];
        this.sensors = [];
        this.sensorMap = new Map();
        this.selectedZoneId = null;
        this.selectedSensorId = null;
        this.trendData = [];
        this.zoneRisks = [];
        this.predictions = [];
    }

    async init() {
        this.monitorCanvas = new MonitorCanvas('monitor-canvas');
        this.alertPanel = new AlertPanel();

        this.alertPanel.onAcknowledge = (alertId) => {
            if (this.wsClient) {
                this.wsClient.send({ type: 'acknowledge_alert', data: { alert_id: alertId } });
            }
        };

        this.alertPanel.onResolve = (alertId) => {
            if (this.wsClient) {
                fetch(`/api/alerts/${alertId}/resolve`, { method: 'POST' })
                    .then(() => this.alertPanel.removeAlert(alertId))
                    .catch(err => console.error('Resolve alert error:', err));
            }
        };

        this.alertPanel.onShowEmergencyGuide = (alertType, alert) => {
            this.showEmergencyGuide(alertType, alert);
        };

        this.monitorCanvas.sensorMarker.onSensorClick = (sensorId) => {
            this.selectSensor(sensorId);
        };

        this._bindControls();
        this._connectWebSocket();
        this._startClock();
        this._loadInitialZoneRisks();
    }

    _connectWebSocket() {
        const wsUrl = `ws://${window.location.host}/ws`;
        this.wsClient = new WsClient(wsUrl);

        this.wsClient.on('connected', () => {
            const statusEl = document.getElementById('ws-status');
            statusEl.innerHTML = '<span class="status-dot connected"></span><span>已连接</span>';
        });

        this.wsClient.on('disconnected', () => {
            const statusEl = document.getElementById('ws-status');
            statusEl.innerHTML = '<span class="status-dot disconnected"></span><span>连接断开</span>';
        });

        this.wsClient.on('initial_state', (data) => {
            this.zones = data.zones || [];
            this.sensors = data.sensors || [];
            this.sensorMap.clear();
            for (const s of this.sensors) {
                if (s.thresholds_json) {
                    s.thresholds = JSON.parse(s.thresholds_json);
                }
                this.sensorMap.set(s.id, s);
            }

            this.monitorCanvas.updateZones(this.zones);
            this.monitorCanvas.updateSensors(this.sensors);
            this._renderZoneList();
            this._updateSensorCount();

            if (data.active_alerts) {
                this.alertPanel.setAlerts(data.active_alerts);
                this._updateActiveAlertCount(data.active_alerts.length);
            }
        });

        this.wsClient.on('sensor_update', (data) => {
            this.monitorCanvas.updateSensorData(data.sensor_id, data.value, data.alert ? { alert: data.alert } : null);
        });

        this.wsClient.on('alert_update', (data) => {
            if (data.action === 'created' && data.alert) {
                this.alertPanel.addAlert(data.alert);
                this._updateActiveAlertCount(this.alertPanel.activeAlerts.size);
            } else if (data.action === 'upgraded' && data.alert) {
                this.alertPanel.updateAlert(data.alert.id, data.alert);
            } else if (data.action === 'resolved') {
                this.alertPanel.removeAlert(data.alert_id || data.alert?.id);
                this._updateActiveAlertCount(this.alertPanel.activeAlerts.size);
            } else if (data.action === 'transitioned' && data.alert) {
                this.alertPanel.updateAlert(data.alert.id, data.alert);
            } else if (data.action === 'escalated' && data.alert) {
                this.alertPanel.updateAlert(data.alert.id, data.alert);
            } else if (data.action === 'converged') {
                this._showConvergenceNotification(data);
            }
        });

        this.wsClient.on('zone_risk_update', (data) => {
            this._handleZoneRiskUpdate(data);
        });

        this.wsClient.on('sensor_history', (data) => {
            this._drawTrendChart(data.records || []);
        });

        this.wsClient.connect();
    }

    _bindControls() {
        document.getElementById('btn-zoom-in').addEventListener('click', () => this.monitorCanvas.zoomIn());
        document.getElementById('btn-zoom-out').addEventListener('click', () => this.monitorCanvas.zoomOut());
        document.getElementById('btn-reset-view').addEventListener('click', () => this.monitorCanvas.resetView());

        document.getElementById('sensor-type-filter').addEventListener('change', (e) => {
            this._filterSensors();
        });

        document.getElementById('sensor-status-filter').addEventListener('change', (e) => {
            this._filterSensors();
        });

        document.getElementById('btn-export-report').addEventListener('click', () => this.showExportModal());

        document.getElementById('close-emergency-guide').addEventListener('click', () => this.hideEmergencyGuide());
        document.getElementById('emergency-guide-modal').addEventListener('click', (e) => {
            if (e.target.id === 'emergency-guide-modal') this.hideEmergencyGuide();
        });

        document.getElementById('close-export-modal').addEventListener('click', () => this.hideExportModal());
        document.getElementById('export-modal').addEventListener('click', (e) => {
            if (e.target.id === 'export-modal') this.hideExportModal();
        });

        document.getElementById('btn-do-export').addEventListener('click', () => this.doExport());
    }

    _renderZoneList() {
        const listEl = document.getElementById('zone-list');
        listEl.innerHTML = '';

        for (const zone of this.zones) {
            const el = document.createElement('div');
            el.className = `zone-item ${this.selectedZoneId === zone.id ? 'active' : ''}`;

            const alertCount = this.alertPanel.activeAlerts
                ? Array.from(this.alertPanel.activeAlerts.values())
                    .filter(a => a.zone_id === zone.id).length
                : 0;

            el.innerHTML = `
                <span class="zone-name">${zone.name}</span>
                <span class="zone-alerts ${alertCount > 0 ? 'has-alerts' : ''}">${alertCount}</span>
            `;

            el.addEventListener('click', () => {
                this.selectedZoneId = zone.id;
                this.wsClient.subscribe(zone.id);
                this._renderZoneList();
                this._filterSensors();
            });

            listEl.appendChild(el);
        }
    }

    _filterSensors() {
        const typeFilter = document.getElementById('sensor-type-filter').value;
        const statusFilter = document.getElementById('sensor-status-filter').value;

        let filtered = this.sensors;

        if (this.selectedZoneId) {
            filtered = filtered.filter(s => s.zone_id === this.selectedZoneId);
        }

        if (typeFilter) {
            filtered = filtered.filter(s => s.type === typeFilter);
        }

        if (statusFilter) {
            filtered = filtered.filter(s => {
                const marker = this.monitorCanvas.sensorMarker.sensors.get(s.id);
                return marker && marker.status === statusFilter;
            });
        }

        this.monitorCanvas.updateSensors(filtered);
    }

    selectSensor(sensorId) {
        this.selectedSensorId = sensorId;
        this.monitorCanvas.sensorMarker.setSelected(sensorId);

        const sensor = this.sensorMap.get(sensorId);
        if (!sensor) return;

        const detailEl = document.getElementById('sensor-detail');
        const typeNames = {
            seepage: '渗流', displacement: '位移', stress: '应力',
            crack: '裂缝', water_level: '水位'
        };

        detailEl.innerHTML = `
            <div class="detail-row"><span class="detail-label">ID</span><span class="detail-value">${sensor.id}</span></div>
            <div class="detail-row"><span class="detail-label">名称</span><span class="detail-value">${sensor.name}</span></div>
            <div class="detail-row"><span class="detail-label">类型</span><span class="detail-value">${typeNames[sensor.type] || sensor.type}</span></div>
            <div class="detail-row"><span class="detail-label">位置</span><span class="detail-value">(${sensor.x.toFixed(1)}, ${sensor.y.toFixed(1)})</span></div>
            <div class="detail-row"><span class="detail-label">单位</span><span class="detail-value">${sensor.unit}</span></div>
        `;

        if (this.wsClient) {
            this.wsClient.send({
                type: 'request_history',
                data: { sensor_id: sensorId, limit: 50 }
            });
        }
    }

    _drawTrendChart(records) {
        const canvas = document.getElementById('trend-canvas');
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        canvas.width = 280 * dpr;
        canvas.height = 160 * dpr;
        ctx.scale(dpr, dpr);

        const w = 280, h = 160;
        const padding = { top: 20, right: 10, bottom: 30, left: 45 };
        const chartW = w - padding.left - padding.right;
        const chartH = h - padding.top - padding.bottom;

        ctx.clearRect(0, 0, w, h);
        ctx.fillStyle = '#0a1628';
        ctx.fillRect(0, 0, w, h);

        if (records.length < 2) return;

        const values = records.map(r => r.value);
        const minVal = Math.min(...values);
        const maxVal = Math.max(...values);
        const valRange = maxVal - minVal || 1;

        ctx.strokeStyle = 'rgba(26, 58, 92, 0.6)';
        ctx.lineWidth = 0.5;
        for (let i = 0; i <= 4; i++) {
            const y = padding.top + (chartH / 4) * i;
            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(w - padding.right, y);
            ctx.stroke();

            ctx.fillStyle = '#8899aa';
            ctx.font = '10px monospace';
            ctx.textAlign = 'right';
            ctx.fillText((maxVal - (valRange / 4) * i).toFixed(2), padding.left - 5, y + 3);
        }

        ctx.beginPath();
        ctx.strokeStyle = '#4caf50';
        ctx.lineWidth = 1.5;

        for (let i = 0; i < records.length; i++) {
            const x = padding.left + (i / (records.length - 1)) * chartW;
            const y = padding.top + chartH - ((records[i].value - minVal) / valRange) * chartH;

            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        }
        ctx.stroke();

        const lastVal = records[records.length - 1].value;
        const lastX = padding.left + chartW;
        const lastY = padding.top + chartH - ((lastVal - minVal) / valRange) * chartH;

        ctx.beginPath();
        ctx.arc(lastX, lastY, 3, 0, Math.PI * 2);
        ctx.fillStyle = '#4caf50';
        ctx.fill();
    }

    _updateSensorCount() {
        document.getElementById('sensor-count').textContent = `传感器: ${this.sensors.length}`;
    }

    _updateActiveAlertCount(count) {
        document.getElementById('active-alerts').textContent = `活跃告警: ${count}`;
    }

    _showConvergenceNotification(data) {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = 'toast critical';
        toast.textContent = `区域 ${data.zoneId} 发生告警收敛: ${data.alertCount} 个告警合并`;
        container.appendChild(toast);
        setTimeout(() => { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 8000);
    }

    _startClock() {
        setInterval(() => {
            document.getElementById('server-time').textContent =
                new Date().toLocaleTimeString('zh-CN');
        }, 1000);
    }

    _loadInitialZoneRisks() {
        fetch('/api/zone-risks')
            .then(res => res.json())
            .then(data => {
                this.zoneRisks = data.data || [];
                this.monitorCanvas.updateZoneRisks(this.zoneRisks);
                this._updateZoneRiskCount();
            })
            .catch(err => console.error('Load zone risks error:', err));
    }

    _handleZoneRiskUpdate(data) {
        const { action, risk, alert } = data;

        if (action === 'created' || action === 'updated') {
            const existingIndex = this.zoneRisks.findIndex(r => r.zone_id === risk.zone_id);
            if (existingIndex >= 0) {
                this.zoneRisks[existingIndex] = risk;
            } else {
                this.zoneRisks.push(risk);
            }

            if (alert) {
                this.alertPanel.addAlert(alert);
                this._updateActiveAlertCount(this.alertPanel.activeAlerts.size);
            }

            this._showToast(`⚠️ ${risk.zone_name} 触发坝段级风险！${risk.alerted_count}/${risk.total_count} 个传感器告警`, 'critical');
        } else if (action === 'cleared') {
            this.zoneRisks = this.zoneRisks.filter(r => r.zone_id !== risk.zone_id);
            this._showToast(`✅ ${risk.zone_name} 坝段级风险已解除`, 'info');
        }

        this.monitorCanvas.updateZoneRisks(this.zoneRisks.filter(r => r.active));
        this._updateZoneRiskCount();
        this._renderZoneList();
    }

    _updateZoneRiskCount() {
        const activeRisks = this.zoneRisks.filter(r => r.active).length;
        document.getElementById('zone-risks-count').textContent = `区域风险: ${activeRisks}`;
    }

    _updatePredictionCount(count) {
        document.getElementById('predictions-count').textContent = `预测预警: ${count}`;
    }

    _showToast(message, level = 'info') {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${level}`;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(() => { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 6000);
    }

    async showEmergencyGuide(alertType, alert) {
        try {
            const res = await fetch(`/api/emergency-guide?alert_type=${alertType}`);
            const data = await res.json();
            if (!data.data) return;

            const guide = data.data;
            const typeNames = { seepage: '渗流', displacement: '位移', stress: '应力', crack: '裂缝', water_level: '水位' };
            document.getElementById('emergency-guide-title').textContent =
                `应急响应指引 - ${typeNames[alertType] || alertType}`;

            let html = '';

            if (alert) {
                html += `
                <div class="emergency-section">
                    <h3>当前告警</h3>
                    <div class="case-item" style="border-left-color: ${alert.level === 'emergency' ? '#9c27b0' : '#f44336'}">
                        <div class="case-header">
                            <span class="case-id">${alert.level.toUpperCase()}</span>
                            <span class="case-date">${new Date(alert.started_at).toLocaleString('zh-CN')}</span>
                        </div>
                        <div class="case-detail">
                            <span class="label">传感器:</span>
                            <span class="value">${alert.sensor_id || '区域级'}</span>
                        </div>
                        <div class="case-detail">
                            <span class="label">告警消息:</span>
                            <span class="value">${alert.message}</span>
                        </div>
                    </div>
                </div>`;
            }

            html += `<div class="emergency-section"><h3>标准处理流程</h3>`;
            for (const step of guide.standardProcedures) {
                html += `
                <div class="procedure-step">
                    <span class="step-number">${step.step}</span>
                    <span class="step-title">${step.title}</span>
                    <div class="step-content">${step.content}</div>
                </div>`;
            }
            html += `</div>`;

            html += `<div class="emergency-section"><h3>应急联系人</h3>`;
            for (const contact of guide.contacts) {
                html += `
                <div class="contact-item">
                    <div class="contact-info">
                        <div class="contact-name">${contact.name}</div>
                        <div class="contact-role">${contact.role} · ${contact.department}</div>
                    </div>
                    <div class="contact-phone">${contact.phone}</div>
                </div>`;
            }
            html += `</div>`;

            html += `<div class="emergency-section"><h3>历史类似案例</h3>`;
            for (const caseItem of guide.historicalCases) {
                html += `
                <div class="case-item">
                    <div class="case-header">
                        <span class="case-id">${caseItem.id}</span>
                        <span class="case-date">${caseItem.date}</span>
                    </div>
                    <div class="case-detail">
                        <span class="label">位置:</span>
                        <span class="value">${caseItem.location}</span>
                    </div>
                    <div class="case-detail">
                        <span class="label">描述:</span>
                        <span class="value">${caseItem.description}</span>
                    </div>
                    <div class="case-detail">
                        <span class="label">根因:</span>
                        <span class="value">${caseItem.rootCause}</span>
                    </div>
                    <div class="case-detail">
                        <span class="label">处置方案:</span>
                        <span class="value">${caseItem.solution}</span>
                    </div>
                    <div class="case-detail">
                        <span class="label">结果:</span>
                        <span class="value">${caseItem.outcome}</span>
                    </div>
                    <div class="case-detail">
                        <span class="label">经验教训:</span>
                        <span class="value">${caseItem.lessonsLearned}</span>
                    </div>
                </div>`;
            }
            html += `</div>`;

            document.getElementById('emergency-guide-content').innerHTML = html;
            document.getElementById('emergency-guide-modal').classList.remove('hidden');
        } catch (err) {
            console.error('Load emergency guide error:', err);
        }
    }

    hideEmergencyGuide() {
        document.getElementById('emergency-guide-modal').classList.add('hidden');
    }

    showExportModal() {
        const zoneSelect = document.getElementById('export-zone');
        zoneSelect.innerHTML = '<option value="">全部区域</option>';
        for (const zone of this.zones) {
            zoneSelect.innerHTML += `<option value="${zone.id}">${zone.name}</option>`;
        }

        const now = new Date();
        const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
        document.getElementById('export-from').value = yesterday.toISOString().slice(0, 16);
        document.getElementById('export-to').value = now.toISOString().slice(0, 16);

        document.getElementById('export-modal').classList.remove('hidden');
    }

    hideExportModal() {
        document.getElementById('export-modal').classList.add('hidden');
    }

    async doExport() {
        const zoneId = document.getElementById('export-zone').value;
        const from = document.getElementById('export-from').value;
        const to = document.getElementById('export-to').value;
        const includeData = document.getElementById('export-include-data').checked;
        const includeAlerts = document.getElementById('export-include-alerts').checked;

        const btn = document.getElementById('btn-do-export');
        const originalText = btn.textContent;
        btn.textContent = '正在生成...';
        btn.disabled = true;

        try {
            const params = new URLSearchParams();
            if (zoneId) params.append('zone_id', zoneId);
            if (from) params.append('from', new Date(from).toISOString());
            if (to) params.append('to', new Date(to).toISOString());
            params.append('include_data', includeData);
            params.append('include_alerts', includeAlerts);

            const res = await fetch(`/api/export/report?${params.toString()}`);
            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            const filename = res.headers.get('Content-Disposition')?.split('filename=')[1]?.replace(/"/g, '') || '监测报告.pdf';
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);

            this._showToast('报告导出成功', 'info');
            this.hideExportModal();
        } catch (err) {
            console.error('Export error:', err);
            this._showToast('报告导出失败: ' + err.message, 'critical');
        } finally {
            btn.textContent = originalText;
            btn.disabled = false;
        }
    }
}

const app = new App();
app.init();
