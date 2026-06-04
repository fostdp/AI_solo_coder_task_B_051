const {
    describe, it, beforeAll, afterAll, assert
} = require('./testUtils');

const { EmergencyGuideService, EMERGENCY_GUIDES } = require('../services/emergencyGuide');

describe('9. 高危告警触发时面板自动弹出测试', () => {
    let service;

    beforeAll(() => {
        service = new EmergencyGuideService();
    });

    it('9.1 critical级别渗流告警应判定为高危', () => {
        const isHigh = service.isHighRisk('seepage', 'critical');
        assert(isHigh === true, 'critical级别渗流告警应判定为高危');
    });

    it('9.2 emergency级别水位告警应判定为高危', () => {
        const isHigh = service.isHighRisk('water_level', 'emergency');
        assert(isHigh === true, 'emergency级别水位告警应判定为高危');
    });

    it('9.3 warning级别告警不应判定为高危', () => {
        const isHigh = service.isHighRisk('seepage', 'warning');
        assert(isHigh === false, 'warning级别告警不应判定为高危');
    });

    it('9.4 info级别告警不应判定为高危', () => {
        const isHigh = service.isHighRisk('displacement', 'info');
        assert(isHigh === false, 'info级别告警不应判定为高危');
    });

    it('9.5 不存在的告警类型应返回false', () => {
        const isHigh = service.isHighRisk('invalid_type', 'critical');
        assert(isHigh === false, '不存在的告警类型应返回false');
    });

    it('9.6 所有5种告警类型的critical级别都应判定为高危', () => {
        const types = ['seepage', 'displacement', 'stress', 'crack', 'water_level'];
        for (const type of types) {
            const isHigh = service.isHighRisk(type, 'critical');
            assert(isHigh === true, `${type}类型的critical级别应判定为高危`);
        }
    });

    it('9.7 所有5种告警类型的emergency级别都应判定为高危', () => {
        const types = ['seepage', 'displacement', 'stress', 'crack', 'water_level'];
        for (const type of types) {
            const isHigh = service.isHighRisk(type, 'emergency');
            assert(isHigh === true, `${type}类型的emergency级别应判定为高危`);
        }
    });
});

