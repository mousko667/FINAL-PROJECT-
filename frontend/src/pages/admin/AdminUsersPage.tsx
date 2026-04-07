import { useTranslation } from 'react-i18next'
export default function AdminUsersPage() { const { t } = useTranslation(); return <div>{t('admin.users.title')}</div> }
