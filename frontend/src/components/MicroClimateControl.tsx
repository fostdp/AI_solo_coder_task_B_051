import { useState, useEffect, useMemo } from 'react'
import ReactECharts from 'echarts-for-react'
import {
  Droplets,
  Wind,
  Thermometer,
  Zap,
  Settings,
  Play,
  Pause,
  RefreshCw,
  TrendingUp,
  TrendingDown,
  Minus,
  Clock,
  Calendar,
  BarChart3,
  Power,
  Target
} from 'lucide-react'
import type { EnvState, ControlRecord, EnergyStats } from '@/utils/microClimateApi'
import microClimateApi from '@/utils/microClimateApi'

interface MicroClimateControlProps {
  chamberId?: string
  useMockData?: boolean
}

const mockEnvState: EnvState = {
  currentRh: 72.5,
  rhTrend: -1.2,
  currentHour: 14,
  dehumidifierStatus: true,
  humidifierStatus: false,
  recommendedAction: 1,
  recommendedActionName: '开启除湿机',
  expectedReward: 0.85,
  targetRh: 55,
  controlMode: 'AUTO_DQN'
}

const mockEnergyStats: EnergyStats = {
  todayEnergy: 2.35,
  weekEnergy: 15.8,
  monthEnergy: 68.5
}

const generateMockHistory = (): ControlRecord[] => {
  const records: ControlRecord[] = []
  const now = Date.now()
  let rh = 65

  for (let i = 23; i >= 0; i--) {
    const timestamp = new Date(now - i * 3600000).toISOString()
    const change = (Math.random() - 0.5) * 5
    rh = Math.max(40, Math.min(90, rh + change))

    const dehumidifierOn = rh > 70
    const humidifierOn = rh < 45

    records.push({
      timestamp,
      currentRh: Number(rh.toFixed(1)),
      dehumidifierOn,
      humidifierOn,
      energyConsumption: dehumidifierOn ? 0.15 : humidifierOn ? 0.08 : 0.02,
      rewardScore: rh >= 40 && rh <= 65 ? 1 : rh > 65 && rh <= 80 ? 0.5 : 0.2,
      action: dehumidifierOn ? 1 : humidifierOn ? 2 : 0
    })
  }

  return records
}

const modeLabels: Record<string, string> = {
  MANUAL: '手动模式',
  AUTO_DQN: '自动DQN',
  SCHEDULE: '定时模式'
}