describe('10. 指引内容与告警类型的匹配准确性测试', () => {
    let service;

    beforeAll(() => {
        service = new EmergencyGuideService();
    });

    it('10.1 渗流类型应返回正确的typeName', () => {
        const guide = service.getGuide('seepage');
        assert(guide !== null, '应返回渗流指引');
        assert.strictEqual(guide.typeName, '渗流', 'typeName应为"渗流"');
        assert.strictEqual(guide.type, 'seepage', 'type应为"seepage"');
    });

    it('10.2 位移类型应返回正确的typeName', () => {
        const guide = service.getGuide('displacement');
        assert(guide !== null, '应返回位移指引');
        assert.strictEqual(guide.typeName, '位移', 'typeName应为"位移"');
    });

    it('10.3 应力类型应返回正确的typeName', () => {
        const guide = service.getGuide('stress');
        assert(guide !== null, '应返回应力指引');
        assert.strictEqual(guide.typeName, '应力', 'typeName应为"应力"');
    });

    it('10.4 裂缝类型应返回正确的typeName', () => {
        const guide = service.getGuide('crack');
        assert(guide !== null, '应返回裂缝指引');
        assert.strictEqual(guide.typeName, '裂缝', 'typeName应为"裂缝"');
    });

    it('10.5 水位类型应返回正确的typeName', () => {
        const guide = service.getGuide('water_level');
        assert(guide !== null, '应返回水位指引');
        assert.strictEqual(guide.typeName, '水位', 'typeName应为"水位"');
    });

    it('10.6 不存在的告警类型应返回null', () => {
        const guide = service.getGuide('invalid_type');
        assert.strictEqual(guide, null, '不存在的告警类型应返回null');
    });

    it('10.7 每种告警类型的标准流程应包含7个步骤', () => {
        const types = ['seepage', 'displacement', 'stress', 'crack', 'water_level'];
        for (const type of types) {
            const procedures = service.getProcedures(type);
            assert.strictEqual(procedures.length, 7, `${type}类型的标准流程应有7个步骤`);

            for (let i = 0; i < 7; i++) {
                assert.strictEqual(procedures[i].step, i + 1, `步骤编号应从1到7，实际为${procedures[i].step}`);
                assert(procedures[i].title, `步骤${i + 1}应有title`);
                assert(procedures[i].content, `步骤${i + 1}应有content`);
            }
        }
    });

    it('10.8 每种告警类型的联系人数量应正确', () => {
        const expectedContacts = {
            seepage: 4,
            displacement: 4,
            stress: 3,
            crack: 3,
            water_level: 4
        };

        for (const [type, expectedCount] of Object.entries(expectedContacts)) {
            const contacts = service.getContacts(type);
            assert.strictEqual(contacts.length, expectedCount,
                `${type}类型的联系人数量应为${expectedCount}，实际为${contacts.length}`);

            for (const contact of contacts) {
                assert(contact.name, '联系人应有name');
                assert(contact.role, '联系人应有role');
                assert(contact.phone, '联系人应有phone');
                assert(contact.department, '联系人应有department');
                assert(/^\d{3}-\d{4}-\d{4}$/.test(contact.phone),
                    `电话号码格式不正确: ${contact.phone}`);
            }
        }
    });

    it('10.9 渗流类型的流程第一步应为"立即报告"', () => {
        const procedures = service.getProcedures('seepage');
        assert.strictEqual(procedures[0].title, '立即报告', '渗流流程第一步应为"立即报告"');
    });

    it('10.10 水位类型的流程第二步应为"洪水预报"', () => {
        const procedures = service.getProcedures('water_level');
        assert.strictEqual(procedures[1].title, '洪水预报', '水位流程第二步应为"洪水预报"');
    });

    it('10.11 所有类型的highRiskLevels应包含critical和emergency', () => {
        const types = ['seepage', 'displacement', 'stress', 'crack', 'water_level'];
        for (const type of types) {
            const guide = service.getGuide(type);
            assert(guide.highRiskLevels.includes('critical'),
                `${type}的highRiskLevels应包含critical`);
            assert(guide.highRiskLevels.includes('emergency'),
                `${type}的highRiskLevels应包含emergency`);
        }
    });

    it('10.12 getAllGuides应返回所有5种类型', () => {
        const allGuides = service.getAllGuides();
        assert.strictEqual(allGuides.length, 5, 'getAllGuides应返回5种类型');
        const types = allGuides.map(g => g.type).sort();
        assert.deepStrictEqual(types, ['crack', 'displacement', 'seepage', 'stress', 'water_level'],
            '类型列表不正确');
    });

    it('10.13 getProcedures不存在的类型应返回空数组', () => {
        const procedures = service.getProcedures('invalid');
        assert.deepStrictEqual(procedures, [], '不存在的类型应返回空数组');
    });

    it('10.14 getContacts不存在的类型应返回空数组', () => {
        const contacts = service.getContacts('invalid');
        assert.deepStrictEqual(contacts, [], '不存在的类型应返回空数组');
    });

    it('10.15 getHistoricalCases不存在的类型应返回空数组', () => {
        const cases = service.getHistoricalCases('invalid');
        assert.deepStrictEqual(cases, [], '不存在的类型应返回空数组');
    });
});

