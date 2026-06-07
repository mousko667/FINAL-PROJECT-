import { Component, type ReactNode } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'

interface Props {
  children: ReactNode
  fallbackTitle?: string
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: { componentStack: string }) {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center h-64 gap-4 text-gray-500">
          <AlertTriangle className="w-10 h-10 text-amber-500" />
          <div className="text-center">
            <p className="font-semibold text-gray-700">
              {this.props.fallbackTitle ?? 'Une erreur est survenue sur cette page'}
            </p>
            <p className="text-sm text-gray-400 mt-1">
              {this.state.error?.message ?? 'Erreur inattendue'}
            </p>
          </div>
          <button
            onClick={() => this.setState({ hasError: false, error: null })}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90"
          >
            <RefreshCw className="w-4 h-4" />
            Réessayer
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
