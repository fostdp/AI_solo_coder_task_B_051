import { LucideIcon, TrendingUp, TrendingDown } from 'lucide-react'
import { ReactNode } from 'react'

interface StatCardProps {
  title: string
  value: string | number
  icon: LucideIcon
  trend?: 'up' | 'down' | 'neutral'
  trendValue?: string
  description?: string
  color?: 'green' | 'red' | 'yellow' | 'blue' | 'ochre' | 'bronze'
  suffix?: string
  prefix?: string
  children?: ReactNode
}

const colorClasses: Record<string, string> = {
  green: 'from-normal-green/20 to-normal-green/5 text-normal-green border-normal-green/30',
  red: 'from-alert-red/20 to-alert-red/5 text-alert-red border-alert-red/30',
  yellow: 'from-warning-yellow/20 to-warning-yellow/5 text-warning-yellow border-warning-yellow/30',
  blue: 'from-blue-500/20 to-blue-500/5 text-blue-400 border-blue-500/30',
  ochre: 'from-ochre/20 to-ochre/5 text-ochre border-ochre/30',
  bronze: 'from-bronze-green/20 to-bronze-green/5 text-bronze-green border-bronze-green/30'
}

const iconBgClasses: Record<string, string> = {
  green: 'bg-normal-green/10 text-normal-green',
  red: 'bg-alert-red/10 text-alert-red',
  yellow: 'bg-warning-yellow/10 text-warning-yellow',
  blue: 'bg-blue-500/10 text-blue-400',
  ochre: 'bg-ochre/10 text-ochre',
  bronze: 'bg-bronze-green/10 text-bronze-green'
}

function StatCard({
  title,
  value,
  icon: Icon,
  trend,
  trendValue,
  description,
  color = 'bronze',
  suffix,
  prefix
}: StatCardProps): JSX.Element {
  return (
    <div className={`glass-card p-5 bg-gradient-to-br ${colorClasses[color]}`}>
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-gray-400 text-sm font-medium mb-2">{title}</p>
          <p className="text-3xl font-bold text-white mb-2">
            {prefix && <span className="text-lg mr-1">{prefix}</span>}
            {value}
            {suffix && <span className="text-lg ml-1">{suffix}</span>}
          </p>
          <div className="flex items-center gap-2">
            {trend && (
              <div
                className={`flex items-center gap-1 text-sm ${
                  trend === 'up' ? 'text-normal-green' : trend === 'down' ? 'text-alert-red' : 'text-gray-400'
                }`}
              >
                {trend === 'up' ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
                <span>{trendValue}</span>
              </div>
            )}
            {description && (
              <span className="text-gray-500 text-sm">{description}</span>
            )}
          </div>
        </div>
        <div className={`p-3 rounded-xl ${iconBgClasses[color]}`}>
          <Icon size={24} />
        </div>
      </div>
    </div>
  )
}

export default StatCard
