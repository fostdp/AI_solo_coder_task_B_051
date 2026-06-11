import { useState, useMemo, useEffect } from 'react'
import ReactECharts from 'echarts-for-react'
import {
  AlertTriangle,
  Shield,
  Activity,
  TrendingUp,
  Droplets,
  Thermometer,
  RefreshCw,
  Info,
  ChevronUp,
  ChevronDown
} from 'lucide-react'
import type { DelaminationRiskData, HistoryItem, RiskLevel } from '@/utils/delaminationApi'

interface DelaminationRiskCardProps {
  data?: DelaminationRiskData
  historyData?: HistoryItem[]
  onParamsChange?: (params: Partial<DelaminationRiskData>) => void
  onAssess?: () => void
  loading?: boolean
}

const riskLevelConfig: Record<RiskLevel, { color: string; bgColor: string; label: string; description: string }> = {
  LOW: {
    color: 'text-normal-green',
    bgColor: 'bg-normal-green/10 border-normal-green/30',
    label: '低风险',
    description: '颜料层状态稳定，暂无起甲风险'
  },
  MEDIUM: {
    color: 'text-warning-yellow',
    bgColor: 'bg-warning-yellow/10 border-warning-yellow/30',
    label: '中风险',
    description: '存在轻度起甲趋势，需加强监测'
  },
  HIGH: {
    color: 'text-orange-500',
    bgColor: 'bg-orange-500/10 border-orange-500/30',
    label: '高风险',
    description: '起甲风险较高，建议采取保护措施'
  },
  CRITICAL: {
    color: 'text-alert-red',
    bgColor: 'bg-alert-red/10 border-alert-red/30',
    label: '极高风险',
    description: '起甲风险极高，需立即采取保护措施'
  }
}

const featureIcons: Record<string, typeof Activity> = {
  crystallizationPressure: Activity,
  adhesionStrength: Shield,
  pressureAdhesionRatio: TrendingUp,
  cycleCount7d: RefreshCw,
  avgDailyRhFluctuation: Droplets,
  temperatureVariation: Thermometer
}

const featureLabels: Record<string, string> = {
  crystallizationPressure: '结晶压力',
  adhesionStrength: '颜料层附着力',
  pressureAdhesionRatio: '压附比',
  cycleCount7d: '7天循环次数',
  avgDailyRhFluctuation: '日均RH波动',
  temperatureVariation: '温度变幅'
}

const defaultMockData: DelaminationRiskData = {
  delaminationProbability: 0.42,
  riskLevel: 'MEDIUM',
  featureContributions: [
    { name: '结晶压力', value: 0.15, featureKey: 'crystallizationPressure' },
    { name: '颜料层附着力', value: -0.1, featureKey: 'adhesionStrength' },
    { name: '压附比', value: 0.22, featureKey: 'pressureAdhesionRatio' },
    { name: '7天循环次数', value: 0.08, featureKey: 'cycleCount7d' },
    { name: '日均RH波动', value: 0.12, featureKey: 'avgDailyRhFluctuation' },
    { name: '温度变幅', value: -0.05, featureKey: 'temperatureVariation' }
  ],
  crystallizationPressure: 2.1,
  adhesionStrength: 1.2,
  pressureAdhesionRatio: 1.75,
  cycleCount7d: 12,
  avgDailyRhFluctuation: 25,
  temperatureVariation: 8,
  suggestion: '建议控制环境湿度波动在15%以内，适当降低温度变化幅度，定期检查颜料层状态。',
  assessmentTime: '2026-06-11 14:30:00'
}

const defaultHistoryData: HistoryItem[] = [
  { date: '06-05', probability: 0.35, level: 'LOW' },
  { date: '06-06', probability: 0.38, level: 'LOW' },
  { date: '06-07', probability: 0.41, level: 'MEDIUM' },
  { date: '06-08', probability: 0.45, level: 'MEDIUM' },
  { date: '06-09', probability: 0.43, level: 'MEDIUM' },
  { date: '06-10', probability: 0.40, level: 'MEDIUM' },
  { date: '06-11', probability: 0.42, level: 'MEDIUM' }
]

