let PDFDocument = null;
try {
    PDFDocument = require('pdfkit');
} catch (e) {
    console.warn('pdfkit not installed, PDF export will use fallback');
}

class PDFExporter {
    constructor(db) {
        this.db = db;
    }

    async generateReport(options) {
        const { zoneId, sensorId, from, to, includeSensorData, includeAlerts } = options;

        const sensors = sensorId
            ? [this.db.getSensor(sensorId)].filter(Boolean)
            : zoneId
                ? this.db.getSensorsByZone(zoneId)
                : this.db.getAllSensors();

        const alerts = this.db.queryAlerts({
            zone_id: zoneId,
            state: undefined
        }).filter(a => {
            if (from && a.started_at < from) return false;
            if (to && a.started_at > to) return false;
            return true;
        });

        if (PDFDocument) {
            return this.generateWithPDFKit(sensors, alerts, from, to, includeSensorData, includeAlerts);
        } else {
            return this.generateFallbackHTML(sensors, alerts, from, to, includeSensorData, includeAlerts);
        }
    }

    generateWithPDFKit(sensors, alerts, from, to, includeSensorData, includeAlerts) {
        return new Promise((resolve, reject) => {
            const doc = new PDFDocument({ size: 'A4', margin: 50 });
            const chunks = [];

            doc.on('data', chunk => chunks.push(chunk));
            doc.on('end', () => resolve(Buffer.concat(chunks)));
            doc.on('error', reject);

            this.renderHeader(doc, from, to);

            const sensorIds = sensors.map(s => s.id);
            const alertSummary = this.summarizeAlerts(alerts, sensorIds);
            this.renderAlertSummary(doc, alertSummary, alerts.length);

            if (includeAlerts && alerts.length > 0) {
                this.renderAlertTable(doc, alerts);
            }

            if (includeSensorData) {
                doc.addPage();
                for (const sensor of sensors.slice(0, 10)) {
                    const data = this.db.getSensorData(sensor.id, 50, from, to);
                    if (data.length > 0) {
                        this.renderSensorTrend(doc, sensor, data);
                        doc.moveDown(2);
                    }
                }
            }

            this.renderFooter(doc);
            doc.end();
        });
    }

    generateFallbackHTML(sensors, alerts, from, to, includeSensorData, includeAlerts) {
        const sensorIds = sensors.map(s => s.id);
        const alertSummary = this.summarizeAlerts(alerts, sensorIds);

        let html = `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>水利工程安全监测报告</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; color: #333; }
        h1 { color: #1a3a5c; border-bottom: 2px solid #1a3a5c; padding-bottom: 10px; }
        h2 { color: #2a6cb8; margin-top: 30px; }
        table { border-collapse: collapse; width: 100%; margin: 20px 0; }
        th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
        th { background: #1a3a5c; color: white; }
        tr:nth-child(even) { background: #f5f5f5; }
        .summary-box { background: #e8f4ff; padding: 15px; border-radius: 8px; margin: 20px 0; }
        .level-badge { padding: 3px 8px; border-radius: 4px; color: white; font-size: 12px; }
        .level-emergency { background: #9c27b0; }
        .level-critical { background: #f44336; }
        .level-warning { background: #ff9800; }
        .level-info { background: #2196f3; }
        .trend-container { margin: 20px 0; page-break-inside: avoid; }
        .trend-chart { width: 100%; height: 200px; background: #f5f5f5; }
        .footer { margin-top: 50px; padding-top: 20px; border-top: 1px solid #ddd; color: #666; font-size: 12px; }
    </style>
</head>
<body>
    <h1>水利工程安全监测报告</h1>
    <p><strong>报告生成时间:</strong> ${new Date().toLocaleString('zh-CN')}</p>
    <p><strong>时间范围:</strong> ${from ? new Date(from).toLocaleString('zh-CN') : '全部'} ~ ${to ? new Date(to).toLocaleString('zh-CN') : '全部'}</p>
    <p><strong>传感器数量:</strong> ${sensors.length}</p>
    <p><strong>告警数量:</strong> ${alerts.length}</p>

    <h2>告警统计摘要</h2>
    <div class="summary-box">
        <p><strong>紧急告警:</strong> <span class="level-badge level-emergency">${alertSummary.emergency}</span></p>
        <p><strong>严重告警:</strong> <span class="level-badge level-critical">${alertSummary.critical}</span></p>
        <p><strong>警告:</strong> <span class="level-badge level-warning">${alertSummary.warning}</span></p>
        <p><strong>提示:</strong> <span class="level-badge level-info">${alertSummary.info}</span></p>
        <p><strong>预测预警:</strong> ${alertSummary.predictions}</p>
        <p><strong>区域风险:</strong> ${alertSummary.zoneRisks}</p>
    </div>`;

        if (includeAlerts && alerts.length > 0) {
            html += `
    <h2>告警记录</h2>
    <table>
        <tr>
            <th>时间</th>
            <th>级别</th>
            <th>类型</th>
            <th>传感器</th>
            <th>状态</th>
            <th>消息</th>
        </tr>`;
            for (const alert of alerts.slice(0, 100)) {
                const typeNames = { seepage: '渗流', displacement: '位移', stress: '应力', crack: '裂缝', water_level: '水位', zone_risk: '区域风险' };
                const levelNames = { emergency: '紧急', critical: '严重', warning: '警告', info: '提示' };
                const stateNames = { active: '活跃', acknowledged: '已确认', resolved: '已解除', escalated: '已升级' };
                html += `
        <tr>
            <td>${new Date(alert.started_at).toLocaleString('zh-CN')}</td>
            <td><span class="level-badge level-${alert.level}">${levelNames[alert.level] || alert.level}</span></td>
            <td>${typeNames[alert.type] || alert.type}</td>
            <td>${alert.sensor_id || '区域级'}</td>
            <td>${stateNames[alert.state] || alert.state}</td>
            <td>${alert.message}</td>
        </tr>`;
            }
            html += `
    </table>`;
        }

        if (includeSensorData) {
            html += `
    <h2>传感器趋势数据</h2>`;
            for (const sensor of sensors.slice(0, 10)) {
                const data = this.db.getSensorData(sensor.id, 50, from, to);
                if (data.length > 0) {
                    const values = data.map(d => d.value);
                    const avg = values.reduce((a, b) => a + b, 0) / values.length;
                    const max = Math.max(...values);
                    const min = Math.min(...values);
                    const typeNames = { seepage: '渗流', displacement: '位移', stress: '应力', crack: '裂缝', water_level: '水位' };

                    html += `
    <div class="trend-container">
        <h3>${sensor.name} (${sensor.id})</h3>
        <p><strong>类型:</strong> ${typeNames[sensor.type] || sensor.type} | <strong>单位:</strong> ${sensor.unit}</p>
        <p><strong>平均值:</strong> ${avg.toFixed(4)} ${sensor.unit} | <strong>最大值:</strong> ${max.toFixed(4)} ${sensor.unit} | <strong>最小值:</strong> ${min.toFixed(4)} ${sensor.unit}</p>
        <canvas class="trend-chart" data-sensor="${sensor.id}"></canvas>
    </div>`;
                }
            }
        }

        html += `
    <div class="footer">
        <p>本报告由水利工程安全监测平台自动生成</p>
        <p>生成时间: ${new Date().toLocaleString('zh-CN')}</p>
    </div>
</body>
</html>`;

        return Buffer.from(html, 'utf-8');
    }