export default function MicroClimateControl({
  chamberId = 'chamber-001',
  useMockData = true
}: MicroClimateControlProps) {
  const [envState, setEnvState] = useState<EnvState>(mockEnvState)
  const [energyStats, setEnergyStats] = useState<EnergyStats>(mockEnergyStats)
  const [historyData, setHistoryData] = useState<ControlRecord[]>(generateMockHistory())
  const [targetRhInput, setTargetRhInput] = useState(mockEnvState.targetRh || 55)
  const [isTraining, setIsTraining] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)

  useEffect(() => {
    if (!useMockData) {
      fetchData()
      const interval = setInterval(fetchData, 30000)
      return () => clearInterval(interval)
    }
  }, [chamberId, useMockData])

  const fetchData = async () => {
    try {
      const [envRes, energyRes, historyRes] = await Promise.all([
        microClimateApi.fetchEnvState(chamberId),
        microClimateApi.fetchEnergyStats(chamberId),
        microClimateApi.fetchControlHistory({ chamberId, pageSize: 24 })
      ])
      if (envRes.code === 200) setEnvState(envRes.data)
      if (energyRes.code === 200) setEnergyStats(energyRes.data)
      if (historyRes.code === 200) setHistoryData(historyRes.data.list)
    } catch (error) {
      console.error('获取微环境数据失败:', error)
    }
  }

  const handleRefresh = async () => {
    setIsRefreshing(true)
    if (useMockData) {
      setEnvState({
        ...mockEnvState,
        currentRh: Number((60 + Math.random() * 20).toFixed(1)),
        rhTrend: Number(((Math.random() - 0.5) * 3).toFixed(1))
      })
      setHistoryData(generateMockHistory())
    } else {
      await fetchData()
    }
    setTimeout(() => setIsRefreshing(false), 500)
  }

  const handleControlToggle = async (type: 'dehumidifier' | 'humidifier', value: boolean) => {
    if (useMockData) {
      setEnvState(prev => ({
        ...prev,
        dehumidifierStatus: type === 'dehumidifier' ? value : prev.dehumidifierStatus,
        humidifierStatus: type === 'humidifier' ? value : prev.humidifierStatus,
        controlMode: 'MANUAL'
      }))
    } else {
      try {
        const res = await microClimateApi.executeControl({
          chamberId,
          [type === 'dehumidifier' ? 'dehumidifierOn' : 'humidifierOn']: value,
          controlMode: 'MANUAL'
        })
        if (res.code === 200) setEnvState(res.data)
      } catch (error) {
        console.error('控制失败:', error)
      }
    }
  }

  const handleTargetRhChange = async () => {
    if (useMockData) {
      setEnvState(prev => ({ ...prev, targetRh: targetRhInput, controlMode: 'MANUAL' }))
    } else {
      try {
        const res = await microClimateApi.executeControl({
          chamberId,
          targetRh: targetRhInput,
          controlMode: 'MANUAL'
        })
        if (res.code === 200) setEnvState(res.data)
      } catch (error) {
        console.error('设置目标RH失败:', error)
      }
    }
  }

  const handleModeChange = async (mode: 'MANUAL' | 'AUTO_DQN' | 'SCHEDULE') => {
    if (useMockData) {
      setEnvState(prev => ({ ...prev, controlMode: mode }))
    } else {
      try {
        const res = await microClimateApi.executeControl({ chamberId, controlMode: mode })
        if (res.code === 200) setEnvState(res.data)
      } catch (error) {
        console.error('切换模式失败:', error)
      }
    }
  }

  const handleTrainModel = async () => {
    if (useMockData) {
      setIsTraining(true)
      setTimeout(() => setIsTraining(false), 3000)
    } else {
      setIsTraining(true)
      try {
        await microClimateApi.trainDqnModel(chamberId, 100)
      } catch (error) {
        console.error('训练失败:', error)
      } finally {
        setIsTraining(false)
      }
    }
  }

  const chartOption = useMemo(() => {
    const times = historyData.map(d =>
      new Date(d.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    )

    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 15, 26, 0.9)',
        borderColor: '#4A7C59',
        textStyle: { color: '#fff' },
        axisPointer: { type: 'cross', label: { backgroundColor: '#4A7C59' } },
        formatter: (params: any[]) => {
          let result = `${params[0].axisValue}<br/>`
          params.forEach((p: any) => {
            if (p.seriesType === 'line') {
              result += `${p.marker} ${p.seriesName}: ${p.value}%<br/>`
            }
          })
          const idx = params[0].dataIndex
          const record = historyData[idx]
          if (record) {
            result += `<br/>设备状态:<br/>`
            result += `除湿机: ${record.dehumidifierOn ? '运行中' : '待机'}<br/>`
            result += `加湿器: ${record.humidifierOn ? '运行中' : '待机'}<br/>`
            result += `能耗: ${record.energyConsumption.toFixed(2)} kWh`
          }
          return result
        }
      },
      legend: {
        data: ['相对湿度', '目标RH'],
        textStyle: { color: '#9CA3AF' },
        top: 0
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        top: '12%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: times,
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: { color: '#9CA3AF', fontSize: 10, rotate: 45 },
        splitLine: { lineStyle: { color: '#1F2937' } }
      },
      yAxis: {
        type: 'value',
        name: '相对湿度 (%)',
        min: 30,
        max: 95,
        nameTextStyle: { color: '#9CA3AF' },
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: { color: '#9CA3AF', formatter: '{value}%' },
        splitLine: { lineStyle: { color: '#1F2937' } }
      },
      series: [
        {
          name: '安全区下限',
          type: 'line',
          data: historyData.map(() => 40),
          lineStyle: { color: 'transparent' },
          stack: 'safe_zone_low',
          symbol: 'none'
        },
        {
          name: '安全区(低)',
          type: 'line',
          data: historyData.map(() => 40),
          lineStyle: { color: 'transparent' },
          stack: 'safe_zone_low',
          areaStyle: {
            color: 'rgba(34, 197, 94, 0.15)'
          },
          symbol: 'none'
        },
        {
          name: '安全区上限',
          type: 'line',
          data: historyData.map(() => 65),
          lineStyle: { color: 'transparent' },
          stack: 'safe_zone_high',
          symbol: 'none'
        },
        {
          name: '安全区(高)',
          type: 'line',
          data: historyData.map(() => 25),
          lineStyle: { color: 'transparent' },
          stack: 'safe_zone_high',
          areaStyle: {
            color: 'rgba(34, 197, 94, 0.15)'
          },
          symbol: 'none'
        },
        {
          name: '潮解危险区',
          type: 'line',
          data: historyData.map(() => 15),
          lineStyle: { color: 'transparent' },
          markArea: {
            silent: true,
            data: [
              [
                { yAxis: 65, itemStyle: { color: 'rgba(239, 68, 68, 0.2)' } },
                { yAxis: 80 }
              ]
            ]
          },
          symbol: 'none'
        },
        {
          name: '相对湿度',
          type: 'line',
          data: historyData.map(d => d.currentRh),
          smooth: true,
          symbol: 'circle',
          symbolSize: 4,
          lineStyle: { width: 2, color: '#3B82F6' },
          itemStyle: { color: '#3B82F6' },
          z: 10,
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(59, 130, 246, 0.3)' },
                { offset: 1, color: 'rgba(59, 130, 246, 0)' }
              ]
            }
          }
        },
        {
          name: '目标RH',
          type: 'line',
          data: historyData.map(() => envState.targetRh || 55),
          lineStyle: {
            color: '#F59E0B',
            type: 'dashed',
            width: 1
          },
          symbol: 'none',
          z: 5
        },
        {
          name: '除湿机运行',
          type: 'scatter',
          data: historyData.map((d, i) => d.dehumidifierOn ? [i, d.currentRh] : null),
          symbolSize: 8,
          itemStyle: { color: '#10B981' },
          z: 15
        },
        {
          name: '加湿器运行',
          type: 'scatter',
          data: historyData.map((d, i) => d.humidifierOn ? [i, d.currentRh] : null),
          symbolSize: 8,
          itemStyle: { color: '#8B5CF6' },
          z: 15
        }
      ]
    }
  }, [historyData, envState.targetRh])

  const deviceStatusColor = (status: boolean) => status ? 'text-normal-green' : 'text-gray-500'
  const deviceBgColor = (status: boolean) => status ? 'bg-normal-green/20 border-normal-green/40' : 'bg-gray-700/50 border-gray-600/40'

  return (
    <div className="w-full">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <Settings className="text-bronze-green" size={24} />
          <h2 className="text-xl font-bold text-white">Feature2 微环境 DQN 调控</h2>
          <span className={`px-3 py-1 rounded-full text-xs font-medium ${
            envState.controlMode === 'AUTO_DQN'
              ? 'bg-bronze-green/20 text-bronze-green border border-bronze-green/40'
              : envState.controlMode === 'MANUAL'
              ? 'bg-ochre/20 text-ochre border border-ochre/40'
              : 'bg-blue-500/20 text-blue-400 border border-blue-500/40'
          }`}>
            {modeLabels[envState.controlMode || 'MANUAL']}
          </span>
        </div>
        <button
          onClick={handleRefresh}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gray-700/50 hover:bg-gray-600/50 text-gray-300 transition-colors"
        >
          <RefreshCw size={16} className={isRefreshing ? 'animate-spin' : ''} />
          <span className="text-sm">刷新</span>
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        <div className="lg:col-span-3 space-y-4">
          <div className="glass-card p-5 bg-gradient-to-br from-bronze-green/20 to-bronze-green/5 border-bronze-green/30">
            <div className="flex items-center justify-between mb-3">
              <span className="text-gray-400 text-sm font-medium">当前湿度</span>
              <div className={`p-2 rounded-lg bg-bronze-green/10`}>
                <Droplets size={20} className="text-bronze-green" />
              </div>
            </div>
            <p className="text-3xl font-bold text-white mb-2">
              {envState.currentRh.toFixed(1)}
              <span className="text-lg ml-1 text-gray-400">%</span>
            </p>
            <div className="flex items-center gap-2">
              <div className={`flex items-center gap-1 text-sm ${
                envState.rhTrend > 0 ? 'text-alert-red' : envState.rhTrend < 0 ? 'text-normal-green' : 'text-gray-400'
              }`}>
                {envState.rhTrend > 0 ? <TrendingUp size={14} /> : envState.rhTrend < 0 ? <TrendingDown size={14} /> : <Minus size={14} />}
                <span>{envState.rhTrend > 0 ? '+' : ''}{envState.rhTrend.toFixed(1)}%</span>
              </div>
              <span className="text-gray-500 text-sm">RH趋势</span>
            </div>
            {envState.currentRh >= 65 && envState.currentRh <= 80 && (
              <div className="mt-3 px-3 py-2 rounded-lg bg-alert-red/10 border border-alert-red/30">
                <span className="text-alert-red text-sm">⚠️ 潮解危险区</span>
              </div>
            )}
          </div>

          <div className="glass-card p-5 bg-gradient-to-br from-blue-500/20 to-blue-500/5 border-blue-500/30">
            <div className="flex items-center justify-between mb-3">
              <span className="text-gray-400 text-sm font-medium">当前时间</span>
              <Clock size={20} className="text-blue-400" />
            </div>
            <p className="text-3xl font-bold text-white mb-2">
              {String(envState.currentHour).padStart(2, '0')}:00
            </p>
            <p className="text-gray-500 text-sm">
              {new Date().toLocaleDateString('zh-CN', { weekday: 'long', month: 'long', day: 'numeric' })}
            </p>
          </div>

          <div className="glass-card p-5">
            <h3 className="text-white font-medium mb-4 flex items-center gap-2">
              <Power size={18} className="text-bronze-green" />
              设备状态
            </h3>
            <div className="space-y-3">
              <div className={`flex items-center justify-between p-3 rounded-lg border ${deviceBgColor(envState.dehumidifierStatus)}`}>
                <div className="flex items-center gap-3">
                  <Wind className={deviceStatusColor(envState.dehumidifierStatus)} size={20} />
                  <span className={`font-medium ${envState.dehumidifierStatus ? 'text-white' : 'text-gray-400'}`}>除湿机</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${envState.dehumidifierStatus ? 'bg-normal-green animate-pulse' : 'bg-gray-500'}`}></span>
                  <span className={`text-sm ${deviceStatusColor(envState.dehumidifierStatus)}`}>
                    {envState.dehumidifierStatus ? '运行中' : '待机'}
                  </span>
                </div>
              </div>
              <div className={`flex items-center justify-between p-3 rounded-lg border ${deviceBgColor(envState.humidifierStatus)}`}>
                <div className="flex items-center gap-3">
                  <Droplets className={deviceStatusColor(envState.humidifierStatus)} size={20} />
                  <span className={`font-medium ${envState.humidifierStatus ? 'text-white' : 'text-gray-400'}`}>加湿器</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${envState.humidifierStatus ? 'bg-normal-green animate-pulse' : 'bg-gray-500'}`}></span>
                  <span className={`text-sm ${deviceStatusColor(envState.humidifierStatus)}`}>
                    {envState.humidifierStatus ? '运行中' : '待机'}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div className="glass-card p-5 bg-gradient-to-br from-ochre/20 to-ochre/5 border-ochre/30">
            <h3 className="text-ochre font-medium mb-3 flex items-center gap-2">
              <Zap size={18} />
              DQN 推荐动作
            </h3>
            <div className="bg-gray-800/50 rounded-lg p-4 mb-3">
              <p className="text-white font-bold text-lg mb-1">{envState.recommendedActionName}</p>
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-400">预期奖励</span>
                <span className="text-bronze-green font-medium">{(envState.expectedReward * 100).toFixed(0)}%</span>
              </div>
              <div className="w-full h-2 bg-gray-700 rounded-full mt-2">
                <div
                  className="h-full bg-gradient-to-r from-bronze-green to-ochre rounded-full transition-all"
                  style={{ width: `${envState.expectedReward * 100}%` }}
                ></div>
              </div>
            </div>
            {envState.controlMode === 'AUTO_DQN' ? (
              <button
                onClick={() => handleModeChange('MANUAL')}
                className="w-full flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-gray-700 hover:bg-gray-600 text-gray-200 transition-colors"
              >
                <Pause size={16} />
                <span>暂停自动控制</span>
              </button>
            ) : (
              <button
                onClick={() => handleModeChange('AUTO_DQN')}
                className="w-full flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-gradient-to-r from-bronze-green to-ochre hover:from-bronze-green/80 hover:to-ochre/80 text-white font-medium transition-all"
              >
                <Play size={16} />
                <span>启动自动控制</span>
              </button>
            )}
          </div>
        </div>

        <div className="lg:col-span-5 space-y-4">
          <div className="glass-card p-5">
            <h3 className="text-white font-medium mb-4 flex items-center gap-2">
              <Settings size={18} className="text-bronze-green" />
              手动控制面板
            </h3>

            <div className="mb-6">
              <p className="text-gray-400 text-sm mb-3">控制模式</p>
              <div className="grid grid-cols-3 gap-2">
                {(['MANUAL', 'AUTO_DQN', 'SCHEDULE'] as const).map(mode => (
                  <button
                    key={mode}
                    onClick={() => handleModeChange(mode)}
                    className={`px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                      envState.controlMode === mode
                        ? 'bg-bronze-green/20 text-bronze-green border border-bronze-green/40'
                        : 'bg-gray-700/50 text-gray-300 border border-transparent hover:bg-gray-600/50'
                    }`}
                  >
                    {modeLabels[mode]}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-4 mb-6">
              <div className="flex items-center justify-between p-4 rounded-lg bg-gray-800/50">
                <div className="flex items-center gap-3">
                  <Wind size={20} className={envState.dehumidifierStatus ? 'text-normal-green' : 'text-gray-500'} />
                  <div>
                    <p className="text-white font-medium">除湿机</p>
                    <p className="text-gray-500 text-xs">降低环境湿度</p>
                  </div>
                </div>
                <button
                  onClick={() => handleControlToggle('dehumidifier', !envState.dehumidifierStatus)}
                  disabled={envState.controlMode === 'AUTO_DQN'}
                  className={`relative w-14 h-7 rounded-full transition-colors ${
                    envState.dehumidifierStatus ? 'bg-normal-green' : 'bg-gray-600'
                  } ${envState.controlMode === 'AUTO_DQN' ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  <span
                    className={`absolute top-1 w-5 h-5 bg-white rounded-full transition-transform ${
                      envState.dehumidifierStatus ? 'left-8' : 'left-1'
                    }`}
                  ></span>
                </button>
              </div>

              <div className="flex items-center justify-between p-4 rounded-lg bg-gray-800/50">
                <div className="flex items-center gap-3">
                  <Droplets size={20} className={envState.humidifierStatus ? 'text-blue-400' : 'text-gray-500'} />
                  <div>
                    <p className="text-white font-medium">加湿器</p>
                    <p className="text-gray-500 text-xs">提高环境湿度</p>
                  </div>
                </div>
                <button
                  onClick={() => handleControlToggle('humidifier', !envState.humidifierStatus)}
                  disabled={envState.controlMode === 'AUTO_DQN'}
                  className={`relative w-14 h-7 rounded-full transition-colors ${
                    envState.humidifierStatus ? 'bg-blue-500' : 'bg-gray-600'
                  } ${envState.controlMode === 'AUTO_DQN' ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  <span
                    className={`absolute top-1 w-5 h-5 bg-white rounded-full transition-transform ${
                      envState.humidifierStatus ? 'left-8' : 'left-1'
                    }`}
                  ></span>
                </button>
              </div>
            </div>

            <div className="mb-6">
              <div className="flex items-center justify-between mb-3">
                <p className="text-gray-400 text-sm flex items-center gap-2">
                  <Target size={14} />
                  目标湿度设定
                </p>
                <span className="text-bronze-green font-bold">{targetRhInput}%</span>
              </div>
              <input
                type="range"
                min="30"
                max="90"
                value={targetRhInput}
                onChange={(e) => setTargetRhInput(Number(e.target.value))}
                disabled={envState.controlMode === 'AUTO_DQN'}
                className="w-full h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-bronze-green disabled:opacity-50 disabled:cursor-not-allowed"
              />
              <div className="flex justify-between text-xs text-gray-500 mt-1">
                <span>30%</span>
                <span>60%</span>
                <span>90%</span>
              </div>
              <button
                onClick={handleTargetRhChange}
                disabled={envState.controlMode === 'AUTO_DQN'}
                className="w-full mt-4 px-4 py-2 rounded-lg bg-ochre/20 hover:bg-ochre/30 text-ochre border border-ochre/40 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                应用目标湿度
              </button>
            </div>

            <div className="border-t border-gray-700 pt-4">
              <button
                onClick={handleTrainModel}
                disabled={isTraining}
                className="w-full flex items-center justify-center gap-2 px-4 py-3 rounded-lg bg-gradient-to-r from-bronze-green/20 to-ochre/20 hover:from-bronze-green/30 hover:to-ochre/30 text-bronze-green border border-bronze-green/40 transition-all disabled:opacity-50"
              >
                <RefreshCw size={16} className={isTraining ? 'animate-spin' : ''} />
                <span>{isTraining ? '训练中...' : '训练 DQN 模型'}</span>
              </button>
              <p className="text-gray-500 text-xs text-center mt-2">
                根据历史数据优化控制策略
              </p>
            </div>
          </div>

          <div className="glass-card p-5">
            <h3 className="text-white font-medium mb-4 flex items-center gap-2">
              <Zap size={18} className="text-ochre" />
              能耗统计
            </h3>
            <div className="grid grid-cols-3 gap-3">
              <div className="text-center p-3 rounded-lg bg-gray-800/50">
                <p className="text-2xl font-bold text-ochre">{energyStats.todayEnergy.toFixed(1)}</p>
                <p className="text-gray-500 text-xs mt-1">今日能耗 (kWh)</p>
              </div>
              <div className="text-center p-3 rounded-lg bg-gray-800/50">
                <p className="text-2xl font-bold text-bronze-green">{energyStats.weekEnergy.toFixed(1)}</p>
                <p className="text-gray-500 text-xs mt-1">本周能耗 (kWh)</p>
              </div>
              <div className="text-center p-3 rounded-lg bg-gray-800/50">
                <p className="text-2xl font-bold text-blue-400">{energyStats.monthEnergy.toFixed(1)}</p>
                <p className="text-gray-500 text-xs mt-1">预计月度 (kWh)</p>
              </div>
            </div>
          </div>
        </div>

        <div className="lg:col-span-4 space-y-4">
          <div className="glass-card p-5">
            <h3 className="text-white font-medium mb-4 flex items-center gap-2">
              <BarChart3 size={18} className="text-bronze-green" />
              控制历史
            </h3>

            <div className="space-y-2 mb-4">
              <div className="flex items-center gap-2 text-xs">
                <span className="w-3 h-3 rounded bg-normal-green/30 border border-normal-green/50"></span>
                <span className="text-gray-400">安全区 (40%-65% & 80%-90%)</span>
              </div>
              <div className="flex items-center gap-2 text-xs">
                <span className="w-3 h-3 rounded bg-alert-red/30 border border-alert-red/50"></span>
                <span className="text-gray-400">潮解危险区 (65%-80%)</span>
              </div>
              <div className="flex items-center gap-2 text-xs">
                <span className="w-2 h-2 rounded-full bg-normal-green"></span>
                <span className="text-gray-400">除湿机运行</span>
              </div>
              <div className="flex items-center gap-2 text-xs">
                <span className="w-2 h-2 rounded-full bg-purple-500"></span>
                <span className="text-gray-400">加湿器运行</span>
              </div>
            </div>

            <ReactECharts
              option={chartOption}
              style={{ height: '320px', width: '100%' }}
              opts={{ renderer: 'canvas' }}
            />
          </div>

          <div className="glass-card p-5">
            <h3 className="text-white font-medium mb-3 flex items-center gap-2">
              <Calendar size={18} className="text-bronze-green" />
              设备运行时间轴
            </h3>
            <div className="space-y-3">
              <div>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-gray-400">除湿机</span>
                  <span className="text-sm text-normal-green">
                    {historyData.filter(d => d.dehumidifierOn).length} 小时
                  </span>
                </div>
                <div className="h-6 bg-gray-800 rounded overflow-hidden flex">
                  {historyData.map((d, i) => (
                    <div
                      key={i}
                      className={`flex-1 ${d.dehumidifierOn ? 'bg-normal-green' : 'bg-transparent'}`}
                      title={`${new Date(d.timestamp).getHours()}:00 - ${d.dehumidifierOn ? '运行' : '待机'}`}
                    />
                  ))}
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-gray-400">加湿器</span>
                  <span className="text-sm text-purple-400">
                    {historyData.filter(d => d.humidifierOn).length} 小时
                  </span>
                </div>
                <div className="h-6 bg-gray-800 rounded overflow-hidden flex">
                  {historyData.map((d, i) => (
                    <div
                      key={i}
                      className={`flex-1 ${d.humidifierOn ? 'bg-purple-500' : 'bg-transparent'}`}
                      title={`${new Date(d.timestamp).getHours()}:00 - ${d.humidifierOn ? '运行' : '待机'}`}
                    />
                  ))}
                </div>
              </div>
            </div>
            <div className="flex justify-between text-xs text-gray-500 mt-2">
              <span>{historyData.length > 0 ? new Date(historyData[0].timestamp).getHours() + ':00' : '--'}</span>
              <span>过去24小时</span>
              <span>{historyData.length > 0 ? new Date(historyData[historyData.length - 1].timestamp).getHours() + ':00' : '--'}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
