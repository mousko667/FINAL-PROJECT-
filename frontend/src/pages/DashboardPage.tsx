import { useTranslation } from 'react-i18next'

export default function DashboardPage() {
  const { t } = useTranslation()
  return <div className="text-xl font-semibold text-gray-800">{t('dashboard.title')}</div>
}
