import { BrowserRouter as Router, Routes, Route, Link, useNavigate } from 'react-router-dom'
import { useRef, useState } from 'react'
import GuestPage from './components/GuestPage'
import AdminLogin from './components/AdminLogin'
import AdminPanel from './components/AdminPanel'

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<GuestLayout />} />
        <Route path="/admin" element={<AdminLayout />} />
      </Routes>
    </Router>
  )
}

function GuestLayout() {
  const navigate = useNavigate()
  const lastTapRef = useRef(0)

  const handleAdminDotTap = () => {
    const now = Date.now()
    if (now - lastTapRef.current < 500) {
      lastTapRef.current = 0
      navigate('/admin')
    } else {
      lastTapRef.current = now
    }
  }

  return (
    <div className="container">
      <div
        className="admin-dot"
        onClick={handleAdminDotTap}
        title="Tap twice to open admin"
      ></div>
      <header>
        <div className="header-content">
          <div>
            <h1>📸 PhotoShare</h1>
            <p>Fa o poza si impartaseste-o cu lumea &lt;3</p>
          </div>
        </div>
      </header>
      <main>
        <GuestPage />
      </main>
    </div>
  )
}

function AdminLayout() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const navigate = useNavigate()

  const handleLogout = () => {
    setIsAuthenticated(false)
    navigate('/')
  }

  if (!isAuthenticated) {
    return (
      <>
        <Link to="/" className="login-back-link">
          ← Back to Gallery
        </Link>
        <AdminLogin onLogin={() => setIsAuthenticated(true)} />
      </>
    )
  }

  return (
    <div className="admin-container">
      <header>
        <div className="header-content">
          <Link to="/" className="back-link">
            ← Back to Gallery
          </Link>
        </div>
      </header>
      <main>
        <AdminPanel onLogout={handleLogout} />
      </main>
    </div>
  )
}

export default App
