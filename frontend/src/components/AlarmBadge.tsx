import { AlertCircle, AlertTriangle, Info, XCircle } from 'lucide-react'
import type { AlarmLevel } from '@/types'

interface AlarmBadgeProps {
  level: AlarmLevel
  showText?: boolean
  size?: 'sm' | 'md' | 'lg'
}

const levelConfig: Record<AlarmLevel, {
  icon: typeof AlertCircle
  bgColor: string
  textColor: string
  label: string
  dotColor: string
}> = {
  INFO: {
    icon: Info,
    bgColor: 'bg-blue-500/10',
    textColor: 'text-blue-400',
    label: '信息',
    dotColor: 'bg-blue-400'
  },
  WARNING: {
    icon: AlertTriangle,
    bgColor: 'bg-warning-yellow/10',
    textColor: 'text-warning-yellow',
    label: '警告',
    dotColor: 'bg-warning-yellow'
  },
  ERROR: {
    icon: AlertCircle,
    bgColor: 'bg-alert-red/10',
    textColor: 'text-alert-red',
    label: '错误',
    dotColor: 'bg-alert-red'
  },
  CRITICAL: {
    icon: XCircle,
    bgColor: 'bg-red-600/10',
    textColor: 'text-red-400',
    label: '严重',
    dotColor: 'bg-red-400'
  }
}

const sizeClasses = {
  sm: {
    icon: 14,
    padding: 'px-2 py-0.5',
    text: 'text-xs'
  },
  md: {
    icon: 16,
    padding: 'px-2.5 py-1',
    text: 'text-sm'
  },
  lg: {
    icon: 18,
    padding: 'px-3 py-1.5',
    text: 'text-base'
  }
}

function AlarmBadge({ level, showText = true, size = 'md' }: AlarmBadgeProps): JSX.Element {
  const config = levelConfig[level]
  const sizes = sizeClasses[size]
  const Icon = config.icon

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full ${config.bgColor} ${config.textColor} ${sizes.padding} font-medium`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${config.dotColor} ${size === 'sm' ? 'animate-pulse' : ''}`} />
      <Icon size={sizes.icon} />
      {showText && <span className={sizes.text}>{config.label}</span>}
    </span>
  )
}

export default AlarmBadge