    renderHeader(doc, from, to) {
        doc.fontSize(20).fillColor('#1a3a5c').text('水利工程安全监测报告', { align: 'center' });
        doc.moveDown(0.5);
        doc.fontSize(12).fillColor('#666').text('Water Safety Monitoring Platform Report', { align: 'center' });
        doc.moveDown(1);

        doc.fontSize(10).fillColor('#333');
        doc.text(`报告生成时间: ${new Date().toLocaleString('zh-CN')}`);
        doc.text(`时间范围: ${from ? new Date(from).toLocaleString('zh-CN') : '全部'} ~ ${to ? new Date(to).toLocaleString('zh-CN') : '全部'}`);
        doc.moveDown(1);

        doc.strokeColor('#1a3a5c').lineWidth(2).moveTo(50, doc.y).lineTo(545, doc.y).stroke();
        doc.moveDown(1);
    }

    renderFooter(doc) {
        const bottom = doc.page.height - 50;
        doc.fontSize(9).fillColor('#999');
        doc.text('水利工程安全监测平台 | 报告自动生成', 50, bottom, { align: 'center' });
        doc.text(`第 ${doc.page.length} 页`, 50, bottom + 15, { align: 'right' });
    }

    summarizeAlerts(alerts, sensorIds) {
        const summary = {
            total: alerts.length,
            emergency: 0,
            critical: 0,
            warning: 0,
            info: 0,
            predictions: 0,
            zoneRisks: 0,
            byType: {}
        };

        for (const alert of alerts) {
            summary[alert.level] = (summary[alert.level] || 0) + 1;
            if (alert.is_prediction) summary.predictions++;
            if (alert.is_zone_risk) summary.zoneRisks++;
            summary.byType[alert.type] = (summary.byType[alert.type] || 0) + 1;
        }

        return summary;
    }

