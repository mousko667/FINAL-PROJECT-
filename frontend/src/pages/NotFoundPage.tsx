import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

export default function NotFoundPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return (
    <div className="min-h-screen flex flex-col items-center justify-center">
      <h1 className="text-6xl font-bold num text-hairline-strong mb-4">404</h1>
      <p className="text-ink-soft mb-6">{t('app.pageNotFound')}</p>
      <button onClick={() => navigate('/')} className="text-primary underline text-sm">
        {t('app.backHome')}
      </button>
    </div>
  )
}
