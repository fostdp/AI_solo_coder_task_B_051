const path = require('path');

const EMERGENCY_GUIDES = require(path.join(__dirname, '..', 'config', 'emergencyGuides.json'));

class EmergencyGuideManager {
    constructor() {
        this.guides = {};
        this._loaded = false;
        this.init();
    }

    init() {
        if (this._loaded) return;
        this.guides = require(path.join(__dirname, '..', 'config', 'emergencyGuides.json'));
        this._loaded = true;
    }

    start() {
        this.init();
    }

    stop() {}

    getGuide(alertType) {
        return this.guides[alertType] || null;
    }

    getAllGuides() {
        return Object.values(this.guides);
    }

    isHighRisk(alertType, alertLevel) {
        const guide = this.guides[alertType];
        if (!guide) return false;
        return guide.highRiskLevels.includes(alertLevel);
    }

    getProcedures(alertType) {
        const guide = this.guides[alertType];
        return guide ? guide.standardProcedures : [];
    }

    getContacts(alertType) {
        const guide = this.guides[alertType];
        return guide ? guide.contacts : [];
    }

    getHistoricalCases(alertType) {
        const guide = this.guides[alertType];
        return guide ? guide.historicalCases : [];
    }

    getCaseById(caseId) {
        for (const guide of Object.values(this.guides)) {
            const found = guide.historicalCases.find(c => c.id === caseId);
            if (found) return found;
        }
        return null;
    }

    getStatus() {
        const guideTypes = Object.keys(this.guides);
        let totalCases = 0;
        for (const guide of Object.values(this.guides)) {
            totalCases += guide.historicalCases.length;
        }
        return {
            loaded: this._loaded,
            guideTypes,
            totalCases
        };
    }

    destroy() {
        this.guides = {};
        this._loaded = false;
    }
}

const EmergencyGuideService = EmergencyGuideManager;

module.exports = { EmergencyGuideManager, EmergencyGuideService, EMERGENCY_GUIDES };
