package com.saltdamage.integration;

import cn.hutool.crypto.digest.DigestUtil;
import com.saltdamage.service.BlockchainService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("四、BlockchainService 区块链存证服务集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BlockchainServiceIntegrationTest {

    @Autowired
    private BlockchainService blockchainService;

    // ===== 正常场景测试 =====

    @Test
    @Order(1)
    @DisplayName("1. 存证→挖矿→验证，完整流程成功")
    void testStoreAndVerify_EndToEnd_Success() {
        log.info("[测试开始] 存证→挖矿→验证完整流程");

        String dataJson = "{\"tombId\":1,\"chamberId\":1,\"rh\":55.5,\"temp\":22.3,\"time\":\"2026-01-01 10:00:00\"}";
        String dataType = "ENVIRONMENT";
        String operator = "test_user_01";

        String dataHash = blockchainService.generateDataHash(dataJson);
        assertNotNull(dataHash, "数据哈希不应为空");
        assertEquals(64, dataHash.length(), "SHA256哈希应为64字符");

        String txHash = blockchainService.storeData(dataType, dataJson, operator);
        assertNotNull(txHash, "交易哈希不应为空");
        log.info("存证提交 - txHash: {}, dataHash: {}", txHash.substring(0, 16) + "...", dataHash.substring(0, 16) + "...");

        BlockchainService.Block block = blockchainService.minePendingBlock();
        assertNotNull(block, "挖矿应返回新区块");
        assertEquals(1L, block.getBlockNumber(), "区块号应为1");
        assertEquals(1, block.getTransactions().size(), "区块应包含1笔交易");
        log.info("挖矿完成 - blockNumber: {}, 交易数: {}, hash前16位: {}",
                block.getBlockNumber(), block.getTransactions().size(), block.getHash().substring(0, 16));

        BlockchainService.VerifyResult verifyResult = blockchainService.verifyData(dataHash);
        assertNotNull(verifyResult, "验证结果不应为空");
        assertTrue(verifyResult.isExists(), "数据应存在");
        assertTrue(verifyResult.isValid(), "数据验证应通过");
        assertEquals(1L, verifyResult.getBlockNumber(), "应在区块1中");
        assertEquals(txHash, verifyResult.getTxHash(), "交易哈希应匹配");
        assertEquals(dataType, verifyResult.getDataType(), "数据类型应匹配");
        assertEquals(operator, verifyResult.getOperator(), "操作人应匹配");
        assertNotNull(verifyResult.getMerkleProof(), "应返回Merkle证明");

        log.info("[测试通过] 完整存证-验证流程成功，blockNumber: {}", verifyResult.getBlockNumber());
    }

    @Test
    @Order(2)
    @DisplayName("2. 存200条数据，挖矿2次，高度+2")
    void testStoreMultiple_MineAll_BlockHeightIncreases() {
        log.info("[测试开始] 批量存证200条+挖矿2次");

        long initialHeight = blockchainService.getBlockchainInfo().getBlockHeight();
        log.info("初始链高: {}", initialHeight);

        for (int i = 0; i < 200; i++) {
            String json = "{\"id\":" + i + ",\"type\":\"BATCH\",\"value\":" + (i * 1.5) + "}";
            blockchainService.storeData("CYCLE_COUNT", json, "batch_user");
        }
        log.info("200条存证提交完成，pending: {}", blockchainService.getPendingTransactionCount());

        BlockchainService.Block block1 = blockchainService.minePendingBlock();
        assertNotNull(block1);
        assertEquals(initialHeight + 1, block1.getBlockNumber());
        assertTrue(block1.getTransactions().size() > 0);
        log.info("第1次挖矿 - block: {}, 交易数: {}", block1.getBlockNumber(), block1.getTransactions().size());

        BlockchainService.Block block2 = blockchainService.minePendingBlock();
        assertNotNull(block2, "仍有待打包交易，应继续挖矿成功");
        assertEquals(initialHeight + 2, block2.getBlockNumber());
        log.info("第2次挖矿 - block: {}, 交易数: {}", block2.getBlockNumber(), block2.getTransactions().size());

        long finalHeight = blockchainService.getBlockchainInfo().getBlockHeight();
        assertEquals(initialHeight + 2, finalHeight, "链高应增加2");
        log.info("[测试通过] 批量存证完成 - 链高: {}→{}, pending: {}",
                initialHeight, finalHeight, blockchainService.getPendingTransactionCount());
    }

    @Test
    @Order(3)
    @DisplayName("3. 新创建的链通过防篡改检查")
    void testTamperProofCheck_FreshChain_Passes() {
        log.info("[测试开始] 全新链防篡改校验");

        BlockchainService.TamperCheckResult result = blockchainService.tamperProofCheck();

        assertNotNull(result);
        assertTrue(result.isValid(), "全新链校验应通过");
        assertTrue(result.getErrors().isEmpty(), "不应有任何错误");
        assertTrue(result.getCheckedBlocks() > 0, "应检查了至少创世区块");
        assertNotNull(result.getCheckTime(), "校验时间不应为空");

        log.info("[测试通过] 防篡改校验通过 - 检查区块数: {}, 错误数: {}",
                result.getCheckedBlocks(), result.getErrors().size());
    }

    @Test
    @Order(4)
    @DisplayName("4. 统计信息准确")
    void testGetBlockchainInfo_AccurateStats() {
        log.info("[测试开始] 区块链统计信息");

        BlockchainService.BlockchainInfo info = blockchainService.getBlockchainInfo();

        assertNotNull(info);
        assertTrue(info.getBlockHeight() >= 0, "链高应>=0");
        assertTrue(info.getTotalTransactions() >= 0, "总交易数应>=0");
        assertTrue(info.getPendingTransactions() >= 0, "待打包交易数应>=0");
        assertTrue(info.getTotalDataSize() >= 0, "总数据量应>=0");
        assertTrue(info.getDifficulty() >= 1, "难度应>=1");
        assertTrue(info.getMaxBlockSize() > 0, "maxBlockSize应>0");
        assertNotNull(info.getLatestBlockHash(), "最新区块哈希不应为空");
        assertNotNull(info.getTypeStats(), "类型统计Map不应为空");
        assertNotNull(info.getDailyStats(), "日统计Map不应为空");

        log.info("[测试通过] 统计信息 - 链高: {}, 总交易: {}, pending: {}, 难度: {}",
                info.getBlockHeight(), info.getTotalTransactions(),
                info.getPendingTransactions(), info.getDifficulty());
    }

    @Test
    @Order(5)
    @DisplayName("5. 4种类型各存1条，typeStats正确")
    void testStoreData_MultipleTypes_CorrectlyIndexed() {
        log.info("[测试开始] 多类型数据存证统计");

        String[] types = {"ENVIRONMENT", "DELAMINATION", "CYCLE_COUNT", "ALARM"};
        long[] initialCounts = new long[4];
        Map<String, Long> initialStats = blockchainService.getTypeStatistics();
        for (int i = 0; i < types.length; i++) {
            initialCounts[i] = initialStats.getOrDefault(types[i], 0L);
        }

        for (int i = 0; i < types.length; i++) {
            String json = "{\"test\":\"value_" + types[i] + "\",\"seq\":" + i + "}";
            String hash = blockchainService.storeData(types[i], json, "type_test");
            assertNotNull(hash);
        }

        Map<String, Long> afterStats = blockchainService.getTypeStatistics();
        for (int i = 0; i < types.length; i++) {
            Long newCount = afterStats.getOrDefault(types[i], 0L);
            assertEquals(initialCounts[i] + 1, newCount,
                    types[i] + "类型计数应+1");
        }

        blockchainService.minePendingBlock();

        log.info("[测试通过] 4种类型各存1条 - ENV:{}+1, DEL:{}+1, CYC:{}+1, ALM:{}+1",
                initialCounts[0], initialCounts[1], initialCounts[2], initialCounts[3]);
    }

    // ===== 边界场景测试 =====

    @Test
    @Order(6)
    @DisplayName("6. 无待打包交易，调用mine返回null，链高不变")
    void testMineEmpty_NoBlockAdded() {
        log.info("[测试开始] 无交易挖矿返回null");

        long heightBefore = blockchainService.getBlockchainInfo().getBlockHeight();
        long pendingBefore = blockchainService.getPendingTransactionCount();

        if (pendingBefore > 0) {
            blockchainService.minePendingBlock();
            heightBefore = blockchainService.getBlockchainInfo().getBlockHeight();
        }

        BlockchainService.Block result = blockchainService.minePendingBlock();
        assertNull(result, "无交易时mine应返回null");

        long heightAfter = blockchainService.getBlockchainInfo().getBlockHeight();
        assertEquals(heightBefore, heightAfter, "链高不应变化");

        log.info("[测试通过] 空挖矿 - 链高维持: {}", heightAfter);
    }

    @Test
    @Order(7)
    @DisplayName("7. 未挖矿的pending状态返回正确")
    void testVerifyData_PendingState_ReturnsExistsNotValid() {
        log.info("[测试开始] PENDING状态数据验证");

        String dataJson = "{\"status\":\"PENDING_TEST\",\"timestamp\":" + System.currentTimeMillis() + "}";
        String dataType = "PENDING_TYPE";
        String operator = "pending_user";

        String dataHash = blockchainService.generateDataHash(dataJson);
        String txHash = blockchainService.storeData(dataType, dataJson, operator);

        assertTrue(blockchainService.getPendingTransactionCount() > 0,
                "应存在待打包交易");

        BlockchainService.VerifyResult result = blockchainService.verifyData(dataHash);
        assertNotNull(result);
        assertTrue(result.isExists(), "数据应存在（pending状态）");
        assertFalse(result.isValid(), "未挖矿的pending数据valid应为false");
        assertEquals(-1L, result.getBlockNumber(), "pending数据blockNumber应为-1");
        assertEquals("PENDING", result.getBlockHash(), "pending数据blockHash应为'PENDING'");
        assertEquals(txHash, result.getTxHash());
        assertEquals(dataType, result.getDataType());
        assertNull(result.getMerkleProof(), "pending状态无Merkle证明");

        log.info("[测试通过] PENDING状态 - exists:true, valid:false, blockHash:PENDING");
    }

    @Test
    @Order(8)
    @DisplayName("8. 刚好100条交易（默认maxBlockSize）的大区块正常打包")
    void testLargeBlock_100Transactions_MinesSuccessfully() {
        log.info("[测试开始] 100条交易的大区块");

        long pendingBefore = blockchainService.getPendingTransactionCount();
        while (pendingBefore > 0) {
            blockchainService.minePendingBlock();
            pendingBefore = blockchainService.getPendingTransactionCount();
        }

        for (int i = 0; i < 100; i++) {
            String json = "{\"largeBlock\":\"true\",\"index\":" + i + ",\"data\":\"padding_" + "x".repeat(20) + "\"}";
            blockchainService.storeData("LARGE_BLOCK_TEST", json, "large_user");
        }
        assertEquals(100, blockchainService.getPendingTransactionCount(),
                "待打包交易应刚好100条");

        BlockchainService.Block largeBlock = blockchainService.minePendingBlock();
        assertNotNull(largeBlock, "100条交易的大区块应成功打包");
        assertTrue(largeBlock.getTransactions().size() >= 100,
                "区块应包含全部100条交易，实际: " + largeBlock.getTransactions().size());
        assertTrue(largeBlock.getHash().startsWith("0"),
                "区块哈希应满足难度要求(前缀0)");
        assertNotNull(largeBlock.getMerkleRoot(), "Merkle根不应为空");

        String firstDataHash = DigestUtil.sha256Hex("{\"largeBlock\":\"true\",\"index\":0,\"data\":\"padding_" + "x".repeat(20) + "\"}");
        BlockchainService.VerifyResult vr = blockchainService.verifyData(firstDataHash);
        assertTrue(vr.isExists(), "第1条应存在");
        assertTrue(vr.isValid(), "第1条验证应通过");
        assertEquals(largeBlock.getBlockNumber(), vr.getBlockNumber());

        log.info("[测试通过] 大区块打包 - blockNumber: {}, 交易数: {}, hash前缀: {}",
                largeBlock.getBlockNumber(), largeBlock.getTransactions().size(),
                largeBlock.getHash().substring(0, 8));
    }

    // ===== 异常场景测试 =====

    @Test
    @Order(9)
    @DisplayName("9. 区块号负数抛异常")
    void testGetBlock_InvalidNegativeNumber_ThrowsException() {
        log.info("[测试开始] 区块号负数异常");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> blockchainService.getBlock(-1L));

        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("不存在"),
                "异常消息应包含'不存在'，实际: " + ex.getMessage());
        log.info("[测试通过] 区块号-1异常: {}", ex.getMessage());
    }

    @Test
    @Order(10)
    @DisplayName("10. 超过链高抛异常")
    void testGetBlock_ExceedsHeight_ThrowsException() {
        log.info("[测试开始] 区块号超过链高异常");

        long currentHeight = blockchainService.getBlockchainInfo().getBlockHeight();
        long invalidNumber = currentHeight + 999;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> blockchainService.getBlock(invalidNumber));

        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("不存在"),
                "异常消息应提及不存在，实际: " + ex.getMessage());
        log.info("[测试通过] 区块号{}>链高{}异常: {}", invalidNumber, currentHeight, ex.getMessage());
    }

    @Test
    @Order(11)
    @DisplayName("11. 篡改链后防篡改检查确实失败（关键验证）")
    void testTamperProofCheck_AfterTampering_Fails() {
        log.info("[测试开始] 篡改后防篡改校验失败");

        BlockchainService freshService = new BlockchainService();
        for (int i = 0; i < 10; i++) {
            freshService.storeData("TAMPER_TEST", "{\"seq\":" + i + "}", "tester");
        }
        freshService.minePendingBlock();
        freshService.minePendingBlock();

        BlockchainService.TamperCheckResult beforeResult = freshService.tamperProofCheck();
        assertTrue(beforeResult.isValid(), "篡改前校验应通过");
        log.info("篡改前校验: valid={}, errors={}", beforeResult.isValid(), beforeResult.getErrors().size());

        try {
            Field blockchainField = BlockchainService.class.getDeclaredField("blockchain");
            blockchainField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<BlockchainService.Block> chain = (List<BlockchainService.Block>) blockchainField.get(freshService);

            if (chain.size() >= 2) {
                BlockchainService.Block targetBlock = chain.get(1);
                List<BlockchainService.Transaction> originalTxs = targetBlock.getTransactions();
                if (!originalTxs.isEmpty()) {
                    List<BlockchainService.Transaction> tamperedTxs = new ArrayList<>(originalTxs);
                    BlockchainService.Transaction firstTx = tamperedTxs.get(0);
                    BlockchainService.Transaction tamperedTx = BlockchainService.Transaction.builder()
                            .txHash(firstTx.getTxHash())
                            .dataType(firstTx.getDataType())
                            .dataHash(firstTx.getDataHash())
                            .dataJson("{\"TAMPERED\":\"YES,HACKED!!!\"}")
                            .operator("HACKER")
                            .timestamp(firstTx.getTimestamp())
                            .build();
                    tamperedTxs.set(0, tamperedTx);
                    targetBlock.setTransactions(tamperedTxs);
                    log.info("已篡改区块1的第1笔交易数据内容");
                }
            }
        } catch (Exception e) {
            log.error("反射篡改失败，改用其他方式: {}", e.getMessage());

            try {
                Field blockchainField = BlockchainService.class.getDeclaredField("blockchain");
                blockchainField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<BlockchainService.Block> chain = (List<BlockchainService.Block>) blockchainField.get(freshService);
                if (chain.size() >= 2) {
                    BlockchainService.Block block = chain.get(1);
                    block.setHash("000000_FAKE_HASH_TAMPERED_1234567890abcdef");
                    log.info("已直接篡改区块1的hash字段");
                }
            } catch (Exception e2) {
                log.error("再次篡改失败: {}", e2.getMessage());
            }
        }

        BlockchainService.TamperCheckResult afterResult = freshService.tamperProofCheck();
        assertNotNull(afterResult);
        assertFalse(afterResult.isValid(), "篡改后校验应失败");
        assertTrue(afterResult.getErrors() != null && !afterResult.getErrors().isEmpty(),
                "应有错误信息");

        log.info("[测试通过] 篡改后校验 - valid:{}, 错误数:{}, 错误:{}",
                afterResult.isValid(),
                afterResult.getErrors().size(),
                afterResult.getErrors().isEmpty() ? "[]" : afterResult.getErrors().get(0));
    }
}
