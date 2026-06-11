import ReactECharts from 'echarts-for-react';
import { useMemo } from 'react';
import { AnalysisResult } from '@/types';

interface AnalysisChartProps {
  data: AnalysisResult[];
  height?: number;
}

export default function AnalysisChart({ data, height = 300 }: AnalysisChartProps) {
  const option = useMemo(() => {
    const sortedData = [...data].sort((a, b) => a.timestamp - b.timestamp);
    const times = sortedData.map(d => new Date(d.timestamp).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    }));
    
    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 15, 26, 0.9)',
        borderColor: '#4A7C59',
        textStyle: { color: '#fff' }
      },
      legend: {
        data: ['结晶压力', '运移速度', '预测压力'],
        textStyle: { color: '#9CA3AF' },
        top: 0
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        top: '15%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: times,
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: { color: '#9CA3AF', fontSize: 10, rotate: 45 },
        splitLine: { lineStyle: { color: '#1F2937' } }
      },
      yAxis: [
        {
          type: 'value',
          name: '结晶压力 (MPa)',
          nameTextStyle: { color: '#D64545' },
          axisLine: { lineStyle: { color: '#374151' } },
          axisLabel: { color: '#9CA3AF' },
          splitLine: { lineStyle: { color: '#1F2937' } }
        },
        {
          type: 'value',
          name: '运移速度 (10⁻⁶ m/s)',
          position: 'right',
          nameTextStyle: { color: '#4A7C59' },
          axisLine: { lineStyle: { color: '#374151' } },
          axisLabel: { color: '#9CA3AF' },
          splitLine: { show: false }
        }
      ],
      series: [
        {
          name: '结晶压力',
          type: 'bar',
          yAxisIndex: 0,
          data: sortedData.map(d => d.crystallizationPressure),
          itemStyle: {
            color: (params: { dataIndex: number }) => {
              const value = sortedData[params.dataIndex]?.crystallizationPressure ?? 0;
              if (value >= 5.0) return '#D64545';
              if (value >= 2.0) return '#E8A838';
              if (value >= 1.0) return '#FBBF24';
              return '#52B788';
            }
          }
        },
        {
          name: '运移速度',
          type: 'line',
          yAxisIndex: 1,
          data: sortedData.map(d => {
            const v = d.migrationVelocity;
            return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 1e6;
          }),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2 },
          itemStyle: { color: '#4A7C59' }
        },
        {
          name: '预测压力',
          type: 'line',
          yAxisIndex: 0,
          data: sortedData.map(d => d.predictedCrystallizationPressure),
          smooth: true,
          symbol: 'dashed',
          symbolSize: 4,
          lineStyle: { width: 2, type: 'dashed' },
          itemStyle: { color: '#A78BFA' }
        }
      ],
      visualMap: {
        show: false,
        pieces: [
          { gte: 5.0, color: '#D64545' },
          { gte: 2.0, lt: 5.0, color: '#E8A838' },
          { gte: 1.0, lt: 2.0, color: '#FBBF24' },
          { lt: 1.0, color: '#52B788' }
        ]
      }
    };
  }, [data]);
  
  return (
    <ReactECharts
      option={option}
      style={{ height: `${height}px`, width: '100%' }}
      opts={{ renderer: 'canvas' }}
    />
  );
}
