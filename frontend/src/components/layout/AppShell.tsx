import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'
import { SidebarDrawerProvider } from './SidebarDrawerContext'

export default function AppShell() {
  return (
    <SidebarDrawerProvider>
      <div className="flex h-screen bg-ground">
        <Sidebar />
        {/* min-w-0 lets `main` shrink below its content width instead of
            overflowing the viewport (AUDIT-019). */}
        <div className="flex flex-col flex-1 overflow-hidden min-w-0">
          <Header />
          <main className="flex-1 overflow-y-auto p-4 md:p-6 bg-page-tint">
            <Outlet />
          </main>
        </div>
      </div>
    </SidebarDrawerProvider>
  )
}
