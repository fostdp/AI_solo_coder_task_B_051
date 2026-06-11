import { Routes, Route } from 'react-router-dom'
import Layout from '@/components/Layout'
import Dashboard from '@/pages/Dashboard'
import Monitor from '@/pages/Monitor'
import Analysis from '@/pages/Analysis'
import AlarmCenter from '@/pages/AlarmCenter'
import DeviceManager from '@/pages/DeviceManager'
import DataCenter from '@/pages/DataCenter'

function App(): JSX.Element {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/monitor" element={<Monitor />} />
        <Route path="/analysis" element={<Analysis />} />
        <Route path="/alarm" element={<AlarmCenter />} />
        <Route path="/device" element={<DeviceManager />} />
        <Route path="/data" element={<DataCenter />} />
      </Routes>
    </Layout>
  )
}

export default App
