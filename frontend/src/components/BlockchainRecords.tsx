import { useState, useEffect, useCallback } from 'react'
import {
  Database,
  ShieldCheck,
  Copy,
  Search,
  FileText,
  Hash,
  Clock,
  CheckCircle,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Plus,
  RefreshCw,
  Activity,
  Layers,
  Zap,
  Eye,
  X
} from 'lucide-react'
import dayjs from 'dayjs'
import type {
  BlockchainInfo,
  BlockData,
  TransactionData,
  VerifyResult,
  RecordsParams,
  StoreDataParams
} from '@/utils/blockchainApi'
import { blockchainApi } from '@/utils/blockchainApi'

const mockBlockchainInfo: BlockchainInfo = {
  blockHeight: 1847293,
  totalTransactions: 52847,
  totalDataSizeMb: 2048.5,
  chainHashValid: true,
  pendingTxCount: 12,
  difficulty: 18234567.89,
  latestBlockTime: '2024-01-15T14:32:18Z',
  latestBlockHash: '0x8a7b3c9f1e2d4a5b6c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b'
}

const mockTransactions: TransactionData[] = [
  {
    txHash: '0x1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b',
    dataType: 'SALT_DATA',
    dataHash: '0xabc123def456...',
    dataSummary: '一号墓室盐份监测数据 - 2024-01-15',
    operator: '张工程师',
    blockNumber: 1847293,
    timestamp: '2024-01-15T14:32:18Z',
    verified: true
  },
  {
    txHash: '0x2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c',
    dataType: 'ENV_DATA',
    dataHash: '0xdef789ghi012...',
    dataSummary: '环境温湿度数据 - 三号墓室',
    operator: '李研究员',
    blockNumber: 1847292,
    timestamp: '2024-01-15T14:28:05Z',
    verified: true
  },
  {
    txHash: '0x3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d',
    dataType: 'REPAIR_RECORD',
    dataHash: '0xjkl345mno678...',
    dataSummary: '壁画修复记录 - 主墓室西侧',
    operator: '王师傅',
    blockNumber: 1847291,
    timestamp: '2024-01-15T13:45:30Z',
    verified: true
  },
  {
    txHash: '0x4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e',
    dataType: 'ANALYSIS_REPORT',
    dataHash: '0xpqr901stu234...',
    dataSummary: '盐份侵蚀分析报告 - Q1季度',
    operator: '赵博士',
    blockNumber: 1847290,
    timestamp: '2024-01-15T12:20:15Z',
    verified: true
  },
  {
    txHash: '0x5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f',
    dataType: 'SALT_DATA',
    dataHash: '0xvwx567yz890...',
    dataSummary: '二号墓室盐份监测数据 - 2024-01-15',
    operator: '张工程师',
    blockNumber: 0,
    timestamp: '2024-01-15T14:35:00Z',
    verified: false
  },
  {
    txHash: '0x6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a',
    dataType: 'ENV_DATA',
    dataHash: '0xabc111def222...',
    dataSummary: '环境温湿度数据 - 一号墓室',
    operator: '陈技术员',
    blockNumber: 0,
    timestamp: '2024-01-15T14:36:30Z',
    verified: false
  }
]

const mockLatestBlock: BlockData = {
  blockNumber: 1847293,
  timestamp: '2024-01-15T14:32:18Z',
  previousHash: '0x7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c',
  merkleRoot: '0x9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e',
  transactionCount: 8,
  nonce: 428571,
  hash: '0x8a7b3c9f1e2d4a5b6c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b',
  transactions: mockTransactions.slice(0, 2)
}

const DATA_TYPES = [
  { value: 'SALT_DATA', label: '盐份数据', color: 'text-cyan-400 bg-cyan-500/20 border-cyan-500/30' },
  { value: 'ENV_DATA', label: '环境数据', color: 'text-green-400 bg-green-500/20 border-green-500/30' },
  { value: 'REPAIR_RECORD', label: '修复记录', color: 'text-yellow-400 bg-yellow-500/20 border-yellow-500/30' },
  { value: 'ANALYSIS_REPORT', label: '分析报告', color: 'text-purple-400 bg-purple-500/20 border-purple-500/30' }
]

