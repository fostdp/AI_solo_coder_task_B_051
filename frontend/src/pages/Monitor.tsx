import { Suspense, useState, useMemo } from 'react'
import { Eye, Layers, Wind } from 'lucide-react'
import TombScene3D from '@/components/TombScene3D'
import SaltIonChart from '@/components/SaltIonChart'
import EnvChart from '@/components/EnvChart'
import { useMockData } from '@/hooks/useMockData'
import type { Device } from '@/types'

function Monitor(): JSX.Element {
  const { tombs, chambers, devices, saltData, envData, analysisResults, loading } = useMockData()
  const [selectedChamberId, setSelectedChamberId] = useState<string>('C003')
  const [selectedTombId, setSelectedTombId] = useState<string>('T001')
  const [showSaltDamage, setShowSaltDamage] = useState(true)
  const [showArrows, setShowArrows] = useState(true)
  const [showSensors, setShowSensors] = useState(true)
  const [showWireframe] = useState(false)
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null)

  const filteredChambers = useMemo(() => chambers.filter(c => c.tombId === selectedTombId), [chambers, selectedTombId])
  const selectedChamber = useMemo(() => chambers.find(c => c.id === selectedChamberId), [chambers, selectedChamberId])
  const chamberDevices = useMemo(() => devices.filter(d => d.chamberId === selectedChamberId), [devices, selectedChamberId])
  const chamberSaltData = useMemo(() => saltData.filter(d => d.chamberId === selectedChamberId), [saltData, selectedChamberId])
  const chamberEnvData = useMemo(() => envData.filter(d => d.chamberId === selectedChamberId), [envData, selectedChamberId])
  const chamberAnalysis = useMemo(() => analysisResults.filter(a => chamberDevices.some(d => d.id === a.deviceId)), [analysisResults, chamberDevices])

  const latestSaltReadings = useMemo(() => {
    if (!chamberSaltData.length) return []
    const maxTs = Math.max(...chamberSaltData.map(d => d.timestamp))
    return chamberSaltData.filter(d => d.timestamp === maxTs)
  }, [chamberSaltData])

  const latestEnvReadings = useMemo(() => {
    if (!chamberEnvData.length) return []
    const maxTs = Math.max(...chamberEnvData.map(d => d.timestamp))
    return chamberEnvData.filter(d => d.timestamp === maxTs)
  }, [chamberEnvData])

  const chartSaltData = useMemo(() => {
    const firstDevice = chamberSaltData[0]?.deviceId
    if (!firstDevice) return []
    return chamberSaltData.filter(d => d.deviceId === firstDevice).slice(-24)
  }, [chamberSaltData])

  const chartEnvData = useMemo(() => {
    const firstDevice = chamberEnvData[0]?.deviceId
    if (!firstDevice) return []
    return chamberEnvData.filter(d => d.deviceId === firstDevice).slice(-24)
  }, [chamberEnvData])

  const avgSalt = useMemo(() => {
    if (!latestSaltReadings.length) return 0
    return Number((latestSaltReadings.reduce((s, d) => s + d.totalSalt, 0) / latestSaltReadings.length).toFixed(2))
  }, [latestSaltReadings])

  const avgTemp = useMemo(() => {
    if (!latestEnvReadings.length) return 0
    return Number((latestEnvReadings.reduce((s, d) => s + d.temperature, 0) / latestEnvReadings.length).toFixed(1))
  }, [latestEnvReadings])

  const avgHumidity = useMemo(() => {
    if (!latestEnvReadings.length) return 0
    return Number((latestEnvReadings.reduce((s, d) => s + d.humidity, 0) / latestEnvReadings.length).toFixed(1))
  }, [latestEnvReadings])

  const avgWind = useMemo(() => {
    if (!latestEnvReadings.length) return 0
    return Number((latestEnvReadings.reduce((s, d) => s + d.windSpeed, 0) / latestEnvReadings.length).toFixed(2))
  }, [latestEnvReadings])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="w-16 h-16 mx-auto mb-4 rounded-full border-4 border-bronze-green/30 border-t-bronze-green animate-spin" />
          <p className="text-gray-400">加载监测数据...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white font-serif-sc">实时监测</h2>
          <p className="text-gray-400 mt-1">实时查看各墓室的环境和盐分数据</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={selectedTombId}
            onChange={(e) => {
              setSelectedTombId(e.target.value)
              const firstChamber = chambers.find(c => c.tombId === e.target.value)
              if (firstChamber) setSelectedChamberId(firstChamber.id)
            }}
            className="input-field w-44"
          >
            {tombs.map(t => (
              <option key={t.id} value={t.id}>{t.name}</option>
            ))}
          </select>
          <select
            value={selectedChamberId}
            onChange={(e) => setSelectedChamberId(e.target.value)}
            className="input-field w-36"
          >
            {filteredChambers.map(c => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
        {[
          { label: '平均盐分', value: avgSalt, unit: 'mg/cm²', status: avgSalt > 5.0 ? 'critical' : avgSalt > 3.0 ? 'warning' : 'normal' },
          { label: '平均温度', value: avgTemp, unit: '°C', status: 'normal' },
          { label: '平均湿度', value: avgHumidity, unit: '%', status: avgHumidity > 75 ? 'warning' : 'normal' },
          { label: '平均风速', value: avgWind, unit: 'm/s', status: 'normal' },
          { label: '盐离子传感器', value: chamberDevices.filter(d => d.type === 'SALT').length, unit: '台', status: 'normal' },
          { label: '微环境传感器', value: chamberDevices.filter(d => d.type === 'ENV').length, unit: '台', status: 'normal' },
        ].map((item) => (
          <div key={item.label} className="glass-card p-4">
            <p className="text-gray-400 text-sm mb-2">{item.label}</p>
            <p className="text-2xl font-bold text-white">
              {item.value}
              <span className="text-sm text-gray-400 ml-1">{item.unit}</span>
            </p>
            <div
              className={`mt-2 h-1 rounded-full ${
                item.status === 'critical' ? 'bg-alert-red' : item.status === 'warning' ? 'bg-warning-yellow' : 'bg-normal-green'
              }`}
            />
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 glass-card p-4">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-white font-serif-sc">3D 墓室监测视图</h3>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setShowSaltDamage(!showSaltDamage)}
                className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  showSaltDamage ? 'bg-alert-red/20 text-alert-red' : 'bg-dark-300 text-gray-400'
                }`}
              >
                <Layers size={14} />
                盐害
              </button>
              <button
                onClick={() => setShowArrows(!showArrows)}
                className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  showArrows ? 'bg-bronze-green/20 text-bronze-green' : 'bg-dark-300 text-gray-400'
                }`}
              >
                <Wind size={14} />
                运移
              </button>
              <button
                onClick={() => setShowSensors(!showSensors)}
                className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  showSensors ? 'bg-blue-500/20 text-blue-400' : 'bg-dark-300 text-gray-400'
                }`}
              >
                <Eye size={14} />
                传感器
              </button>
            </div>
          </div>
          <div className="w-full h-[500px] bg-dark-300 rounded-lg overflow-hidden">
            {selectedChamber && (
              <Suspense fallback={<div className="w-full h-full flex items-center justify-center text-gray-400">加载3D场景...</div>}>
                <TombScene3D
                  chamber={selectedChamber}
                  devices={chamberDevices}
                  saltData={chamberSaltData}
                  envData={chamberEnvData}
                  analysisResults={chamberAnalysis}
                  onSensorClick={(device) => setSelectedDevice(device)}
                  showSaltDamage={showSaltDamage}
                  showArrows={showArrows}
                  showSensors={showSensors}
                  showWireframe={showWireframe}
                />
              </Suspense>
            )}
          </div>
        </div>

        <div className="glass-card p-6">
          <h3 className="text-lg font-bold text-white mb-4 font-serif-sc">传感器实时数据</h3>
          <div className="space-y-3 max-h-[500px] overflow-auto">
            {latestSaltReadings.slice(0, 10).map((data, index) => {
              const device = devices.find(d => d.id === data.deviceId)
              const isExceed = data.totalSalt > 5.0
              return (
                <div
                  key={`${data.deviceId}-${index}`}
                  className={`p-3 bg-dark-300/50 rounded-lg border-l-2 ${
                    isExceed ? 'border-alert-red' : 'border-bronze-green'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-xs text-bronze-green font-mono">{device?.code || data.deviceId}</span>
                    <span className="text-xs text-gray-500">
                      {new Date(data.timestamp).toLocaleTimeString()}
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-2 mt-2">
                    <div className="text-xs">
                      <span className="text-gray-500">Na⁺ </span>
                      <span className="text-blue-400">{data.naPlus.toFixed(2)}</span>
                    </div>
                    <div className="text-xs">
                      <span className="text-gray-500">Ca²⁺ </span>
                      <span className="text-green-400">{data.ca2Plus.toFixed(2)}</span>
                    </div>
                    <div className="text-xs">
                      <span className="text-gray-500">SO₄²⁻ </span>
                      <span className="text-yellow-400">{data.so42Minus.toFixed(2)}</span>
                    </div>
                    <div className="text-xs">
                      <span className="text-gray-500">Cl⁻ </span>
                      <span className="text-pink-400">{data.clMinus.toFixed(2)}</span>
                    </div>
                  </div>
                  <div className="mt-2 flex items-center justify-between">
                    <span className="text-xs text-gray-500">总量</span>
                    <span className={`text-sm font-bold ${isExceed ? 'text-alert-red' : 'text-white'}`}>
                      {data.totalSalt.toFixed(2)} mg/cm²
                    </span>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="glass-card p-6">
          <h3 className="text-lg font-bold text-white mb-4 font-serif-sc">盐离子浓度趋势</h3>
          {chartSaltData.length > 0 ? (
            <SaltIonChart data={chartSaltData} height={300} />
          ) : (
            <div className="h-[300px] flex items-center justify-center text-gray-500">暂无数据</div>
          )}
        </div>
        <div className="glass-card p-6">
          <h3 className="text-lg font-bold text-white mb-4 font-serif-sc">微环境数据趋势</h3>
          {chartEnvData.length > 0 ? (
            <EnvChart data={chartEnvData} height={300} />
          ) : (
            <div className="h-[300px] flex items-center justify-center text-gray-500">暂无数据</div>
          )}
        </div>
      </div>

      {selectedDevice && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="glass-card p-6 w-full max-w-lg mx-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-xl font-bold text-white font-serif-sc">传感器详情</h3>
              <button
                onClick={() => setSelectedDevice(null)}
                className="p-2 text-gray-400 hover:text-white hover:bg-dark-300 rounded transition-colors"
              >
                ✕
              </button>
            </div>
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-gray-500 text-sm">设备编码</p>
                  <p className="text-white font-mono">{selectedDevice.code}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-sm">设备类型</p>
                  <p className="text-white">{selectedDevice.type === 'SALT' ? '盐离子传感器' : '微环境传感器'}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-sm">运行状态</p>
                  <span className={`inline-flex items-center gap-1.5 text-sm ${
                    selectedDevice.status === 'ONLINE' ? 'text-normal-green' : 'text-alert-red'
                  }`}>
                    <span className={`w-2 h-2 rounded-full ${selectedDevice.status === 'ONLINE' ? 'bg-normal-green' : 'bg-alert-red'}`} />
                    {selectedDevice.status === 'ONLINE' ? '在线' : '离线'}
                  </span>
                </div>
                <div>
                  <p className="text-gray-500 text-sm">安装位置</p>
                  <p className="text-white text-sm">
                    X:{selectedDevice.position.x.toFixed(1)} Y:{selectedDevice.position.y.toFixed(1)} Z:{selectedDevice.position.z.toFixed(1)}
                  </p>
                </div>
              </div>
              {selectedDevice.type === 'SALT' && (() => {
                const latest = saltData.filter(d => d.deviceId === selectedDevice.id)
                  .sort((a, b) => b.timestamp - a.timestamp)[0]
                if (!latest) return null
                return (
                  <div className="pt-3 border-t border-gray-700">
                    <p className="text-gray-500 text-sm mb-2">最新盐分数据</p>
                    <div className="grid grid-cols-2 gap-2">
                      <div className="p-2 bg-dark-300/50 rounded">
                        <p className="text-xs text-gray-500">Na⁺</p>
                        <p className="text-blue-400 font-bold">{latest.naPlus.toFixed(3)}</p>
                      </div>
                      <div className="p-2 bg-dark-300/50 rounded">
                        <p className="text-xs text-gray-500">Ca²⁺</p>
                        <p className="text-green-400 font-bold">{latest.ca2Plus.toFixed(3)}</p>
                      </div>
                      <div className="p-2 bg-dark-300/50 rounded">
                        <p className="text-xs text-gray-500">SO₄²⁻</p>
                        <p className="text-yellow-400 font-bold">{latest.so42Minus.toFixed(3)}</p>
                      </div>
                      <div className="p-2 bg-dark-300/50 rounded">
                        <p className="text-xs text-gray-500">Cl⁻</p>
                        <p className="text-pink-400 font-bold">{latest.clMinus.toFixed(3)}</p>
                      </div>
                    </div>
                    <div className="mt-2 flex items-center justify-between p-2 bg-dark-300/50 rounded">
                      <span className="text-gray-400 text-sm">总量</span>
                      <span className={`font-bold ${latest.totalSalt > 5.0 ? 'text-alert-red' : 'text-white'}`}>
                        {latest.totalSalt.toFixed(3)} mg/cm²
                      </span>
                    </div>
                  </div>
                )
              })()}
              {selectedDevice.type === 'ENV' && (() => {
                const latest = envData.filter(d => d.deviceId === selectedDevice.id)
                  .sort((a, b) => b.timestamp - a.timestamp)[0]
                if (!latest) return null
                return (
                  <div className="pt-3 border-t border-gray-700">
                    <p className="text-gray-500 text-sm mb-2">最新环境数据</p>
                    <div className="grid grid-cols-3 gap-2">
                      <div className="p-2 bg-dark-300/50 rounded">
                        <p className="text-xs text-gray-500">温度</p>
                        <p className="text-yellow-400 font-bold">{latest.temperature.toFixed(1)}°C</p>
                      </div>
                      <div className="p-2 bg-dark-300/50 rounded">
                        <p className="text-xs text-gray-500">湿度</p>
                        <p className={`font-bold ${latest.humidity > 75 ? 'text-alert-red' : 'text-blue-400'}`}>{latest.humidity.toFixed(1)}%</p>
                      </div>
                      <div className="p-2 bg-dark-300/50 rounded">
                        <p className="text-xs text-gray-500">风速</p>
                        <p className="text-green-400 font-bold">{latest.windSpeed.toFixed(2)}</p>
                      </div>
                    </div>
                  </div>
                )
              })()}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Monitor
