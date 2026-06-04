const EventEmitter = require('events');
const { v4: uuidv4 } = require('uuid');

const EXPORT_CONFIG = {
    maxRecordsPerFile: 50000,
    maxConcurrentExports: 2,
    timeoutMs: 5 * 60 * 1000,
    cleanupIntervalMs: 10 * 60 * 1000,
    resultRetentionMs: 30 * 60 * 1000
};

class ExportQueue extends EventEmitter {
    constructor(pdfExporter) {
        super();
        this.pdfExporter = pdfExporter;
        this.queue = [];
        this.activeExports = new Map();
        this.completedExports = new Map();
        this.processing = false;
        this.cleanupTimer = null;
        this.startCleanup();
    }

    submitExport(options) {
        const exportId = `export-${uuidv4().slice(0, 12)}`;
        const task = {
            id: exportId,
            options,
            status: 'pending',
            createdAt: Date.now(),
            progress: 0,
            result: null,
            error: null
        };

        this.queue.push(task);
        this.emit('submitted', exportId);
        
        setImmediate(() => this.processQueue());
        
        return { exportId, status: 'pending' };
    }

    getExportStatus(exportId) {
        if (this.completedExports.has(exportId)) {
            return this.completedExports.get(exportId);
        }
        if (this.activeExports.has(exportId)) {
            return this.activeExports.get(exportId);
        }
        const queued = this.queue.find(t => t.id === exportId);
        if (queued) return queued;
        
        return null;
    }

    getExportResult(exportId) {
        return this.completedExports.get(exportId);
    }

    async processQueue() {
        if (this.processing) return;
        this.processing = true;

        while (this.queue.length > 0 && this.activeExports.size < EXPORT_CONFIG.maxConcurrentExports) {
            const task = this.queue.shift();
            this.activeExports.set(task.id, task);
            this.processTask(task);
        }

        this.processing = false;
    }

    async processTask(task) {
        task.status = 'processing';
        task.startedAt = Date.now();
        this.emit('started', task.id);

        const timeout = setTimeout(() => {
            this.failTask(task, 'Export timeout exceeded');
        }, EXPORT_CONFIG.timeoutMs);

        try {
            const result = await this.performExport(task);
            clearTimeout(timeout);
            this.completeTask(task, result);
        } catch (error) {
            clearTimeout(timeout);
            this.failTask(task, error.message);
        }
    }

    async performExport(task) {
        const { options } = task;
        const { zoneId, from, to } = options;

        const alerts = this.pdfExporter.db.queryAlerts({
            zone_id: zoneId,
            state: undefined
        }).filter(a => {
            if (from && a.started_at < from) return false;
            if (to && a.started_at > to) return false;
            return true;
        });

        task.progress = 10;

        if (alerts.length <= EXPORT_CONFIG.maxRecordsPerFile) {
            const buffer = await this.pdfExporter.generateReport(options);
            return {
                files: [{
                    name: this.pdfExporter.getReportFilename(options),
                    data: buffer.toString('base64'),
                    size: buffer.length
                }],
                totalRecords: alerts.length,
                totalFiles: 1
            };
        }

        const totalFiles = Math.ceil(alerts.length / EXPORT_CONFIG.maxRecordsPerFile);
        const files = [];
        const sensors = zoneId
            ? this.pdfExporter.db.getSensorsByZone(zoneId)
            : this.pdfExporter.db.getAllSensors();

        for (let i = 0; i < totalFiles; i++) {
            const startIdx = i * EXPORT_CONFIG.maxRecordsPerFile;
            const endIdx = Math.min(startIdx + EXPORT_CONFIG.maxRecordsPerFile, alerts.length);
            const batchAlerts = alerts.slice(startIdx, endIdx);

            const batchOptions = {
                ...options,
                _batchAlerts: batchAlerts,
                _batchInfo: {
                    batchIndex: i,
                    totalBatches: totalFiles,
                    startRecord: startIdx + 1,
                    endRecord: endIdx
                }
            };

            const buffer = await this.generateBatchReport(sensors, batchOptions, batchAlerts);
            
            files.push({
                name: `Report_part${i + 1}_of_${totalFiles}.pdf`,
                data: buffer.toString('base64'),
                size: buffer.length,
                recordRange: { start: startIdx + 1, end: endIdx }
            });

            task.progress = 10 + Math.round((i + 1) / totalFiles * 80);
        }

        task.progress = 100;

        return {
            files,
            totalRecords: alerts.length,
            totalFiles,
            maxRecordsPerFile: EXPORT_CONFIG.maxRecordsPerFile
        };
    }

