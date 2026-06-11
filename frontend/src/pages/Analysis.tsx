import { Suspense, useState, useMemo } from 'react'
import { AlertTriangle, Beaker, Thermometer, Shield, ArrowRight, RefreshCw, Activity } from 'lucide-react'
import { useMockData } from '@/hooks/useMockData'
import AnalysisChart from '@/components/AnalysisChart'
import CycleCountChart from '@/components/CycleCountChart'
import TombScene3D from '@/components/TombScene3D'
import Loading from '@/components/Loading'
import { RiskLevel } from '@/types'

const riskLevelColors: Record<RiskLevel, string> = {
  NONE: 'bg-emerald-500',
  LOW: 'bg-blue-500',
  MEDIUM: 'bg-amber-500',
  HIGH: 'bg-orange-500',
  CRITICAL: 'bg-red-600',
}

const riskLevelLabels: Record<RiskLevel, string> = {
  NONE: '无风险',
  LOW: '低风险',
  MEDIUM: '中等风险',
  HIGH: '高风险',
  CRITICAL: '严重风险',
}

function Analysis(): JSX.Element {
  const { chambers, devices, saltData, envData, analysisResults, cycleCountDataList, loading } = useMockData()
  const [selectedChamberId, setSelectedChamberId] = useState<string>('')
  const [activeTab, setActiveTab] = useState<'analysis' | 'cycle-count'>('analysis')

  const filteredAnalysis = useMemo(() => {
    if (!selectedChamberId) return analysisResults
    const chamberDevices = devices
      .filter((d) => d.chamberId === selectedChamberId)
      .map((d) => d.id)
    return analysisResults.filter((a) => chamberDevices.includes(a.deviceId))
  }, [analysisResults, devices, selectedChamberId])

  const filteredSaltData = useMemo(() => {
    if (!selectedChamberId) return saltData
    return saltData.filter((s) => s.chamberId === selectedChamberId)
  }, [saltData, selectedChamberId])

  const filteredEnvData = useMemo(() => {
    if (!selectedChamberId) return envData
    return envData.filter((e) => e.chamberId === selectedChamberId)
  }, [envData, selectedChamberId])

  const filteredDevices = useMemo(() => {
    if (!selectedChamberId) return devices
    return devices.filter((d) => d.chamberId === selectedChamberId)
  }, [devices, selectedChamberId])

  const selectedChamber = useMemo(() => {
    if (!selectedChamberId) return chambers[0] || { id: '', tombId: '', name: '全部墓室', code: '', width: 5, height: 5, length: 5 }
    return chambers.find((c) => c.id === selectedChamberId) || chambers[0]
  }, [chambers, selectedChamberId])

  const riskCards = useMemo(() => {
    if (filteredAnalysis.length === 0) {
      return [
        { title: '盐害风险指数', value: 0, level: 'NONE' as RiskLevel, icon: Beaker },
        { title: '结构稳定性', value: 100, level: 'NONE' as RiskLevel, icon: Shield },
        { title: '环境适宜度', value: 100, level: 'NONE' as RiskLevel, icon: Thermometer },
      ]
    }

    const highCount = filteredAnalysis.filter((a) => a.riskLevel === 'HIGH' || a.riskLevel === 'CRITICAL').length
    const saltRisk = Math.min(100, Math.round((highCount / filteredAnalysis.length) * 100 + 20))
    const avgPressure = filteredAnalysis.reduce((sum, a) => sum + a.crystallizationPressure, 0) / filteredAnalysis.length
    const structuralStability = Math.max(0, Math.round(100 - avgPressure * 10))
    const avgSalt = filteredAnalysis.reduce((sum, a) => sum + a.predictedTotalSalt, 0) / filteredAnalysis.length
    const envSuitability = Math.max(0, Math.round(100 - avgSalt * 8))

    const toRiskLevel = (v: number): RiskLevel => {
      if (v >= 80) return RiskLevel.CRITICAL
      if (v >= 60) return RiskLevel.HIGH
      if (v >= 40) return RiskLevel.MEDIUM
      if (v >= 20) return RiskLevel.LOW
      return RiskLevel.NONE
    }

    return [
      { title: '盐害风险指数', value: saltRisk, level: toRiskLevel(saltRisk), icon: Beaker },
      { title: '结构稳定性', value: structuralStability, level: toRiskLevel(100 - structuralStability), icon: Shield },
      { title: '环境适宜度', value: envSuitability, level: toRiskLevel(100 - envSuitability), icon: Thermometer },
    ]
  }, [filteredAnalysis])

  const hotspots = useMemo(() => {
    return filteredAnalysis
      .filter((a) => a.riskLevel === 'HIGH' || a.riskLevel === 'CRITICAL')
      .map((a) => {
        const device = devices.find((d) => d.id === a.deviceId)
        const chamberInfo = chambers.find((c) => c.id === device?.chamberId)
        return {
          id: a.deviceId,
          location: `(${a.location.x.toFixed(1)}, ${a.location.y.toFixed(1)}, ${a.location.z.toFixed(1)})`,
          chamber: chamberInfo?.name || '未知',
          riskLevel: a.riskLevel,
          pressure: a.crystallizationPressure,
        }
      })
  }, [filteredAnalysis, devices, chambers])

  const saturationData = useMemo(() => {
    if (filteredSaltData.length === 0) return []
    const latestByDevice = new Map<string, typeof filteredSaltData[0]>()
    for (const s of filteredSaltData) {
      const existing = latestByDevice.get(s.deviceId)
      if (!existing || s.timestamp > existing.timestamp) {
        latestByDevice.set(s.deviceId, s)
      }
    }
    return Array.from(latestByDevice.values()).map((s) => {
      const naClSat = (s.naPlus / 6.1) * 100
      const caCl2Sat = (s.ca2Plus / 5.5) * 100
      const na2so4Sat = (s.so42Minus / 4.0) * 100
      return {
        deviceId: s.deviceId,
        naCl: Math.min(naClSat, 100),
        caCl2: Math.min(caCl2Sat, 100),
        na2so4: Math.min(na2so4Sat, 100),
      }
    })
  }, [filteredSaltData])

  const filteredCycleCount = useMemo(() => {
    if (!selectedChamberId) return cycleCountDataList
    return cycleCountDataList.filter((c) => c.chamberId === selectedChamberId)
  }, [cycleCountDataList, selectedChamberId])

  const selectedCycleCount = useMemo(() => {
    if (filteredCycleCount.length === 0) return null
    return filteredCycleCount[0]
  }, [filteredCycleCount])

  if (loading) {
    return <Loading centered text="加载分析数据..." />
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white font-serif-sc">盐害分析</h2>
          <p className="text-gray-400 mt-1">使用AI模型分析盐害风险和预测趋势</p>
        </div>
        <div className="flex items-center gap-3">
          <label className="text-gray-400 text-sm whitespace-nowrap">选择墓室</label>
          <select
            value={selectedChamberId}
            onChange={(e) => setSelectedChamberId(e.target.value)}
            className="input-field min-w-[180px]"
          >
            <option value="">全部墓室</option>
            {chambers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex items-center gap-2 border-b border-gray-700/50 pb-2">
        <button
          onClick={() => setActiveTab('analysis')}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            activeTab === 'analysis'
              ? 'bg-bronze-green/20 text-bronze-green border border-bronze-green/30'
              : 'text-gray-400 hover:text-white hover:bg-dark-300'
          }`}
        >
          <div className="flex items-center gap-2">
            <Beaker size={16} />
            <span>盐害分析</span>
          </div>
        </button>
        <button
          onClick={() => setActiveTab('cycle-count')}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            activeTab === 'cycle-count'
              ? 'bg-bronze-green/20 text-bronze-green border border-bronze-green/30'
              : 'text-gray-400 hover:text-white hover:bg-dark-300'
          }`}
        >
          <div className="flex items-center gap-2">
            <Activity size={16} />
            <span>循环计数统计</span>
          </div>
        </button>
      </div>

      {activeTab === 'analysis' && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {riskCards.map((card) => (
              <div key={card.title} className="glass-card p-5">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <card.icon size={18} className="text-gray-400" />
                <span className="text-gray-400 text-sm">{card.title}</span>
              </div>
              <span className={`w-3 h-3 rounded-full ${riskLevelColors[card.level]}`} />
            </div>
            <p className="text-3xl font-bold text-white">
              {card.value}
              <span className="text-lg text-gray-400 ml-1">%</span>
            </p>
            <div className="mt-3 h-2 bg-dark-300 rounded-full overflow-hidden">
              <div
                className={`h-full ${riskLevelColors[card.level]} transition-all duration-500`}
                style={{ width: `${card.value}%` }}
              />
            </div>
            <p className="text-xs text-gray-500 mt-2">{riskLevelLabels[card.level]}</p>
          </div>
        ))}
      </div>

      <div className="glass-card p-4">
        <h3 className="text-lg font-bold text-white mb-3 font-serif-sc">三维盐分运移可视化</h3>
        <div className="w-full h-[400px] rounded-lg overflow-hidden">
          <Suspense fallback={<Loading centered text="加载三维场景..." />}>
            <TombScene3D
              chamber={selectedChamber}
              devices={filteredDevices}
              saltData={filteredSaltData}
              envData={filteredEnvData}
              analysisResults={filteredAnalysis}
              showArrows={true}
              showSaltDamage={true}
              showSensors={true}
            />
          </Suspense>
        </div>
      </div>

      <div className="glass-card p-6">
        <h3 className="text-lg font-bold text-white mb-4 font-serif-sc">分析结果趋势</h3>
        {filteredAnalysis.length > 0 ? (
          <AnalysisChart data={filteredAnalysis} height={350} />
        ) : (
          <div className="flex items-center justify-center h-[200px] text-gray-500">
            暂无分析数据，请选择墓室
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="glass-card p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-white font-serif-sc">热点区域分析</h3>
            <AlertTriangle size={20} className="text-amber-500" />
          </div>
          {hotspots.length > 0 ? (
            <div className="space-y-3 max-h-[320px] overflow-y-auto pr-1">
              {hotspots.map((h) => (
                <div
                  key={h.id}
                  className="flex items-center justify-between p-3 bg-dark-300/50 rounded-lg"
                >
                  <div className="flex items-center gap-3">
                    <span className={`w-3 h-3 rounded-full ${riskLevelColors[h.riskLevel]}`} />
                    <div>
                      <span className="text-white text-sm">{h.chamber}</span>
                      <span className="text-gray-500 text-xs ml-2">{h.location}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-400 text-xs">{h.pressure.toFixed(2)} MPa</span>
                    <span className={`text-xs px-2 py-0.5 rounded ${riskLevelColors[h.riskLevel]} text-white`}>
                      {riskLevelLabels[h.riskLevel]}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="flex items-center justify-center h-[100px] text-gray-500 text-sm">
              当前未检测到高风险区域
            </div>
          )}
        </div>

        <div className="glass-card p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-white font-serif-sc">Na₂SO₄ 饱和指数</h3>
            <Beaker size={20} className="text-bronze-green" />
          </div>
          {saturationData.length > 0 ? (
            <div className="space-y-3 max-h-[320px] overflow-y-auto pr-1">
              {saturationData.map((s) => (
                <div key={s.deviceId} className="p-3 bg-dark-300/50 rounded-lg">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-white text-sm">{s.deviceId}</span>
                    <span className={`text-xs ${s.na2so4 >= 80 ? 'text-red-400' : s.na2so4 >= 50 ? 'text-amber-400' : 'text-emerald-400'}`}>
                      {s.na2so4.toFixed(1)}%
                    </span>
                  </div>
                  <div className="space-y-1.5">
                    <div>
                      <div className="flex justify-between text-xs text-gray-500 mb-0.5">
                        <span>NaCl</span>
                        <span>{s.naCl.toFixed(1)}%</span>
                      </div>
                      <div className="h-1.5 bg-dark-300 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-blue-500 transition-all duration-500"
                          style={{ width: `${Math.min(s.naCl, 100)}%` }}
                        />
                      </div>
                    </div>
                    <div>
                      <div className="flex justify-between text-xs text-gray-500 mb-0.5">
                        <span>CaCl₂</span>
                        <span>{s.caCl2.toFixed(1)}%</span>
                      </div>
                      <div className="h-1.5 bg-dark-300 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-purple-500 transition-all duration-500"
                          style={{ width: `${Math.min(s.caCl2, 100)}%` }}
                        />
                      </div>
                    </div>
                    <div>
                      <div className="flex justify-between text-xs text-gray-500 mb-0.5">
                        <span>Na₂SO₄</span>
                        <span>{s.na2so4.toFixed(1)}%</span>
                      </div>
                      <div className="h-1.5 bg-dark-300 rounded-full overflow-hidden">
                        <div
                          className={`h-full transition-all duration-500 ${s.na2so4 >= 80 ? 'bg-red-500' : s.na2so4 >= 50 ? 'bg-amber-500' : 'bg-emerald-500'}`}
                          style={{ width: `${Math.min(s.na2so4, 100)}%` }}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="flex items-center justify-center h-[100px] text-gray-500 text-sm">
              暂无盐分饱和度数据
            </div>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="glass-card p-6">
          <div className="flex items-center gap-2 mb-4">
            <ArrowRight size={20} className="text-bronze-green" />
            <h3 className="text-lg font-bold text-white font-serif-sc">盐分运移模型</h3>
          </div>
          <div className="bg-dark-300/50 rounded-lg p-4 mb-4">
            <div className="text-center">
              <p className="text-2xl font-mono text-bronze-green tracking-wider">
                v = −(k/μ)·∇P + D·∇C
              </p>
            </div>
          </div>
          <div className="space-y-3 text-sm">
            <div className="flex gap-2">
              <span className="text-bronze-green font-mono shrink-0">v</span>
              <span className="text-gray-300">— 盐溶液运移速度矢量</span>
            </div>
            <div className="flex gap-2">
              <span className="text-bronze-green font-mono shrink-0">k</span>
              <span className="text-gray-300">— 墙体材料渗透率 (m²)</span>
            </div>
            <div className="flex gap-2">
              <span className="text-bronze-green font-mono shrink-0">μ</span>
              <span className="text-gray-300">— 溶液动力黏度 (Pa·s)</span>
            </div>
            <div className="flex gap-2">
              <span className="text-bronze-green font-mono shrink-0">∇P</span>
              <span className="text-gray-300">— 压力梯度 (Pa/m)，基于达西定律</span>
            </div>
            <div className="flex gap-2">
              <span className="text-bronze-green font-mono shrink-0">D</span>
              <span className="text-gray-300">— 离子扩散系数 (m²/s)</span>
            </div>
            <div className="flex gap-2">
              <span className="text-bronze-green font-mono shrink-0">∇C</span>
              <span className="text-gray-300">— 浓度梯度 (mol/m⁴)，菲克扩散定律</span>
            </div>
          </div>
          <div className="mt-4 p-3 bg-dark-300/30 rounded-lg border border-gray-700/50">
            <p className="text-xs text-gray-400 leading-relaxed">
              该模型结合达西定律描述的压力驱动流和菲克定律描述的离子扩散过程，
              综合考虑了毛细吸力、重力渗流和浓度差驱动的分子扩散三种盐分运移机制。
              适用于多孔建筑材料中盐溶液的运移行为模拟。
            </p>
          </div>
        </div>

        <div className="glass-card p-6">
          <div className="flex items-center gap-2 mb-4">
            <Shield size={20} className="text-red-400" />
            <h3 className="text-lg font-bold text-white font-serif-sc">结晶压力预测模型</h3>
          </div>
          <div className="bg-dark-300/50 rounded-lg p-4 mb-4">
            <div className="text-center">
              <p className="text-2xl font-mono text-red-400 tracking-wider">
                ΔP = (RT/V<sub>m</sub>)·ln(a/a₀)
              </p>
            </div>
          </div>
          <div className="space-y-3 text-sm">
            <div className="flex gap-2">
              <span className="text-red-400 font-mono shrink-0">ΔP</span>
              <span className="text-gray-300">— 结晶压力 (MPa)</span>
            </div>
            <div className="flex gap-2">
              <span className="text-red-400 font-mono shrink-0">R</span>
              <span className="text-gray-300">— 通用气体常数 (8.314 J/(mol·K))</span>
            </div>
            <div className="flex gap-2">
              <span className="text-red-400 font-mono shrink-0">T</span>
              <span className="text-gray-300">— 绝对温度 (K)</span>
            </div>
            <div className="flex gap-2">
              <span className="text-red-400 font-mono shrink-0">V<sub>m</sub></span>
              <span className="text-gray-300">— 盐晶体的摩尔体积 (m³/mol)</span>
            </div>
            <div className="flex gap-2">
              <span className="text-red-400 font-mono shrink-0">a</span>
              <span className="text-gray-300">— 当前溶液活度</span>
            </div>
            <div className="flex gap-2">
              <span className="text-red-400 font-mono shrink-0">a₀</span>
              <span className="text-gray-300">— 饱和溶液活度</span>
            </div>
          </div>
          <div className="mt-4 p-3 bg-dark-300/30 rounded-lg border border-gray-700/50">
            <p className="text-xs text-gray-400 leading-relaxed">
              基于 Na₂SO₄ 相变理论，当环境温湿度变化导致芒硝 (Na₂SO₄·10H₂O) 与无水芒硝
              (Na₂SO₄) 之间发生相变时，体积膨胀可达 314%，在孔隙中产生巨大的结晶压力。
              该模型特别关注 32.4°C 的相变临界温度和湿度循环对结晶/溶解过程的驱动效应。
            </p>
          </div>
        </div>
      </div>
        </>
      )}

      {activeTab === 'cycle-count' && (
        <div className="space-y-6">
          <div className="glass-card p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Activity size={20} className="text-bronze-green" />
                <h3 className="text-lg font-bold text-white font-serif-sc">盐结晶潮解循环统计</h3>
              </div>
              <div className="flex items-center gap-2">
                <button className="btn-secondary flex items-center gap-2">
                  <RefreshCw size={16} />
                  <span>重新统计</span>
                </button>
              </div>
            </div>
            <p className="text-gray-400 text-sm mb-4">
              采用雨流计数法对相对湿度时序数据进行循环统计，分析盐结晶潮解循环次数及疲劳损伤评估
            </p>
          </div>

          {selectedCycleCount ? (
            <CycleCountChart data={selectedCycleCount} height={300} />
          ) : (
            <div className="glass-card p-6">
              <div className="flex items-center justify-center h-[300px] text-gray-500">
                暂无循环统计数据，请选择墓室
              </div>
            </div>
          )}

          <div className="glass-card p-6">
            <div className="flex items-center gap-2 mb-4">
              <Beaker size={18} className="text-bronze-green" />
              <h3 className="text-base font-bold text-white font-serif-sc">雨流计数法说明</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-3">
                <div className="flex gap-2">
                  <span className="text-bronze-green font-mono shrink-0">原理</span>
                  <span className="text-gray-300 text-sm">
                    雨流计数法是一种常用的循环计数方法，通过模拟雨水从屋顶流下的过程，对载荷时间历程进行循环计数。
                  </span>
                </div>
                <div className="flex gap-2">
                  <span className="text-bronze-green font-mono shrink-0">应用</span>
                  <span className="text-gray-300 text-sm">
                    用于统计盐结晶/潮解循环次数，评估循环载荷对文物建筑材料的疲劳损伤。
                  </span>
                </div>
              </div>
              <div className="space-y-3">
                <div className="flex gap-2">
                  <span className="text-bronze-green font-mono shrink-0">潮解点</span>
                  <span className="text-gray-300 text-sm">
                    75%RH 为 Na₂SO₄ 的临界潮解湿度，超过此湿度盐分会发生潮解。
                  </span>
                </div>
                <div className="flex gap-2">
                  <span className="text-bronze-green font-mono shrink-0">损伤评估</span>
                  <span className="text-gray-300 text-sm">
                    基于Miner线性累积损伤理论，计算循环幅度越大，对材料的损伤越严重。
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Analysis