function DataTypeBadge({ type }: { type: string }) {
  const typeInfo = DATA_TYPES.find(t => t.value === type)
  const colorClass = typeInfo?.color || 'text-gray-400 bg-gray-500/20 border-gray-500/30'
  return (
    <span className={`px-2 py-1 text-xs font-medium rounded border ${colorClass}`}>
      {typeInfo?.label || type}
    </span>
  )
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('复制失败:', err)
    }
  }

  return (
    <button
      onClick={handleCopy}
      className="p-1.5 rounded-md hover:bg-gray-700/50 text-gray-400 hover:text-cyan-400 transition-colors"
      title={copied ? '已复制!' : '复制'}
    >
      {copied ? <CheckCircle size={14} className="text-green-400" /> : <Copy size={14} />}
    </button>
  )
}

function HashDisplay({ hash, showFull = false }: { hash: string; showFull?: boolean }) {
  const displayHash = showFull ? hash : `${hash.slice(0, 10)}...${hash.slice(-8)}`
  return (
    <div className="flex items-center gap-2 group">
      <span className="font-mono text-sm text-cyan-300" title={hash}>
        {displayHash}
      </span>
      <CopyButton text={hash} />
    </div>
  )
}

function ChainStatusIndicator({ valid }: { valid: boolean }) {
  return (
    <div className="flex items-center gap-2">
      <div className="relative">
        <div className={`w-3 h-3 rounded-full ${valid ? 'bg-green-400' : 'bg-red-400'}`} />
        {valid && (
          <div className="absolute inset-0 w-3 h-3 rounded-full bg-green-400 animate-ping opacity-75" />
        )}
      </div>
      <span className={`text-sm font-medium ${valid ? 'text-green-400' : 'text-red-400'}`}>
        {valid ? '链正常' : '链异常'}
      </span>
    </div>
  )
}

function StatCard({
  title,
  value,
  icon: Icon,
  color = 'cyan',
  suffix,
  description
}: {
  title: string
  value: string | number
  icon: React.ElementType
  color?: 'cyan' | 'green' | 'yellow' | 'blue'
  suffix?: string
  description?: string
}) {
  const colorClasses: Record<string, string> = {
    cyan: 'from-cyan-500/20 to-cyan-500/5 text-cyan-400 border-cyan-500/30',
    green: 'from-green-500/20 to-green-500/5 text-green-400 border-green-500/30',
    yellow: 'from-yellow-500/20 to-yellow-500/5 text-yellow-400 border-yellow-500/30',
    blue: 'from-blue-500/20 to-blue-500/5 text-blue-400 border-blue-500/30'
  }
  const iconBgClasses: Record<string, string> = {
    cyan: 'bg-cyan-500/10 text-cyan-400',
    green: 'bg-green-500/10 text-green-400',
    yellow: 'bg-yellow-500/10 text-yellow-400',
    blue: 'bg-blue-500/10 text-blue-400'
  }

  return (
    <div className={`glass-card p-5 bg-gradient-to-br ${colorClasses[color]}`}>
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-gray-400 text-sm font-medium mb-2">{title}</p>
          <p className="text-2xl font-bold text-white mb-1">
            {value}
            {suffix && <span className="text-base ml-1 text-gray-400">{suffix}</span>}
          </p>
          {description && <p className="text-gray-500 text-sm">{description}</p>}
        </div>
        <div className={`p-3 rounded-xl ${iconBgClasses[color]}`}>
          <Icon size={24} />
        </div>
      </div>
    </div>
  )
}

