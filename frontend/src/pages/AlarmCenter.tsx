import { useState, useMemo } from 'react'
import { Filter, Check, XCircle, AlertOctagon, Eye, Play } from 'lucide-react'
import Loading from '@/components/Loading'
import AlarmBadge from '@/components/AlarmBadge'
import { useMockData } from '@/hooks/useMockData'
import { AlarmLevel, AlarmStatus } from '@/types'
import type { Alarm } from '@/types'

const alarmTypeLabels: Record<string, string> = {
  SALT_EXCEED: '盐分超标',
  HUMIDITY_EXCEED: '湿度超标',
  DEVICE_OFFLINE: '设备离线',
}

const statusLabels: Record<AlarmStatus, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  ACKNOWLEDGED: '已确认',
  RESOLVED: '已解决',
  DISMISSED: '已忽略',
}

const statusFilterOptions = [
  { value: '', label: '全部状态' },
  { value: AlarmStatus.PENDING, label: '待处理' },
  { value: AlarmStatus.PROCESSING, label: '处理中' },
  { value: AlarmStatus.ACKNOWLEDGED, label: '已确认' },
  { value: AlarmStatus.RESOLVED, label: '已解决' },
  { value: AlarmStatus.DISMISSED, label: '已忽略' },
]

const levelFilterOptions = [
  { value: '', label: '全部级别' },
  { value: AlarmLevel.CRITICAL, label: '严重' },
  { value: AlarmLevel.ERROR, label: '错误' },
  { value: AlarmLevel.WARNING, label: '警告' },
  { value: AlarmLevel.INFO, label: '信息' },
]

