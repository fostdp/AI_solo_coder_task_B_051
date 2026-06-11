import ReactECharts from 'echarts-for-react'
import { useMemo } from 'react'
import { Activity, Waves, AlertTriangle, Gauge, TrendingUp, type LucideIcon } from 'lucide-react'
import { CycleCountData, DamageLevel } from '@/types'

interface CycleCountChartProps {
  data: CycleCountData
  height?: number
  showStats?: boolean
  showRhChart?: boolean
}

const damageLevelColors: Record<DamageLevel, string> = {
  LOW: '#52B788',
  MEDIUM: '#E8A838',
  HIGH: '#F97316',
  CRITICAL: '#D64545'
}

const damageLevelBgColors: Record<DamageLevel, string> = {
  LOW: 'from-normal-green/20 to-normal-green/5 text-normal-green border-normal-green/30',
  MEDIUM: 'from-warning-yellow/20 to-warning-yellow/5 text-warning-yellow border-warning-yellow/30',
  HIGH: 'from-orange-500/20 to-orange-500/5 text-orange-400 border-orange-500/30',
  CRITICAL: 'from-alert-red/20 to-alert-red/5 text-alert-red border-alert-red/30'
}

const damageLevelIconBgColors: Record<DamageLevel, string> = {
  LOW: 'bg-normal-green/10 text-normal-green',
  MEDIUM: 'bg-warning-yellow/10 text-warning-yellow',
  HIGH: 'bg-orange-500/10 text-orange-400',
  CRITICAL: 'bg-alert-red/10 text-alert-red'
}

const damageLevelLabels: Record<DamageLevel, string> = {
  LOW: '低损伤',
  MEDIUM: '中等损伤',
  HIGH: '高损伤',
  CRITICAL: '严重损伤'
}

function StatCard({
  title,
  value,
  icon: Icon,
  color,
  suffix,
  description
}: {
  title: string
  value: string | number
  icon: LucideIcon
  color: string
  suffix?: string
  description?: string
}): JSX.Element {
  return (
    <div className={`glass-card p-4 bg-gradient-to-br ${color}`}>
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-gray-400 text-xs font-medium mb-1">{title}</p>
          <p className="text-2xl font-bold text-white mb-1">
            {value}
            {suffix && <span className="text-sm ml-1 text-gray-400">{suffix}</span>}
          </p>
          {description && (
            <p className="text-gray-500 text-xs">{description}</p>
          )}
        </div>
        <div className={`p-2.5 rounded-xl ${color.includes('normal-green') ? 'bg-normal-green/10 text-normal-green' : color.includes('warning-yellow') ? 'bg-warning-yellow/10 text-warning-yellow' : color.includes('orange') ? 'bg-orange-500/10 text-orange-400' : color.includes('alert-red') ? 'bg-alert-red/10 text-alert-red' : 'bg-bronze-green/10 text-bronze-green'}`}>
          <Icon size={20} />
        </div>
      </div>
    </div>
  )
}