function DetailModal({
  transaction,
  onClose
}: {
  transaction: TransactionData | null
  onClose: () => void
}) {
  if (!transaction) return null

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="glass-card bg-dark-200/95 rounded-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-5 border-b border-gray-700/50">
          <h3 className="text-lg font-bold text-white flex items-center gap-2">
            <FileText size={20} className="text-cyan-400" />
            存证详情
          </h3>
          <button
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-gray-700/50 text-gray-400 hover:text-white transition-colors"
          >
            <X size={20} />
          </button>
        </div>
        <div className="p-5 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-gray-400 text-sm mb-1">交易哈希</p>
              <HashDisplay hash={transaction.txHash} />
            </div>
            <div>
              <p className="text-gray-400 text-sm mb-1">数据类型</p>
              <DataTypeBadge type={transaction.dataType} />
            </div>
            <div>
              <p className="text-gray-400 text-sm mb-1">数据哈希</p>
              <HashDisplay hash={transaction.dataHash} />
            </div>
            <div>
              <p className="text-gray-400 text-sm mb-1">操作人</p>
              <p className="text-white">{transaction.operator}</p>
            </div>
            <div>
              <p className="text-gray-400 text-sm mb-1">区块号</p>
              <p className="text-white font-mono">
                {transaction.blockNumber > 0 ? `#${transaction.blockNumber}` : '待打包'}
              </p>
            </div>
            <div>
              <p className="text-gray-400 text-sm mb-1">状态</p>
              <span className={`inline-flex items-center gap-1 text-sm ${
                transaction.verified ? 'text-green-400' : 'text-yellow-400'
              }`}>
                {transaction.verified ? <CheckCircle size={14} /> : <Clock size={14} />}
                {transaction.verified ? '已确认' : '待打包'}
              </span>
            </div>
          </div>
          <div>
            <p className="text-gray-400 text-sm mb-1">数据摘要</p>
            <p className="text-white">{transaction.dataSummary}</p>
          </div>
          <div>
            <p className="text-gray-400 text-sm mb-1">时间戳</p>
            <p className="text-white">
              {dayjs(transaction.timestamp).format('YYYY-MM-DD HH:mm:ss')}
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

function BlockchainRecords() {
  const [blockchainInfo, setBlockchainInfo] = useState<BlockchainInfo>(mockBlockchainInfo)
  const [latestBlock, setLatestBlock] = useState<BlockData>(mockLatestBlock)
  const [records, setRecords] = useState<TransactionData[]>(mockTransactions)
  const [totalRecords, setTotalRecords] = useState(mockTransactions.length)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize] = useState(10)
  const [dataTypeFilter, setDataTypeFilter] = useState<string>('')
  const [verifyInput, setVerifyInput] = useState('')
  const [verifyResult, setVerifyResult] = useState<VerifyResult | null>(null)
  const [isVerifying, setIsVerifying] = useState(false)
  const [selectedTransaction, setSelectedTransaction] = useState<TransactionData | null>(null)
  const [storeDataType, setStoreDataType] = useState('SALT_DATA')
  const [storeData, setStoreData] = useState('')
  const [storeOperator, setStoreOperator] = useState('')
  const [isStoring, setIsStoring] = useState(false)
  const [storeResult, setStoreResult] = useState<{ txHash: string } | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const loadBlockchainInfo = useCallback(async () => {
    try {
      const response = await blockchainApi.fetchBlockchainInfo()
      if (response.code === 200) {
        setBlockchainInfo(response.data)
      }
    } catch {
      setBlockchainInfo(mockBlockchainInfo)
    }
  }, [])

  const loadLatestBlock = useCallback(async () => {
    try {
      const response = await blockchainApi.fetchLatestBlock()
      if (response.code === 200) {
        setLatestBlock(response.data)
      }
    } catch {
      setLatestBlock(mockLatestBlock)
    }
  }, [])

  const loadRecords = useCallback(async () => {
    setIsLoading(true)
    try {
      const params: RecordsParams = {
        page: currentPage,
        pageSize
      }
      if (dataTypeFilter) {
        params.dataType = dataTypeFilter
      }
      const response = await blockchainApi.fetchRecords(params)
      if (response.code === 200) {
        setRecords(response.data.list)
        setTotalRecords(response.data.total)
      }
    } catch {
      const filtered = dataTypeFilter
        ? mockTransactions.filter(t => t.dataType === dataTypeFilter)
        : mockTransactions
      setRecords(filtered)
      setTotalRecords(filtered.length)
    } finally {
      setIsLoading(false)
    }
  }, [currentPage, pageSize, dataTypeFilter])

  useEffect(() => {
    loadBlockchainInfo()
    loadLatestBlock()
    loadRecords()
  }, [loadBlockchainInfo, loadLatestBlock, loadRecords])

  useEffect(() => {
    loadRecords()
  }, [currentPage, dataTypeFilter, loadRecords])

  const handleVerify = async () => {
    if (!verifyInput.trim()) return
    setIsVerifying(true)
    setVerifyResult(null)
    try {
      const response = await blockchainApi.verifyData(verifyInput.trim())
      if (response.code === 200) {
        setVerifyResult(response.data)
      }
    } catch {
      const found = mockTransactions.find(t =>
        t.dataHash.includes(verifyInput) || t.txHash.includes(verifyInput)
      )
      if (found) {
        setVerifyResult({
          valid: true,
          blockNumber: found.blockNumber,
          txHash: found.txHash,
          merkleProof: [
            '0x111...aaa',
            '0x222...bbb',
            '0x333...ccc'
          ],
          message: '数据存在于区块链中，未被篡改'
        })
      } else {
        setVerifyResult({
          valid: false,
          message: '未找到对应的数据存证记录'
        })
      }
    } finally {
      setIsVerifying(false)
    }
  }

  const handleStore = async () => {
    if (!storeData.trim() || !storeOperator.trim()) return
    setIsStoring(true)
    setStoreResult(null)
    try {
      const params: StoreDataParams = {
        dataType: storeDataType,
        data: storeData,
        operator: storeOperator
      }
      const response = await blockchainApi.storeData(params)
      if (response.code === 200) {
        setStoreResult(response.data)
        loadBlockchainInfo()
        loadRecords()
      }
    } catch {
      const newTx: TransactionData = {
        txHash: '0x' + Math.random().toString(16).slice(2) + Math.random().toString(16).slice(2),
        dataType: storeDataType,
        dataHash: '0x' + Math.random().toString(16).slice(2) + '...',
        dataSummary: storeData.slice(0, 50) + (storeData.length > 50 ? '...' : ''),
        operator: storeOperator,
        blockNumber: 0,
        timestamp: new Date().toISOString(),
        verified: false
      }
      setRecords(prev => [newTx, ...prev])
      setStoreResult({ txHash: newTx.txHash })
      setBlockchainInfo(prev => ({
        ...prev,
        pendingTxCount: prev.pendingTxCount + 1
      }))
    } finally {
      setIsStoring(false)
    }
  }

  const handleMine = async () => {
    try {
      await blockchainApi.mineBlock()
      loadBlockchainInfo()
      loadLatestBlock()
      loadRecords()
    } catch {
      const newBlock: BlockData = {
        ...mockLatestBlock,
        blockNumber: mockLatestBlock.blockNumber + 1,
        timestamp: new Date().toISOString(),
        hash: '0x' + Math.random().toString(16).slice(2) + Math.random().toString(16).slice(2),
        previousHash: mockLatestBlock.hash
      }
      setLatestBlock(newBlock)
      setBlockchainInfo(prev => ({
        ...prev,
        blockHeight: prev.blockHeight + 1,
        totalTransactions: prev.totalTransactions + prev.pendingTxCount,
        pendingTxCount: 0,
        latestBlockTime: newBlock.timestamp,
        latestBlockHash: newBlock.hash
      }))
      setRecords(prev =>
        prev.map(tx =>
          !tx.verified
            ? { ...tx, verified: true, blockNumber: newBlock.blockNumber }
            : tx
        )
      )
    }
  }

  const totalPages = Math.ceil(totalRecords / pageSize)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-white flex items-center gap-3">
          <ShieldCheck size={28} className="text-cyan-400" />
          区块链存证系统
        </h2>
        <button
          onClick={handleMine}
          className="flex items-center gap-2 px-4 py-2 bg-cyan-500/20 border border-cyan-500/30 text-cyan-400 rounded-lg hover:bg-cyan-500/30 transition-colors"
        >
          <Zap size={18} />
          手动挖矿
        </button>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <StatCard
          title="区块高度"
          value={blockchainInfo.blockHeight.toLocaleString()}
          icon={Layers}
          color="cyan"
        />
        <StatCard
          title="总存证数"
          value={blockchainInfo.totalTransactions.toLocaleString()}
          icon={Database}
          color="blue"
        />
        <StatCard
          title="数据总量"
          value={blockchainInfo.totalDataSizeMb.toFixed(1)}
          icon={FileText}
          color="green"
          suffix="MB"
        />
        <StatCard
          title="链状态"
          value={blockchainInfo.chainHashValid ? '正常' : '异常'}
          icon={Activity}
          color={blockchainInfo.chainHashValid ? 'green' : 'yellow'}
          description={blockchainInfo.chainHashValid ? '哈希验证通过' : '需检查'}
        />
        <StatCard
          title="待打包"
          value={blockchainInfo.pendingTxCount}
          icon={Clock}
          color="yellow"
          suffix="笔"
        />
        <StatCard
          title="当前难度"
          value={(blockchainInfo.difficulty / 1000000).toFixed(2)}
          icon={Hash}
          color="cyan"
          suffix="M"
        />
      </div>

      <div className="glass-card p-5 bg-gradient-to-br from-dark-200/80 to-dark-300/80">
        <div className="flex items-center gap-2 mb-4">
          <RefreshCw size={20} className="text-cyan-400" />
          <h3 className="text-lg font-bold text-white">最新区块</h3>
          <span className="text-gray-400 text-sm">#{latestBlock.blockNumber}</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div>
            <p className="text-gray-400 text-sm mb-1">区块哈希</p>
            <HashDisplay hash={latestBlock.hash} />
          </div>
          <div>
            <p className="text-gray-400 text-sm mb-1">前一区块哈希</p>
            <HashDisplay hash={latestBlock.previousHash} />
          </div>
          <div>
            <p className="text-gray-400 text-sm mb-1">Merkle根</p>
            <HashDisplay hash={latestBlock.merkleRoot} />
          </div>
          <div className="space-y-2">
            <div>
              <p className="text-gray-400 text-sm mb-1">出块时间</p>
              <p className="text-white text-sm">
                {dayjs(latestBlock.timestamp).format('YYYY-MM-DD HH:mm:ss')}
              </p>
            </div>
            <div>
              <p className="text-gray-400 text-sm mb-1">交易数 / Nonce</p>
              <p className="text-white text-sm">
                {latestBlock.transactionCount} 笔 / {latestBlock.nonce}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 glass-card p-5 bg-dark-200/80">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <FileText size={20} className="text-cyan-400" />
              存证记录
            </h3>
            <div className="flex items-center gap-3">
              <select
                value={dataTypeFilter}
                onChange={e => setDataTypeFilter(e.target.value)}
                className="px-3 py-1.5 bg-dark-300 border border-gray-600 rounded-lg text-white text-sm focus:outline-none focus:border-cyan-500"
              >
                <option value="">全部类型</option>
                {DATA_TYPES.map(type => (
                  <option key={type.value} value={type.value}>
                    {type.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-700/50">
                  <th className="text-left py-3 px-3 text-gray-400 text-sm font-medium">交易哈希</th>
                  <th className="text-left py-3 px-3 text-gray-400 text-sm font-medium">类型</th>
                  <th className="text-left py-3 px-3 text-gray-400 text-sm font-medium">数据摘要</th>
                  <th className="text-left py-3 px-3 text-gray-400 text-sm font-medium">操作人</th>
                  <th className="text-left py-3 px-3 text-gray-400 text-sm font-medium">区块</th>
                  <th className="text-left py-3 px-3 text-gray-400 text-sm font-medium">状态</th>
                  <th className="text-left py-3 px-3 text-gray-400 text-sm font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map(record => (
                  <tr
                    key={record.txHash}
                    className="border-b border-gray-700/30 hover:bg-cyan-500/5 transition-colors cursor-pointer"
                    onClick={() => setSelectedTransaction(record)}
                  >
                    <td className="py-3 px-3">
                      <HashDisplay hash={record.txHash} />
                    </td>
                    <td className="py-3 px-3">
                      <DataTypeBadge type={record.dataType} />
                    </td>
                    <td className="py-3 px-3">
                      <p className="text-gray-300 text-sm truncate max-w-48">{record.dataSummary}</p>
                    </td>
                    <td className="py-3 px-3">
                      <p className="text-gray-300 text-sm">{record.operator}</p>
                    </td>
                    <td className="py-3 px-3">
                      <p className="text-gray-300 text-sm font-mono">
                        {record.blockNumber > 0 ? `#${record.blockNumber}` : '待打包'}
                      </p>
                    </td>
                    <td className="py-3 px-3">
                      <span className={`inline-flex items-center gap-1 text-sm ${
                        record.verified ? 'text-green-400' : 'text-yellow-400'
                      }`}>
                        {record.verified ? <CheckCircle size={14} /> : <Clock size={14} />}
                        {record.verified ? '已确认' : '待打包'}
                      </span>
                    </td>
                    <td className="py-3 px-3">
                      <button
                        className="p-1.5 rounded-md hover:bg-gray-700/50 text-gray-400 hover:text-cyan-400 transition-colors"
                        onClick={e => {
                          e.stopPropagation()
                          setSelectedTransaction(record)
                        }}
                      >
                        <Eye size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
                {records.length === 0 && (
                  <tr>
                    <td colSpan={7} className="py-8 text-center text-gray-500">
                      暂无存证记录
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-700/50">
              <p className="text-gray-400 text-sm">
                共 {totalRecords} 条记录，第 {currentPage}/{totalPages} 页
              </p>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                  disabled={currentPage === 1}
                  className="p-2 rounded-lg border border-gray-600 text-gray-400 hover:text-white hover:border-cyan-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronLeft size={18} />
                </button>
                <button
                  onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                  disabled={currentPage === totalPages}
                  className="p-2 rounded-lg border border-gray-600 text-gray-400 hover:text-white hover:border-cyan-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronRight size={18} />
                </button>
              </div>
            </div>
          )}
        </div>

        <div className="space-y-6">
          <div className="glass-card p-5 bg-dark-200/80">
            <h3 className="text-lg font-bold text-white mb-4 flex items-center gap-2">
              <Search size={20} className="text-cyan-400" />
              存证验证
            </h3>
            <div className="space-y-4">
              <div>
                <label className="block text-gray-400 text-sm mb-2">数据哈希</label>
                <input
                  type="text"
                  value={verifyInput}
                  onChange={e => setVerifyInput(e.target.value)}
                  placeholder="输入数据哈希进行验证..."
                  className="w-full px-4 py-2 bg-dark-300 border border-gray-600 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:border-cyan-500 font-mono text-sm"
                  onKeyDown={e => e.key === 'Enter' && handleVerify()}
                />
              </div>
              <button
                onClick={handleVerify}
                disabled={isVerifying || !verifyInput.trim()}
                className="w-full py-2.5 bg-cyan-500/20 border border-cyan-500/30 text-cyan-400 rounded-lg hover:bg-cyan-500/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
              >
                {isVerifying ? (
                  <RefreshCw size={18} className="animate-spin" />
                ) : (
                  <ShieldCheck size={18} />
                )}
                {isVerifying ? '验证中...' : '开始验证'}
              </button>

              {verifyResult && (
                <div className={`p-4 rounded-lg border ${
                  verifyResult.valid
                    ? 'bg-green-500/10 border-green-500/30'
                    : 'bg-red-500/10 border-red-500/30'
                }`}>
                  <div className="flex items-center gap-2 mb-2">
                    {verifyResult.valid ? (
                      <CheckCircle size={20} className="text-green-400" />
                    ) : (
                      <AlertCircle size={20} className="text-red-400" />
                    )}
                    <span className={`font-medium ${
                      verifyResult.valid ? 'text-green-400' : 'text-red-400'
                    }`}>
                      {verifyResult.valid ? '验证通过' : '验证失败'}
                    </span>
                  </div>
                  <p className="text-gray-300 text-sm mb-2">{verifyResult.message}</p>
                  {verifyResult.valid && verifyResult.blockNumber && (
                    <div className="space-y-1 text-sm">
                      <div className="flex justify-between">
                        <span className="text-gray-400">区块号:</span>
                        <span className="text-white font-mono">#{verifyResult.blockNumber}</span>
                      </div>
                      {verifyResult.txHash && (
                        <div className="flex justify-between items-center">
                          <span className="text-gray-400">交易哈希:</span>
                          <HashDisplay hash={verifyResult.txHash} />
                        </div>
                      )}
                    </div>
                  )}
                  {verifyResult.merkleProof && verifyResult.merkleProof.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-gray-700/50">
                      <p className="text-gray-400 text-sm mb-2">Merkle证明:</p>
                      <div className="space-y-1">
                        {verifyResult.merkleProof.map((proof, idx) => (
                          <div key={idx} className="flex items-center gap-2">
                            <span className="text-gray-500 text-xs">{idx + 1}.</span>
                            <span className="font-mono text-xs text-cyan-300">{proof}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="glass-card p-5 bg-dark-200/80">
            <h3 className="text-lg font-bold text-white mb-4 flex items-center gap-2">
              <Plus size={20} className="text-cyan-400" />
              提交存证
            </h3>
            <div className="space-y-4">
              <div>
                <label className="block text-gray-400 text-sm mb-2">数据类型</label>
                <select
                  value={storeDataType}
                  onChange={e => setStoreDataType(e.target.value)}
                  className="w-full px-4 py-2 bg-dark-300 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-cyan-500"
                >
                  {DATA_TYPES.map(type => (
                    <option key={type.value} value={type.value}>
                      {type.label}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-gray-400 text-sm mb-2">操作人</label>
                <input
                  type="text"
                  value={storeOperator}
                  onChange={e => setStoreOperator(e.target.value)}
                  placeholder="请输入操作人姓名"
                  className="w-full px-4 py-2 bg-dark-300 border border-gray-600 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:border-cyan-500"
                />
              </div>
              <div>
                <label className="block text-gray-400 text-sm mb-2">数据内容 (JSON)</label>
                <textarea
                  value={storeData}
                  onChange={e => setStoreData(e.target.value)}
                  placeholder='{"key": "value"}'
                  rows={4}
                  className="w-full px-4 py-2 bg-dark-300 border border-gray-600 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:border-cyan-500 font-mono text-sm resize-none"
                />
              </div>
              <button
                onClick={handleStore}
                disabled={isStoring || !storeData.trim() || !storeOperator.trim()}
                className="w-full py-2.5 bg-gradient-to-r from-cyan-500 to-blue-500 text-white rounded-lg hover:from-cyan-600 hover:to-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2 font-medium"
              >
                {isStoring ? (
                  <RefreshCw size={18} className="animate-spin" />
                ) : (
                  <Database size={18} />
                )}
                {isStoring ? '提交中...' : '提交存证'}
              </button>

              {storeResult && (
                <div className="p-3 rounded-lg bg-green-500/10 border border-green-500/30">
                  <div className="flex items-center gap-2 mb-1">
                    <CheckCircle size={16} className="text-green-400" />
                    <span className="text-green-400 text-sm font-medium">提交成功</span>
                  </div>
                  <p className="text-gray-400 text-xs mb-1">交易哈希:</p>
                  <HashDisplay hash={storeResult.txHash} />
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <DetailModal
        transaction={selectedTransaction}
        onClose={() => setSelectedTransaction(null)}
      />
    </div>
  )
}

export default BlockchainRecords
