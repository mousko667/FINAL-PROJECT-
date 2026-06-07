import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import apiClient from '@/services/apiClient'

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const [newPassword, setNewPassword] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState('')
  const token = searchParams.get('token') ?? ''

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await apiClient.post('/auth/reset-password', { token, newPassword })
      setSubmitted(true)
    } catch {
      setError('Invalid or expired reset link.')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <form onSubmit={submit} className="w-full max-w-md bg-white border rounded-xl p-6 space-y-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Choose a new password</h1>
          <p className="text-sm text-gray-500 mt-1">Use at least 8 characters.</p>
        </div>
        {submitted ? (
          <div className="text-sm text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-lg p-3">
            Password reset successful. You can now sign in.
          </div>
        ) : (
          <>
            <label className="block text-sm font-medium text-gray-700">
              New password
              <input
                type="password"
                required
                minLength={8}
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                className="mt-1 w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </label>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <button disabled={!token} className="w-full bg-primary text-primary-foreground rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50">
              Reset password
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
