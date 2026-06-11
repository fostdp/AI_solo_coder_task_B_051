import request from './request'
import type { ApiResponse, PageResult } from '@/types'

export interface BlockchainInfo {
  blockHeight: number
  totalTransactions: number
  totalDataSizeMb: number
  chainHashValid: boolean
  pendingTxCount: number
  difficulty: number
  latestBlockTime: string
  latestBlockHash: string
}

export interface BlockData {
  blockNumber: number
  timestamp: string
  previousHash: string
  merkleRoot: string
  transactionCount: number
  nonce: number
  hash: string
  transactions: TransactionData[]
}

export interface TransactionData {
  txHash: string
  dataType: string
  dataHash: string
  dataSummary: string
  operator: string
  blockNumber: number
  timestamp: string
  verified: boolean
}

export interface VerifyResult {
  valid: boolean
  blockNumber?: number
  txHash?: string
  merkleProof?: string[]
  message: string
}

export interface StoreDataParams {
  dataType: string
  data: string
  operator: string
}

export interface RecordsParams {
  dataType?: string
  page?: number
  pageSize?: number
}

export const blockchainApi = {
  fetchBlockchainInfo: (): Promise<ApiResponse<BlockchainInfo>> => {
    return request.get<ApiResponse<BlockchainInfo>>('/blockchain/info')
  },

  fetchBlock: (blockNumber: number): Promise<ApiResponse<BlockData>> => {
    return request.get<ApiResponse<BlockData>>(`/blockchain/block/${blockNumber}`)
  },

  fetchLatestBlock: (): Promise<ApiResponse<BlockData>> => {
    return request.get<ApiResponse<BlockData>>('/blockchain/block/latest')
  },

  fetchRecords: (params: RecordsParams): Promise<ApiResponse<PageResult<TransactionData>>> => {
    return request.get<ApiResponse<PageResult<TransactionData>>>('/blockchain/records', { params })
  },

  storeData: (data: StoreDataParams): Promise<ApiResponse<{ txHash: string }>> => {
    return request.post<ApiResponse<{ txHash: string }>>('/blockchain/store', data)
  },

  verifyData: (dataHash: string): Promise<ApiResponse<VerifyResult>> => {
    return request.get<ApiResponse<VerifyResult>>(`/blockchain/verify/${dataHash}`)
  },

  mineBlock: (): Promise<ApiResponse<BlockData>> => {
    return request.post<ApiResponse<BlockData>>('/blockchain/mine')
  }
}
