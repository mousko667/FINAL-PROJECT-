import { useTranslation } from 'react-i18next'
import SupplierRegisterPage from './SupplierRegisterPage'

/**
 * Public registration is for SUPPLIERS ONLY.
 * All staff accounts (AA, validators, DAF, Admin) are created exclusively
 * by the Administrator via the User Management console.
 * There is no public self-registration for internal roles.
 */
export default function RegisterPage() {
  const { t } = useTranslation()

  return (
    <div className="min-h-screen bg-ground">
      <div className="max-w-3xl mx-auto pt-8 px-4 pb-2">
        <div className="bg-primary/10 border border-blue-200 rounded-xl px-4 py-3 text-sm text-blue-800">
          <p className="font-semibold">{t('register.supplierOnly.title', 'Supplier portal registration')}</p>
          <p className="mt-0.5 text-primary">
            {t('register.supplierOnly.note', 'This form is for external suppliers only. OCT staff accounts are created by the System Administrator — contact your IT department for access.')}
          </p>
        </div>
      </div>
      <SupplierRegisterPage />
    </div>
  )
}
