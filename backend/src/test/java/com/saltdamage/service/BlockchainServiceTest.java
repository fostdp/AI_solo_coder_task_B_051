package com.saltdamage.service;

import cn.hutool.core.util.ReflectUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("BlockchainService 区块链服务单元测试")
class BlockchainServiceTest {

    private static final Pattern HEX_64_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private BlockchainService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new BlockchainService();
        setDifficulty(service, 1);
    }

    private void setDifficulty(BlockchainService svc, int value) throws Exception {
        Field field = BlockchainService.class.getDeclaredField("difficulty");
        field.setAccessible(true);
        field.setInt(svc, value);
    }

    private int getDifficulty(BlockchainService svc) throws Exception {
        Field field = BlockchainService.class.getDeclaredField("difficulty");
        field.setAccessible(true);
        return field.getInt(svc);
    }

    @SuppressWarnings("unchecked")
    private List<BlockchainService.Block> getBlockchain(BlockchainService svc) throws Exception {
        Field field = BlockchainService.class.getDeclaredField("blockchain");
        field.setAccessible(true);
        return (List<BlockchainService.Block>) field.get(svc);
    }

    @SuppressWarnings("unchecked")
    private List<BlockchainService.Transaction> getPendingTransactions(BlockchainService svc) throws Exception {
        Field field = BlockchainService.class.getDeclaredField("pendingTransactions");
        field.setAccessible(true);
        return (List<BlockchainService.Transaction>) field.get(svc);
    }

    // ==================== 一、初始化测试 ====================

    @Test
    @DisplayName("1. 初始化后创世区块存在，blockNumber=0")
    void testGenesisBlock_CreatedOnInitialization() {
        BlockchainService.Block genesis = service.getLatestBlock();
        assertNotNull(genesis, "创世区块不应为null");
        assertEquals(0L, genesis.getBlockNumber(), "创世区块号应为0");
    }

    @Test
    @DisplayName("2. 创世区块哈希符合PoW要求（前导零数量≥difficulty）")
    void testGenesisBlock_HashValid() throws Exception {
        int diff = getDifficulty(service);
        String target = "0".repeat(diff);
        String hash = service.getLatestBlock().getHash();
        assertTrue(hash.startsWith(target),
                "创世区块哈希前导零应≥" + diff + ", 实际哈希: " + hash);
        assertTrue(HEX_64_PATTERN.matcher(hash).matches(), "哈希应为64位十六进制字符串");
    }

    @Test
    @DisplayName("3. 创世区块无交易")
    void testGenesisBlock_NoTransactions() {
        BlockchainService.Block genesis = service.getLatestBlock();
        assertNotNull(genesis.getTransactions(), "交易列表不应为null");
        assertTrue(genesis.getTransactions().isEmpty(), "创世区块应无交易");
        String expectedEmptyRoot = service.generateDataHash("");
        assertEquals(expectedEmptyRoot, genesis.getMerkleRoot(),
                "空交易列表的MerkleRoot应为空字符串的SHA-256");
    }

    // ==================== 二、哈希测试 ====================

    @Test
    @DisplayName("4. 相同输入生成相同哈希（SHA-256确定性）")
    void testGenerateDataHash_Deterministic() {
        String input = "{\"name\":\"测试数据\",\"value\":123}";
        String hash1 = service.generateDataHash(input);
        String hash2 = service.generateDataHash(input);
        assertEquals(hash1, hash2, "相同输入应产生相同哈希");
        assertTrue(HEX_64_PATTERN.matcher(hash1).matches(), "哈希应为64位十六进制");
    }

    @Test
    @DisplayName("5. 输入改变1位，输出完全不同（雪崩效应）")
    void testGenerateDataHash_AvalancheEffect() {
        String input1 = "Hello World";
        String input2 = "Hello Worle";
        String hash1 = service.generateDataHash(input1);
        String hash2 = service.generateDataHash(input2);
        assertNotEquals(hash1, hash2, "不同输入应产生不同哈希");
        int diffBits = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) diffBits++;
        }
        assertTrue(diffBits >= hash1.length() / 2,
                "雪崩效应：不同字符数应超过一半，实际不同: " + diffBits + "/" + hash1.length());
    }

    @Test
    @DisplayName("6. 空字符串也返回合法哈希（64位十六进制）")
    void testGenerateDataHash_EmptyString_ReturnsValidHash() {
        String hash = service.generateDataHash("");
        assertNotNull(hash, "空字符串不应返回null");
        assertTrue(HEX_64_PATTERN.matcher(hash).matches(), "空字符串哈希应为64位十六进制");
        log.info("空字符串SHA-256哈希: {}", hash);
    }

    // ==================== 三、存证测试 ====================

    @Test
    @DisplayName("7. ⭐ 存证成功返回合法txHash（64位hex）")
    void testStoreData_ReturnsValidTxHash() {
        String txHash = service.storeData("SENSOR", "{\"temp\":25.5}", "admin");
        assertNotNull(txHash, "存证应返回txHash");
        assertTrue(HEX_64_PATTERN.matcher(txHash).matches(),
                "txHash应为64位十六进制，实际: " + txHash);
        log.info("存证成功, txHash: {}", txHash);
    }

    @Test
    @DisplayName("8. 相同数据内容两次存证，txHash不同（含时间戳）")
    void testStoreData_SameDataDifferentTx_NotSameTxHash() {
        String json = "{\"name\":\"壁画\",\"location\":\"一号墓\"}";
        String txHash1 = service.storeData("MONITOR", json, "userA");
        String txHash2 = service.storeData("MONITOR", json, "userA");
        assertNotEquals(txHash1, txHash2,
                "两次存证的txHash应不同（含System.nanoTime）");
        log.info("txHash1: {}", txHash1);
        log.info("txHash2: {}", txHash2);
    }

    @Test
    @DisplayName("9. ⭐ 核心验证：相同JSON数据两次存证，dataHash完全相同")
    void testStoreData_DataHashUnchangedForSameContent() {
        String json = "{\"id\":\"T001\",\"saltLevel\":\"high\",\"temperature\":22.3,\"humidity\":65.0}";
        String dataHashExpected = service.generateDataHash(json);

        String txHash1 = service.storeData("ANALYSIS", json, "system");
        String txHash2 = service.storeData("ANALYSIS", json, "operator");

        BlockchainService.VerifyResult v1 = service.verifyData(dataHashExpected);
        BlockchainService.VerifyResult v2 = service.verifyData(dataHashExpected);

        assertTrue(v1.isExists(), "第一次存证后dataHash应能查询到");
        assertTrue(v2.isExists(), "第二次存证后同一dataHash仍能查询到");
        assertEquals(dataHashExpected, service.generateDataHash(json),
                "相同JSON的generateDataHash结果应恒定");

        log.info("同一数据两次存证，dataHash均为: {}", dataHashExpected);
        log.info("  txHash1: {}", txHash1);
        log.info("  txHash2: {}", txHash2);
    }

    @Test
    @DisplayName("10. 4种数据类型都能存证")
    void testStoreData_MultipleTypes_AllStored() {
        String[] types = {"SENSOR", "MONITOR", "ANALYSIS", "ALARM"};
        String[] operators = {"sensor01", "monitor_svc", "analysis_engine", "alert_mgr"};

        for (int i = 0; i < types.length; i++) {
            String txHash = service.storeData(
                    types[i],
                    "{\"type\":\"" + types[i] + "\",\"index\":" + i + "}",
                    operators[i]
            );
            assertTrue(HEX_64_PATTERN.matcher(txHash).matches(),
                    "类型 " + types[i] + " 存证失败");
            log.info("类型 [{}] 存证成功, txHash: {}", types[i], txHash);
        }

        Map<String, Long> stats = service.getTypeStatistics();
        for (String t : types) {
            assertEquals(1L, stats.get(t), "类型 " + t + " 统计应为1");
        }
    }

    // ==================== 四、挖矿测试 ====================

    @Test
    @DisplayName("11. 挖矿后区块高度+1")
    void testMinePendingBlock_BlockAddedToChain() throws Exception {
        long initialHeight = service.getBlockchainInfo().getBlockHeight();
        service.storeData("SENSOR", "{\"v\":1}", "op");

        BlockchainService.Block mined = service.minePendingBlock();
        assertNotNull(mined, "有交易时挖矿不应返回null");

        long newHeight = service.getBlockchainInfo().getBlockHeight();
        assertEquals(initialHeight + 1, newHeight, "区块高度应+1");
        assertEquals(initialHeight + 1, mined.getBlockNumber(), "挖出的区块号应正确");
    }

    @Test
    @DisplayName("12. 挖出的区块哈希满足难度要求")
    void testMinePendingBlock_BlockHashValidPoW() throws Exception {
        int diff = getDifficulty(service);
        String target = "0".repeat(diff);

        service.storeData("SENSOR", "{\"v\":2}", "op");
        long start = System.nanoTime();
        BlockchainService.Block mined = service.minePendingBlock();
        long elapsed = System.nanoTime() - start;

        assertNotNull(mined);
        assertTrue(mined.getHash().startsWith(target),
                "挖出区块哈希前导零应≥" + diff + ", 实际: " + mined.getHash());
        log.info("挖矿完成, 难度={}, 耗时={}ms, nonce={}, hash={}",
                diff, elapsed / 1_000_000, mined.getNonce(), mined.getHash());
    }

    @Test
    @DisplayName("13. 打包后待处理交易列表清空")
    void testMinePendingBlock_TransactionsCleared() throws Exception {
        service.storeData("SENSOR", "{\"v\":3}", "op1");
        service.storeData("MONITOR", "{\"v\":4}", "op2");
        assertEquals(2, getPendingTransactions(service).size(), "挖矿前应有2笔待处理");

        service.minePendingBlock();
        assertEquals(0, getPendingTransactions(service).size(), "挖矿后待处理列表应清空");
    }

    @Test
    @DisplayName("14. 无待打包交易返回null（边界）")
    void testMine_NoPendingTransactions_ReturnsNull() {
        BlockchainService.Block result = service.minePendingBlock();
        assertNull(result, "无待处理交易时应返回null");
    }

    @Test
    @DisplayName("15. 难度2比难度1耗时显著增加（难度测试）")
    void testDifficultyAdjustment_MoreZerosTakesLonger() throws Exception {
        long timeDiff1 = measureMiningTime(1);
        long timeDiff2 = measureMiningTime(2);

        log.info("难度1挖矿耗时: {}ms, 难度2挖矿耗时: {}ms", timeDiff1, timeDiff2);
        assertTrue(timeDiff2 >= timeDiff1,
                "难度2耗时(" + timeDiff2 + "ms)应≥难度1耗时(" + timeDiff1 + "ms)");
    }

    private long measureMiningTime(int diff) throws Exception {
        BlockchainService svc = new BlockchainService();
        setDifficulty(svc, diff);
        svc.storeData("TEST", "{\"v\":\"diff" + diff + "\"}", "tester");
        long s = System.nanoTime();
        svc.minePendingBlock();
        return (System.nanoTime() - s) / 1_000_000;
    }

    // ==================== 五、Merkle树测试 ====================

    @Test
    @DisplayName("16. 只有1笔交易时，Merkle根就是交易哈希")
    void testMerkleRoot_SingleTransaction_EqualsTxHash() {
        String txHash = service.storeData("SENSOR", "{\"single\":true}", "op");
        BlockchainService.Block mined = service.minePendingBlock();

        assertNotNull(mined);
        assertEquals(1, mined.getTransactions().size(), "应有1笔交易");
        assertEquals(txHash, mined.getMerkleRoot(),
                "单交易时Merkle根应等于该交易的txHash");
        log.info("单交易Merkle根 = txHash = {}", txHash);
    }

    @Test
    @DisplayName("17. 偶数和奇数个交易Merkle树都能构建")
    void testMerkleRoot_EvenOddTransactionCount() {
        BlockchainService svcEven = new BlockchainService();
        ReflectUtil.setFieldValue(svcEven, "difficulty", 1);
        svcEven.storeData("A", "{\"i\":1}", "op");
        svcEven.storeData("A", "{\"i\":2}", "op");
        BlockchainService.Block evenBlock = svcEven.minePendingBlock();
        assertNotNull(evenBlock);
        assertEquals(2, evenBlock.getTransactions().size());
        assertNotNull(evenBlock.getMerkleRoot());
        assertTrue(HEX_64_PATTERN.matcher(evenBlock.getMerkleRoot()).matches());
        log.info("偶数(2)笔交易Merkle根: {}", evenBlock.getMerkleRoot());

        BlockchainService svcOdd = new BlockchainService();
        ReflectUtil.setFieldValue(svcOdd, "difficulty", 1);
        svcOdd.storeData("B", "{\"i\":1}", "op");
        svcOdd.storeData("B", "{\"i\":2}", "op");
        svcOdd.storeData("B", "{\"i\":3}", "op");
        BlockchainService.Block oddBlock = svcOdd.minePendingBlock();
        assertNotNull(oddBlock);
        assertEquals(3, oddBlock.getTransactions().size());
        assertNotNull(oddBlock.getMerkleRoot());
        assertTrue(HEX_64_PATTERN.matcher(oddBlock.getMerkleRoot()).matches());
        log.info("奇数(3)笔交易Merkle根: {}", oddBlock.getMerkleRoot());
    }

    @Test
    @DisplayName("18. 生成的Merkle证明能正确验证")
    void testMerkleProof_GeneratedAndValid() {
        for (int i = 0; i < 5; i++) {
            service.storeData("TEST", "{\"idx\":" + i + "}", "op" + i);
        }
        BlockchainService.Block block = service.minePendingBlock();
        assertNotNull(block);
        assertEquals(5, block.getTransactions().size());

        for (BlockchainService.Transaction tx : block.getTransactions()) {
            BlockchainService.MerkleProof proof = service.generateMerkleProof(
                    block.getTransactions(), tx.getTxHash());
            assertNotNull(proof);
            assertEquals(tx.getTxHash(), proof.getTxHash());
            assertEquals(block.getMerkleRoot(), proof.getMerkleRoot());

            boolean valid = service.verifyMerkleProof(
                    tx.getTxHash(), block.getMerkleRoot(), proof);
            assertTrue(valid, "交易 " + tx.getTxHash().substring(0, 8) + " 的Merkle证明应验证通过");
        }
        log.info("5笔交易的Merkle证明全部验证通过");
    }

    @Test
    @DisplayName("19. 篡改交易哈希后Merkle证明验证失败（防篡改）")
    void testMerkleProof_TamperedHash_FailsVerification() {
        service.storeData("X", "{\"a\":1}", "op");
        service.storeData("X", "{\"b\":2}", "op");
        BlockchainService.Block block = service.minePendingBlock();
        assertNotNull(block);

        String realTxHash = block.getTransactions().get(0).getTxHash();
        BlockchainService.MerkleProof proof = service.generateMerkleProof(
                block.getTransactions(), realTxHash);

        String fakeTxHash = "0000000000000000000000000000000000000000000000000000000000000000";
        boolean resultWithFake = service.verifyMerkleProof(
                fakeTxHash, block.getMerkleRoot(), proof);
        assertFalse(resultWithFake, "篡改交易哈希后Merkle证明应验证失败");

        boolean resultWithReal = service.verifyMerkleProof(
                realTxHash, block.getMerkleRoot(), proof);
        assertTrue(resultWithReal, "真实哈希应验证通过（对照）");

        log.info("Merkle防篡改验证: 真实={}, 篡改={}", resultWithReal, resultWithFake);
    }

    // ==================== 六、验证测试 ====================

    @Test
    @DisplayName("20. 挖矿后验证数据返回exists=true, valid=true")
    void testVerifyData_AfterMining_ReturnsExistsAndValid() {
        String json = "{\"verify\":\"after_mining\",\"value\":\"important\"}";
        String dataHash = service.generateDataHash(json);
        service.storeData("MONITOR", json, "verifier");
        service.minePendingBlock();

        BlockchainService.VerifyResult result = service.verifyData(dataHash);

        assertNotNull(result);
        assertTrue(result.isExists(), "挖矿后数据应存在");
        assertTrue(result.isValid(), "挖矿后数据应有效");
        assertTrue(result.getBlockNumber() >= 1, "区块号应≥1");
        assertNotEquals("PENDING", result.getBlockHash(), "区块哈希不应是PENDING");
        assertNotNull(result.getMerkleProof(), "应返回Merkle证明");
        log.info("挖矿后验证: exists={}, valid={}, block={}, hash={}",
                result.isExists(), result.isValid(), result.getBlockNumber(), result.getBlockHash());
    }

    @Test
    @DisplayName("21. 未挖矿的pending状态数据验证结果")
    void testVerifyData_Pending_ReturnsExistsButNotValid() {
        String json = "{\"status\":\"pending_only\"}";
        String dataHash = service.generateDataHash(json);
        service.storeData("SENSOR", json, "tester");

        BlockchainService.VerifyResult result = service.verifyData(dataHash);

        assertNotNull(result);
        assertTrue(result.isExists(), "Pending数据应能找到（存在）");
        assertFalse(result.isValid(), "Pending数据不应有效（未上链）");
        assertEquals(-1L, result.getBlockNumber(), "Pending区块号应为-1");
        assertEquals("PENDING", result.getBlockHash(), "Pending状态标记应为PENDING");
        assertNull(result.getMerkleProof(), "Pending不应有Merkle证明");
        log.info("Pending验证: exists={}, valid={}", result.isExists(), result.isValid());
    }

    @Test
    @DisplayName("22. 从未存证过的数据返回false")
    void testVerifyData_Nonexistent_ReturnsNotExists() {
        String ghostHash = service.generateDataHash("{\"never\":\"stored\"}");
        BlockchainService.VerifyResult result = service.verifyData(ghostHash);

        assertNotNull(result);
        assertFalse(result.isExists(), "未存证数据不应存在");
        assertFalse(result.isValid(), "未存证数据不应有效");
        log.info("不存在数据验证: exists={}, valid={}", result.isExists(), result.isValid());
    }

    // ==================== 七、防篡改测试 ====================

    @Test
    @DisplayName("23. 正常链通过防篡改校验")
    void testTamperProofCheck_HealthyChain_ReturnsValid() {
        for (int b = 0; b < 3; b++) {
            for (int t = 0; t < 2; t++) {
                service.storeData("DATA", "{\"block\":" + b + ",\"tx\":" + t + "}", "op");
            }
            service.minePendingBlock();
        }

        BlockchainService.TamperCheckResult result = service.tamperProofCheck();
        assertTrue(result.isValid(), "健康链应通过防篡改校验");
        assertTrue(result.getErrors().isEmpty(), "健康链不应有错误");
        assertEquals(4, result.getCheckedBlocks(), "应校验4个区块（含创世）");
        log.info("健康链校验: valid={}, checkedBlocks={}, errors={}",
                result.isValid(), result.getCheckedBlocks(), result.getErrors().size());
    }

    @Test
    @DisplayName("24. 篡改某区块哈希后校验失败（核心防篡改验证）")
    void testTamperProofCheck_TamperedBlockHash_Fails() throws Exception {
        service.storeData("A", "{\"t\":1}", "op");
        service.storeData("A", "{\"t\":2}", "op");
        service.minePendingBlock();

        List<BlockchainService.Block> chain = getBlockchain(service);
        BlockchainService.Block block1 = chain.get(1);
        String originalHash = block1.getHash();
        String fakeHash = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        block1.setHash(fakeHash);
        log.info("篡改前区块1哈希: {}", originalHash);
        log.info("篡改后区块1哈希: {}", fakeHash);

        BlockchainService.TamperCheckResult result = service.tamperProofCheck();
        assertFalse(result.isValid(), "篡改区块哈希后校验应失败");
        assertFalse(result.getErrors().isEmpty(), "应有错误信息");
        log.info("篡改区块哈希校验: valid={}, errors={}", result.isValid(), result.getErrors());
    }

    @Test
    @DisplayName("25. 篡改区块内交易数据后校验失败")
    void testTamperProofCheck_TamperedTransaction_Fails() throws Exception {
        service.storeData("B", "{\"secret\":\"original\"}", "op");
        service.minePendingBlock();

        List<BlockchainService.Block> chain = getBlockchain(service);
        BlockchainService.Block block1 = chain.get(1);
        String originalJson = block1.getTransactions().get(0).getDataJson();
        block1.getTransactions().get(0).setDataJson("{\"secret\":\"TAMPERED!\"}");
        log.info("篡改前交易数据: {}", originalJson);
        log.info("篡改后交易数据: {}", block1.getTransactions().get(0).getDataJson());

        BlockchainService.TamperCheckResult result = service.tamperProofCheck();
        assertFalse(result.isValid(), "篡改交易数据后Merkle根校验应失败");
        log.info("篡改交易校验: valid={}, errors={}", result.isValid(), result.getErrors());
    }

    @Test
    @DisplayName("26. 破坏prevHash链接后校验失败")
    void testTamperProofCheck_BrokenPrevHashLink_Fails() throws Exception {
        service.storeData("C", "{\"i\":1}", "op");
        service.minePendingBlock();
        service.storeData("C", "{\"i\":2}", "op");
        service.minePendingBlock();

        List<BlockchainService.Block> chain = getBlockchain(service);
        BlockchainService.Block block2 = chain.get(2);
        String originalPrev = block2.getPreviousHash();
        block2.setPreviousHash("0000000000000000000000000000000000000000000000000000000000000000");
        log.info("破坏前区块2.prevHash: {}", originalPrev);
        log.info("破坏后区块2.prevHash: {}", block2.getPreviousHash());

        BlockchainService.TamperCheckResult result = service.tamperProofCheck();
        assertFalse(result.isValid(), "破坏prevHash链接后校验应失败");
        log.info("破坏prevHash校验: valid={}, errors={}", result.isValid(), result.getErrors());
    }

    // ==================== 八、区块链信息测试 ====================

    @Test
    @DisplayName("27. getBlockchainInfo返回的统计数据与实际一致")
    void testBlockchainInfo_StatsAccurate() {
        String[] types = {"SENSOR", "MONITOR"};
        int txPerBlock = 2;
        int blocks = 2;
        long dataSizeSum = 0;

        for (int b = 0; b < blocks; b++) {
            for (int t = 0; t < txPerBlock; t++) {
                String json = "{\"blk\":" + b + ",\"tx\":" + t + ",\"type\":\"" + types[b % types.length] + "\"}";
                service.storeData(types[b % types.length], json, "op");
                dataSizeSum += json.getBytes().length;
            }
            service.minePendingBlock();
        }
        service.storeData("ANALYSIS", "{\"pending\":true}", "op");

        BlockchainService.BlockchainInfo info = service.getBlockchainInfo();

        assertEquals(1L + blocks, info.getBlockHeight(), "区块高度应=创世+已挖");
        assertEquals((long) blocks * txPerBlock, info.getTotalTransactions(), "已打包交易数");
        assertEquals(1L, info.getPendingTransactions(), "未打包pending交易数");
        assertEquals(1, info.getDifficulty(), "难度应=1");
        assertEquals(dataSizeSum + "{\"pending\":true}".getBytes().length,
                info.getTotalDataSize(), "总数据大小应匹配");

        log.info("链信息: height={}, totalTx={}, pending={}, dataSize={}",
                info.getBlockHeight(), info.getTotalTransactions(),
                info.getPendingTransactions(), info.getTotalDataSize());
    }

    @Test
    @DisplayName("28. 通过区块号查询正确区块")
    void testBlockLookup_ValidBlockNumber_ReturnsBlock() {
        String json1 = "{\"mark\":\"block1\"}";
        service.storeData("TEST", json1, "op");
        BlockchainService.Block b1 = service.minePendingBlock();

        String json2 = "{\"mark\":\"block2\"}";
        service.storeData("TEST", json2, "op");
        BlockchainService.Block b2 = service.minePendingBlock();

        BlockchainService.Block lookup0 = service.getBlock(0);
        BlockchainService.Block lookup1 = service.getBlock(1);
        BlockchainService.Block lookup2 = service.getBlock(2);

        assertEquals(0L, lookup0.getBlockNumber());
        assertEquals(b1.getHash(), lookup1.getHash());
        assertEquals(b2.getHash(), lookup2.getHash());
        assertEquals(1, lookup1.getTransactions().size());
        assertEquals(json1, lookup1.getTransactions().get(0).getDataJson());
    }

    @Test
    @DisplayName("29. 非法区块号抛出异常（边界）")
    void testBlockLookup_InvalidBlockNumber_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getBlock(-1), "负数区块号应抛异常");
        assertThrows(IllegalArgumentException.class,
                () -> service.getBlock(999), "超大区块号应抛异常");
        assertThrows(IllegalArgumentException.class,
                () -> service.getBlock(1), "创世后只有height=0，查询1应抛异常");
    }

    // ==================== 九、区块连续性测试 ====================

    @Test
    @DisplayName("30. 新区块的previousHash等于上一区块的hash（链的连续性）")
    void testBlockChain_PreviousHashLinks() throws Exception {
        int blocksToMine = 4;
        for (int i = 0; i < blocksToMine; i++) {
            service.storeData("CHAIN", "{\"link\":" + i + "}", "op");
            service.minePendingBlock();
        }

        List<BlockchainService.Block> chain = getBlockchain(service);
        assertEquals(blocksToMine + 1, chain.size(), "链长应=创世+" + blocksToMine);

        for (int i = 1; i < chain.size(); i++) {
            BlockchainService.Block prev = chain.get(i - 1);
            BlockchainService.Block curr = chain.get(i);
            assertEquals(prev.getHash(), curr.getPreviousHash(),
                    "区块" + i + ".prevHash应等于区块" + (i - 1) + ".hash");
            log.info("链链接校验: block[{}].hash[:12]={} -> block[{}].prevHash[:12]={}",
                    i - 1, prev.getHash().substring(0, 12),
                    i, curr.getPreviousHash().substring(0, 12));
        }
    }

    @Test
    @DisplayName("31. 区块号连续递增")
    void testBlockChain_BlockNumbersSequential() throws Exception {
        for (int i = 0; i < 5; i++) {
            service.storeData("SEQ", "{\"n\":" + i + "}", "op");
            if (i % 2 == 1) service.minePendingBlock();
        }
        service.minePendingBlock();

        List<BlockchainService.Block> chain = getBlockchain(service);
        for (int i = 0; i < chain.size(); i++) {
            assertEquals(i, chain.get(i).getBlockNumber(),
                    "区块号应严格连续, index=" + i);
        }
        log.info("区块号连续校验通过: [0..{}]", chain.size() - 1);
    }
}
