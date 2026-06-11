import { Suspense, useState, useMemo } from 'react'
import { Activity, AlertTriangle, HardDrive, Droplets } from 'lucide-react'
import StatCard from '@/components/StatCard'
import AlarmBadge from '@/components/AlarmBadge'
import SaltIonChart from '@/components/SaltIonChart'
import EnvChart from '@/components/EnvChart'
import TombScene3D from '@/components/TombScene3D'
import { useMockData } from '@/hooks/useMockData'
import { AlarmLevel } from '@/types'

function Dashboard(): JSX.Element {
  const { tombs, chambers, devices, saltData, envData, analysisResults, alarms, loading } = useMockData()
  const [selectedChamberId, setSelectedChamberId] = useState<string>('C003')

  const selectedChamber = useMemo(() => chambers.find(c => c.id === selectedChamberId), [chambers, selectedChamberId])
  const chamberDevices = useMemo(() => devices.filter(d => d.chamberId === selectedChamberId), [devices, selectedChamberId])
  const chamberSaltData = useMemo(() => saltData.filter(d => d.chamberId === selectedChamberId), [saltData, selectedChamberId])
  const chamberEnvData = useMemo(() => envData.filter(d => d.chamberId === selectedChamberId), [envData, selectedChamberId])
  const chamberAnalysis = useMemo(() => analysisResults.filter(a => chamberDevices.some(d => d.id === a.deviceId)), [analysisResults, chamberDevices])

  const onlineDevices = useMemo(() => devices.filter(d => d.status === 'ONLINE').length, [devices])
  const activeAlarms = useMemo(() => alarms.filter(a => a.status === 'PENDING' || a.status === 'PROCESSING').length, [alarms])
  const avgSalt = useMemo(() => {
    const latest = saltData.filter(d => d.timestamp === Math.max(...saltData.map(s => s.timestamp)))
    if (!latest.length) return 0
    return Number((latest.reduce((s, d) => s + d.totalSalt, 0) / latest.length).toFixed(2))
  }, [saltData])
  const avgHumidity = useMemo(() => {
    const latest = envData.filter(d => d.timestamp === Math.max(...envData.map(s => s.timestamp)))
    if (!latest.length) return 0
    return Number((latest.reduce((s, d) => s + d.humidity, 0) / latest.length).toFixed(1))
  }, [envData])

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

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="w-16 h-16 mx-auto mb-4 rounded-full border-4 border-bronze-green/30 border-t-bronze-green animate-spin" />
          <p className="text-gray-400">加载中...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title="在线设备"
          value={onlineDevices}
          suffix={`/ ${devices.length}`}
          icon={HardDrive}
          color="green"
          description="设备运行正常"
          trend="up"
          trendValue={`${Math.round(onlineDevices / devices.length * 100)}%`}
        />
        <StatCard
          title="活跃告警"
          value={activeAlarms}
          icon={AlertTriangle}
          color="red"
          description="需要及时处理"
        />
        <StatCard
          title="平均盐分浓度"
          value={avgSalt}
          suffix="mg/cm²"
          icon={Activity}
          color={avgSalt > 5.0 ? 'red' : avgSalt > 3.0 ? 'yellow' : 'ochre'}
          description={avgSalt > 5.0 ? '超过阈值' : '正常范围'}
        />
        <StatCard
          title="平均湿度"
          value={avgHumidity}
          suffix="%"
          icon={Droplets}
          color={avgHumidity > 75 ? 'red' : 'blue'}
          description={avgHumidity > 75 ? '超过阈值' : '正常范围'}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 glass-card p-4">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-white font-serif-sc">墓室3D视图</h3>
            <select
              value={selectedChamberId}
              onChange={(e) => setSelectedChamberId(e.target.value)}
              className="input-field w-48"
            >
              {chambers.map(c => (
                <option key={c.id} value={c.id}>{c.name} ({tombs.find(t => t.id === c.tombId)?.name})</option>
              ))}
            </select>
          </div>
          <div className="w-full h-[400px] bg-dark-300 rounded-lg overflow-hidden">
            {selectedChamber && (
              <Suspense fallback={<div className="w-full h-full flex items-center justify-center text-gray-400">加载3D场景...</div>}>
                <TombScene3D
                  chamber={selectedChamber}
                  devices={chamberDevices}
                  saltData={chamberSaltData}
                  envData={chamberEnvData}
                  analysisResults={chamberAnalysis}
                  showArrows={false}
                />
              </Suspense>
            )}
          </div>
        </div>

        <div className="glass-card p-6">
          <h3 className="text-lg font-bold text-white mb-4 font-serif-sc">最近告警</h3>
          <div className="space-y-3 max-h-[400px] overflow-auto">
            {alarms.length > 0 ? (
              alarms.map((alarm) => (
                <div
                  key={alarm.id}
                  className="flex items-center justify-between p-3 bg-dark-300/50 rounded-lg hover:bg-dark-300 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <AlarmBadge level={alarm.level as AlarmLevel} size="sm" />
                    <div>
                      <p className="text-white text-sm font-medium">
                        {alarm.type === 'SALT_EXCEED' ? '盐分超标' : alarm.type === 'HUMIDITY_EXCEED' ? '湿度超标' : '设备离线'}
                      </p>
                      <p className="text-gray-500 text-xs">{alarm.message.slice(0, 40)}...</p>
                    </div>
                  </div>
                  <span className="text-gray-500 text-xs">
                    {new Date(alarm.timestamp).toLocaleTimeString()}
                  </span>
                </div>
              ))
            ) : (
              <p className="text-gray-500 text-center py-8">暂无告警信息</p>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="glass-card p-6">
          <h3 className="text-lg font-bold text-white mb-4 font-serif-sc">盐离子浓度趋势</h3>
          {chartSaltData.length > 0 ? (
            <SaltIonChart data={chartSaltData} height={280} />
          ) : (
            <div className="h-[280px] flex items-center justify-center text-gray-500">暂无数据</div>
          )}
        </div>
        <div className="glass-card p-6">
          <h3 className="text-lg font-bold text-white mb-4 font-serif-sc">微环境数据趋势</h3>
          {chartEnvData.length > 0 ? (
            <EnvChart data={chartEnvData} height={280} />
          ) : (
            <div className="h-[280px] flex items-center justify-center text-gray-500">暂无数据</div>
          )}
        </div>
      </div>

      <div className="glass-card p-6">
        <h3 className="text-lg font-bold text-white mb-4 font-serif-sc">墓葬概览</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {tombs.map(tomb => (
            <div key={tomb.id} className="p-4 bg-dark-300/50 rounded-lg">
              <div className="flex items-center gap-3 mb-3">
                <div className="w-10 h-10 rounded-lg bg-ochre/10 flex items-center justify-center text-ochre text-lg font-bold font-serif-sc">
                  {tomb.name[0]}
                </div>
                <div>
                  <h4 className="text-white font-bold">{tomb.name}</h4>
                  <p className="text-gray-500 text-sm">{tomb.dynasty} · {tomb.address}</p>
                </div>
              </div>
              <div className="grid grid-cols-3 gap-3">
                {chambers.filter(c => c.tombId === tomb.id).map(chamber => {
                  const chamberDevCount = devices.filter(d => d.chamberId === chamber.id).length
                  const chamberOnline = devices.filter(d => d.chamberId === chamber.id && d.status === 'ONLINE').length
                  return (
                    <div key={chamber.id} className="p-2 bg-dark-200/50 rounded text-center">
                      <p className="text-white text-sm font-medium">{chamber.name}</p>
                      <p className="text-gray-500 text-xs">{chamberOnline}/{chamberDevCount} 在线</p>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default Dashboard
