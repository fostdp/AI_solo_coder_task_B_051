class AlertPanel {
    constructor() {
        this.alertListEl = document.getElementById('alert-list');
        this.activeAlerts = new Map();
        this.maxAlerts = 50;
        this.onShowEmergencyGuide = null;
    }

    addAlert(alert) {
        this.activeAlerts.set(alert.id, alert);
        this._render();
        this._showToast(alert);

        if (this._isHighRisk(alert)) {
            this._autoShowEmergencyGuide(alert);
        }
    }

    _isHighRisk(alert) {
        if (alert.is_zone_risk) return true;
        if (alert.level === 'critical' || alert.level === 'emergency') {
            return alert.type !== 'zone_risk';
        }
        return false;
    }

    _autoShowEmergencyGuide(alert) {
        if (this.onShowEmergencyGuide) {
            const alertType = alert.is_zone_risk ? 'seepage' : alert.type;
            this.onShowEmergencyGuide(alertType, alert);
        }
    }

    updateAlert(alertId, data) {
        const alert = this.activeAlerts.get(alertId);
        if (alert) {
            Object.assign(alert, data);
            this._render();
        }
    }

    removeAlert(alertId) {
        this.activeAlerts.delete(alertId);
        this._render();
    }

    setAlerts(alerts) {
        this.activeAlerts.clear();
        for (const a of alerts) {
            this.activeAlerts.set(a.id, a);
        }
        this._render();
    }

    _render() {
        this.alertListEl.innerHTML = '';
        const sorted = Array.from(this.activeAlerts.values())
            .sort((a, b) => {
                const levelOrder = { emergency: 0, critical: 1, warning: 2, info: 3 };
                return (levelOrder[a.level] || 99) - (levelOrder[b.level] || 99);
            });

        const toShow = sorted.slice(0, this.maxAlerts);

        for (const alert of toShow) {
            const el = document.createElement('div');
            let className = `alert-item level-${alert.level}`;
            if (alert.is_prediction) className += ' is-prediction';
            if (alert.is_zone_risk) className += ' is-zone-risk';
            el.className = className;

            const levelNames = {
                info: '提示', warning: '警告',
                critical: '严重', emergency: '紧急'
            };

            const timeStr = alert.started_at
                ? new Date(alert.started_at).toLocaleTimeString('zh-CN')
                : '';

            el.innerHTML = `
                <div class="alert-header">
                    <span class="alert-level">${levelNames[alert.level] || alert.level}</span>
                    <span class="alert-time">${timeStr}</span>
                </div>
                <div class="alert-msg">${alert.message || ''}</div>
                ${alert.state !== 'acknowledged' && alert.state !== 'resolved' ? `
                <div class="alert-actions">
                    <button class="btn-ack" data-alert-id="${alert.id}">确认</button>
                    <button class="btn-resolve" data-alert-id="${alert.id}">解除</button>
                    ${!alert.is_zone_risk ? `<button class="btn-guide" data-alert-id="${alert.id}" data-alert-type="${alert.type}">应急指引</button>` : ''}
                </div>
                ` : ''}
            `;

            this.alertListEl.appendChild(el);
        }

        this.alertListEl.querySelectorAll('.btn-ack').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const alertId = btn.dataset.alertId;
                if (this.onAcknowledge) this.onAcknowledge(alertId);
            });
        });

        this.alertListEl.querySelectorAll('.btn-resolve').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const alertId = btn.dataset.alertId;
                if (this.onResolve) this.onResolve(alertId);
            });
        });

        this.alertListEl.querySelectorAll('.btn-guide').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const alertType = btn.dataset.alertType;
                const alertId = btn.dataset.alertId;
                if (this.onShowEmergencyGuide) {
                    this.onShowEmergencyGuide(alertType, this.activeAlerts.get(alertId));
                }
            });
        });
    }

    _showToast(alert) {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${alert.level}`;
        toast.textContent = alert.message || `新告警: ${alert.level}`;
        container.appendChild(toast);

        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 5000);
    }
}
