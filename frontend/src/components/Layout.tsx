import { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'

interface LayoutProps {
  children: ReactNode
}

const pageTitles: Record<string, string> = {
  '/': '首页总览',
  '/monitor': '实时监测',
  '/analysis': '盐害分析',
  '/alarm': '告警中心',
  '/device': '设备管理',
  '/data': '数据中心'
}

function Layout({ children }: LayoutProps): JSX.Element {
  const location = useLocation()
  const title = pageTitles[location.pathname] || '古墓葬盐害监测系统'

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-dark-500">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header title={title} />
        <main className="flex-1 overflow-auto p-6">
          {children}
        </main>
      </div>
    </div>
  )
}

export default Layout