    async generateBatchReport(sensors, options, batchAlerts) {
        const { from, to, includeSensorData, includeAlerts } = options;
        
        let PDFDocument = null;
        try {
            PDFDocument = require('pdfkit');
        } catch (e) {
            return this.pdfExporter.generateFallbackHTML(sensors, batchAlerts, from, to, includeSensorData, includeAlerts);
        }

        return new Promise((resolve, reject) => {
            const doc = new PDFDocument({ size: 'A4', margin: 50 });
            const chunks = [];

            doc.on('data', chunk => chunks.push(chunk));
            doc.on('end', () => resolve(Buffer.concat(chunks)));
            doc.on('error', reject);

            this.renderBatchHeader(doc, options);

            const sensorIds = sensors.map(s => s.id);
            const alertSummary = this.pdfExporter.summarizeAlerts(batchAlerts, sensorIds);
            this.pdfExporter.renderAlertSummary(doc, alertSummary, batchAlerts.length);

            if (includeAlerts && batchAlerts.length > 0) {
                this.pdfExporter.renderAlertTable(doc, batchAlerts);
            }

            if (includeSensorData) {
                doc.addPage();
                for (const sensor of sensors.slice(0, 10)) {
                    const data = this.pdfExporter.db.getSensorData(sensor.id, 50, from, to);
                    if (data.length > 0) {
                        this.pdfExporter.renderSensorTrend(doc, sensor, data);
                        doc.moveDown(2);
                    }
                }
            }

            this.pdfExporter.renderFooter(doc);
            doc.end();
        });
    }

    renderBatchHeader(doc, options) {
        const { from, to, _batchInfo } = options;
        
        doc.fontSize(20).fillColor('#1a3a5c').text('水利工程安全监测报告', { align: 'center' });
        doc.moveDown(0.5);
        
        if (_batchInfo) {
            doc.fontSize(10).fillColor('#ff9800').text(
                `分批导出: 第 ${_batchInfo.batchIndex + 1}/${_batchInfo.totalBatches} 部分 (记录 ${_batchInfo.startRecord}-${_batchInfo.endRecord})`,
                { align: 'center' }
            );
        }
        
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

    completeTask(task, result) {
        task.status = 'completed';
        task.completedAt = Date.now();
        task.result = result;
        task.progress = 100;
        
        this.activeExports.delete(task.id);
        this.completedExports.set(task.id, task);
        this.emit('completed', task.id, result);
        
        setImmediate(() => this.processQueue());
    }

    failTask(task, errorMessage) {
        task.status = 'failed';
        task.completedAt = Date.now();
        task.error = errorMessage;
        
        this.activeExports.delete(task.id);
        this.completedExports.set(task.id, task);
        this.emit('failed', task.id, errorMessage);
        
        setImmediate(() => this.processQueue());
    }

    startCleanup() {
        this.cleanupTimer = setInterval(() => {
            const now = Date.now();
            for (const [id, task] of this.completedExports) {
                if (now - task.completedAt > EXPORT_CONFIG.resultRetentionMs) {
                    this.completedExports.delete(id);
                }
            }
        }, EXPORT_CONFIG.cleanupIntervalMs);
    }

    destroy() {
        if (this.cleanupTimer) {
            clearInterval(this.cleanupTimer);
        }
        this.queue = [];
        this.activeExports.clear();
        this.completedExports.clear();
    }
}

module.exports = { ExportQueue, EXPORT_CONFIG };
