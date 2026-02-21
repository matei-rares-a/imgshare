import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom'
import GuestPage from './components/GuestPage'
import AdminPage from './components/AdminPage'

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
  return (
    <div className="container">
      <Link to="/admin" className="admin-dot"></Link>
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
        <AdminPage />
      </main>
    </div>
  )
}

export default App