function AlarmCenter(): JSX.Element {
  const { alarms: mockAlarms, loading } = useMockData()
  const [alarms, setAlarms] = useState<Alarm[]>(mockAlarms)
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [levelFilter, setLevelFilter] = useState<string>('')
  const [selectedAlarm, setSelectedAlarm] = useState<Alarm | null>(null)

  useMemo(() => {
    setAlarms(mockAlarms)
  }, [mockAlarms])

  const filteredAlarms = useMemo(() => {
    return alarms.filter((alarm) => {
      if (statusFilter && alarm.status !== statusFilter) return false
      if (levelFilter && alarm.level !== levelFilter) return false
      return true
    })
  }, [alarms, statusFilter, levelFilter])

  const stats = useMemo(() => {
    const pending = alarms.filter((a) => a.status === AlarmStatus.PENDING).length
    const processing = alarms.filter((a) => a.status === AlarmStatus.PROCESSING).length
    const resolved = alarms.filter((a) => a.status === AlarmStatus.RESOLVED).length
    return { pending, processing, resolved, total: alarms.length }
  }, [alarms])

  const handleStartProcessing = (alarmId: string) => {
    setAlarms((prev) =>
      prev.map((a) => (a.id === alarmId ? { ...a, status: AlarmStatus.PROCESSING } : a))
    )
    if (selectedAlarm?.id === alarmId) {
      setSelectedAlarm((prev) => (prev ? { ...prev, status: AlarmStatus.PROCESSING } : null))
    }
  }

  const handleResolve = (alarmId: string) => {
    setAlarms((prev) =>
      prev.map((a) => (a.id === alarmId ? { ...a, status: AlarmStatus.RESOLVED } : a))
    )
    if (selectedAlarm?.id === alarmId) {
      setSelectedAlarm((prev) => (prev ? { ...prev, status: AlarmStatus.RESOLVED } : null))
    }
  }

  const handleDismiss = (alarmId: string) => {
    setAlarms((prev) =>
      prev.map((a) => (a.id === alarmId ? { ...a, status: AlarmStatus.DISMISSED } : a))
    )
    if (selectedAlarm?.id === alarmId) {
      setSelectedAlarm((prev) => (prev ? { ...prev, status: AlarmStatus.DISMISSED } : null))
    }
  }

  const getStatusBadgeClass = (status: AlarmStatus) => {
    switch (status) {
      case AlarmStatus.PENDING:
        return 'bg-alert-red/10 text-alert-red'
      case AlarmStatus.PROCESSING:
        return 'bg-warning-yellow/10 text-warning-yellow'
      case AlarmStatus.ACKNOWLEDGED:
        return 'bg-blue-500/10 text-blue-400'
      case AlarmStatus.RESOLVED:
        return 'bg-normal-green/10 text-normal-green'
      case AlarmStatus.DISMISSED:
        return 'bg-gray-600/10 text-gray-400'
      default:
        return 'bg-gray-600/10 text-gray-400'
    }
  }

  if (loading) {
    return <Loading centered text="加载告警数据..." />
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white font-serif-sc">告警中心</h2>
          <p className="text-gray-400 mt-1">查看和处理系统告警信息</p>
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="glass-card p-4">
          <p className="text-gray-400 text-sm mb-1">待处理</p>
          <p className="text-3xl font-bold text-alert-red">{stats.pending}</p>
        </div>
        <div className="glass-card p-4">
          <p className="text-gray-400 text-sm mb-1">处理中</p>
          <p className="text-3xl font-bold text-warning-yellow">{stats.processing}</p>
        </div>
        <div className="glass-card p-4">
          <p className="text-gray-400 text-sm mb-1">已解决</p>
          <p className="text-3xl font-bold text-normal-green">{stats.resolved}</p>
        </div>
        <div className="glass-card p-4">
          <p className="text-gray-400 text-sm mb-1">总数</p>
          <p className="text-3xl font-bold text-gray-400">{stats.total}</p>
        </div>
      </div>

      <div className="glass-card p-6">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <Filter size={18} className="text-gray-400" />
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="input-field w-40"
              >
                {statusFilterOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <select
              value={levelFilter}
              onChange={(e) => setLevelFilter(e.target.value)}
              className="input-field w-40"
            >
              {levelFilterOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-700">
                <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">级别</th>
                <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">类型</th>
                <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">告警信息</th>
                <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">设备</th>
                <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">状态</th>
                <th className="text-left py-3 px-4 text-gray-400 font-medium text-sm">时间</th>
                <th className="text-right py-3 px-4 text-gray-400 font-medium text-sm">操作</th>
              </tr>
            </thead>
            <tbody>
              {filteredAlarms.length > 0 ? (
                filteredAlarms.map((alarm) => (
                  <tr
                    key={alarm.id}
                    className="border-b border-gray-800 hover:bg-dark-300/50 transition-colors cursor-pointer"
                    onClick={() => setSelectedAlarm(alarm)}
                  >
                    <td className="py-4 px-4">
                      <AlarmBadge level={alarm.level} size="sm" />
                    </td>
                    <td className="py-4 px-4 text-gray-300">
                      {alarmTypeLabels[alarm.type] ?? alarm.type}
                    </td>
                    <td className="py-4 px-4">
                      <p className="text-white max-w-xs truncate">{alarm.message}</p>
                    </td>
                    <td className="py-4 px-4 text-gray-400 font-mono text-sm">
                      {alarm.deviceId}
                    </td>
                    <td className="py-4 px-4">
                      <span
                        className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusBadgeClass(alarm.status)}`}
                      >
                        {statusLabels[alarm.status]}
                      </span>
                    </td>
                    <td className="py-4 px-4 text-gray-400 text-sm">
                      {new Date(alarm.timestamp).toLocaleString()}
                    </td>
                    <td className="py-4 px-4">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={(e) => {
                            e.stopPropagation()
                            setSelectedAlarm(alarm)
                          }}
                          className="p-2 text-gray-400 hover:text-white hover:bg-dark-300 rounded transition-colors"
                          title="查看详情"
                        >
                          <Eye size={16} />
                        </button>
                        {alarm.status === AlarmStatus.PENDING && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              handleStartProcessing(alarm.id)
                            }}
                            className="p-2 text-warning-yellow hover:text-warning-yellow hover:bg-warning-yellow/10 rounded transition-colors"
                            title="开始处理"
                          >
                            <Play size={16} />
                          </button>
                        )}
                        {alarm.status === AlarmStatus.PROCESSING && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              handleResolve(alarm.id)
                            }}
                            className="p-2 text-normal-green hover:text-normal-green hover:bg-normal-green/10 rounded transition-colors"
                            title="标记已解决"
                          >
                            <Check size={16} />
                          </button>
                        )}
                        {(alarm.status === AlarmStatus.PENDING || alarm.status === AlarmStatus.PROCESSING) && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              handleDismiss(alarm.id)
                            }}
                            className="p-2 text-gray-400 hover:text-gray-300 hover:bg-gray-600/10 rounded transition-colors"
                            title="忽略告警"
                          >
                            <XCircle size={16} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-12 text-center">
                    <AlertOctagon size={48} className="mx-auto mb-3 text-gray-600" />
                    <p className="text-gray-500">暂无告警数据</p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between mt-6 pt-4 border-t border-gray-700">
          <p className="text-gray-400 text-sm">
            显示 {filteredAlarms.length} 条记录，共 {alarms.length} 条
          </p>
        </div>
      </div>

      {selectedAlarm && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="glass-card p-6 w-full max-w-2xl mx-4">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-xl font-bold text-white font-serif-sc">告警详情</h3>
              <button
                onClick={() => setSelectedAlarm(null)}
                className="p-2 text-gray-400 hover:text-white hover:bg-dark-300 rounded transition-colors"
              >
                <XCircle size={20} />
              </button>
            </div>
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <AlarmBadge level={selectedAlarm.level} size="lg" />
                <span className="text-white text-lg font-bold">
                  {alarmTypeLabels[selectedAlarm.type] ?? selectedAlarm.type}
                </span>
              </div>
              <p className="text-gray-300">{selectedAlarm.message}</p>
              <div className="grid grid-cols-2 gap-4 pt-4 border-t border-gray-700">
                <div>
                  <p className="text-gray-500 text-sm mb-1">设备ID</p>
                  <p className="text-white font-mono">{selectedAlarm.deviceId}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-sm mb-1">墓室ID</p>
                  <p className="text-white font-mono">{selectedAlarm.chamberId}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-sm mb-1">告警时间</p>
                  <p className="text-white">{new Date(selectedAlarm.timestamp).toLocaleString()}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-sm mb-1">当前状态</p>
                  <span
                    className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusBadgeClass(selectedAlarm.status)}`}
                  >
                    {statusLabels[selectedAlarm.status]}
                  </span>
                </div>
                <div>
                  <p className="text-gray-500 text-sm mb-1">当前值</p>
                  <p className="text-white">{selectedAlarm.value ?? '-'}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-sm mb-1">阈值</p>
                  <p className="text-white">{selectedAlarm.threshold ?? '-'}</p>
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-gray-700">
              <button
                onClick={() => setSelectedAlarm(null)}
                className="px-4 py-2 btn-secondary"
              >
                关闭
              </button>
              {selectedAlarm.status === AlarmStatus.PENDING && (
                <button
                  onClick={() => {
                    handleStartProcessing(selectedAlarm.id)
                  }}
                  className="px-4 py-2 btn-primary"
                >
                  开始处理
                </button>
              )}
              {selectedAlarm.status === AlarmStatus.PROCESSING && (
                <button
                  onClick={() => {
                    handleResolve(selectedAlarm.id)
                  }}
                  className="px-4 py-2 btn-primary"
                >
                  标记已解决
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default AlarmCenter
