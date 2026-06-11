package com.saltdamage.blockchain.controller;

import cn.hutool.json.JSONUtil;
import com.saltdamage.blockchain.dto.BlockchainInfoDTO;
import com.saltdamage.blockchain.dto.BlockchainRecordDTO;
import com.saltdamage.blockchain.dto.StoreDataRequest;
import com.saltdamage.blockchain.entity.BlockchainRecordEntity;
import com.saltdamage.blockchain.repository.BlockchainRecordRepository;
import com.saltdamage.blockchain.service.BlockchainService;
import com.saltdamage.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/blockchain")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class BlockchainController {

    private final BlockchainService blockchainService;
    private final BlockchainRecordRepository blockchainRecordRepository;

    @PostMapping("/store")
    public ApiResponse<BlockchainRecordDTO> storeData(@RequestBody StoreDataRequest request) {
        log.info("存证数据, dataType: {}, operator: {}", request.getDataType(), request.getOperator());
        try {
            String txHash = blockchainService.storeData(
                    request.getDataType(),
                    request.getDataJson(),
                    request.getOperator()
            );

            String dataHash = blockchainService.generateDataHash(request.getDataJson());

            BlockchainRecordEntity entity = new BlockchainRecordEntity();
            entity.setTxHash(txHash);
            entity.setBlockNumber(-1L);
            entity.setDataType(request.getDataType());
            entity.setDataHash(dataHash);
            entity.setDataSummary(request.getDescription());
            entity.setOperator(request.getOperator());
            entity.setTimestamp(java.time.LocalDateTime.now());
            entity.setVerified(false);

            blockchainRecordRepository.save(entity);

            BlockchainRecordDTO dto = convertToDTO(entity);
            return ApiResponse.success("存证成功", dto);
        } catch (Exception e) {
            log.error("存证数据失败", e);
            return ApiResponse.error(500, "存证失败: " + e.getMessage());
        }
    }

    @GetMapping("/verify/{dataHash}")
    public ApiResponse<BlockchainService.VerifyResult> verifyData(@PathVariable String dataHash) {
        log.info("验证数据存证, dataHash: {}", dataHash);
        try {
            BlockchainService.VerifyResult result = blockchainService.verifyData(dataHash);
            if (result.isExists()) {
                return ApiResponse.success("验证完成", result);
            } else {
                return ApiResponse.error(404, "未找到该数据的存证记录");
            }
        } catch (Exception e) {
            log.error("验证数据失败", e);
            return ApiResponse.error(500, "验证失败: " + e.getMessage());
        }
    }

    @GetMapping("/block/{blockNumber}")
    public ApiResponse<BlockchainService.Block> getBlock(@PathVariable Long blockNumber) {
        log.info("查询区块, blockNumber: {}", blockNumber);
        try {
            BlockchainService.Block block = blockchainService.getBlock(blockNumber);
            return ApiResponse.success(block);
        } catch (IllegalArgumentException e) {
            log.warn("区块不存在, blockNumber: {}", blockNumber);
            return ApiResponse.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("查询区块失败", e);
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    public ApiResponse<BlockchainInfoDTO> getBlockchainInfo() {
        log.info("获取区块链信息");
        try {
            BlockchainService.BlockchainInfo info = blockchainService.getBlockchainInfo();
            BlockchainService.TamperCheckResult tamperCheck = blockchainService.tamperProofCheck();

            BlockchainInfoDTO dto = new BlockchainInfoDTO();
            dto.setBlockHeight(info.getBlockHeight());
            dto.setTotalTransactions(info.getTotalTransactions());
            dto.setTotalDataSizeMb(info.getTotalDataSize() / (1024.0 * 1024.0));
            dto.setChainHashValid(tamperCheck.isValid());
            dto.setPendingTxCount(info.getPendingTransactions());
            dto.setDifficulty(info.getDifficulty());
            dto.setLatestBlockHash(info.getLatestBlockHash());
            dto.setTypeStats(info.getTypeStats());
            dto.setDailyStats(info.getDailyStats());

            BlockchainService.Block latestBlock = blockchainService.getLatestBlock();
            if (latestBlock != null) {
                dto.setLatestBlockTime(latestBlock.getTimestamp());
            }

            return ApiResponse.success(dto);
        } catch (Exception e) {
            log.error("获取区块链信息失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/records")
    public ApiResponse<Page<BlockchainRecordDTO>> getRecords(
            @RequestParam(required = false) String dataType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询存证记录列表, dataType: {}, page: {}, size: {}", dataType, page, size);
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<BlockchainRecordEntity> entityPage;

            if (dataType != null && !dataType.isEmpty()) {
                entityPage = blockchainRecordRepository.findByDataTypeOrderByTimestampDesc(dataType, pageable);
            } else {
                entityPage = blockchainRecordRepository.findAllByOrderByTimestampDesc(pageable);
            }

            Page<BlockchainRecordDTO> dtoPage = entityPage.map(this::convertToDTO);
            return ApiResponse.success(dtoPage);
        } catch (Exception e) {
            log.error("查询存证记录失败", e);
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/mine")
    public ApiResponse<BlockchainService.Block> mineBlock() {
        log.info("手动挖矿打包");
        try {
            BlockchainService.Block block = blockchainService.minePendingBlock();
            if (block != null) {
                updateBlockRecords(block);
                return ApiResponse.success("挖矿成功", block);
            } else {
                return ApiResponse.error(400, "没有待打包的交易");
            }
        } catch (Exception e) {
            log.error("挖矿失败", e);
            return ApiResponse.error(500, "挖矿失败: " + e.getMessage());
        }
    }

    @GetMapping("/tamper-check")
    public ApiResponse<BlockchainService.TamperCheckResult> tamperCheck() {
        log.info("全链防篡改检查");
        try {
            BlockchainService.TamperCheckResult result = blockchainService.tamperProofCheck();
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("防篡改检查失败", e);
            return ApiResponse.error(500, "检查失败: " + e.getMessage());
        }
    }

    private BlockchainRecordDTO convertToDTO(BlockchainRecordEntity entity) {
        BlockchainRecordDTO dto = new BlockchainRecordDTO();
        dto.setId(entity.getId());
        dto.setTxHash(entity.getTxHash());
        dto.setBlockNumber(entity.getBlockNumber());
        dto.setDataType(entity.getDataType());
        dto.setDataHash(entity.getDataHash());
        dto.setDataSummary(entity.getDataSummary());
        dto.setOperator(entity.getOperator());
        dto.setMerkleProof(entity.getMerkleProof());
        dto.setTimestamp(entity.getTimestamp());
        dto.setVerified(entity.getVerified());
        dto.setCreateTime(entity.getCreateTime());

        if (entity.getBlockNumber() != null && entity.getBlockNumber() >= 0) {
            try {
                BlockchainService.Block block = blockchainService.getBlock(entity.getBlockNumber());
                dto.setBlockHash(block.getHash());
                dto.setPreviousBlockHash(block.getPreviousHash());
            } catch (Exception e) {
                log.warn("获取区块信息失败, blockNumber: {}", entity.getBlockNumber());
            }
        }

        return dto;
    }

    private void updateBlockRecords(BlockchainService.Block block) {
        for (BlockchainService.Transaction tx : block.getTransactions()) {
            blockchainRecordRepository.findByTxHash(tx.getTxHash()).ifPresent(entity -> {
                entity.setBlockNumber(block.getBlockNumber());
                entity.setVerified(true);

                try {
                    BlockchainService.MerkleProof merkleProof = blockchainService.generateMerkleProof(
                            block.getTransactions(), tx.getTxHash());
                    entity.setMerkleProof(JSONUtil.toJsonStr(merkleProof));
                } catch (Exception e) {
                    log.warn("生成Merkle证明失败, txHash: {}", tx.getTxHash());
                }

                blockchainRecordRepository.save(entity);
            });
        }
    }
}
