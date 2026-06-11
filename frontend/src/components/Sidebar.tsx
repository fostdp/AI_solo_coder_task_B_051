import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  Activity,
  LineChart,
  AlertTriangle,
  Server,
  Database
} from 'lucide-react'

interface SidebarProps {
  collapsed?: boolean
}

const menuItems = [
  { path: '/', label: '首页总览', icon: LayoutDashboard },
  { path: '/monitor', label: '实时监测', icon: Activity },
  { path: '/analysis', label: '盐害分析', icon: LineChart },
  { path: '/alarm', label: '告警中心', icon: AlertTriangle },
  { path: '/device', label: '设备管理', icon: Server },
  { path: '/data', label: '数据中心', icon: Database }
]

function Sidebar({ collapsed = false }: SidebarProps): JSX.Element {
  return (
    <aside
      className={`h-full bg-dark-200 border-r border-gray-700/50 transition-all duration-300 ${
        collapsed ? 'w-16' : 'w-64'
      }`}
    >
      <div className="h-16 flex items-center justify-center border-b border-gray-700/50">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-ochre to-bronze-green flex items-center justify-center">
            <span className="text-white font-bold font-serif-sc text-lg">盐</span>
          </div>
          {!collapsed && (
            <span className="text-white font-bold font-serif-sc text-lg whitespace-nowrap">
              盐害监测系统
            </span>
          )}
        </div>
      </div>

      <nav className="p-4 space-y-2">
        {menuItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.path === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 ${
                isActive
                  ? 'bg-bronze-green/20 text-bronze-green border-l-2 border-bronze-green'
                  : 'text-gray-400 hover:text-white hover:bg-dark-300/50'
              }`
            }
          >
            <item.icon size={20} />
            {!collapsed && <span className="font-medium whitespace-nowrap">{item.label}</span>}
          </NavLink>
        ))}
      </nav>

      {!collapsed && (
        <div className="absolute bottom-4 left-4 right-4">
          <div className="glass-card p-4">
            <p className="text-gray-400 text-sm mb-2">系统状态</p>
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-normal-green animate-pulse" />
              <span className="text-normal-green text-sm">运行正常</span>
            </div>
          </div>
        </div>
      )}
    </aside>
  )
}

export default Sidebar
