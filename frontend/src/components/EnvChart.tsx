import ReactECharts from 'echarts-for-react';
import { useMemo } from 'react';
import { EnvData } from '@/types';

interface EnvChartProps {
  data: EnvData[];
  height?: number;
}

export default function EnvChart({ data, height = 300 }: EnvChartProps) {
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
        data: ['温度', '相对湿度', '风速'],
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
          name: '温度 (℃)',
          position: 'left',
          nameTextStyle: { color: '#F59E0B' },
          axisLine: { lineStyle: { color: '#374151' } },
          axisLabel: { color: '#9CA3AF', formatter: '{value}℃' },
          splitLine: { lineStyle: { color: '#1F2937' } }
        },
        {
          type: 'value',
          name: '湿度 (%)',
          position: 'right',
          nameTextStyle: { color: '#3B82F6' },
          axisLine: { lineStyle: { color: '#374151' } },
          axisLabel: { color: '#9CA3AF', formatter: '{value}%' },
          splitLine: { show: false }
        },
        {
          type: 'value',
          name: '风速 (m/s)',
          position: 'right',
          offset: 60,
          nameTextStyle: { color: '#10B981' },
          axisLine: { lineStyle: { color: '#374151' } },
          axisLabel: { color: '#9CA3AF', formatter: '{value}m/s' },
          splitLine: { show: false }
        }
      ],
      series: [
        {
          name: '温度',
          type: 'line',
          yAxisIndex: 0,
          data: sortedData.map(d => d.temperature),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2 },
          itemStyle: { color: '#F59E0B' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(245, 158, 11, 0.2)' },
                { offset: 1, color: 'rgba(245, 158, 11, 0)' }
              ]
            }
          }
        },
        {
          name: '相对湿度',
          type: 'line',
          yAxisIndex: 1,
          data: sortedData.map(d => d.humidity),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2 },
          itemStyle: { color: '#3B82F6' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(59, 130, 246, 0.2)' },
                { offset: 1, color: 'rgba(59, 130, 246, 0)' }
              ]
            }
          },
          markLine: {
            silent: true,
            data: [{ yAxis: 75, label: { formatter: '告警阈值 75%' }, lineStyle: { color: '#D64545', type: 'dashed' } }]
          }
        },
        {
          name: '风速',
          type: 'line',
          yAxisIndex: 2,
          data: sortedData.map(d => d.windSpeed),
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { width: 2 },
          itemStyle: { color: '#10B981' }
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
