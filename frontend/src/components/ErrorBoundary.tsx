import { Component, type ReactNode } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'
// AUDIT-044: a class component cannot use the useTranslation hook. Import the bare i18next
// instance (like lib/format.ts), NOT '@/i18n' — importing the init module breaks tests that
// mock react-i18next (PROB-144).
import i18n from 'i18next'

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
        <div className="flex flex-col items-center justify-center h-64 gap-4 text-ink-soft">
          <AlertTriangle className="w-10 h-10 text-warn" />
          <div className="text-center">
            <p className="font-semibold text-ink-soft">
              {this.props.fallbackTitle ?? i18n.t('errorBoundary.title')}
            </p>
            <p className="text-sm text-ink-faint mt-1">
              {this.state.error?.message ?? i18n.t('errorBoundary.message')}
            </p>
          </div>
          <button
            onClick={() => this.setState({ hasError: false, error: null })}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90"
          >
            <RefreshCw className="w-4 h-4" />
            {i18n.t('errorBoundary.retry')}
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
