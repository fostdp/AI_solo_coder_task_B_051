import ReactECharts from 'echarts-for-react';
import { useMemo } from 'react';
import { SaltData } from '@/types';

interface SaltIonChartProps {
  data: SaltData[];
  height?: number;
}

export default function SaltIonChart({ data, height = 300 }: SaltIonChartProps) {
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
        textStyle: { color: '#fff' },
        axisPointer: {
          type: 'cross',
          label: { backgroundColor: '#4A7C59' }
        }
      },
      legend: {
        data: ['Na⁺', 'Ca²⁺', 'SO₄²⁻', 'Cl⁻', '盐分总量'],
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
          name: '浓度 (mg/cm²)',
          nameTextStyle: { color: '#9CA3AF' },
          axisLine: { lineStyle: { color: '#374151' } },
          axisLabel: { color: '#9CA3AF' },
          splitLine: { lineStyle: { color: '#1F2937' } }
        }
      ],
      series: [
        {
          name: 'Na⁺',
          type: 'line',
          data: sortedData.map(d => d.naPlus),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2 },
          itemStyle: { color: '#60A5FA' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(96, 165, 250, 0.3)' },
                { offset: 1, color: 'rgba(96, 165, 250, 0)' }
              ]
            }
          }
        },
        {
          name: 'Ca²⁺',
          type: 'line',
          data: sortedData.map(d => d.ca2Plus),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2 },
          itemStyle: { color: '#34D399' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(52, 211, 153, 0.3)' },
                { offset: 1, color: 'rgba(52, 211, 153, 0)' }
              ]
            }
          }
        },
        {
          name: 'SO₄²⁻',
          type: 'line',
          data: sortedData.map(d => d.so42Minus),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2 },
          itemStyle: { color: '#FBBF24' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(251, 191, 36, 0.3)' },
                { offset: 1, color: 'rgba(251, 191, 36, 0)' }
              ]
            }
          }
        },
        {
          name: 'Cl⁻',
          type: 'line',
          data: sortedData.map(d => d.clMinus),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2 },
          itemStyle: { color: '#F472B6' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(244, 114, 182, 0.3)' },
                { offset: 1, color: 'rgba(244, 114, 182, 0)' }
              ]
            }
          }
        },
        {
          name: '盐分总量',
          type: 'line',
          data: sortedData.map(d => d.totalSalt),
          smooth: true,
          symbol: 'diamond',
          symbolSize: 8,
          lineStyle: { width: 3, color: '#D64545' },
          itemStyle: { color: '#D64545' },
          markLine: {
            silent: true,
            data: [{ yAxis: 5.0, label: { formatter: '告警阈值 5.0' }, lineStyle: { color: '#D64545', type: 'dashed' } }]
          }
        }
      ]
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
