import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await apiClient.post('/auth/forgot-password', { email })
      setSubmitted(true)
    } catch {
      setError('Unable to request password reset. Please try again.')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <form onSubmit={submit} className="w-full max-w-md bg-white border rounded-xl p-6 space-y-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Reset password</h1>
          <p className="text-sm text-gray-500 mt-1">Enter your account email to receive a reset link.</p>
        </div>
        {submitted ? (
          <div className="text-sm text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-lg p-3">
            If an account exists for this email, a reset link has been sent.
          </div>
        ) : (
          <>
            <label className="block text-sm font-medium text-gray-700">
              Email
              <input
                type="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="mt-1 w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </label>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <button className="w-full bg-primary text-primary-foreground rounded-lg px-4 py-2 text-sm font-medium">
              Send reset link
            </button>
          </>
        )}
        <Link to="/login" className="block text-center text-sm text-primary hover:underline">
          Back to login
        </Link>
      </form>
    </div>
  )
}
