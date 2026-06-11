import { useState, useMemo } from 'react'
import { Search, Download, Filter, Database, FileSpreadsheet, ChevronLeft, ChevronRight } from 'lucide-react'
import { useMockData } from '@/hooks/useMockData'
import { SaltData, EnvData } from '@/types'

const PAGE_SIZE = 15

function formatTimestamp(ts: number): string {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function DataCenter(): JSX.Element {
  const { saltData, envData, devices, chambers, loading } = useMockData()
  const [dataType, setDataType] = useState<'salt' | 'env'>('salt')
  const [searchQuery, setSearchQuery] = useState('')
  const [chamberFilter, setChamberFilter] = useState('')
  const [currentPage, setCurrentPage] = useState(1)

  const deviceMap = useMemo(() => {
    const map = new Map<string, { code: string; chamberId: string }>()
    devices.forEach(d => map.set(d.id, { code: d.code, chamberId: d.chamberId }))
    return map
  }, [devices])

  const chamberMap = useMemo(() => {
    const map = new Map<string, string>()
    chambers.forEach(c => map.set(c.id, c.name))
    return map
  }, [chambers])

  const chamberOptions = useMemo(() => {
    return chambers.map(c => ({ id: c.id, name: c.name }))
  }, [chambers])

  const filteredSaltData = useMemo(() => {
    let data = saltData
    if (searchQuery) {
      const q = searchQuery.toLowerCase()
      data = data.filter(d => {
        const code = deviceMap.get(d.deviceId)?.code?.toLowerCase() ?? ''
        return code.includes(q) || d.deviceId.toLowerCase().includes(q)
      })
    }
    if (chamberFilter) {
      data = data.filter(d => d.chamberId === chamberFilter)
    }
    return data
  }, [saltData, searchQuery, chamberFilter, deviceMap])

  const filteredEnvData = useMemo(() => {
    let data = envData
    if (searchQuery) {
      const q = searchQuery.toLowerCase()
      data = data.filter(d => {
        const code = deviceMap.get(d.deviceId)?.code?.toLowerCase() ?? ''
        return code.includes(q) || d.deviceId.toLowerCase().includes(q)
      })
    }
    if (chamberFilter) {
      data = data.filter(d => d.chamberId === chamberFilter)
    }
    return data
  }, [envData, searchQuery, chamberFilter, deviceMap])

  const currentData = dataType === 'salt' ? filteredSaltData : filteredEnvData
  const totalPages = Math.max(1, Math.ceil(currentData.length / PAGE_SIZE))
  const safeCurrentPage = Math.min(currentPage, totalPages)
  const paginatedData = currentData.slice((safeCurrentPage - 1) * PAGE_SIZE, safeCurrentPage * PAGE_SIZE)

  const todayStart = new Date()
  todayStart.setHours(0, 0, 0, 0)
  const todayTimestamp = todayStart.getTime()
  const todaySaltCount = saltData.filter(d => d.timestamp >= todayTimestamp).length
  const todayEnvCount = envData.filter(d => d.timestamp >= todayTimestamp).length
  const totalRecords = saltData.length + envData.length
  const storageMB = (totalRecords * 0.15).toFixed(1)

  const getSaltRowColor = (totalSalt: number): string => {
    if (totalSalt > 5.0) return 'text-red-400 font-bold'
    if (totalSalt > 3.0) return 'text-yellow-400 font-bold'
    return 'text-green-400'
  }

  const getHumidityColor = (humidity: number): string => {
    if (humidity > 75) return 'text-red-400 font-bold'
    return 'text-white'
  }

  const handleDataTypeChange = (type: 'salt' | 'env') => {
    setDataType(type)
    setCurrentPage(1)
    setSearchQuery('')
    setChamberFilter('')
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-gray-400 text-lg">加载中...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white font-serif-sc">数据中心</h2>
          <p className="text-gray-400 mt-1">查询、浏览和导出历史监测数据</p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2 bg-dark-200 p-1 rounded-lg">
          <button
            onClick={() => handleDataTypeChange('salt')}
            className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
              dataType === 'salt'
                ? 'bg-bronze-green text-white'
                : 'text-gray-400 hover:text-white hover:bg-dark-300'
            }`}
          >
            <Database size={16} />
            盐分数据
          </button>
          <button
            onClick={() => handleDataTypeChange('env')}
            className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
              dataType === 'env'
                ? 'bg-bronze-green text-white'
                : 'text-gray-400 hover:text-white hover:bg-dark-300'
            }`}
          >
            <Database size={16} />
            环境数据
          </button>
        </div>
      </div>

      <div className="glass-card p-6">
        <div className="flex flex-wrap items-center gap-4 mb-6">
          <div className="flex items-center gap-2 flex-1 max-w-md">
            <Search size={18} className="text-gray-400" />
            <input
              type="text"
              placeholder="搜索设备编号..."
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1) }}
              className="input-field flex-1"
            />
          </div>
          <div className="flex items-center gap-2">
            <Filter size={18} className="text-gray-400" />
            <select
              value={chamberFilter}
              onChange={(e) => { setChamberFilter(e.target.value); setCurrentPage(1) }}
              className="input-field w-40"
            >
              <option value="">所有墓室</option>
              {chamberOptions.map(c => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 btn-primary">
            <Download size={16} />
            导出数据
          </button>
        </div>

        <div className="overflow-x-auto">
          {dataType === 'salt' ? (
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-700">
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">设备编号</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">墓室</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">Na⁺</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">Ca²⁺</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">SO₄²⁻</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">Cl⁻</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">总盐量</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">采集时间</th>
                </tr>
              </thead>
              <tbody>
                {(paginatedData as SaltData[]).map((row, idx) => {
                  const deviceCode = deviceMap.get(row.deviceId)?.code ?? row.deviceId
                  const chamberName = chamberMap.get(row.chamberId) ?? row.chamberId
                  return (
                    <tr key={`${row.deviceId}-${row.timestamp}-${idx}`} className="border-b border-gray-800 hover:bg-dark-300/50 transition-colors">
                      <td className="py-4 px-4 text-white font-mono text-sm">{deviceCode}</td>
                      <td className="py-4 px-4 text-gray-400">{chamberName}</td>
                      <td className="py-4 px-4 text-white">{row.naPlus.toFixed(2)}</td>
                      <td className="py-4 px-4 text-white">{row.ca2Plus.toFixed(2)}</td>
                      <td className="py-4 px-4 text-white">{row.so42Minus.toFixed(2)}</td>
                      <td className="py-4 px-4 text-white">{row.clMinus.toFixed(2)}</td>
                      <td className="py-4 px-4">
                        <span className={getSaltRowColor(row.totalSalt)}>
                          {row.totalSalt.toFixed(2)} mg/cm²
                        </span>
                      </td>
                      <td className="py-4 px-4 text-gray-400 text-sm">{formatTimestamp(row.timestamp)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          ) : (
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-700">
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">设备编号</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">墓室</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">温度</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">湿度</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">风速</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">采集时间</th>
                </tr>
              </thead>
              <tbody>
                {(paginatedData as EnvData[]).map((row, idx) => {
                  const deviceCode = deviceMap.get(row.deviceId)?.code ?? row.deviceId
                  const chamberName = chamberMap.get(row.chamberId) ?? row.chamberId
                  return (
                    <tr key={`${row.deviceId}-${row.timestamp}-${idx}`} className="border-b border-gray-800 hover:bg-dark-300/50 transition-colors">
                      <td className="py-4 px-4 text-white font-mono text-sm">{deviceCode}</td>
                      <td className="py-4 px-4 text-gray-400">{chamberName}</td>
                      <td className="py-4 px-4 text-white">{row.temperature.toFixed(1)}°C</td>
                      <td className="py-4 px-4">
                        <span className={getHumidityColor(row.humidity)}>
                          {row.humidity.toFixed(1)}%
                        </span>
                      </td>
                      <td className="py-4 px-4 text-white">{row.windSpeed.toFixed(2)} m/s</td>
                      <td className="py-4 px-4 text-gray-400 text-sm">{formatTimestamp(row.timestamp)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>

        <div className="flex items-center justify-between mt-6 pt-4 border-t border-gray-700">
          <p className="text-gray-400 text-sm">
            显示第 {(safeCurrentPage - 1) * PAGE_SIZE + 1}-{Math.min(safeCurrentPage * PAGE_SIZE, currentData.length)} 条，共 {currentData.length} 条记录
          </p>
          <div className="flex items-center gap-2">
            <button
              className="px-3 py-1.5 btn-secondary text-sm disabled:opacity-50"
              disabled={safeCurrentPage <= 1}
              onClick={() => setCurrentPage(p => p - 1)}
            >
              <ChevronLeft size={16} />
            </button>
            <span className="text-gray-400 text-sm px-3">第 {safeCurrentPage} 页 / 共 {totalPages} 页</span>
            <button
              className="px-3 py-1.5 btn-secondary text-sm disabled:opacity-50"
              disabled={safeCurrentPage >= totalPages}
              onClick={() => setCurrentPage(p => p + 1)}
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div className="glass-card p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 rounded-lg bg-bronze-green/10 flex items-center justify-center">
              <Database size={20} className="text-bronze-green" />
            </div>
            <h4 className="text-white font-bold">数据统计</h4>
          </div>
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">总记录数</span>
              <span className="text-white font-medium">{totalRecords.toLocaleString()}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">今日盐分数据</span>
              <span className="text-white font-medium">{todaySaltCount.toLocaleString()}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">今日环境数据</span>
              <span className="text-white font-medium">{todayEnvCount.toLocaleString()}</span>
            </div>
          </div>
        </div>
        <div className="glass-card p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 rounded-lg bg-bronze-green/10 flex items-center justify-center">
              <FileSpreadsheet size={20} className="text-bronze-green" />
            </div>
            <h4 className="text-white font-bold">存储信息</h4>
          </div>
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">存储占用</span>
              <span className="text-white font-medium">{storageMB} MB</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">盐分记录</span>
              <span className="text-white font-medium">{saltData.length.toLocaleString()}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">环境记录</span>
              <span className="text-white font-medium">{envData.length.toLocaleString()}</span>
            </div>
          </div>
        </div>
        <div className="glass-card p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 rounded-lg bg-bronze-green/10 flex items-center justify-center">
              <Download size={20} className="text-bronze-green" />
            </div>
            <h4 className="text-white font-bold">导出记录</h4>
          </div>
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">本月导出</span>
              <span className="text-white font-medium">15 次</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">最近导出</span>
              <span className="text-white font-medium">{formatTimestamp(Date.now() - 86400000).split(' ')[0]}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-400 text-sm">文件总数</span>
              <span className="text-white font-medium">87 个</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default DataCenter
