import { useState } from 'react'
import '../styles/AdminLogin.css'

const ADMIN_PASSWORD = '1234'

interface AdminLoginProps {
  onLogin: () => void
}

function AdminLogin({ onLogin }: AdminLoginProps) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setIsSubmitting(true)

    // Simulate delay for security feel
    setTimeout(() => {
      if (password === ADMIN_PASSWORD) {
        onLogin()
      } else {
        setError('Invalid password')
        setPassword('')
      }
      setIsSubmitting(false)
    }, 300)
  }

  return (
    <div className="login-container">
      <div className="login-box">
        <h2>🔐 Admin Access</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="password">Enter Admin Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value)
                setError('')
              }}
              placeholder="Enter password"
              disabled={isSubmitting}
              autoFocus
            />
          </div>
          {error && <div className="error-message">{error}</div>}
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting ? '🔓 Verifying...' : '🔓 Login'}
          </button>
        </form>
      </div>
    </div>
  )
}

export default AdminLogin
