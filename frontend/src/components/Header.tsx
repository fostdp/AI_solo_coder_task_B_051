import { Bell, Settings, User, Clock } from 'lucide-react'
import { useEffect, useState } from 'react'
import dayjs from 'dayjs'
import useAlarmStore from '@/store/useAlarmStore'

interface HeaderProps {
  title: string
  onToggleSidebar?: () => void
}

function Header({ title }: HeaderProps): JSX.Element {
  const [currentTime, setCurrentTime] = useState<string>(dayjs().format('YYYY-MM-DD HH:mm:ss'))
  const { getPendingCount } = useAlarmStore()
  const pendingCount = getPendingCount()

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(dayjs().format('YYYY-MM-DD HH:mm:ss'))
    }, 1000)

    return () => clearInterval(timer)
  }, [])

  return (
    <header className="h-16 bg-dark-200/80 backdrop-blur-sm border-b border-gray-700/50 px-6 flex items-center justify-between">
      <div className="flex items-center gap-4">
        <h1 className="text-xl font-bold font-serif-sc text-white">{title}</h1>
      </div>

      <div className="flex items-center gap-6">
        <div className="flex items-center gap-2 text-gray-400">
          <Clock size={18} />
          <span className="font-mono text-sm">{currentTime}</span>
        </div>

        <button className="relative p-2 text-gray-400 hover:text-white transition-colors">
          <Bell size={20} />
          {pendingCount > 0 && (
            <span className="absolute top-0 right-0 w-5 h-5 bg-alert-red text-white text-xs rounded-full flex items-center justify-center font-bold">
              {pendingCount > 99 ? '99+' : pendingCount}
            </span>
          )}
        </button>

        <button className="p-2 text-gray-400 hover:text-white transition-colors">
          <Settings size={20} />
        </button>

        <div className="flex items-center gap-3 pl-4 border-l border-gray-700">
          <div className="w-8 h-8 rounded-full bg-bronze-green flex items-center justify-center">
            <User size={16} className="text-white" />
          </div>
          <span className="text-white text-sm font-medium">管理员</span>
        </div>
      </div>
    </header>
  )
}

export default Header
