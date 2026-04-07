import { useTranslation } from 'react-i18next'
export default function InvoiceDetailPage() { const { t } = useTranslation(); return <div>{t('invoice.details')}</div> }
