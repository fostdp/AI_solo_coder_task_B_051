const { runner } = require('./testUtils');

require('./predictiveEngine.test.js');
require('./zoneRiskAnalyzer.test.js');
require('./emergencyGuide.test.js');
require('./pdfExporter.test.js');
require('./refactoredModules.test.js');

console.log('========================================');
console.log('  水利工程安全监测平台 - 完整测试套件');
console.log('========================================\n');

runner.run().then(success => {
    console.log('\n========================================');
    if (success) {
        console.log('  ✅ 所有测试通过!');
        process.exit(0);
    } else {
        console.log('  ❌ 部分测试失败!');
        process.exit(1);
    }
});
