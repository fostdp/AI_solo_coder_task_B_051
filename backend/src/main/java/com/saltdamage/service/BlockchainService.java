package com.saltdamage.service;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BlockchainService {

    @Value("${blockchain.difficulty:3}")
    private int difficulty;

    @Value("${blockchain.max-block-size:100}")
    private int maxBlockSize;

    @Value("${blockchain.genesis-message:Genesis Block - Salt Damage Monitor System}")
    private String genesisMessage;

    private final List<Block> blockchain = new CopyOnWriteArrayList<>();
    private final List<Transaction> pendingTransactions = new CopyOnWriteArrayList<>();
    private final Map<String, TransactionReceipt> transactionIndex = new ConcurrentHashMap<>();
    private final AtomicLong totalDataSize = new AtomicLong(0);
    private final Map<String, Long> typeStats = new ConcurrentHashMap<>();
    private final Map<String, Long> dailyStats = new ConcurrentHashMap<>();

    public BlockchainService() {
        initGenesisBlock();
    }

    private void initGenesisBlock() {
        log.info("初始化创世区块...");
        Block genesisBlock = Block.builder()
                .blockNumber(0L)
                .timestamp(LocalDateTime.now())
                .previousHash("0")
                .merkleRoot("")
                .transactions(Collections.emptyList())
                .nonce(0L)
                .hash("")
                .build();

        genesisBlock.setMerkleRoot(computeMerkleRoot(Collections.emptyList()));
        genesisBlock.setHash(computeBlockHash(genesisBlock));
        genesisBlock.setNonce(mineBlock(genesisBlock));
        genesisBlock.setHash(computeBlockHashWithNonce(genesisBlock, genesisBlock.getNonce()));

        blockchain.add(genesisBlock);
        log.info("创世区块初始化完成, 区块哈希: {}", genesisBlock.getHash());
    }

    public String generateDataHash(String dataJson) {
        return DigestUtil.sha256Hex(dataJson);
    }

    public String storeData(String dataType, String dataJson, String operator) {
        log.info("存证数据, dataType: {}, operator: {}", dataType, operator);

        String dataHash = generateDataHash(dataJson);
        String txHash = DigestUtil.sha256Hex(dataType + dataHash + operator + System.nanoTime());

        Transaction tx = Transaction.builder()
                .txHash(txHash)
                .dataType(dataType)
                .dataHash(dataHash)
                .dataJson(dataJson)
                .operator(operator)
                .timestamp(LocalDateTime.now())
                .build();

        pendingTransactions.add(tx);

        transactionIndex.put(txHash, TransactionReceipt.builder()
                .txHash(txHash)
                .dataHash(dataHash)
                .dataType(dataType)
                .status("PENDING")
                .timestamp(LocalDateTime.now())
                .build());

        totalDataSize.addAndGet(dataJson != null ? dataJson.getBytes().length : 0);
        typeStats.merge(dataType, 1L, Long::sum);
        String dayKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        dailyStats.merge(dayKey, 1L, Long::sum);

        if (pendingTransactions.size() >= maxBlockSize) {
            log.info("待存证交易数达到阈值 {}, 触发自动打包", maxBlockSize);
            minePendingBlock();
        }

        log.info("数据存证提交成功, txHash: {}", txHash);
        return txHash;
    }

    public VerifyResult verifyData(String dataHash) {
        log.info("验证数据存证, dataHash: {}", dataHash);

        for (Block block : blockchain) {
            for (Transaction tx : block.getTransactions()) {
                if (tx.getDataHash().equals(dataHash)) {
                    MerkleProof merkleProof = generateMerkleProof(block.getTransactions(), tx.getTxHash());
                    boolean valid = verifyMerkleProof(tx.getTxHash(), block.getMerkleRoot(), merkleProof);

                    return VerifyResult.builder()
                            .exists(true)
                            .valid(valid)
                            .blockNumber(block.getBlockNumber())
                            .blockHash(block.getHash())
                            .txHash(tx.getTxHash())
                            .dataType(tx.getDataType())
                            .operator(tx.getOperator())
                            .timestamp(tx.getTimestamp())
                            .merkleProof(merkleProof)
                            .build();
                }
            }
        }

        TransactionReceipt receipt = transactionIndex.values().stream()
                .filter(r -> r.getDataHash().equals(dataHash))
                .findFirst()
                .orElse(null);

        if (receipt != null) {
            return VerifyResult.builder()
                    .exists(true)
                    .valid(false)
                    .blockNumber(-1L)
                    .blockHash("PENDING")
                    .txHash(receipt.getTxHash())
                    .dataType(receipt.getDataType())
                    .timestamp(receipt.getTimestamp())
                    .build();
        }

        return VerifyResult.builder()
                .exists(false)
                .valid(false)
                .build();
    }

    public Block getBlock(long blockNumber) {
        log.info("查询区块, blockNumber: {}", blockNumber);
        if (blockNumber < 0 || blockNumber >= blockchain.size()) {
            throw new IllegalArgumentException("区块号不存在: " + blockNumber);
        }
        return blockchain.get((int) blockNumber);
    }

    public Block getLatestBlock() {
        return blockchain.isEmpty() ? null : blockchain.get(blockchain.size() - 1);
    }

    public BlockchainInfo getBlockchainInfo() {
        long totalTxCount = blockchain.stream()
                .mapToLong(b -> b.getTransactions().size())
                .sum();
        long pendingCount = pendingTransactions.size();

        return BlockchainInfo.builder()
                .blockHeight(blockchain.size() - 1)
                .totalTransactions(totalTxCount)
                .pendingTransactions(pendingCount)
                .totalDataSize(totalDataSize.get())
                .difficulty(difficulty)
                .maxBlockSize(maxBlockSize)
                .latestBlockHash(getLatestBlock() != null ? getLatestBlock().getHash() : "")
                .typeStats(new HashMap<>(typeStats))
                .dailyStats(new HashMap<>(dailyStats))
                .build();
    }

    public synchronized Block minePendingBlock() {
        if (pendingTransactions.isEmpty()) {
            log.warn("没有待打包的交易");
            return null;
        }

        log.info("开始挖矿打包, 待打包交易数: {}", pendingTransactions.size());

        List<Transaction> batchTxs = new ArrayList<>(pendingTransactions.subList(0, Math.min(maxBlockSize, pendingTransactions.size())));

        Block previousBlock = getLatestBlock();
        long nextBlockNumber = previousBlock.getBlockNumber() + 1;

        Block newBlock = Block.builder()
                .blockNumber(nextBlockNumber)
                .timestamp(LocalDateTime.now())
                .previousHash(previousBlock.getHash())
                .transactions(batchTxs)
                .nonce(0L)
                .hash("")
                .build();

        newBlock.setMerkleRoot(computeMerkleRoot(batchTxs));
        long nonce = mineBlock(newBlock);
        newBlock.setNonce(nonce);
        newBlock.setHash(computeBlockHashWithNonce(newBlock, nonce));

        blockchain.add(newBlock);

        for (Transaction tx : batchTxs) {
            TransactionReceipt receipt = transactionIndex.get(tx.getTxHash());
            if (receipt != null) {
                receipt.setStatus("CONFIRMED");
                receipt.setBlockNumber(nextBlockNumber);
                receipt.setBlockHash(newBlock.getHash());
            }
        }

        pendingTransactions.removeAll(batchTxs);

        log.info("区块挖矿完成, blockNumber: {}, 交易数: {}, 哈希: {}",
                nextBlockNumber, batchTxs.size(), newBlock.getHash());

        return newBlock;
    }

    private long mineBlock(Block block) {
        String target = "0".repeat(difficulty);
        long nonce = 0;

        while (true) {
            String hash = computeBlockHashWithNonce(block, nonce);
            if (hash.startsWith(target)) {
                log.debug("挖矿成功, nonce: {}, hash: {}", nonce, hash);
                return nonce;
            }
            nonce++;
        }
    }

    private String computeBlockHash(Block block) {
        String content = block.getBlockNumber() +
                block.getTimestamp().toString() +
                block.getPreviousHash() +
                block.getMerkleRoot() +
                block.getNonce();
        return DigestUtil.sha256Hex(content);
    }

    private String computeBlockHashWithNonce(Block block, long nonce) {
        String content = block.getBlockNumber() +
                block.getTimestamp().toString() +
                block.getPreviousHash() +
                block.getMerkleRoot() +
                nonce;
        return DigestUtil.sha256Hex(content);
    }

    private String computeMerkleRoot(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return DigestUtil.sha256Hex("");
        }

        List<String> hashes = transactions.stream()
                .map(Transaction::getTxHash)
                .collect(Collectors.toList());

        return buildMerkleRoot(hashes);
    }

    private String buildMerkleRoot(List<String> hashes) {
        if (hashes.size() == 1) {
            return hashes.get(0);
        }

        List<String> nextLevel = new ArrayList<>();
        for (int i = 0; i < hashes.size(); i += 2) {
            String left = hashes.get(i);
            String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
            nextLevel.add(DigestUtil.sha256Hex(left + right));
        }

        return buildMerkleRoot(nextLevel);
    }

    public MerkleProof generateMerkleProof(List<Transaction> transactions, String txHash) {
        List<String> hashes = transactions.stream()
                .map(Transaction::getTxHash)
                .collect(Collectors.toList());

        int index = hashes.indexOf(txHash);
        if (index == -1) {
            throw new IllegalArgumentException("交易不在区块中: " + txHash);
        }

        List<MerkleProofNode> proof = new ArrayList<>();
        List<String> currentLevel = new ArrayList<>(hashes);

        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            int nextIndex = index / 2;

            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;

                if (i == index) {
                    proof.add(MerkleProofNode.builder()
                            .direction("right")
                            .hash(right)
                            .build());
                } else if (i + 1 == index) {
                    proof.add(MerkleProofNode.builder()
                            .direction("left")
                            .hash(left)
                            .build());
                }

                nextLevel.add(DigestUtil.sha256Hex(left + right));
            }

            currentLevel = nextLevel;
            index = nextIndex;
        }

        return MerkleProof.builder()
                .txHash(txHash)
                .merkleRoot(currentLevel.get(0))
                .proof(proof)
                .build();
    }

    public boolean verifyMerkleProof(String txHash, String merkleRoot, MerkleProof merkleProof) {
        if (merkleProof == null || merkleProof.getProof() == null) {
            return txHash.equals(merkleRoot);
        }

        String currentHash = txHash;
        for (MerkleProofNode node : merkleProof.getProof()) {
            if ("left".equals(node.getDirection())) {
                currentHash = DigestUtil.sha256Hex(node.getHash() + currentHash);
            } else {
                currentHash = DigestUtil.sha256Hex(currentHash + node.getHash());
            }
        }

        return currentHash.equals(merkleRoot);
    }

    public TamperCheckResult tamperProofCheck() {
        log.info("开始全链防篡改校验...");

        boolean valid = true;
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < blockchain.size(); i++) {
            Block currentBlock = blockchain.get(i);
            Block previousBlock = blockchain.get(i - 1);

            if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) {
                valid = false;
                errors.add("区块 " + i + " 的前一区块哈希不匹配");
            }

            String computedHash = computeBlockHashWithNonce(currentBlock, currentBlock.getNonce());
            if (!currentBlock.getHash().equals(computedHash)) {
                valid = false;
                errors.add("区块 " + i + " 的哈希校验失败");
            }

            String target = "0".repeat(difficulty);
            if (!currentBlock.getHash().startsWith(target)) {
                valid = false;
                errors.add("区块 " + i + " 的工作量证明无效");
            }

            String computedMerkleRoot = computeMerkleRoot(currentBlock.getTransactions());
            if (!currentBlock.getMerkleRoot().equals(computedMerkleRoot)) {
                valid = false;
                errors.add("区块 " + i + " 的Merkle根校验失败");
            }
        }

        log.info("全链防篡改校验完成, 结果: {}, 错误数: {}", valid, errors.size());

        return TamperCheckResult.builder()
                .valid(valid)
                .checkedBlocks(blockchain.size())
                .errors(errors)
                .checkTime(LocalDateTime.now())
                .build();
    }

    @Scheduled(fixedRate = 30000)
    public void scheduledMining() {
        if (!pendingTransactions.isEmpty()) {
            log.info("定时任务触发: 自动打包待存证交易, 数量: {}", pendingTransactions.size());
            minePendingBlock();
        }
    }

    public Map<String, Long> getTypeStatistics() {
        return new HashMap<>(typeStats);
    }

    public Map<String, Long> getDailyStatistics() {
        return new HashMap<>(dailyStats);
    }

    public Map<String, Long> getDailyStatisticsByDate(String date) {
        Long count = dailyStats.get(date);
        Map<String, Long> result = new HashMap<>();
        result.put(date, count != null ? count : 0L);
        return result;
    }

    public long getPendingTransactionCount() {
        return pendingTransactions.size();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockchainInfo {
        private long blockHeight;
        private long totalTransactions;
        private long pendingTransactions;
        private long totalDataSize;
        private int difficulty;
        private int maxBlockSize;
        private String latestBlockHash;
        private Map<String, Long> typeStats;
        private Map<String, Long> dailyStats;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Block {
        private long blockNumber;
        private LocalDateTime timestamp;
        private String previousHash;
        private String merkleRoot;
        private List<Transaction> transactions;
        private long nonce;
        private String hash;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transaction {
        private String txHash;
        private String dataType;
        private String dataHash;
        private String dataJson;
        private String operator;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerkleProof {
        private String txHash;
        private String merkleRoot;
        private List<MerkleProofNode> proof;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerkleProofNode {
        private String direction;
        private String hash;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyResult {
        private boolean exists;
        private boolean valid;
        private long blockNumber;
        private String blockHash;
        private String txHash;
        private String dataType;
        private String operator;
        private LocalDateTime timestamp;
        private MerkleProof merkleProof;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TamperCheckResult {
        private boolean valid;
        private int checkedBlocks;
        private List<String> errors;
        private LocalDateTime checkTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TransactionReceipt {
        private String txHash;
        private String dataHash;
        private String dataType;
        private String status;
        private Long blockNumber;
        private String blockHash;
        private LocalDateTime timestamp;
    }
}
