import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AdminLogin from './AdminLogin'
import AdminPanel from './AdminPanel'

function AdminPage() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const navigate = useNavigate()

  const handleLogin = () => {
    setIsAuthenticated(true)
  }

  const handleLogout = () => {
    setIsAuthenticated(false)
    navigate('/')
  }

  return (
    <>
      {!isAuthenticated ? (
        <AdminLogin onLogin={handleLogin} />
      ) : (
        <AdminPanel onLogout={handleLogout} />
      )}
    </>
  )
}

export default AdminPage