export default function CycleCountChart({
  data,
  height = 300,
  showStats = true,
  showRhChart = true
}: CycleCountChartProps): JSX.Element {
  const histogramOption = useMemo(() => {
    const histogramData = data.amplitudeHistogram
    let xAxisData: string[] = []
    let seriesData: number[] = []

    if (Array.isArray(histogramData) && histogramData.length > 0) {
      const firstItem = histogramData[0]
      if (Array.isArray(firstItem)) {
        const numberArrData = histogramData as number[][]
        xAxisData = numberArrData.map((item) => `${item[0]}-${item[1]}%`)
        seriesData = numberArrData.map((item) => item[2] ?? item[1])
      } else {
        const objData = histogramData as { range: [number, number]; count: number }[]
        xAxisData = objData.map((item) => `${item.range[0]}-${item.range[1]}%`)
        seriesData = objData.map((item) => item.count)
      }
    }

    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 15, 26, 0.9)',
        borderColor: '#4A7C59',
        textStyle: { color: '#fff' },
        axisPointer: {
          type: 'shadow'
        },
        formatter: (params: Array<{ name: string; value: number }>) => {
          const param = params[0]
          return `
            <div style="padding: 4px 8px;">
              <div style="margin-bottom: 4px; color: #9CA3AF;">幅度范围: ${param.name}</div>
              <div style="color: #fff;">循环次数: <span style="color: #4A7C59; font-weight: bold;">${param.value}</span></div>
            </div>
          `
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '8%',
        top: '10%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: {
          color: '#9CA3AF',
          fontSize: 10,
          rotate: 45
        },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        name: '循环次数',
        nameTextStyle: { color: '#9CA3AF', fontSize: 11 },
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: { color: '#9CA3AF' },
        splitLine: { lineStyle: { color: '#1F2937' } }
      },
      series: [
        {
          name: '循环次数',
          type: 'bar',
          data: seriesData,
          barWidth: '60%',
          itemStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: '#4A7C59' },
                { offset: 1, color: '#2D5A3D' }
              ]
            },
            borderRadius: [4, 4, 0, 0]
          },
          emphasis: {
            itemStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: '#52B788' },
                  { offset: 1, color: '#4A7C59' }
                ]
              }
            }
          }
        }
      ]
    }
  }, [data.amplitudeHistogram])

  const rhChartOption = useMemo(() => {
    if (!data.rhTimeSeries || data.rhTimeSeries.length === 0) {
      return null
    }

    const times = data.rhTimeSeries.map((item) => {
      const date = new Date(item.time)
      return date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      })
    })
    const rhValues = data.rhTimeSeries.map((item) => item.rh)

    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 15, 26, 0.9)',
        borderColor: '#4A7C59',
        textStyle: { color: '#fff' }
      },
      legend: {
        data: ['相对湿度', '潮解点'],
        textStyle: { color: '#9CA3AF' },
        top: 0
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '8%',
        top: '15%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: times,
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: {
          color: '#9CA3AF',
          fontSize: 10,
          rotate: 45
        },
        splitLine: { lineStyle: { color: '#1F2937' } }
      },
      yAxis: {
        type: 'value',
        name: '相对湿度 (%)',
        nameTextStyle: { color: '#9CA3AF', fontSize: 11 },
        min: 0,
        max: 100,
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: { color: '#9CA3AF' },
        splitLine: { lineStyle: { color: '#1F2937' } }
      },
      series: [
        {
          name: '相对湿度',
          type: 'line',
          data: rhValues,
          smooth: true,
          symbol: 'circle',
          symbolSize: 4,
          lineStyle: { width: 2, color: '#4A7C59' },
          itemStyle: { color: '#4A7C59' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(74, 124, 89, 0.3)' },
                { offset: 1, color: 'rgba(74, 124, 89, 0.02)' }
              ]
            }
          }
        },
        {
          name: '潮解点',
          type: 'line',
          data: new Array(times.length).fill(75),
          lineStyle: {
            type: 'dashed',
            width: 2,
            color: '#D64545'
          },
          symbol: 'none',
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: {
              type: 'dashed',
              color: '#D64545',
              width: 2
            },
            label: {
              show: true,
              position: 'end',
              formatter: '潮解点 75%',
              color: '#D64545',
              fontSize: 11
            },
            data: [
              {
                yAxis: 75
              }
            ]
          }
        }
      ]
    }
  }, [data.rhTimeSeries])

  return (
    <div className="space-y-4">
      {showStats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <StatCard
            title="总循环次数"
            value={data.totalCycles}
            icon={Activity}
            color="from-bronze-green/20 to-bronze-green/5 text-bronze-green border-bronze-green/30"
            description="雨流计数法统计"
          />
          <StatCard
            title="穿越潮解点"
            value={data.crossingCycles}
            icon={Waves}
            color="from-blue-500/20 to-blue-500/5 text-blue-400 border-blue-500/30"
            description="跨越75%RH阈值"
          />
          <StatCard
            title="总疲劳损伤"
            value={data.totalDamage.toFixed(4)}
            icon={Gauge}
            color="from-ochre/20 to-ochre/5 text-ochre border-ochre/30"
            description="累计损伤值"
          />
          <div className={`glass-card p-4 bg-gradient-to-br ${damageLevelBgColors[data.damageLevel]}`}>
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <p className="text-gray-400 text-xs font-medium mb-1">损伤等级</p>
                <p className="text-2xl font-bold text-white mb-1">
                  {damageLevelLabels[data.damageLevel]}
                </p>
                <div className="flex items-center gap-1">
                  <div className="flex-1 h-1.5 bg-dark-300 rounded-full overflow-hidden">
                    <div
                      className="h-full rounded-full transition-all duration-500"
                      style={{
                        width: data.damageLevel === 'LOW' ? '25%' : data.damageLevel === 'MEDIUM' ? '50%' : data.damageLevel === 'HIGH' ? '75%' : '100%',
                        backgroundColor: damageLevelColors[data.damageLevel]
                      }}
                    />
                  </div>
                  <span className="text-xs text-gray-500">{data.damageLevel}</span>
                </div>
              </div>
              <div className={`p-2.5 rounded-xl ${damageLevelIconBgColors[data.damageLevel]}`}>
                <AlertTriangle size={20} />
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="space-y-4">
        <div className="glass-card p-4">
          <div className="flex items-center gap-2 mb-3">
            <TrendingUp size={16} className="text-bronze-green" />
            <h4 className="text-sm font-semibold text-white">循环幅度分布直方图</h4>
          </div>
          <ReactECharts
            option={histogramOption}
            style={{ height: `${height}px`, width: '100%' }}
            opts={{ renderer: 'canvas' }}
          />
        </div>

        {showRhChart && data.rhTimeSeries && data.rhTimeSeries.length > 0 && (
          <div className="glass-card p-4">
            <div className="flex items-center gap-2 mb-3">
              <Waves size={16} className="text-bronze-green" />
              <h4 className="text-sm font-semibold text-white">RH时序曲线与潮解点</h4>
            </div>
            <ReactECharts
              option={rhChartOption}
              style={{ height: `${height}px`, width: '100%' }}
              opts={{ renderer: 'canvas' }}
            />
          </div>
        )}
      </div>

      {data.periodStart && data.periodEnd && (
        <div className="flex items-center justify-between text-xs text-gray-500 px-1">
          <span>统计周期: {new Date(data.periodStart).toLocaleDateString('zh-CN')}</span>
          <span>至 {new Date(data.periodEnd).toLocaleDateString('zh-CN')}</span>
        </div>
      )}
    </div>
  )
}