    renderAlertSummary(doc, summary, totalAlerts) {
        doc.fontSize(14).fillColor('#2a6cb8').text('告警统计摘要');
        doc.moveDown(0.5);

        const y = doc.y;
        const boxWidth = 120;
        const boxHeight = 60;

        const stats = [
            { label: '紧急', value: summary.emergency, color: '#9c27b0' },
            { label: '严重', value: summary.critical, color: '#f44336' },
            { label: '警告', value: summary.warning, color: '#ff9800' },
            { label: '预测预警', value: summary.predictions, color: '#2196f3' }
        ];

        for (let i = 0; i < stats.length; i++) {
            const x = 50 + i * (boxWidth + 10);
            doc.rect(x, y, boxWidth, boxHeight).fillAndStroke(stats[i].color + '20', stats[i].color);
            doc.fillColor(stats[i].color).fontSize(20).text(stats[i].value.toString(), x, y + 15, { width: boxWidth, align: 'center' });
            doc.fillColor('#333').fontSize(10).text(stats[i].label, x, y + 40, { width: boxWidth, align: 'center' });
        }

        doc.y = y + boxHeight + 20;
    }

    renderAlertTable(doc, alerts) {
        doc.fontSize(14).fillColor('#2a6cb8').text('告警记录表');
        doc.moveDown(0.5);

        const tableTop = doc.y;
        const colWidths = [130, 60, 60, 80, 60, 180];
        const colX = [50, 180, 240, 300, 380, 440];

        doc.fillColor('#1a3a5c').fontSize(9).font('Helvetica-Bold');
        const headers = ['时间', '级别', '类型', '传感器', '状态', '消息'];
        for (let i = 0; i < headers.length; i++) {
            doc.rect(colX[i], tableTop, colWidths[i], 20).fill();
            doc.fillColor('white').text(headers[i], colX[i] + 5, tableTop + 6);
        }

        doc.font('Helvetica').fillColor('#333');
        let y = tableTop + 20;
        const typeNames = { seepage: '渗流', displacement: '位移', stress: '应力', crack: '裂缝', water_level: '水位', zone_risk: '区域风险' };
        const levelNames = { emergency: '紧急', critical: '严重', warning: '警告', info: '提示' };
        const stateNames = { active: '活跃', acknowledged: '已确认', resolved: '已解除', escalated: '已升级' };

        for (const alert of alerts.slice(0, 15)) {
            doc.rect(colX[0], y, colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4] + colWidths[5], 25).strokeColor('#ddd').stroke();
            doc.fillColor('#333').fontSize(8);
            doc.text(new Date(alert.started_at).toLocaleString('zh-CN'), colX[0] + 3, y + 8);
            doc.fillColor(alert.level === 'emergency' ? '#9c27b0' : alert.level === 'critical' ? '#f44336' : '#ff9800');
            doc.text(levelNames[alert.level] || alert.level, colX[1] + 3, y + 8);
            doc.fillColor('#333');
            doc.text(typeNames[alert.type] || alert.type, colX[2] + 3, y + 8);
            doc.text(alert.sensor_id || '区域级', colX[3] + 3, y + 8);
            doc.text(stateNames[alert.state] || alert.state, colX[4] + 3, y + 8);
            doc.text(alert.message.substring(0, 30), colX[5] + 3, y + 8);
            y += 25;
        }

        doc.y = y + 10;
    }

    renderSensorTrend(doc, sensor, data) {
        const typeNames = { seepage: '渗流', displacement: '位移', stress: '应力', crack: '裂缝', water_level: '水位' };
        const values = data.map(d => d.value);
        const avg = values.reduce((a, b) => a + b, 0) / values.length;
        const max = Math.max(...values);
        const min = Math.min(...values);

        doc.fontSize(12).fillColor('#333').text(`${sensor.name} (${sensor.id})`);
        doc.fontSize(9).fillColor('#666').text(`类型: ${typeNames[sensor.type] || sensor.type} | 单位: ${sensor.unit} | 数据点: ${data.length}`);
        doc.text(`平均值: ${avg.toFixed(4)} ${sensor.unit} | 最大值: ${max.toFixed(4)} ${sensor.unit} | 最小值: ${min.toFixed(4)} ${sensor.unit}`);
        doc.moveDown(0.3);

        const chartX = 60;
        const chartY = doc.y;
        const chartW = 470;
        const chartH = 120;

        doc.rect(chartX, chartY, chartW, chartH).strokeColor('#ddd').stroke();

        const valRange = max - min || 1;
        const sorted = [...data].sort((a, b) => a.timestamp.localeCompare(b.timestamp));

        doc.strokeColor('#4caf50').lineWidth(1.5);
        for (let i = 0; i < sorted.length; i++) {
            const x = chartX + (i / (sorted.length - 1)) * chartW;
            const y = chartY + chartH - ((sorted[i].value - min) / valRange) * chartH;
            if (i === 0) doc.moveTo(x, y);
            else doc.lineTo(x, y);
        }
        doc.stroke();

        doc.y = chartY + chartH + 10;
    }

    getReportFilename(options) {
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
        return `Report_${timestamp}.${PDFDocument ? 'pdf' : 'html'}`;
    }

    getContentType() {
        return PDFDocument ? 'application/pdf' : 'text/html; charset=utf-8';
    }
}

module.exports = { PDFExporter };