function DelaminationRiskCard({
  data: propsData,
  historyData: propsHistoryData,
  onParamsChange,
  onAssess,
  loading = false
}: DelaminationRiskCardProps): JSX.Element {
  const [localData, setLocalData] = useState<DelaminationRiskData>(propsData || defaultMockData)
  const [historyData] = useState<HistoryItem[]>(propsHistoryData || defaultHistoryData)

  useEffect(() => {
    if (propsData) {
      setLocalData(propsData)
    }
  }, [propsData])

  const gaugeOption = useMemo(() => {
    const probability = localData.delaminationProbability * 100
    const level = localData.riskLevel

    const getGaugeColor = () => {
      switch (level) {
        case 'LOW': return '#52B788'
        case 'MEDIUM': return '#E8A838'
        case 'HIGH': return '#F97316'
        case 'CRITICAL': return '#D64545'
        default: return '#52B788'
      }
    }

    return {
      backgroundColor: 'transparent',
      series: [
        {
          type: 'gauge',
          startAngle: 200,
          endAngle: -20,
          min: 0,
          max: 100,
          radius: '90%',
          center: ['50%', '60%'],
          progress: {
            show: true,
            width: 12,
            itemStyle: {
              color: getGaugeColor()
            }
          },
          axisLine: {
            lineStyle: {
              width: 12,
              color: [[1, 'rgba(75, 85, 99, 0.3)']]
            }
          },
          axisTick: {
            show: false
          },
          splitLine: {
            show: false
          },
          axisLabel: {
            show: false
          },
          pointer: {
            show: false
          },
          anchor: {
            show: false
          },
          title: {
            show: false
          },
          detail: {
            valueAnimation: true,
            fontSize: 36,
            fontWeight: 'bold',
            color: getGaugeColor(),
            formatter: '{value}%',
            offsetCenter: [0, '0%']
          },
          data: [
            {
              value: probability.toFixed(1)
            }
          ]
        }
      ]
    }
  }, [localData.delaminationProbability, localData.riskLevel])

  const contributionOption = useMemo(() => {
    const contributions = localData.featureContributions || []
    const sortedContributions = [...contributions].sort((a, b) => Math.abs(b.value) - Math.abs(a.value))

    return {
      backgroundColor: 'transparent',
      grid: {
        left: '25%',
        right: '10%',
        top: '5%',
        bottom: '5%',
        containLabel: false
      },
      xAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
        splitLine: { show: false }
      },
      yAxis: {
        type: 'category',
        data: sortedContributions.map(c => c.name),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          color: '#9CA3AF',
          fontSize: 12
        }
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
        backgroundColor: 'rgba(15, 15, 26, 0.9)',
        borderColor: '#4A7C59',
        textStyle: { color: '#fff' },
        formatter: (params: { name: string; value: number }[]) => {
          const item = params[0]
          const value = (item.value * 100).toFixed(1)
          return `${item.name}<br/>贡献度: ${value > 0 ? '+' : ''}${value}%`
        }
      },
      series: [
        {
          type: 'bar',
          data: sortedContributions.map(c => ({
            value: c.value,
            itemStyle: {
              color: c.value >= 0 ? '#D64545' : '#52B788',
              borderRadius: [0, 4, 4, 0]
            }
          })),
          barWidth: 12,
          label: {
            show: true,
            position: 'right',
            formatter: (params: { value: number }) => {
              const value = (params.value * 100).toFixed(1)
              return `${value > 0 ? '+' : ''}${value}%`
            },
            color: '#9CA3AF',
            fontSize: 11
          }
        }
      ]
    }
  }, [localData.featureContributions])

  const trendOption = useMemo(() => {
    const dates = historyData.map(h => h.date)
    const probabilities = historyData.map(h => h.probability * 100)

    return {
      backgroundColor: 'transparent',
      grid: {
        left: '10%',
        right: '5%',
        top: '15%',
        bottom: '15%',
        containLabel: true
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 15, 26, 0.9)',
        borderColor: '#4A7C59',
        textStyle: { color: '#fff' },
        formatter: (params: { name: string; value: number }[]) => {
          const item = params[0]
          return `${item.name}<br/>起甲概率: ${item.value.toFixed(1)}%`
        }
      },
      xAxis: {
        type: 'category',
        data: dates,
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: { color: '#9CA3AF', fontSize: 10 },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        axisLine: { show: false },
        axisLabel: {
          color: '#9CA3AF',
          fontSize: 10,
          formatter: '{value}%'
        },
        splitLine: { lineStyle: { color: '#1F2937' } }
      },
      series: [
        {
          type: 'line',
          data: probabilities,
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: {
            width: 2,
            color: '#E8A838'
          },
          itemStyle: {
            color: '#E8A838'
          },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(232, 168, 56, 0.3)' },
                { offset: 1, color: 'rgba(232, 168, 56, 0)' }
              ]
            }
          }
        }
      ]
    }
  }, [historyData])

  const handleParamChange = (key: keyof DelaminationRiskData, value: number) => {
    const newData = { ...localData, [key]: value }

    if (key === 'crystallizationPressure' || key === 'adhesionStrength') {
      const pressure = key === 'crystallizationPressure' ? value : localData.crystallizationPressure
      const adhesion = key === 'adhesionStrength' ? value : localData.adhesionStrength
      newData.pressureAdhesionRatio = adhesion > 0 ? Number((pressure / adhesion).toFixed(2)) : 0
    }

    setLocalData(newData)
    onParamsChange?.(newData)
  }

  const riskConfig = riskLevelConfig[localData.riskLevel]

  const inputParams = [
    {
      key: 'crystallizationPressure' as const,
      label: '结晶压力',
      min: 0,
      max: 5,
      step: 0.1,
      unit: 'MPa'
    },
    {
      key: 'adhesionStrength' as const,
      label: '颜料层附着力',
      min: 0,
      max: 2,
      step: 0.1,
      unit: 'MPa'
    },
    {
      key: 'pressureAdhesionRatio' as const,
      label: '压附比',
      min: 0,
      max: 5,
      step: 0.1,
      unit: '',
      disabled: true
    },
    {
      key: 'cycleCount7d' as const,
      label: '7天循环次数',
      min: 0,
      max: 30,
      step: 1,
      unit: '次'
    },
    {
      key: 'avgDailyRhFluctuation' as const,
      label: '日均RH波动',
      min: 0,
      max: 50,
      step: 1,
      unit: '%'
    },
    {
      key: 'temperatureVariation' as const,
      label: '温度变幅',
      min: 0,
      max: 20,
      step: 0.5,
      unit: '℃'
    }
  ]

  return (
    <div className="glass-card p-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-warning-yellow/10">
            <AlertTriangle className="w-6 h-6 text-warning-yellow" />
          </div>
          <div>
            <h3 className="text-lg font-semibold text-white">壁画颜料层起甲风险评估</h3>
            <p className="text-sm text-gray-400">基于环境参数的智能预测模型</p>
          </div>
        </div>
        <button
          onClick={onAssess}
          disabled={loading}
          className="btn-primary flex items-center gap-2"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          {loading ? '评估中...' : '重新评估'}
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="space-y-6">
          <div className="bg-dark-300/50 rounded-lg p-4">
            <h4 className="text-sm font-medium text-gray-300 mb-2">起甲概率</h4>
            <div className="h-52">
              <ReactECharts
                option={gaugeOption}
                style={{ height: '100%', width: '100%' }}
                opts={{ renderer: 'canvas' }}
              />
            </div>
          </div>

          <div className={`rounded-lg p-4 border ${riskConfig.bgColor}`}>
            <div className="flex items-center gap-3 mb-3">
              <div className={`p-2 rounded-lg bg-white/5 ${riskConfig.color}`}>
                <Shield className="w-6 h-6" />
              </div>
              <div>
                <span className={`text-2xl font-bold ${riskConfig.color}`}>
                  {riskConfig.label}
                </span>
                <p className="text-sm text-gray-400 mt-1">{riskConfig.description}</p>
              </div>
            </div>
            <div className="grid grid-cols-4 gap-2 pt-3 border-t border-white/10">
              {(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as RiskLevel[]).map((level) => (
                <div
                  key={level}
                  className={`text-center py-1 rounded text-xs font-medium ${
                    level === localData.riskLevel
                      ? riskLevelConfig[level].bgColor + ' ' + riskLevelConfig[level].color
                      : 'bg-white/5 text-gray-500'
                  }`}
                >
                  {level === 'LOW' ? '低' : level === 'MEDIUM' ? '中' : level === 'HIGH' ? '高' : '极高'}
                </div>
              ))}
            </div>
          </div>

          <div className="bg-dark-300/50 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-3">
              <Info className="w-4 h-4 text-bronze-green" />
              <h4 className="text-sm font-medium text-gray-300">保护建议</h4>
            </div>
            <p className="text-sm text-gray-400 leading-relaxed">
              {localData.suggestion || '暂无建议'}
            </p>
          </div>
        </div>

        <div className="space-y-6">
          <div className="bg-dark-300/50 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-4">
              <TrendingUp className="w-4 h-4 text-bronze-green" />
              <h4 className="text-sm font-medium text-gray-300">特征贡献度</h4>
            </div>
            <div className="h-64">
              <ReactECharts
                option={contributionOption}
                style={{ height: '100%', width: '100%' }}
                opts={{ renderer: 'canvas' }}
              />
            </div>
            <div className="flex justify-center gap-6 mt-4 pt-3 border-t border-white/10">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded bg-alert-red"></div>
                <span className="text-xs text-gray-400">增加风险</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded bg-normal-green"></div>
                <span className="text-xs text-gray-400">降低风险</span>
              </div>
            </div>
          </div>

          <div className="bg-dark-300/50 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-4">
              <Activity className="w-4 h-4 text-bronze-green" />
              <h4 className="text-sm font-medium text-gray-300">近7天趋势</h4>
            </div>
            <div className="h-48">
              <ReactECharts
                option={trendOption}
                style={{ height: '100%', width: '100%' }}
                opts={{ renderer: 'canvas' }}
              />
            </div>
          </div>
        </div>

        <div className="space-y-4">
          <div className="bg-dark-300/50 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-4">
              <Shield className="w-4 h-4 text-bronze-green" />
              <h4 className="text-sm font-medium text-gray-300">输入参数</h4>
            </div>
            <div className="space-y-4">
              {inputParams.map((param) => {
                const value = localData[param.key] as number
                const Icon = featureIcons[param.key]

                return (
                  <div key={param.key} className="space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        {Icon && <Icon className="w-4 h-4 text-gray-400" />}
                        <span className="text-xs text-gray-400">{param.label}</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <input
                          type="number"
                          value={value}
                          min={param.min}
                          max={param.max}
                          step={param.step}
                          disabled={param.disabled}
                          onChange={(e) => {
                            const val = Number(e.target.value)
                            if (!isNaN(val) && val >= param.min && val <= param.max) {
                              handleParamChange(param.key, val)
                            }
                          }}
                          className="w-16 px-2 py-1 text-xs bg-dark-200 border border-gray-600 rounded text-white text-right focus:outline-none focus:border-bronze-green disabled:opacity-50"
                        />
                        <span className="text-xs text-gray-500 w-6">{param.unit}</span>
                      </div>
                    </div>
                    <div className="relative">
                      <input
                        type="range"
                        min={param.min}
                        max={param.max}
                        step={param.step}
                        value={value}
                        disabled={param.disabled}
                        onChange={(e) => handleParamChange(param.key, Number(e.target.value))}
                        className="w-full h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer disabled:opacity-50"
                        style={{
                          background: `linear-gradient(to right, #4A7C59 0%, #4A7C59 ${((value - param.min) / (param.max - param.min)) * 100}%, #374151 ${((value - param.min) / (param.max - param.min)) * 100}%, #374151 100%)`
                        }}
                      />
                      <div className="flex justify-between mt-1">
                        <span className="text-[10px] text-gray-500">{param.min}</span>
                        <span className="text-[10px] text-gray-500">{param.max}</span>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          <div className="bg-dark-300/50 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-3">
              <Info className="w-4 h-4 text-bronze-green" />
              <h4 className="text-sm font-medium text-gray-300">评估信息</h4>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-400">评估时间</span>
                <span className="text-gray-300">{localData.assessmentTime || '-'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-400">模型版本</span>
                <span className="text-gray-300">v2.1.0</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-400">置信度</span>
                <span className="text-normal-green">92.5%</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default DelaminationRiskCard