describe('11. 历史案例查询正确性测试', () => {
    let service;

    beforeAll(() => {
        service = new EmergencyGuideService();
    });

    it('11.1 渗流类型应有2个历史案例', () => {
        const cases = service.getHistoricalCases('seepage');
        assert.strictEqual(cases.length, 2, '渗流类型应有2个历史案例');
        assert(cases[0].id.startsWith('CASE-'), '案例ID格式不正确');
    });

    it('11.2 位移类型应有1个历史案例', () => {
        const cases = service.getHistoricalCases('displacement');
        assert.strictEqual(cases.length, 1, '位移类型应有1个历史案例');
    });

    it('11.3 应力类型应有1个历史案例', () => {
        const cases = service.getHistoricalCases('stress');
        assert.strictEqual(cases.length, 1, '应力类型应有1个历史案例');
    });

    it('11.4 裂缝类型应有1个历史案例', () => {
        const cases = service.getHistoricalCases('crack');
        assert.strictEqual(cases.length, 1, '裂缝类型应有1个历史案例');
    });

    it('11.5 水位类型应有1个历史案例', () => {
        const cases = service.getHistoricalCases('water_level');
        assert.strictEqual(cases.length, 1, '水位类型应有1个历史案例');
    });

    it('11.6 每个案例应包含所有必需字段', () => {
        const allCases = [];
        for (const guide of Object.values(EMERGENCY_GUIDES)) {
            allCases.push(...guide.historicalCases);
        }

        assert(allCases.length > 0, '应有历史案例');

        for (const caseItem of allCases) {
            assert(caseItem.id, '案例应有id');
            assert(caseItem.date, '案例应有date');
            assert(caseItem.location, '案例应有location');
            assert(caseItem.description, '案例应有description');
            assert(caseItem.rootCause, '案例应有rootCause');
            assert(caseItem.solution, '案例应有solution');
            assert(caseItem.outcome, '案例应有outcome');
            assert(caseItem.lessonsLearned, '案例应有lessonsLearned');

            assert(/^\d{4}-\d{2}-\d{2}$/.test(caseItem.date),
                `日期格式不正确: ${caseItem.date}`);
        }
    });

    it('11.7 getCaseById应能正确查询CASE-001', () => {
        const caseItem = service.getCaseById('CASE-001');
        assert(caseItem !== null, '应找到CASE-001');
        assert.strictEqual(caseItem.id, 'CASE-001');
        assert.strictEqual(caseItem.location, '主坝0+350断面');
        assert(caseItem.description.includes('渗流量'), '案例描述应包含渗流量');
    });

    it('11.8 getCaseById应能正确查询不同类型的案例', () => {
        const caseIds = ['CASE-001', 'CASE-011', 'CASE-021', 'CASE-031', 'CASE-041'];
        for (const id of caseIds) {
            const caseItem = service.getCaseById(id);
            assert(caseItem !== null, `应找到案例${id}`);
            assert.strictEqual(caseItem.id, id, `案例ID不匹配: ${id}`);
        }
    });

    it('11.9 getCaseById不存在的ID应返回null', () => {
        const caseItem = service.getCaseById('CASE-999');
        assert.strictEqual(caseItem, null, '不存在的ID应返回null');
    });

    it('11.10 渗流案例CASE-002的根本原因应正确', () => {
        const caseItem = service.getCaseById('CASE-002');
        assert(caseItem !== null, '应找到CASE-002');
        assert(caseItem.rootCause.includes('压实度不足'),
            `根本原因不正确: ${caseItem.rootCause}`);
    });

    it('11.11 水位案例CASE-041的解决方案应包含预泄', () => {
        const caseItem = service.getCaseById('CASE-041');
        assert(caseItem !== null, '应找到CASE-041');
        assert(caseItem.solution.includes('预泄'),
            `解决方案应包含预泄: ${caseItem.solution}`);
    });

    it('11.12 所有案例都应包含经验教训', () => {
        const allCases = [];
        for (const guide of Object.values(EMERGENCY_GUIDES)) {
            allCases.push(...guide.historicalCases);
        }

        for (const caseItem of allCases) {
            assert(caseItem.lessonsLearned.length > 10,
                `案例${caseItem.id}的经验教训内容过短`);
        }
    });

    it('11.13 案例ID应全局唯一', () => {
        const allIds = [];
        for (const guide of Object.values(EMERGENCY_GUIDES)) {
            for (const caseItem of guide.historicalCases) {
                assert(!allIds.includes(caseItem.id),
                    `案例ID重复: ${caseItem.id}`);
                allIds.push(caseItem.id);
            }
        }
    });

    it('11.14 位移案例应包含位移速率相关内容', () => {
        const cases = service.getHistoricalCases('displacement');
        assert(cases[0].description.includes('位移速率'),
            '位移案例描述应包含位移速率');
    });

    it('11.15 裂缝案例应包含裂缝宽度相关内容', () => {
        const cases = service.getHistoricalCases('crack');
        assert(cases[0].description.includes('宽度'),
            '裂缝案例描述应包含宽度');
    });
});
