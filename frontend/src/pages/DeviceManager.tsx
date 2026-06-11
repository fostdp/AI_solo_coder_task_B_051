import { useState, useMemo } from 'react'
import { Search, Settings, Wifi, WifiOff, X, Thermometer, Droplets, Beaker } from 'lucide-react'
import Loading from '@/components/Loading'
import { useMockData } from '@/hooks/useMockData'
import type { Device } from '@/types'

const statusDotColors: Record<string, string> = {
  ONLINE: 'bg-green-500',
  OFFLINE: 'bg-red-500',
  ERROR: 'bg-red-500',
  MAINTENANCE: 'bg-yellow-500',
}

const statusLabels: Record<string, string> = {
  ONLINE: '在线',
  OFFLINE: '离线',
  ERROR: '异常',
  MAINTENANCE: '维护中',
}

const statusTextColors: Record<string, string> = {
  ONLINE: 'text-green-400',
  OFFLINE: 'text-red-400',
  ERROR: 'text-red-400',
  MAINTENANCE: 'text-yellow-400',
}

const typeLabels: Record<string, string> = {
  SALT: '盐离子传感器',
  ENV: '微环境传感器',
}

function DeviceManager(): JSX.Element {
  const { devices, chambers, tombs, saltData, envData, loading } = useMockData()
  const [searchQuery, setSearchQuery] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [typeFilter, setTypeFilter] = useState<string>('')
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null)

  const onlineCount = useMemo(
    () => devices.filter(d => d.status === 'ONLINE').length,
    [devices]
  )

  const offlineErrorCount = useMemo(
    () => devices.filter(d => d.status === 'OFFLINE' || d.status === 'ERROR').length,
    [devices]
  )

  const chamberMap = useMemo(() => {
    const map: Record<string, string> = {}
    chambers.forEach(c => { map[c.id] = c.name })
    return map
  }, [chambers])

  const tombMap = useMemo(() => {
    const map: Record<string, string> = {}
    tombs.forEach(t => { map[t.id] = t.name })
    return map
  }, [tombs])

  const latestSaltMap = useMemo(() => {
    const map: Record<string, number> = {}
    const sorted = [...saltData].sort((a, b) => b.timestamp - a.timestamp)
    for (const d of sorted) {
      if (!(d.deviceId in map)) {
        map[d.deviceId] = d.totalSalt
      }
    }
    return map
  }, [saltData])

  const latestEnvMap = useMemo(() => {
    const map: Record<string, { temperature: number; humidity: number }> = {}
    const sorted = [...envData].sort((a, b) => b.timestamp - a.timestamp)
    for (const d of sorted) {
      if (!(d.deviceId in map)) {
        map[d.deviceId] = { temperature: d.temperature, humidity: d.humidity }
      }
    }
    return map
  }, [envData])

  const filteredDevices = useMemo(() => {
    return devices.filter(device => {
      const matchesSearch =
        device.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        device.code.toLowerCase().includes(searchQuery.toLowerCase()) ||
        device.id.toLowerCase().includes(searchQuery.toLowerCase())
      const matchesStatus = !statusFilter || device.status === statusFilter
      const matchesType = !typeFilter || device.type === typeFilter
      return matchesSearch && matchesStatus && matchesType
    })
  }, [devices, searchQuery, statusFilter, typeFilter])

  if (loading) {
    return <Loading centered text="加载设备列表..." />
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white font-serif-sc">设备管理</h2>
          <p className="text-gray-400 mt-1">管理监测设备和传感器</p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="glass-card p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-gray-400 text-sm">设备总数</p>
              <p className="text-3xl font-bold text-white mt-1">{devices.length}</p>
            </div>
            <div className="w-12 h-12 rounded-xl bg-bronze-green/10 flex items-center justify-center">
              <Settings size={24} className="text-bronze-green" />
            </div>
          </div>
        </div>
        <div className="glass-card p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-gray-400 text-sm">在线设备</p>
              <p className="text-3xl font-bold text-green-400 mt-1">{onlineCount}</p>
            </div>
            <div className="w-12 h-12 rounded-xl bg-green-500/10 flex items-center justify-center">
              <Wifi size={24} className="text-green-400" />
            </div>
          </div>
        </div>
        <div className="glass-card p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-gray-400 text-sm">离线/异常</p>
              <p className="text-3xl font-bold text-red-400 mt-1">{offlineErrorCount}</p>
            </div>
            <div className="w-12 h-12 rounded-xl bg-red-500/10 flex items-center justify-center">
              <WifiOff size={24} className="text-red-400" />
            </div>
          </div>
        </div>
      </div>

      <div className="glass-card p-6">
        <div className="flex items-center gap-4 mb-6 flex-wrap">
          <div className="relative flex-1 min-w-[200px] max-w-md">
            <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder="搜索设备名称或编码..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="input-field pl-10"
            />
          </div>
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="input-field w-40"
          >
            <option value="">全部类型</option>
            <option value="SALT">盐离子传感器</option>
            <option value="ENV">微环境传感器</option>
          </select>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="input-field w-40"
          >
            <option value="">全部状态</option>
            <option value="ONLINE">在线</option>
            <option value="OFFLINE">离线</option>
            <option value="ERROR">异常</option>
            <option value="MAINTENANCE">维护中</option>
          </select>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredDevices.length > 0 ? (
            filteredDevices.map((device) => (
              <div
                key={device.id}
                className="glass-card p-5 hover:border-bronze-green/50 transition-colors cursor-pointer"
                onClick={() => setSelectedDevice(device)}
              >
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-3 h-3 rounded-full ${statusDotColors[device.status]} ${
                        device.status === 'ONLINE' ? 'animate-pulse' : ''
                      }`}
                    />
                    <div>
                      <h4 className="text-white font-bold">{device.name}</h4>
                      <p className="text-gray-500 text-xs font-mono">{device.code}</p>
                    </div>
                  </div>
                  <span
                    className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                      device.type === 'SALT'
                        ? 'bg-blue-500/10 text-blue-400'
                        : 'bg-purple-500/10 text-purple-400'
                    }`}
                  >
                    {typeLabels[device.type]}
                  </span>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500 text-sm">状态</span>
                    <span className={`text-sm font-medium ${statusTextColors[device.status]}`}>
                      {statusLabels[device.status]}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500 text-sm">所属墓室</span>
                    <span className="text-white text-sm">{chamberMap[device.chamberId] || device.chamberId}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500 text-sm">所属墓葬</span>
                    <span className="text-white text-sm">{tombMap[device.tombId] || device.tombId}</span>
                  </div>

                  {device.type === 'SALT' && latestSaltMap[device.id] !== undefined && (
                    <div className="flex items-center justify-between pt-2 border-t border-gray-700/50">
                      <span className="text-gray-500 text-sm flex items-center gap-1">
                        <Beaker size={14} /> 盐分总量
                      </span>
                      <span className={`text-sm font-mono font-medium ${
                        latestSaltMap[device.id] > 5.0 ? 'text-red-400' : latestSaltMap[device.id] > 3.0 ? 'text-yellow-400' : 'text-green-400'
                      }`}>
                        {latestSaltMap[device.id].toFixed(2)} mg/cm²
                      </span>
                    </div>
                  )}

                  {device.type === 'ENV' && latestEnvMap[device.id] && (
                    <div className="pt-2 border-t border-gray-700/50 space-y-1">
                      <div className="flex items-center justify-between">
                        <span className="text-gray-500 text-sm flex items-center gap-1">
                          <Thermometer size={14} /> 温度
                        </span>
                        <span className="text-sm font-mono text-blue-400">
                          {latestEnvMap[device.id].temperature.toFixed(1)}°C
                        </span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-gray-500 text-sm flex items-center gap-1">
                          <Droplets size={14} /> 湿度
                        </span>
                        <span className={`text-sm font-mono ${
                          latestEnvMap[device.id].humidity > 75 ? 'text-yellow-400' : 'text-blue-400'
                        }`}>
                          {latestEnvMap[device.id].humidity.toFixed(1)}%
                        </span>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ))
          ) : (
            <div className="col-span-full py-12 text-center">
              <Settings size={48} className="mx-auto mb-3 text-gray-600" />
              <p className="text-gray-500">暂无设备数据</p>
            </div>
          )}
        </div>
      </div>

      {selectedDevice && (
        <div
          className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50"
          onClick={() => setSelectedDevice(null)}
        >
          <div
            className="glass-card p-6 w-full max-w-2xl mx-4 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-3">
                <div
                  className={`w-3 h-3 rounded-full ${statusDotColors[selectedDevice.status]} ${
                    selectedDevice.status === 'ONLINE' ? 'animate-pulse' : ''
                  }`}
                />
                <h3 className="text-xl font-bold text-white font-serif-sc">{selectedDevice.name}</h3>
              </div>
              <button
                onClick={() => setSelectedDevice(null)}
                className="p-2 text-gray-400 hover:text-white hover:bg-dark-300 rounded transition-colors"
              >
                <X size={20} />
              </button>
            </div>

            <div className="grid grid-cols-2 gap-6">
              <div>
                <p className="text-gray-500 text-sm mb-1">设备编码</p>
                <p className="text-white font-mono">{selectedDevice.code}</p>
              </div>
              <div>
                <p className="text-gray-500 text-sm mb-1">设备类型</p>
                <p className="text-white">
                  <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                    selectedDevice.type === 'SALT'
                      ? 'bg-blue-500/10 text-blue-400'
                      : 'bg-purple-500/10 text-purple-400'
                  }`}>
                    {typeLabels[selectedDevice.type]}
                  </span>
                </p>
              </div>
              <div>
                <p className="text-gray-500 text-sm mb-1">运行状态</p>
                <span className={`inline-flex items-center gap-2 px-2 py-1 rounded-full text-sm ${
                  selectedDevice.status === 'ONLINE' ? 'bg-green-500/10 text-green-400'
                    : selectedDevice.status === 'ERROR' || selectedDevice.status === 'OFFLINE' ? 'bg-red-500/10 text-red-400'
                    : 'bg-yellow-500/10 text-yellow-400'
                }`}>
                  <span className={`w-2 h-2 rounded-full ${statusDotColors[selectedDevice.status]}`} />
                  {statusLabels[selectedDevice.status]}
                </span>
              </div>
              <div>
                <p className="text-gray-500 text-sm mb-1">设备型号</p>
                <p className="text-white">{selectedDevice.model}</p>
              </div>
              <div>
                <p className="text-gray-500 text-sm mb-1">所属墓葬</p>
                <p className="text-white">{tombMap[selectedDevice.tombId] || selectedDevice.tombId}</p>
              </div>
              <div>
                <p className="text-gray-500 text-sm mb-1">所属墓室</p>
                <p className="text-white">{chamberMap[selectedDevice.chamberId] || selectedDevice.chamberId}</p>
              </div>
              <div>
                <p className="text-gray-500 text-sm mb-1">安装位置</p>
                <p className="text-white font-mono text-sm">
                  X: {selectedDevice.position.x.toFixed(2)}, Y: {selectedDevice.position.y.toFixed(2)}, Z: {selectedDevice.position.z.toFixed(2)}
                </p>
              </div>
              <div>
                <p className="text-gray-500 text-sm mb-1">安装时间</p>
                <p className="text-white">{new Date(selectedDevice.installTime).toLocaleString()}</p>
              </div>
            </div>

            {selectedDevice.type === 'SALT' && latestSaltMap[selectedDevice.id] !== undefined && (
              <div className="mt-6 pt-4 border-t border-gray-700">
                <p className="text-gray-400 text-sm mb-3 flex items-center gap-2">
                  <Beaker size={16} /> 最新盐分数据
                </p>
                <div className="glass-card p-4">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-400">盐分总量</span>
                    <span className={`text-lg font-mono font-bold ${
                      latestSaltMap[selectedDevice.id] > 5.0 ? 'text-red-400' : latestSaltMap[selectedDevice.id] > 3.0 ? 'text-yellow-400' : 'text-green-400'
                    }`}>
                      {latestSaltMap[selectedDevice.id].toFixed(2)} mg/cm²
                    </span>
                  </div>
                  <div className="mt-2 flex gap-1">
                    <div className="h-1.5 rounded-full bg-green-500/30 flex-1" />
                    <div className="h-1.5 rounded-full bg-yellow-500/30 flex-1" />
                    <div className="h-1.5 rounded-full bg-red-500/30 flex-1" />
                  </div>
                  <div className="flex justify-between text-xs text-gray-500 mt-1">
                    <span>0</span>
                    <span>3.0</span>
                    <span>5.0</span>
                  </div>
                </div>
              </div>
            )}

            {selectedDevice.type === 'ENV' && latestEnvMap[selectedDevice.id] && (
              <div className="mt-6 pt-4 border-t border-gray-700">
                <p className="text-gray-400 text-sm mb-3 flex items-center gap-2">
                  <Thermometer size={16} /> 最新环境数据
                </p>
                <div className="grid grid-cols-2 gap-4">
                  <div className="glass-card p-4">
                    <div className="flex items-center gap-2 mb-2">
                      <Thermometer size={16} className="text-blue-400" />
                      <span className="text-gray-400 text-sm">温度</span>
                    </div>
                    <p className="text-2xl font-mono font-bold text-blue-400">
                      {latestEnvMap[selectedDevice.id].temperature.toFixed(1)}°C
                    </p>
                  </div>
                  <div className="glass-card p-4">
                    <div className="flex items-center gap-2 mb-2">
                      <Droplets size={16} className={
                        latestEnvMap[selectedDevice.id].humidity > 75 ? 'text-yellow-400' : 'text-blue-400'
                      } />
                      <span className="text-gray-400 text-sm">湿度</span>
                    </div>
                    <p className={`text-2xl font-mono font-bold ${
                      latestEnvMap[selectedDevice.id].humidity > 75 ? 'text-yellow-400' : 'text-blue-400'
                    }`}>
                      {latestEnvMap[selectedDevice.id].humidity.toFixed(1)}%
                    </p>
                  </div>
                </div>
              </div>
            )}

            <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-gray-700">
              <button
                onClick={() => setSelectedDevice(null)}
                className="px-4 py-2 btn-secondary"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default DeviceManager
